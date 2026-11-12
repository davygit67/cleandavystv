# Quiet Machine (cleandavystv fork)

This native interface belongs to `davygit67/cleandavystv`, on
`feature/quiet-machine-rams-ui`. It is not an upstream CleanYourTV change.

## Using it

One visual system across three screens, in two palettes that share one orange accent (#F36B21) and the
bundled Outfit font (SIL OFL). **Dark** is the default: graphite (#1B1B19) with warm off-white text.
**Light** follows the reference board: warm ivory (#F2F0E9), graphite text (#222220) and hairline
dividers (#C9C7BE). The Light mode / Dark mode button in Control switches between them, and the choice
is saved. Icons are
drawn in code, so they add no image assets. Focus is never shown by colour alone: the focused control
gets a 2 dp orange outline and an inset fill, and tiles also get an orange underline.

- **Rail** (every screen): Home, Library, Search, Settings, with line icons. The current screen has
  an orange dot, a filled icon and bold text. OK switches screens. Back returns to Home.
- **Home**: a time-aware greeting (theme `greeting_name`, otherwise "Davy"), the date and 24-hour
  clock from the TV, the monochrome panorama, and a green dot only while Android reports a validated
  internet connection. Below them are the WATCH tile (the existing live-TV/HDMI picker) and the
  configured tiles in their saved order, with each app's grayscale banner or icon. Five or fewer
  tiles fill the row; more tiles scroll. Menu on an app tile opens its App info. The footer shows
  the selected photograph's credit.
- **Library / Search**: a three-column installed-app library with tabs for All, Movies, Series,
  Documentaries and Music. Movies, Series and Documentaries say that no catalog is connected. Music
  lists music apps. SORT switches between Recent (apps opened from Home most recently, then A–Z) and
  A–Z. Each card shows "Opened …" when Home has opened the app and "Installed app" otherwise. Search
  matches names and package names. App exclusions from the configuration apply. Rows are recycled,
  and banners are drawn once into 160×90 bitmaps held in a 48-entry cache.
- **Control**: the Picture, Sound, Network and System tabs (marked ↗) open the TV's own settings
  through the launcher's vendor fallbacks. The header shows the device name and the network dot.
  - **Sound**: the real media volume. Left and Right step it, Up and Down leave the bar, and OK
    opens Sound settings. Each change is read back. If the TV or a connected audio device keeps its
    own level, the card says so. Fixed-volume TVs show "Fixed".
  - **Ambient light**: says Not connected and is not focusable.
  - **Focus**: Off, Day, Evening and Night, saved. The mode dims this launcher and requests a window
    brightness. Firmware may ignore the brightness request, but the dimming always shows.
  - **Display**: the real output mode (for example 2160p, 3840 × 2160 · 60 Hz) and a Picture
    settings link. No picture-mode values are invented.
  - Below the cards: Launcher preferences, HDMI / inputs, Classic launcher and the Light/Dark switch.
- **Classic launcher** returns to the complete original interface. To come back, press Right on
  Classic's Settings pill to reach the "Quiet Machine" button. No preference is erased either way.

No weather, light level or catalog is synthesised without a source.

## Fork compatibility components

The TV this fork targets (Hisense 55E6QF, Fire OS 8) ran an earlier `com.gathin.tvhome` build from
another source tree. That build's accessibility service and screensaver were switched on in the TV's
settings. This fork declares both components under the same names, so an in-place update keeps those
settings pointing at working components. Installing it turns nothing on.

- `HomeRedirectService`: Fire OS never hands the Home key to an accessibility service. This service
  watches window changes instead and opens this launcher when Amazon's home screen appears, except
  in the moment Amazon's home flashes on the way into Settings. It also opens the launcher once at
  boot. Its behaviour matches the old build, except that it now sends a Home intent, so Quiet Machine
  returns to its Home screen.
- `PhotoDream`: the photo screensaver. It shows the LA/USC/Florence pack in grayscale with a slow
  zoom and a clock. Without the pack it uses the theme's `wallpaper.jpg`, and without that only the
  clock. The old build's twelve bundled photographs are not included, because they would exceed the
  APK size gate.

## Photographs

The [LA, USC and Florence photo pack](../launcher/quiet-machine-wallpapers/CREDITS.md) contains
attributed, licensed Wikimedia photographs. The pack stays outside the APK to keep the launcher
within its size budget. Select the panorama and press OK to cycle LA → USC → Florence. The choice
is saved, and the credit for the selected photo is shown on Home.

Install the pack through the existing theme provider. This writes only `wallpapers/*.jpg` and never
touches `config.json`:

```sh
for p in la usc florence; do
  adb -s <tv-serial> push launcher/quiet-machine-wallpapers/$p.jpg /data/local/tmp/quiet-$p.jpg
  adb -s <tv-serial> shell "content write --uri content://com.gathin.tvhome.theme/wallpapers/$p.jpg < /data/local/tmp/quiet-$p.jpg"
  adb -s <tv-serial> shell rm /data/local/tmp/quiet-$p.jpg
done
```

## Build

Use JDK 17, Android SDK platform 34 and build-tools 34.0.0, and sign with the key of the build
already on the TV (a private Gradle init script can point `signingConfigs.debug` at it):

```sh
cd launcher
./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease assembleDebug
```

The package remains `com.gathin.tvhome`, with minSdk 26 and targetSdk 34. There are no new
permissions or dependencies. The manifest adds only the two compatibility components above. The
release APK must stay under 589,824 bytes.

## Safe installation and rollback

1. Back up first: the installed APK (`pm path`, then `adb pull`), `dumpsys package com.gathin.tvhome`
   and the secure settings (`enabled_accessibility_services`, `screensaver_components`, the Home
   resolver). Keep stock Home enabled. Do not disable packages or enable other helpers.
2. Check that the new APK's signing certificate matches the installed one
   (`apksigner verify --print-certs`). If it differs, stop. Never uninstall to work around a
   mismatch.
3. Install the **debuggable** build (`app-debug.apk`) with `adb install -r`. On a TV with
   `ro.debuggable=0`, Android allows a later downgrade only if the installed app is debuggable.
   That keeps step 6 possible. The release APK is what the size gate measures.
4. Afterwards, `dumpsys accessibility` should still list `HomeRedirectService` as bound, and
   `screensaver_components` should be unchanged. Launch `com.gathin.tvhome/.MainActivity` without
   changing the default Home. Test the physical remote's Home button with a real press.
5. Immediate UI rollback: Settings → Classic launcher.
6. Code rollback: `adb install -r -d previous.apk` (same key, lower versionCode). This keeps app data.
   The old build's components carry the same names, so the accessibility and screensaver settings
   keep working.
7. Close wireless/USB debugging afterwards, as `AGENTS.md` describes, and confirm with
   `tools/adb-closed.py`.

## Validation

See the newest section of [HANDOFF.md](../HANDOFF.md) for what was built, tested and installed, and
for what still needs a physical-remote check.
