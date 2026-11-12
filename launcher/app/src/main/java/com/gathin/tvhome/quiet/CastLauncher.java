package com.gathin.tvhome.quiet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Native Windows PC screen-casting entry point.
 *
 * Uses the Fire TV's existing Miracast receiver — no Moonlight, Sunshine, custom
 * server or new mirroring protocol. Candidate activities are probed in order with
 * PackageManager; nothing is hardcoded as final. The first resolvable candidate
 * wins, otherwise we fall back to the relevant system settings screen.
 *
 * <p>Candidates must be verified on the Hisense 55E6QF (Fire OS 8.1.8.0) with
 * non-destructive ADB inspection, e.g.:
 * <pre>
 *   adb shell cmd package query-activities -a android.settings.CAST_SETTINGS
 *   adb shell dumpsys package com.amazon.tv.settings | grep -i -A2 -B2 mirror
 *   adb shell dumpsys package com.amazon.tv.launcher | grep -i -A2 -B2 display
 * </pre>
 * Update CANDIDATES if inspection finds the true receiver component.
 */
public final class CastLauncher {
    private CastLauncher() {}

    /** Instruction shown while the receiver is available. */
    public static final String WIN_K_INSTRUCTION = "On your Windows PC, press Win + K to connect.";

    /** Ordered explicit candidates — see MirroringPicker (pure, tested). */
    static final List<String> CANDIDATES = MirroringPicker.CANDIDATES;

    /** Implicit intents tried when no explicit candidate resolves. */
    static final List<String> IMPLICIT_ACTIONS = MirroringPicker.FALLBACK_ACTIONS;

    /**
     * Pure, unit-testable picker: returns the first candidate present in
     * {@code available}, or -1 when none matches (caller uses the settings fallback).
     */
    public static int pickBest(List<String> candidates, java.util.Set<String> available) {
        return MirroringPicker.pickBest(candidates, available);
    }

    /** Resolve the best launch intent for screen casting on this TV. Never null. */
    public static Intent resolve(Context context) {
        PackageManager pm = context.getPackageManager();
        List<String> available = new ArrayList<>();
        for (String flat : CANDIDATES) {
            ComponentName cn = ComponentName.unflattenFromString(flat);
            if (cn == null) continue;
            try {
                pm.getPackageInfo(cn.getPackageName(), 0);
                // Package exists; check the activity itself resolves.
                Intent probe = new Intent().setComponent(cn);
                if (pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    available.add(flat);
                }
            } catch (Exception ignored) {
                // Package absent — try the next candidate.
            }
        }
        java.util.Set<String> set = new java.util.HashSet<>(available);
        int best = pickBest(CANDIDATES, set);
        if (best >= 0) {
            return new Intent().setComponent(
                    ComponentName.unflattenFromString(CANDIDATES.get(best)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        for (String action : IMPLICIT_ACTIONS) {
            Intent intent = new Intent(action);
            try {
                if (pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
            } catch (Exception ignored) {
                // Continue to the next fallback.
            }
        }
        return new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
