# Quiet Machine 2.0 (feat/quiet-machine-2)

Single Home UI. No Classic toggle. Version 3.2 (versionCode 11), applicationId
`com.gathin.tvhome` unchanged for in-place updates.

## Architecture

Quiet Machine is the only user-facing interface. Classic layouts render nothing:
`MainActivity` keeps `AppAdapter`/`InputSources` as the launch/input data path,
hosts them in an invisible, non-focusable surface, and always shows
`QuietMachineView`. The stale `quiet_classic` preference is cleared on start.

New, independent modules:

- `quiet/QuietDesign` — glass/aluminum/slate tokens (translucent fills, hairline
  strokes, 12–18dp radii, warm accent only for focus/active). No blur.
- `quiet/MirroringPicker` (pure, tested) + `quiet/CastLauncher` (PackageManager
  probe) — native Miracast entry point.
- `govee/GoveeApi` (HttpURLConnection, no new dependencies), `govee/GoveeModels`
  (capability-aware devices), `govee/GoveeCredentialStore` (KeyStore AES/GCM),
  `govee/GoveeRepository` (cloud-only transport, debounced control, recent-cache
  fallback), `govee/GoveePayloads` (pure, tested builders/constants).
- `QuietMachineView` screens: Home, Library, Search, **Lights**, Settings
  (Control). Tiles: Watch, **PC Cast**, Lights + configured apps.

Preserved: `HomeRedirectService` component name, `PhotoDream`, accessibility and
screensaver compatibility, return-to-Amazon path (Settings → TV home), saved
preferences across updates (same SharedPreferences names).

## Design (glass, aluminum, slate)

- Fewer boxes: resting surfaces use 12dp translucent fills; focus uses a 2dp
  accent ring + 14dp radius + 4% scale (not colour alone).
- Frosted glass = translucent fill + hairline stroke (one layer). Brushed
  aluminum = vertical two-stop gradient (one layer). No realtime blur, no heavy
  shadows, no neon, no gratuitous gradients.
- Charcoal/graphite/silver/slate tones; one warm accent (#F36B21) for focus.
- Motion: 100–140ms scale/fade; `quiet_reduce_motion` preference shortens and
  flattens motion. Respect D-pad predictability (rail ↔ body links kept).
- Photos/artwork stay prominent; text stays legible at TV distance.

## PC screen casting (native Miracast)

Home → **PC Cast** tile (also Settings → PC Cast) resolves in order:

1. Explicit Fire OS candidates via PackageManager (`MirroringPicker.CANDIDATES`
   — Display/Mirroring activities under `com.amazon.tv.settings`,
   `com.amazon.tv.launcher`, `com.amazon.firetv.settings`).
2. Implicit fallbacks: `CAST_SETTINGS` → `WIFI_DISPLAY_SETTINGS` →
   `DISPLAY_SETTINGS` → `SETTINGS`.
3. Last resort: system Settings.

Nothing is hardcoded as final: candidates are probed with
`PackageManager.resolveActivity`, never assumed. Success shows
"On your Windows PC, press Win + K to connect." Mirroring itself is the Fire
OS receiver — no Moonlight/Sunshine/custom server. Return navigation is the
system Back/Home; the launcher never interrupts an active session (no redirect,
screensaver, or background work claims focus mid-cast).

Verify on the TV (needs physical approval) with non-destructive ADB first:

```sh
adb shell cmd package query-activities -a android.settings.CAST_SETTINGS
adb shell dumpsys package com.amazon.tv.settings | grep -i -B2 -A2 mirror
adb shell dumpsys package com.amazon.tv.launcher | grep -i -B2 -A2 display
```

Then test connect / disconnect / reconnect from a Windows 11 PC (Win+K).

## Govee lighting (cloud only)

Docs: https://developer.govee.com — base `https://openapi.api.govee.com`,
header `Govee-API-Key`, endpoints `GET /router/api/v1/user/devices`,
`POST /router/api/v1/device/state|control`, `POST /router/api/v1/device/scenes`.
LAN control is intentionally disabled: multicast discovery is unreliable on the
managed residential Wi-Fi, and cloud operation must not depend on it.

Lights screen:

- API-key row (save/remove), rotation warning, manual Refresh.
- Device list from real discovery: friendly name, SKU, online status, and only
  the capabilities each device reports (power, brightness, color temp, RGB,
  scenes). Unsupported controls are never shown. TV backlight (H61/H65/H66/H70
  heuristic + name match) is surfaced first where present.
- Master all-lights power, per-device On/Off (urgent POST), brightness ±10
  (debounced to the 1s control floor), color-temp presets (2700/4000/5000/6500K),
  warm-white RGB preset. Scenes list where the API reports them.
- Failures show a message + Retry; a recent cache (15 min) covers transient
  errors. All network work is off the UI thread.

Security rules (enforced):

- The supplied development key is compromised and temporary. It appears nowhere
  in source, docs, tests, screenshots, logs, or build artifacts.
- Stored only via Android KeyStore AES/GCM in private prefs; rotated from the
  Lights screen without rebuilding. Rotate the exposed key in the Govee app
  before daily use.

DreamView note: no DreamView/sync controls are shown unless the account's
`capabilities[]` for that device reports a synchronization mode. Camera-based
music/DreamView modes are not claimed without API evidence.

## Debloat

See `docs/debloat-fireos-8-audit.md`. Read-only audit; nothing is disabled
without the explicit package list being shown and approved. Stock Home,
settings, Wi-Fi/Bluetooth, HDMI/input, Miracast, package installer, playback/DRM,
recovery, and the used apps (Netflix, YouTube, HBO Max, Spotify, Instagram) are
preserved.

## Build, test, install (Windows 11)

Requires the existing JDK 17 + Android SDK 34 / build-tools 34.0.0.0
(see HANDOFF.md for the `../fire-tv-cleanup/.local-tools` locations):

```powershell
$env:JAVA_HOME=(Resolve-Path '../fire-tv-cleanup/.local-tools/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path '../fire-tv-cleanup/.local-tools/android-sdk').Path
./launcher/gradlew.bat -p launcher --no-daemon testReleaseUnitTest lintRelease assembleRelease assembleDebug
```

Release APK must stay under 589,824 B (no new dependencies were added).
JVM tests added: `MirroringPickerTest`, `GoveePayloadsTest`.

Hardware validation (needs physical approval — do not run unattended):
backup APK + settings, verify Hisense AFTOLCBC82 + signing cert, install
debuggable build with `adb install -r`, then Home button (real press), reboot,
D-pad edges, app launches/returns, screensaver, HDMI, settings, volume,
Miracast connect/disconnect/reconnect, Govee discovery/power/brightness/color,
memory/responsiveness, rollback (`adb install -r -d previous.apk`).
Close network debugging afterwards (`tools/adb-closed.py`).
