# Quiet Machine 2.0 — safe on-TV test plan

Run only with physical access to the Hisense 55E6QF. Do every step in order.
Stop at the first regression and roll back (section 7) — never push through a
failure. Nothing here uninstalls, disables, or flashes anything.

Shell: PowerShell on Windows 11, repo root. The TV's IP changes (was
100.121.2.89) — verify it every session, never commit it. In Git Bash set
`MSYS_NO_PATHCONV=1` so device paths like `/data/local/tmp` survive.

## 0. Preconditions

- The TV is the Hisense 55E6QF in front of you, on your own network.
- `feat/quiet-machine-2` is checked out and the Gradle build passes
  (`testReleaseUnitTest lintRelease assembleRelease assembleDebug`).
- You have the physical remote in hand. Injected keys (`adb shell input keyevent`,
  `tools/tv.py`) prove nothing about the real Home/D-pad buttons.
- Stock Amazon Home stays enabled the whole time — it is the recovery path.

## 1. Connect and confirm the right TV

```powershell
adb connect <tv-ip>
adb shell getprop ro.product.model ro.product.manufacturer ro.build.version.release
# expect: AFTOLCBC82, Hisense, 8.1.8.0. Anything else: STOP, wrong device.
adb shell dumpsys package com.gathin.tvhome | Select-String "versionCode|signatures"
```

## 2. Back up the working build (do not skip)

```powershell
mkdir backup\quiet-machine-2-$(Get-Date -Format yyyyMMdd)
adb shell pm path com.gathin.tvhome
adb pull <path-from-above> backup\quiet-machine-2-<date>\previous.apk
adb shell "dumpsys package com.gathin.tvhome" > backup\quiet-machine-2-<date>\package.txt
adb shell settings get secure enabled_accessibility_services > backup\quiet-machine-2-<date>\accessibility.txt
adb shell settings get secure screensaver_components > backup\quiet-machine-2-<date>\screensaver.txt
adb shell getprop > backup\quiet-machine-2-<date>\getprop.txt
```

Keep this folder outside the repo (it is gitignored for a reason).

## 3. Signing-cert check — abort on any mismatch

```powershell
apksigner verify --print-certs launcher\app\build\outputs\apk\debug\app-debug.apk
apksigner verify --print-certs backup\quiet-machine-2-<date>\previous.apk
# The SHA-256 digests must match (previously 2fbba8ad…70a5). If they differ:
# STOP. Never `uninstall` to work around it — find the matching key instead.
```

Background: this TV has `ro.debuggable=0`, so a downgrade is only allowed while
the installed app is debuggable. That is why the **debuggable** build
(`app-debug.apk`) is installed on the TV; the release APK is what the size gate
measures.

## 4. Install and verify the services bound

```powershell
adb install -r launcher\app\build\outputs\apk\debug\app-debug.apk
adb shell dumpsys accessibility | Select-String HomeRedirectService
adb shell settings get secure screensaver_components
# HomeRedirectService must still be bound; screensaver_components unchanged;
# stock default Home still com.amazon.tv.launcher/.ui/HomeActivity_vNext.
```

## 5. Physical-remote checks (real presses only)

| # | Check | Pass when |
|---|-------|-----------|
| 1 | Physical Home button | Quiet Machine Home opens (WATCH focused), via Amazon home + redirect |
| 2 | Reboot, wait up to 3 min | Launcher opens by itself, no setup wizard trap |
| 3 | D-pad Up/Down/Left/Right on every screen edge | Focus moves predictably, never lost into an invisible surface |
| 4 | Open Netflix, YouTube, HBO Max, Spotify, Instagram, then Back | Each launches; Back returns to Quiet Machine Home |
| 5 | Home screen tiles: Watch, PC Cast, Lights | Watch opens the HDMI/input picker; PC Cast opens the receiver screen + shows the Win+K hint; Lights opens the Lights screen |
| 6 | Lights with the rotated Govee key | Discovery lists real devices; power/brightness/color-temp/RGB affect the bedroom lights; failures show a message, never a blank screen |
| 7 | Win+K from a Windows 11 PC | PC connects, mirrors; ending mirroring returns to the launcher |
| 8 | HDMI inputs 1/2/3 via Watch | Each input actually switches the picture |
| 9 | Volume keys | On-screen level tracks the real level |
| 10 | Screensaver (wait for idle or start PhotoDream) | Photo + clock show; any key wakes back to the launcher |
| 11 | Settings → TV home | Amazon system interface opens (recovery path works) |
| 12 | General feel | No stutter on focus moves, no ANR after 10 minutes of use |

Record failures with the step number, what you pressed, and what the TV did.

## 6. Govee notes for this pass

- The previously exposed key must already be rotated in the Govee app. Enter the
  new key once on the Lights screen; it is stored only as a KeyStore-sealed blob.
- Confirm the TV backlight appears first (it is matched by model/name heuristic)
  and that no control is shown for a capability the device did not report.
- No DreamView/sync modes exist unless the device's own `capabilities[]` says so.

## 7. Rollback — exact commands

Code rollback (keeps app data; the old build's component names are identical, so
accessibility and screensaver settings keep working):

```powershell
adb install -r -d backup\quiet-machine-2-<date>\previous.apk
adb shell dumpsys accessibility | Select-String HomeRedirectService
```

If a package was ever disabled during the debloat audit (only with prior
explicit approval, one at a time):

```powershell
adb shell pm enable <package>
```

Then re-run checks 1–4 and 11. If anything still misbehaves, restore the secure
settings values saved in section 2:

```powershell
adb shell settings put secure enabled_accessibility_services "<value-from-accessibility.txt>"
adb shell settings put secure screensaver_components "<value-from-screensaver.txt>"
```

Note: Quiet Machine 2.0 has no in-UI "back to Classic" switch — APK reinstall
above is the rollback. That is intentional and rehearsed (see HANDOFF.md).

## 8. Close ADB when testing is done

Unless you explicitly choose to keep it: Wireless debugging and Network ADB off,
USB debugging off unless wanted, authorisations revoked. Then confirm, wired or
not:

```powershell
tools/adb-closed.py <tv-ip>            # must report 5555 closed
```

Wireless debugging uses its own TLS port (find it with `adb mdns services`,
service `_adb-tls-connect._tcp`) — check that port closed too, then reboot and
re-check. Port 5555 is plaintext; only the tool's "closed" counts.
