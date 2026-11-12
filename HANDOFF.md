# Quiet Machine — handoff (28 October 2026, Claude Code session)

Fork `davygit67/cleandavystv`, branch `feature/quiet-machine-rams-ui`. Push only to origin's feature
branch: never upstream, never main, never force. Older handoff text is in git history (`git log -p HANDOFF.md`).
Leave the unrelated dirty Gradle wrapper files in `addons/ad-blocking`, `addons/airplay-status` and
`addons/bluetooth-connect` alone: do not commit or revert them.

## State: installed on the TV and checked there

The redesign is installed **in place** on Davy's Hisense 55E6QF (Fire OS 8.1.8.0, Android 11 / SDK 30) as the
debuggable build of this branch (versionCode 10). It is signed with the original key, which matches the old
build. Stock default Home is still `com.amazon.tv.launcher/.ui.HomeActivity_vNext`, and nothing was disabled.
No accessibility, screensaver or Home setting was changed.

### How the earlier "do not install" blocker was resolved

The TV ran an older `com.gathin.tvhome` build (versionCode 2, from the separate `fire-tv-cleanup` tree), and
its settings pointed at two components this branch lacked:
`enabled_accessibility_services=com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService` and
`screensaver_components=com.gathin.tvhome/com.gathin.tvhome.PhotoDream`. The branch now declares both under the
same names (`HomeRedirectService.java`, `PhotoDream.java`, `res/xml/home_redirect_service.xml`, manifest). An
in-place update therefore keeps both settings pointing at working components. Installing turns nothing on.
The redirect behaves as the old build did, except that it now sends a Home intent. PhotoDream shows the
LA/USC/Florence pack in grayscale; the old twelve bundled photos would exceed the APK size gate.

### Rollback, tested on the TV

- The TV has `ro.debuggable=0`, so Android allows a downgrade only while the installed app is debuggable.
  That is why the debug build is the one installed.
- Drill done: `adb install -r -d backup/quiet-machine-20261028/previous.apk` succeeded (versionCode 2, Home
  redirect bound, screensaver setting intact). Then `adb install -r app-debug.apk` went forward again
  (versionCode 10, redirect bound, no crashed services, screensaver intact, stock Home unchanged).
- UI rollback without reinstalling: Settings → Classic launcher. The way back is Right from Classic's Settings
  pill, onto the "Quiet Machine" button.
- Private recovery files (gitignored): `backup/quiet-machine-20261028/` (previous.apk, matching
  original-debug.keystore, settings, package and accessibility records). Build signing goes through the private
  `work/quiet-signing.gradle` init script.

## Verified on the TV (adb, injected keys, screenshots kept locally only)

- Home: time-aware greeting ("Good morning, Davy"), the date and 24-hour clock, a grayscale panorama with its
  licence credit in the footer, and a green dot backed by a validated network. WATCH opens the real
  HDMI 1/2/3 picker anchored under the tile. The Netflix tile opened Netflix.
- The Home key, injected, goes through Amazon's home and the preserved HomeRedirectService back to Quiet
  Machine Home with WATCH focused.
- Photo: Up from any tile reaches the panorama. OK cycles LA → USC → Florence. The choice survived reinstalls
  and restarts.
- Library: the three-column grid with grayscale banners. Opening Files and pressing Back returned to the Files
  card. Recent sorting moved it first, with "Opened 1 minute ago". The right grid edge stays put. Movies says
  no catalog is connected.
- Search: typing "hbo" finds HBO Max.
- Control: volume 0–100. Fire OS ignores `setStreamVolume`, so the meter steps with `adjustStreamVolume`, the
  same way the remote does. A read-back confirmed the level moved, and it was left at the original 5. Focus
  Night visibly dims the launcher and Off restores it. Display reads 2160p, 3840 × 2160 · 60 Hz from the real
  output mode. The device name and network dot are shown. Launcher preferences opens, and Back returns to its
  button.
- Dark mode is the default (the user asked for it). The Light mode / Dark mode button in Control switches it,
  the choice is saved, and focus stays on the button. The TV was left in Dark.
- Classic launcher: switching to it and back works. Its full original UI is intact.
- Screensaver: started through `Somnambulator`, `mCurrentDreamName` was PhotoDream. It showed the grayscale
  photograph and clock, and a key press woke it back to the launcher.
