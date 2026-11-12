package com.gathin.tvhome.quiet;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Pure, JVM-testable picker for the native Miracast receiver.
 * Android PackageManager probing lives in CastLauncher; this class only orders
 * candidates so the policy is unit-tested without Android stubs.
 */
public final class MirroringPicker {
    private MirroringPicker() {}

    /** Ordered explicit candidates (package/class flattened with '/'). */
    public static final List<String> CANDIDATES = Arrays.asList(
            "com.amazon.tv.settings/.DisplaySettingsActivity",
            "com.amazon.tv.settings/.display.DisplaySettingsActivity",
            "com.amazon.tv.launcher/.ui.DisplayMirroringActivity",
            "com.amazon.firetv.settings/.display.DisplayMirroringActivity",
            "com.amazon.tv.settings/.MirroringSettingsActivity");

    /** Implicit fallback actions, most specific first. */
    public static final List<String> FALLBACK_ACTIONS = Arrays.asList(
            "android.settings.CAST_SETTINGS",
            "android.settings.WIFI_DISPLAY_SETTINGS",
            "android.settings.DISPLAY_SETTINGS",
            "android.settings.SETTINGS");

    /**
     * Returns the index into CANDIDATES of the first entry present in
     * {@code available}, or -1 when none matches (use the settings fallback).
     */
    public static int pickBest(List<String> candidates, Set<String> available) {
        if (candidates == null || available == null) return -1;
        for (int i = 0; i < candidates.size(); i++) {
            if (available.contains(candidates.get(i))) return i;
        }
        return -1;
    }

    /** First fallback action present in {@code available}, or the last resort. */
    public static String pickFallback(List<String> actions, Set<String> available) {
        if (actions == null || actions.isEmpty()) return "android.settings.SETTINGS";
        if (available != null) {
            for (String a : actions) if (available.contains(a)) return a;
        }
        return actions.get(actions.size() - 1);
    }
}
