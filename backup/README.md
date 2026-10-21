# backup/

`tools\backup.ps1` writes your device snapshot here, and `tools\debloat.ps1`
writes `disabled.log` here. **Everything in this folder except this file is
git-ignored**, and that is deliberate: the dumps contain your box's serial
number, Wi-Fi MAC address, `android_id`, device name and full settings
namespaces. None of that should end up in a public repository.

Keep the folder. `restore.ps1` cannot roll anything back without it.

| File | What's in it |
|---|---|
| `disabled.log` | The debloat journal: `package`, `reason`, `timestamp`, one per line. **This is the only file `restore.ps1` reads.** It is appended to only after `debloat.ps1` has confirmed the package really went to `disabled`, so a failed disable never becomes a restore instruction. |
| `packages-*.txt` | `pm list packages` in six flavours — all, enabled, disabled, system, third-party, uninstalled — plus `-f` for APK paths. |
| `settings-{global,secure,system}.txt` | All three settings namespaces, verbatim. |
| `getprop.txt` | Every build property. |
| `home-activities.txt` | Who claimed `CATEGORY_HOME` before you changed it. |
| `leanback-launchers.txt` | Everything that declares `CATEGORY_LEANBACK_LAUNCHER` — i.e. every app the launcher will show. |
| `tv-input-services.txt` | TV input services. Worth checking before disabling anything tuner-shaped. |
| `features.txt`, `users.txt`, `meminfo.txt`, `display.txt`, `accessibility.txt` | Device facts. |
