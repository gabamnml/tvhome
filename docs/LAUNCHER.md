# The launcher

`com.binario.tvhome` — about 95 KB, built from the installed SDK only. No
Gradle, no Maven, no AndroidX, no third-party code at all. `minSdk` and
`targetSdk` 28.

![The home screen](img/home-dock.png)

---

## Contents

- [What replaced the libraries](#what-replaced-the-libraries)
- [The one file you edit](#the-one-file-you-edit)
- [What happens when you focus an app](#what-happens-when-you-focus-an-app)
- [D-pad behaviour](#d-pad-behaviour)
- [The top bar](#the-top-bar)
- [Weather](#weather)
- [The view tree](#the-view-tree)
- [Typography](#typography)

---

## What replaced the libraries

| Instead of | This uses |
|---|---|
| `androidx.recyclerview` | A static view tree. With ~15 apps there is nothing to recycle, and RecyclerView's focus search fights deterministic D-pad movement. |
| `androidx.palette` | `ColorSampler` — renders the banner at 32×18 and picks the most saturated pixel. ~40 lines. |
| `androidx.dynamicanimation` | `Spring` — a damped spring driven by a `Choreographer.FrameCallback`. ~30 lines. |
| `androidx.leanback` | Three framework views and a `ScrollView` subclass. |

---

## The one file you edit

[`launcher/res/values/config.xml`](../launcher/res/values/config.xml).

### Dock order

```xml
<string-array name="dock_packages">
    <item>com.google.android.youtube.tv</item>   <!-- leftmost -->
    <item>com.netflix.ninja</item>
    <item>com.stremio.one</item>
    <item>com.spotify.tv.android</item>          <!-- rightmost -->
</string-array>
```

Reorder, swap, or add any package name. A package that isn't installed is
skipped silently, so leaving spares in the list is safe. Anything not in the
dock lands in the grid, sorted A–Z. To find a package name:

```powershell
. .\_env.ps1
Sh "pm list packages" | Select-String netflix
```

### Wallpaper

Default is pitch black, defined by `launcher/res/drawable/wallpaper.xml`.
To use a picture instead: **delete `wallpaper.xml`**, drop your image in as
`launcher/res/drawable/wallpaper.png` (1920×1080), rebuild. One resource name,
two possible forms.

### Every other knob

| Setting | Does |
|---|---|
| `grid_columns` | Columns below the dock (4) |
| `weather_enabled` | Weather beside the clock. **The only thing in the launcher that uses the network** — set `false` and it makes no requests at all. |
| `weather_fahrenheit` | °F instead of °C (false) |
| `show_ambient_glow` | The full-screen colour wash. Focusing an app tints the whole background with a colour sampled from its banner. |
| `backdrop_intensity` | How strong that wash is, 0–100. 25 is a whisper, 60 is bold (35) |
| `show_tile_labels` | App name under the focused grid tile. **Off** — the background colour is the only thing that reacts to focus. Set `true` to bring the labels back. |
| `tile_corner_radius` | Corner radius of the app banners, in dp (10). The glass highlight derives its own radius from this (`tile × FOCUS_SCALE + GLASS_GAP`) so the two curves stay concentric. `0` = square corners. |
| `spring_stiffness` | Higher = the glass highlight snaps faster (900) |
| `spring_damping` | 1.0 stops dead, below 1.0 overshoots slightly (0.78) |
| `hidden_packages` | Apps never shown in dock or grid |

Greetings and accessibility labels live in `res/values/strings.xml` (English)
and `res/values-es/strings.xml` (Spanish). The box picks by system locale.

After any edit:

```powershell
.\build-install.ps1 -Restart
```

---

## What happens when you focus an app

Nothing is written on screen. The only feedback is colour.

| | |
|---|---|
| ![Netflix focused](img/home-dock.png) | ![Spotify focused](img/home-focus-green.png) |

`BackdropView` washes the entire background with a colour sampled from that
app's banner, cross-fading over 420 ms while sliding its centre toward the app
you're on. It's a wide `RadialGradient`, not a real blur — at TV viewing
distance it reads the same and costs one `drawRect` per frame, which matters
with ~1 GB free.

The focused tile also scales up by `TileView.FOCUS_SCALE` (1.08). The highlight
is sized from the tile's **scaled** bounds plus `HomeActivity.GLASS_GAP` (5 dp),
not from the layout rect plus a fixed inset — because the tile is wider than it
is tall, a uniform scale adds ~6 dp of width but only ~3 dp of height, so a
fixed inset left the left and right edges looking pinched while the top and
bottom looked correct:

```java
float grow = (TileView.FOCUS_SCALE - 1f) / 2f;
r.inset(-Math.round(r.width() * grow), -Math.round(r.height() * grow));
r.inset(-dp(GLASS_GAP), -dp(GLASS_GAP));
glass.moveTo(r);
```

Change `GLASS_GAP` to make the margin tighter or looser.

### Two traps in the colour sampler

In case you change `ColorSampler`:

- **Rejecting pixels by brightness alone throws away pure `#FF0000`**, whose
  HSV value is 1.0. That is exactly why YouTube came out grey while Netflix's
  `#E50914` (value 0.898) worked. "Near white" has to mean *unsaturated* **and**
  bright:

  ```java
  if (hsv[1] < 0.18f && hsv[2] > 0.88f) continue;
  ```

- **At 16×9 a small logo on a large white banner averages itself out of
  existence.** 32×18 keeps it.

And one that cost an hour: the sampler is handed the *same* `Drawable` instance
the `ImageView` is displaying. Calling `setBounds(0, 0, 16, 9)` on it and not
putting the bounds back rendered YouTube's banner as a thumbnail in the corner
of its own tile. Save and restore in a `finally`.

---

## D-pad behaviour

Every next-focus edge is set by hand; nothing is left to focus search.

- Up/Down move exactly one row.
- Up from the dock goes to the top bar.
- The ends of the top bar point at themselves, so Left/Right can't fall through
  to the tiles.
- The last grid row's Down points at itself.
- The last focused tile is remembered across launches.
- Down from the cog returns to the tile you came **up** from, not to a fixed one.
- Back doesn't exit — it returns to the top of the dock.

Scrolling is manual. `HomeScroll` overrides
`computeScrollDeltaToGetChildRectOnScreen()` to return `0`, because
`ScrollView`'s own idea of "make the focused child visible" produces half-row
offsets. `SCROLL_ANCHOR` (40 dp) is the amount that clears the dock in exactly
one Down.

---

## The top bar

![Settings pill focused](img/settings-pill.png)

Top right holds a single icon-only pill opening system Settings. It started as
three pills (Power, Wi-Fi, Settings) and both of the others were removed as
redundant — Power duplicated the remote's own button, and Wi-Fi lives inside
Settings anyway. Each removal cleaned up more than a button.

**Power** was backed by a bundled `AccessibilityService`, because an ordinary
app cannot sleep or restart an Android 9 box: `goToSleep()` needs
`DEVICE_POWER`, reboot needs `REBOOT`, both are signature-level, and `pm grant`
only hands out runtime permissions. The service raised the system power dialog
via `GLOBAL_ACTION_POWER_DIALOG` and worked — but with the button gone it was
an accessibility service bound for no reason, so it was deleted from the APK
**and** switched off on the device:

```powershell
Sh "settings put secure enabled_accessibility_services ''"
Sh "settings put secure accessibility_enabled 0"
```

**Wi-Fi** showed the SSID, which on Android 9 requires `ACCESS_FINE_LOCATION`
*and* location services switched on. Dropping that pill dropped the launcher's
last need for a location permission. It now declares only three:

```
ACCESS_NETWORK_STATE    INTERNET    QUERY_ALL_PACKAGES
```

The cog itself is `res/drawable/ic_settings.xml`, a hand-written
`VectorDrawable`. The eight teeth are one path repeated through
`<group android:rotation>` rather than hand-authored path data, so the spacing
is exact by construction and the tooth shape can be edited in one place.

---

## Weather

Beside the clock: current temperature, conditions, and today's high/low.

- **Conditions** from [Open-Meteo](https://open-meteo.com) — free, no API key,
  no registration, so there is no credential to manage or leak.
- **Position** from an IP lookup at `ipapi.co`, cached for a week because a TV
  does not move. Note what this means: **that service sees your box's public
  IP.** There is no location permission involved, and none is requested.
- **Units** follow `weather_fahrenheit` in `config.xml`, not the system locale.
- **Phrasing** is localised; `values-es/` has the Spanish set.

This is the one place the "no network polling" rule is bent, so it's bent
carefully:

- The **cached** reading paints first, so the screen is populated instantly at
  boot and stays populated with no network at all.
- The network is touched at most **once every 30 minutes**, and only while the
  home screen is actually resumed. `refresh()` self-throttles, so the
  once-a-minute clock tick calling it costs nothing.
- **Failure is silent.** Offline, rate-limited, malformed JSON — the last good
  reading stays on screen. A home screen never shows an error.

`INTERNET` is declared in the manifest **only** for this. Set
`weather_enabled` to `false` and the launcher goes back to making no network
requests whatsoever.

WMO weather codes are mapped to eleven string resources by
`Weather.describe()`, which returns `0` for anything unknown — and then only
the temperature is drawn, never a wrong description.

---

## The view tree

```
root (FrameLayout)
├── BackdropView            the colour wash — does not scroll, sits behind everything
└── HomeScroll (ScrollView)
    └── content (FrameLayout)
        ├── GlassHighlight  one view, springs between targets
        └── column (LinearLayout, vertical)
            ├── screenOne   exactly one display tall: clock, weather, top bar, dock
            └── gridBlock   everything else, 4 columns, A–Z
```

The highlight is a sibling of the content, not a child of any tile. That's what
makes it one object that moves, rather than a border each tile draws for itself.

### The LinearLayout trap

A vertical `LinearLayout`'s `generateDefaultLayoutParams()` returns
**`MATCH_PARENT` width**. `addView(child)` with no explicit params therefore
sized the clock to its parent — whose width came from the only `wrap_content`
sibling, the date line — and silently clipped `" PM"` off the end of
`9:38 PM`. The symptom looked like a font or formatting bug; logging proved the
string was right and the view was 232 px wide while the paint wanted 321.

Always pass explicit params in that layout. There's a `wrap()` helper in
`HomeActivity` for exactly this.

---

## Typography

[Outfit](https://fonts.google.com/specimen/Outfit), a variable font, loaded at
weights 200 / 300 / 400 through `Typeface.Builder` and
`setFontVariationSettings` (API 26+), with a `sans-serif` fallback if the asset
can't be read.

If the clock ever renders in Roboto, the font didn't load — see the `aapt2 -A`
trap in [BUILD.md](BUILD.md), which fails exactly this silently.
