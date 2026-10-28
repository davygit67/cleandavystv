# Quiet Machine — local continuation checkpoint (28 October 2026)

**User requested an immediate handoff to Claude Code. This is unfinished, build-tested work; no updated APK was installed on the TV.**

## Start here

- Work only in `davygit67/cleandavystv`, branch `feature/quiet-machine-rams-ui`.
- This continuation starts from `d0491fda`; use `git log -1` for the saved continuation commit.
- Both origin (Davy's fork) and upstream are configured locally. Push ONLY origin's feature branch, never upstream/main, never force-push.
- Read AGENTS.md, this newest section, docs/quiet-machine.md and the original brief. The old handoff below is historical.
- Preserve unrelated pre-existing dirty Gradle wrapper files in `addons/ad-blocking`, `addons/airplay-status`, and `addons/bluetooth-connect`. Do not commit or revert them.
- User authorized implementation, builds, tests, commits/pushes and safe in-place TV updates after target identification, backup and signing checks. No uninstall/disable, Home-default change, new remote helpers, firmware changes or unrelated settings changes.

## Critical TV blocker — do not install yet

The user identified a **Hisense 55E6QF**, Fire OS **8.1.8.0**, firmware **RS8180.3746N**. ADB reports model **AFTOLCBC82**, Android **11**, SDK **30**. Existing saved pairing worked via encrypted wireless ADB; discover its current port with `adb mdns services` and match the target privately. Do not put endpoints, serials or account/network information in git.

- Stock default Home is `com.amazon.tv.launcher/.ui.HomeActivity_vNext`, still enabled.
- Installed `com.gathin.tvhome` is an older **versionCode 2 / versionName 2.0**, targetSdk 29, debug build, APK 8,655,818 bytes. This branch remains versionCode 10, targetSdk 34, minSdk 26.
- **Enabled and bound accessibility service is `com.gathin.tvhome/com.gathin.tvhome.HomeRedirectService`. This branch does NOT declare that service; it has HomeKeyService instead. An update would remove the user's current Home-button path. Stop installation until preservation is concretely solved within the user's constraints. Do not silently enable a different helper or alter the default Home.**
- Original helper source is available read-only in the adjacent `../fire-tv-cleanup/launcher/app/src/main/java/com/gathin/tvhome/HomeRedirectService.java`. No changes were made to that repository.
- Signing mismatch with the default local key was detected. The matching original key was found under adjacent `.local-tools/android-user/debug.keystore`, verified against installed APK and copied privately. Build below uses that key, not the unrelated global key.
- Current display: physical 3840x2160; existing override 1920x1080; density 320. Do not change these system settings without separate authorization.
- No package install, Home launch, system setting change, force-stop, debloat, screenshot capture, or photo upload was performed in this continuation. No physical remote test yet.
- Debugging remains enabled for continuation. Both TLS and plaintext ADB were advertised. Finish using the repository's debugging closure/port-verification procedure when device work is over; closure has NOT been verified.

## Private local recovery files (gitignored)

`backup/quiet-machine-20261028/` contains previous.apk, launcher-data.tar, package/property/disabled-package/settings/default-Home/accessibility records, original-debug.keystore (matching), and local-debug.keystore (nonmatching).
The private archive was successfully read via run-as, 3,072 bytes; it contained cache/code_cache/shared_prefs directories with no preference files. External app-files path returned missing; broader Android/data listing was denied. **External theme/config recovery is not fully established; inspect with provider or app UID before installing.**
The old APK has a lower versionCode: do not promise a simple downgrade rollback from a non-debug release. Establish and document a viable same-key recovery path first. Stock Home stays available.

## Code changes saved in this continuation

- MainActivity wraps the Classic surface in an invisible container blocking descendant focus/accessibility; skips Classic reload entrance and wallpaper startup while Quiet Machine is active. HDMI popup uses a visible Quiet Machine anchor.
- Library uses RecyclerView/GridLayoutManager (existing dependency), a 48-entry icon cache with 64px icons loaded on demand; scans metadata without eagerly decoding every icon. Package refresh invalidates catalog/cache.
- Photo cycling retains focused panorama instead of rebuilding Home. Truthful missing-photo panel; stale async results discarded; both image dimensions downsampled to <=1024. One new JVM test covers landscape/portrait bounds.
- Home refresh attempts stable-tag focus restoration; filter tabs and Focus mode selections restore their selected control; focus search prevents escape to underlying Classic controls.
- Volume changes synchronize via broadcast/resume, permission/vendor failures are caught, and OK on slider opens settings. Control body scrolls for taller text; Focus buttons enlarged.
- Settings actions now use existing SystemSettings vendor fallback (important for Fire OS CTS dummy handlers).
- No manifest, permissions, dependencies, SDK levels, install guards or CI changed. Photo credits/assets untouched.
- **Review critically: native runtime/focus/grid-edge behavior and visual fidelity remain unverified.** Large-list runtime/memory measurements, saved-state across recreation, configured library component selection, honest CEC/fixed-volume handling and all sizing still need testing/refinement.

## Actual latest validation

- JDK 17, SDK platform 34/build-tools 34.0.0.
- Final `testReleaseUnitTest lintRelease assembleRelease`: **BUILD SUCCESSFUL**, 1m21s.
- **298 Java tests, zero failures/errors/skips.**
- Lint: **0 errors, 101 warnings**.
- APK **559,669 bytes**, below 589,824-byte gate.
- Full Python suite launched and still running at urgent handoff; inspect `work/quiet-python.log` for final result. Do not reuse the old cloud suite's passing numbers as this run's result.
- No implemented UI screenshots or emulator/TV runtime validation. Do not claim completion.

## Local build tools and commands

Tools are available read-only from adjacent `../fire-tv-cleanup/.local-tools/`:
`jdk-17.0.20.1+1`, `android-sdk` (platform 34/build-tools 34.0.0), `platform-tools/adb.exe`.
Private `work/quiet-signing.gradle` points debug signing at the backed-up matching original key; no build.gradle signing changes or global key replacement were made.

PowerShell from repo root:
```powershell
$env:JAVA_HOME=(Resolve-Path '../fire-tv-cleanup/.local-tools/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path '../fire-tv-cleanup/.local-tools/android-sdk').Path
./launcher/gradlew.bat -p launcher -I "$pwd/work/quiet-signing.gradle" --no-daemon testReleaseUnitTest lintRelease assembleRelease
./tools/venv/Scripts/python.exe -m unittest discover -s tests -v
```
Logs: `work/quiet-build-final.log`, `work/quiet-python.log`; lint/test reports under launcher/app/build/. All private artifacts stay out of commits. Recheck final APK signer before any installation.

## Next work

1. Inspect latest commit, dirty unrelated wrappers and running Python log. Review continuation diff and resolve any failures.
2. Resolve legacy HomeRedirectService preservation and recoverable configuration/rollback before an in-place update. User explicitly prohibited new helpers/default-Home changes; ask only if the needed solution exceeds that authorization.
3. Continue visual implementation against the supplied ivory/graphite/orange three-screen board; panorama photos are LA/USC/Florence, not Manhattan. Original full brief is at local attachment `C:/Users/davyt/.codex/attachments/e7965195-1db7-404c-b3d0-e3230d6a0d7b/Pasted text.txt`.
4. Once safe, exercise actual Home/Library/Search/Control, app launches/return focus, every D-pad edge, OK/Back/Home, HDMI, settings, Classic recovery, idle screensaver, volume and persistence. Physical helper buttons require real presses. Merge photographs without replacing config.json. Capture actual screenshots and compare/refine.
5. Finish tests, APK size/signature checks, truthful docs, commit/push ONLY fork feature branch. Close/debugging verify when done.

---

# Historical cloud handoff (superseded by the local checkpoint above)

# Quiet Machine — handoff to VS Code Codex

## State at pause (28 October 2026)

The user requested a pause and a committed handoff to continue locally with TV access.
**This is a buildable implementation checkpoint, not a completed or TV-validated redesign.**

- Repository: https://github.com/davygit67/cleandavystv
- Branch: `feature/quiet-machine-rams-ui`
- GitHub REST metadata confirmed `fork: true`, parent `Gathin23/cleanyourtv`.
- Only `origin` pointing at Davy's fork is configured. Never push to upstream or merge
  to main without an explicit request. No force push.
- Read `AGENTS.md`, `docs/quiet-machine.md`, and the original user brief before continuing.
- User wants the provided “02 / Quiet Machine” three-panel reference followed closely.
  The reference was visible in chat, but no separate reference PNG was available as a
  workspace file. Have the user attach it in the new VS Code conversation if needed.
- Latest visual change: **LA, USC and Florence photographs**, replacing the initial
  Manhattan request. Three licensed photos and attribution are in
  `launcher/quiet-machine-wallpapers/`.
- User authorized routine decisions and asked to stop repeated approval questions.
  Sandbox execution approvals are separate from task authorization. Do not repeatedly
  ask about decisions already authorized. User intends to continue with physical TV
  access locally; inspect the actual target, backup state and signing key before any
  installation. Do not infer that a connected device is the intended TV.

## Implemented in this checkpoint

- `QuietMachineView.java`: native Java Home/Library/Control surface, persistent rail,
  ivory/graphite palette, thin borders, thicker orange focus outlines and inset fills.
- Home: time-aware personalized greeting (existing name or Davy), device clock/date,
  downsampled grayscale wallpaper, WATCH wired to existing HDMI/live-TV picker,
  existing adapter tiles and order, real adapter launch actions.
- Library/Search: installed launchable apps, real bounded-size app icons, three columns,
  label/package search and music-app filtering. Unsupported media catalogs explicitly
  say they are not connected. No fictional production media catalog.
- Control: actual media volume when supported, disabled/unconnected ambient light,
  persistent Focus modes, display/sound/network/system settings links, launcher
  preferences, HDMI and Classic launcher recovery.
- Focus sets launcher-only dimming plus a best-effort window-brightness request;
  no global picture, permission or service changes.
- Photo selection cycles LA/USC/Florence by OK on the panorama, persisted privately.
  Photo pack is separate from APK to preserve the size budget. Existing
  `wallpaper.jpg` is the fallback. Photo installation instructions preserve config.
- MainActivity retains its existing identity, lifecycle, boot/remote/HDMI paths;
  Quiet Machine is a native overlay. `AppAdapter` exposes a snapshot and its existing
  launch implementation. Classic launcher remains accessible and has a return button.
- `QuietMachineModelTest.java`: greeting boundaries, truthful filters, brightness policy.
- Documentation: `docs/quiet-machine.md`, linked from README; photo credits included.

## Validation and remaining work

Completed before pause:

- Final repository-wide Python suite: **2,132 tests, 36 skipped, OK**.
- Final offline Java test/lint/release build: **BUILD SUCCESSFUL** (47 seconds).
- Final lint: **0 errors, 100 warnings**; final APK: **557,893 bytes**.

- JDK 17 + platform 34 + build-tools 34.0.0 configured in the cloud workspace.
- `testReleaseUnitTest lintRelease assembleRelease` passed during implementation.
- 297 JVM tests, zero failures. Lint completed with zero errors (warnings remain,
  including English text formatting and the programmatically constructed custom view).
- Release APK measured 557,893 bytes on the final successful build, below
  589,824-byte / 576-KiB gate. Check the final size again after further edits.
- No manifest, permission, dependency, minSdk, package identity, Home guard,
  install/restore script, system package or physical TV changes.

**Not completed:**

- No screenshots of implemented screens yet. No visual comparison/refinement cycle.
- No runtime verification of D-pad/OK/Back/Home, actual app launches, HDMI, volume,
  classic recovery, screen sizing or screensaver on a TV or working emulator.
- Software Android TV API 30 emulator was started without KVM; it remained offline
  during checks. Do not describe it as a successful emulator test.
- Photos are committed as a separate pack, not installed on a TV. Without the pack or
  an existing wallpaper, the panorama is blank. Improve that empty state before delivery.

Priority next steps:

1. Pull this branch, inspect the diff, and build locally using the original signing key.
2. Review overlay integration: underlying launcher focus/animation callbacks may still
   contend with the new surface. Exercise all rail/content edges, return-from-app focus,
   package changes, Home intents, settings return, and HDMI popup anchoring. Fix observed
   issues without bypassing existing recovery/remote logic.
3. Check 720p/1080p/4K and real TV font scaling. Control cards/footers may need further
   spacing refinement. Library currently constructs native rows rather than recycling
   them; profile a large installed-app list before making performance claims.
4. Confirm volume behavior on the actual TV, including fixed-volume/CEC and denied
   access. Ensure the fallback remains honest. Confirm Focus feedback on firmware that
   ignores window brightness.
5. Add photo pack safely (merge individual images through the existing ThemeProvider;
   do not replace the user's config.json), improve absent-photo behavior, capture all
   three real screens, compare to the provided reference, refine until close.
6. Verify that Classic launcher retains configuration, module cards, ordering, screensaver,
   installed-app detection, source input access and physical remote helpers.
7. Finish full tests/lint/build, inspect size/permissions, document real device/firmware
   results and limitations. Commit and push only this fork branch.

## Cloud paths (not assumed to exist on the local machine)

Repository `/workspace/cleandavystv`; SDK `/workspace/android-sdk`; JDK
`/workspace/jdk17`; Gradle cache `/workspace/gradle`.
Build logs `/tmp/quiet-build.log`, `/tmp/quiet-build-final.log`;
Python suite `/tmp/repo-tests.log`; emulator `/tmp/emulator.log`.
These paths and logs are ephemeral and are not committed. Build output APKs, SDKs,
keys and screenshots containing device identifiers must not be committed.

## Resume prompt

> Continue the Quiet Machine redesign in my fork `davygit67/cleandavystv`, branch
> `feature/quiet-machine-rams-ui`. Pull the branch and read HANDOFF.md, AGENTS.md and
> docs/quiet-machine.md first. This is a buildable checkpoint, not a finished redesign.
> Preserve my current app configuration, package identity, HDMI/remote/Home recovery
> and rollback paths. Follow the attached three-panel reference, using the updated LA,
> USC and Florence photo direction. Finish the native UI, runtime tests, screenshots
> and visual refinement. I authorize routine implementation and testing decisions;
> don't ask repeatedly for decisions already approved. You may inspect my connected
> TV and prepare installation, but identify the correct device, back up existing state
> and verify the original signing key before updating. Do not uninstall/disable system
> packages or change the default Home app. Push commits to my fork's feature branch
> only. Report exactly what was tested and what still needs physical remote verification.
