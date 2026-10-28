package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fork compatibility (cleandavystv): the Fire OS Home redirect from the launcher this fork replaces on Davy's
 * Hisense Fire TV, kept under the same component name so an in-place update leaves the TV's existing
 * enabled_accessibility_services entry pointing at a working service. Nothing here turns it on: it runs only
 * where it was already enabled, and an update never changes that setting.
 *
 * Fire OS never passes the Home key to accessibility services, so this watches windows instead: when Amazon's
 * home screen comes up, this launcher opens over it. Amazon's home screen also flashes up on the way into
 * Settings, right after a window from Amazon's settings app, so a home screen that follows a settings window
 * within SETTINGS_GRACE_MS is left alone. Also opens the launcher once at boot.
 */
public class HomeRedirectService extends AccessibilityService {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "HomeActivity";
    private static final String[] SETTINGS_APPS = {"com.amazon.tv.settings", "com.amazon.tv.quicksettings"};
    private static final long SETTINGS_GRACE_MS = 2000;     // measured: settings window -> home flash ~200 ms
    private static final long BOOT_WINDOW_MS = 3 * 60_000;  // service connecting this soon after boot = boot

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = this::openLauncher;
    private long settingsSeenAt = -SETTINGS_GRACE_MS;

    @Override
    protected void onServiceConnected() {
        if (SystemClock.elapsedRealtime() < BOOT_WINDOW_MS) openLauncher();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName(), cls = event.getClassName();
        if (pkg == null || cls == null) return;
        long now = SystemClock.uptimeMillis();
        for (String app : SETTINGS_APPS) if (pkg.toString().startsWith(app)) settingsSeenAt = now;
        handler.removeCallbacks(redirect);
        if (!AMAZON_LAUNCHER.contentEquals(pkg) || !cls.toString().contains(AMAZON_HOME)) return;
        if (now - settingsSeenAt < SETTINGS_GRACE_MS) return;
        handler.post(redirect);
    }

    @Override
    public void onInterrupt() {
    }

    /** CATEGORY_HOME, as HomeKeyService sends: MainActivity treats it as a Home press and shows Home. */
    private void openLauncher() {
        startActivity(new Intent(this, MainActivity.class).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }
}
