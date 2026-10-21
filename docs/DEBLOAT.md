# Debloating

> **Read this before running `debloat.ps1`.** The package list in it was written
> for a **Xiaomi Mi Box S on Android 9**. Most of it is harmless anywhere, some
> of it is Xiaomi-specific, and a box that isn't this one may need packages this
> one doesn't. Take a backup, go in batches, look at the screen.

Everything here is reversible. Nothing is uninstalled; packages are **disabled
for user 0**, which survives reboots and is undone with one command.

---

## How it works

```powershell
cd tools
.\backup.ps1        # snapshot first - always
.\debloat.ps1       # disable, one package per call, verifying each
.\restore.ps1       # put it all back
```

Four rules are built into the scripts, and they're the reason this is safe to
run on a TV you depend on:

1. **`pm disable-user --user 0`, never `pm uninstall`.** On a non-rooted device
   uninstalling a system app is either impossible or permanent until a factory
   reset.
2. **One `pm` command per shell call.** Chained commands silently stop after
   the first one on this box, which would leave the log claiming successes that
   never happened.
3. **Verify, then record.** After each disable, the package state is read back.
   Only confirmed successes are appended to `backup/disabled.log` — so
   `restore.ps1`, which reads that log and nothing else, can never be asked to
   re-enable something this tool never touched. (Verified: it correctly refused
   `com.netflix.ninja`, which was never disabled.)