- The photo pack was written only through `content://com.gathin.tvhome.theme/wallpapers/*.jpg`. No
  `config.json` existed or was written.

## Not verified yet: needs Davy with the real remote

- **A physical press of the remote's Home button.** Injected HOME worked, but AGENTS.md requires a real press
  for helper-routed buttons.
- A physical-remote pass of the D-pad at every edge. Up, Down, Left and Right were spot-checked with injected
  keys on each screen, not exhaustively.
- Picking an HDMI input, actually switching sources, and the Picture/Sound/Network/System tabs landing on
  Fire OS's real settings pages (they go through `SystemSettings` fallbacks).
- Boot behaviour: the redirect opens the launcher within 3 minutes of boot. Not rebooted in this session.
- Idle-timeout screensaver: started by hand only.
- 720p and 4K-density layouts: only this TV's 1920×1080 override at density 320 (a 960×540 dp canvas) was
  seen. Large font scale was not tried, though Control scrolls.
- Memory with a very large app list: about 50 apps here. Rows recycle and art is a 48-entry cache of small
  bitmaps, but nothing was profiled.
- The HDMI picker's last row sits low on screen (HDMI 3 partly below the fold; it scrolls). This is the
  existing Classic popup, anchored under WATCH.

## ADB is still open

Wireless debugging and network ADB remain **on**, deliberately, for the remote-control pass and further work.
When that is done, close them as AGENTS.md says ("Close ADB afterwards"), and confirm with
`tools/adb-closed.py <tv ip>` and the TLS port check.

## Build and test results (this session)

- `testReleaseUnitTest lintRelease assembleRelease assembleDebug` with JDK 17 and SDK 34 / build-tools
  34.0.0: BUILD SUCCESSFUL.
- 303 JVM tests, 0 failures. Lint: 0 errors, 101 warnings.
- Release APK 568,453 B, under the 589,824 B gate. The debug APK (installed) is about 2.36 MB.
- Same signing certificate (SHA-256 `2fbba8ad…70a5`) on the debug and release builds and on the backed-up
  installed APK.
- Permissions are unchanged from upstream. There are no new dependencies. The manifest adds only the two
  compatibility components.
- Python suite on Windows: the failures are the same environment-only set that fails on the pre-branch
  baseline `095b71ad` (POSIX shell, venv `bin/` paths, file permissions). This branch had broken
  `test_agents_md` (AGENTS.md over 16 KiB) and `test_doc_addresses` (a home-folder path in this file). Both are
  fixed, and both modules pass.

## Commands (PowerShell, repo root)

```powershell
$env:JAVA_HOME=(Resolve-Path '../fire-tv-cleanup/.local-tools/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path '../fire-tv-cleanup/.local-tools/android-sdk').Path
./launcher/gradlew.bat -p launcher -I "$pwd/work/quiet-signing.gradle" --no-daemon testReleaseUnitTest lintRelease assembleRelease assembleDebug
```

adb is `../fire-tv-cleanup/.local-tools/platform-tools/adb.exe`. Find the TV with `adb mdns services` and
check that the model is AFTOLCBC82. In Git Bash, set `MSYS_NO_PATHCONV=1`, or device paths such as
`/data/local/tmp` get rewritten. Install with `adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk`.
Afterwards check `dumpsys accessibility` (HomeRedirectService bound) and `settings get secure
screensaver_components`.

## Code map

- `QuietMachineView.java`: the whole surface. It has the Dark/Light palette tokens, rail, Home, Library/Search,
  Control, the `Meter` level bar and `Glyph` line icons drawn in code.
- `QuietMachineModel.java` (+ test): greeting, dimming, catalog availability, Recent/A–Z order, resolution
  label, volume step.
- `MainActivity.java`: hosts the overlay over the invisible Classic surface. In Classic, the return button is
  reachable from the Settings pill.
- `AppAdapter.java`: `quietEntries()` and `quietLaunch()`; Quiet Machine launches record LastOpened.
- `HomeRedirectService.java`, `PhotoDream.java`: the fork compatibility components.
- Details for users: `docs/quiet-machine.md`.
