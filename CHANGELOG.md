# Changelog

All notable changes to this project.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Planned
- Cinema-mode pill that fires a Home Assistant scene (design settled: a second
  glass pill left of the cog, icon only, one-shot, service derived from the
  entity id prefix — `scene.` → `scene.turn_on`, `script.` → `script.turn_on`,
  `automation.` → `automation.trigger`; token supplied at runtime via an Intent
  extra and stored in app-private prefs, never in `config.xml`)
- Data-driven debloat list so other devices can ship their own
- Clock-only screensaver to replace the one that was disabled
- Shell port of `tools/` for Linux and macOS

## [1.0.0] — 2026-10-19

First working version, built and verified on a Xiaomi Mi Box S (Android 9,
API 28, 1920×1080 @ 320 dpi).

### The launcher
- Pitch-black home screen: greeting, large thin clock, date, weather, a dock of
  four apps and an A–Z grid below it
- One translucent glass highlight that springs from tile to tile, sized from
  the focused tile's **scaled** bounds so the margin is even on all four sides
- Full-screen colour wash sampled from the focused app's own banner,
  cross-fading over 420 ms (`BackdropView`)
- Rounded banner corners, with the highlight's radius derived from the tile's
  so the two curves stay concentric
- Weather beside the clock: Open-Meteo + coarse IP geolocation, cached first,
  network at most every 30 minutes, silent on failure
- A single icon-only Settings pill, top right, with a hand-written cog
  `VectorDrawable` whose eight teeth are one path rotated eight times
- Deterministic D-pad: one row per press, clamped ends, remembered focus,
  Down from the cog returns to the tile you came up from
- Outfit variable font at weights 200/300/400, with a `sans-serif` fallback
- English and Spanish strings
- 95 KB, zero dependencies, three permissions

### The tooling
- `backup.ps1` — read-only snapshot of packages, settings, properties, HOME
  claimants, TV inputs
- `debloat.ps1` — one `pm` command per shell call, verified, protected list,
  journals only confirmed successes
- `restore.ps1` — generated from the journal; refuses anything not in it
- `build-install.ps1` — Gradle-free pipeline, ~7 s full cycle
- `shot.ps1`, `key.ps1`, `private-dns.ps1`

### Removed before release
- `PowerHelperService` (accessibility service for the system power dialog) —
  deleted with the Power pill, and switched off on the device
- The Wi-Fi pill — and with it the launcher's last need for a location
  permission
- `GlowView` — replaced by the full-screen `BackdropView` wash
- Per-tile focus labels

### Fixed along the way
- `d8` from build-tools 34 crashes on any class implementing a generic
  interface; the build now downloads and uses R8 8.7.18
- `aapt2 -A` wrote `assets/fonts\outfit.ttf` with a backslash on Windows, which
  `AssetManager` cannot find — the font silently fell back to Roboto. Assets are
  packed with `jar` instead
- `keytool` and `jar` are not exposed by Oracle's `javapath` shim; the real JDK
  bin is resolved from `java.home`
- The build work directory could overlap its own source directory and delete it;
  there is now a guard that throws
- A vertical `LinearLayout` hands out `MATCH_PARENT` width by default, which
  clipped `" PM"` off the clock
- `ColorSampler` mutated the bounds of the `Drawable` the `ImageView` was
  showing, rendering YouTube's banner as a thumbnail in its own tile
- Rejecting sampled pixels by brightness alone discarded pure `#FF0000`, so
  YouTube's backdrop came out grey
- `cmd package set-home-activity` reports success but does not take effect while
  the stock launcher is enabled