4. **A hardcoded `$PROTECTED` list** that `debloat.ps1` refuses to disable even
   if you ask. See [below](#deliberately-left-alone).

---

## What was disabled, and why

27 packages, in six batches with a screenshot between each.

### Telemetry and automatic content recognition (6)

| Package | What it is |
|---|---|
| `com.miui.tv.analytics` | Xiaomi analytics. Held `READ_LOGS` + `INTERNET` + `ACCESS_FINE_LOCATION`. |
| `tv.alphonso.alphonso_eula` | **Alphonso** — the audio-fingerprinting ACR ad-targeting stack the Mi Box S shipped with. |
| `com.google.android.sss.authbridge` | Held `CAPTURE_AUDIO_HOTWORD` + `RECORD_AUDIO` + `CAPTURE_AUDIO_OUTPUT` + `READ_LOGS`. |
| `com.google.android.tv.bugreportsender` | Sends bug reports. |
| `com.google.android.feedback` | Crash / feedback reporting. |
| `com.google.android.partnersetup` | Partner referral tracking. |

### Xiaomi content, ads and the silent-install channel (8)

| Package | What it is |
|---|---|
| `mitv.service` | Held `INSTALL_PACKAGES` + `WRITE_SECURE_SETTINGS`. |
| `com.mitv.download.service` | Same pair of permissions. **This is how Xiaomi pushes apps onto the box.** |
| `com.mitv.tvhome.atv` | PatchWall. |
| `com.mitv.tvhome.michannel` | PatchWall content channels. |
| `com.xm.webcontent` | Web content feed. |
| `com.mitv.videoplayer` | Xiaomi's own player. |
| `com.xiaomi.android.tvsetup.partnercustomizer` | Partner customisation at setup. |
| `android.autoinstalls.config.xioami.mibox3` | Auto-install config. The typo is Xiaomi's, not ours. |

### Redundant background services (7)

`com.mitv.milinkservice` (Xiaomi's duplicate of Chromecast — Google Cast was
kept), `com.android.printspooler`, `com.android.camera2` (the box has no
camera), `com.google.android.syncadapters.contacts`,
`com.google.android.syncadapters.calendar`,
`com.google.android.onetimeinitializer`, `com.android.onetimeinitializer`.

### Apps the owner doesn't use (3)

`cl.movistarplay`, `com.google.android.videos` (Play Movies / Google TV),
`com.google.android.play.games`.

**Change this batch for your own box** — it's the only one that's a matter of
taste rather than hygiene.

### Launcher and screensaver (3)

`com.google.android.tvlauncher`, `com.google.android.tvrecommendations`,
`com.google.android.backdrop` (the ambient photo screensaver).

Disabling `tvlauncher` is **not optional** if you want tvhome to be HOME —
see [the set-home-activity note](BUILD.md#setting-the-default-launcher).

---

## Deliberately left alone

Nothing that a TV needs to work was touched:

| Kept | Why |
|---|---|
| `com.droidlogic`, `.ble`, `.overlay` | Amlogic: display, audio, **and the Bluetooth remote**. |
| `com.android.bluetooth` | Remote pairing. |
| `com.android.inputdevices` | Input. |
| `com.google.android.companiondevicemanager` | Remote pairing. |
| `com.google.android.tv.remote.service` | Remote service. |
| Gboard (`com.google.android.tvkeyboard` / latin IME) | The on-screen keyboard. Disable it and you cannot type a Wi-Fi password. |
| `com.android.vending`, `com.google.android.gms`, `com.google.android.gsf` | Play Store, Play Services, Services Framework. |
| `com.google.android.packageinstaller` | Installing anything, ever. |
| `com.android.tv.settings` | Settings — **and `FallbackHome`**, the safety net. |
| `com.android.captiveportallogin` | Hotel/café Wi-Fi. |
| `com.google.android.webview` | Half the apps on the box. |
| `katniss`, `speech.pumpkin` | Google Assistant (kept by choice). |
| `com.google.android.mediashell` | Chromecast (kept by choice). |
| Xiaomi update service | Firmware updates (kept by choice). |
| TalkBack | Accessibility. |

`debloat.ps1` carries these in `$PROTECTED` and refuses to disable them even if
you add them to a batch.

> **Firmware updates are still enabled.** A Xiaomi update may re-enable some of
> these and could reset the default launcher. Nothing is lost if that happens —
> re-run `debloat.ps1` and set HOME again.

---

## Rolling back

```powershell
.\restore.ps1 -WhatIf                        # what a full rollback would do
.\restore.ps1                                # re-enable all 27
.\restore.ps1 -Packages com.netflix.ninja    # re-enable just one
.\restore.ps1 -All -StockLauncher            # back to how the box shipped
```

`-All -StockLauncher` also re-enables `com.google.android.tvlauncher`, points
HOME back at it, and disables `com.binario.tvhome`.

`restore.ps1` warns and skips anything that isn't in `backup/disabled.log`.
That's the point of the log, not a limitation of it.

---

## Optional: ad-blocking DNS

Off by default. It blocks ad and tracker domains for every app on the box, over
DNS-over-TLS.

```powershell
.\private-dns.ps1 on          # AdGuard; then launches YouTube and screenshots it
.\private-dns.ps1 off         # undo
.\private-dns.ps1 status
```

**It will not block YouTube ads.** Those come from the same hostnames as the
video itself, so no DNS blocklist can separate them. Anyone telling you
otherwise is selling something.

Undo is `settings put global private_dns_mode opportunistic`, which is what
`off` does. Note that "opportunistic" is the Android *default* — encrypted DNS
when the network offers it, plain otherwise — and is **not** the same as the
"Off" option in the Settings UI.

---

## Turning ADB back off when you're done

Some Android TV devices cut USB port power while USB debugging is on. If a
keyboard/mouse dongle in the box's USB port has been flaky, this is why.

> TV → Settings → Device Preferences → About → press OK on **Build** seven
> times → back → **Developer options** → turn off **USB debugging** and
> **Network debugging**.

Turning it off does not undo any of the work above — it just means you need to
turn it back on before running these scripts again.

On the box this was built for, ADB over network on port 5555 was *already*
enabled before the project started, and came back by itself after a reboot. It
is persistent until you explicitly switch it off.

### Is an open port 5555 dangerous?

On this box, no — and it's worth knowing why, because the ADB worm family
(ADB.Miner, Ares and friends) is real. Those target devices shipped with
`ro.adb.secure=0`, where any host on the network is authorised automatically.
Check yours:

```powershell
Sh "getprop ro.adb.secure"    # must be 1
Sh "getprop ro.secure"        # must be 1
Sh "getprop ro.debuggable"    # must be 0
```

With `ro.adb.secure=1`, every new host has to be accepted by hand on the TV
screen via the *"Allow USB debugging?"* dialog. That's the whole defence, and
it holds. Switch debugging off anyway when you're done — defence in depth costs
you nothing here.
