# Troubleshooting

## The nuclear option

Whatever is wrong, this puts the box back the way it shipped:

```powershell
cd tools
.\restore.ps1 -All -StockLauncher
```

Re-enables every package in `backup/disabled.log`, re-enables the stock
launcher, points HOME back at it, and disables `com.binario.tvhome`.

---

## The launcher won't start / black screen

You are not stuck. `com.android.tv.settings` registers a `FallbackHome` at
priority **−1000**, underneath everything, so the box can never be left with no
home at all. Worst case you get a blank screen with a working Settings app.

From your PC:

```powershell
.\restore.ps1 -All -StockLauncher
```

If you'd rather debug it than roll back:

```powershell
. .\_env.ps1
Adb logcat -d | Select-String -Pattern 'tvhome|AndroidRuntime' | Select-Object -Last 40
```

---

## An app vanished from the grid

It's disabled, not gone:

```powershell
.\restore.ps1 -Packages <package>
```

To see everything currently disabled on the box, including things that were
already disabled before this project touched it:

```powershell
Sh "pm list packages -d"
```

Only the ones in `backup/disabled.log` were disabled by `debloat.ps1`.

---

## The grid is missing an app that is definitely installed

The launcher lists apps that declare `CATEGORY_LEANBACK_LAUNCHER`. Phone-style
apps sideloaded onto a TV often declare only `CATEGORY_LAUNCHER` and are
therefore invisible to *any* TV launcher, including the stock one. Check:

```powershell
Sh "cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER" |
    Select-String packageName
```

It also needs something drawable as a banner or icon. `AppRepository.pickArt()`
rasterises each candidate at 12×12 and requires at least 8 covered pixels, so an
app whose banner is a transparent placeholder falls through to
`tile_fallback.xml`, not to nothing.

---

## The remote stopped responding

Nothing that touches the remote was disabled — `com.droidlogic.ble`,
`com.android.bluetooth` and `com.google.android.companiondevicemanager` are all
in `debloat.ps1`'s `$PROTECTED` list, which it refuses to disable even if asked.

Try re-pairing: hold **Back + Home** on the remote near the box.

If it's a USB dongle rather than Bluetooth, see the next item.

---

## A USB keyboard/dongle is flaky

Some Android TV devices cut USB port power while USB debugging is on. Turn
debugging off when you're not using these scripts:

> TV → Settings → Device Preferences → About → press OK on **Build** seven
> times → back → **Developer options** → turn off **USB debugging** and
> **Network debugging**.

---

## `adb` says `unauthorized`

The *"Allow USB debugging?"* dialog is waiting **on the TV screen**. Accept it
and tick *"Always allow from this computer"*. If you never see the dialog,
you're probably talking to the box with the wrong `adb` — see below.

## `adb` says `offline`, or connects and immediately drops

Almost always the wrong `adb` client. This machine has a 2018-era `adb`
(v28.0.3, installed by Touch Portal) ahead of the SDK's on `PATH`, and the old
client mishandles modern devices. Every script here pins the SDK's binary:

```powershell
. .\_env.ps1
$ADB                 # the one the scripts use
& $ADB version
```

If you run `adb` by hand, use that full path.

---

## The clock renders in the wrong font

Outfit didn't load and it fell back to `sans-serif`. This fails *silently* by
design, so check the APK:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead("..\build\tvhome.apk")
$z.Entries | Where-Object { $_.FullName -match 'outfit' } | ForEach-Object { $_.FullName }
$z.Dispose()
```

It must print `assets/fonts/outfit.ttf` with a **forward** slash. A backslash
means assets were packed by `aapt2 -A` — see
[BUILD.md trap 2](BUILD.md#2-aapt2--a-writes-windows-path-separators-into-the-zip).

---

## The weather is wrong, or missing

- **Missing entirely:** `weather_enabled` is `false`, or the first fetch hasn't
  landed yet. Failure is silent on purpose — a home screen never shows an error.
- **Wrong city:** position comes from an IP lookup, so a VPN moves you. It's
  cached for a week; clearing app data re-fetches it.
- **Wrong units:** `weather_fahrenheit` in `config.xml`, not the system locale.
- **Wrong language:** phrasing follows the system locale. The box in question
  runs `en-US`, so it shows "Partly cloudy" even in Chile. `values-es/` has the
  Spanish set — switch the system language and it follows.

---

## `pm install` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

The APK on the box was signed with a different key. Either restore your
`launcher/keystore/tvhome.jks`, or uninstall first and lose nothing but the
remembered focus position:

```powershell
Sh "pm uninstall com.binario.tvhome"
```

---

## A firmware update undid everything

Expected, and harmless. Firmware updates are deliberately left enabled. An
update can re-enable packages and reset the default launcher. Re-run:

```powershell
.\debloat.ps1
. .\_env.ps1
Sh "cmd package set-home-activity com.binario.tvhome/.HomeActivity"
Sh "pm disable-user --user 0 com.google.android.tvlauncher"
```
