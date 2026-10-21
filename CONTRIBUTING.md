# Contributing

This project exists because an Android TV box was annoying and the fix had to
be reversible. That's the whole design brief, and it's worth keeping.

## The rules that aren't negotiable

These are not style preferences — they're the reason the thing is safe to run
on a TV you actually use.

1. **Never uninstall a system package.** `pm disable-user --user 0` only.
   Uninstalling a system app on a non-rooted device is either impossible or
   permanent-until-factory-reset, and neither is a good outcome.
2. **Never disable what a TV can't live without.** The remote and Bluetooth
   stack, input devices, the on-screen keyboard, Play Store, Play Services,
   the package installer, WebView, Settings, captive-portal login, TV input
   services. `debloat.ps1` keeps a `$PROTECTED` list — add to it, don't remove
   from it.
3. **One `pm` command per shell call.** Chained commands silently stop after
   the first one on some boxes. Every helper in `_env.ps1` takes exactly one.
4. **Verify, then record.** `debloat.ps1` re-reads the package state after each
   disable and appends to `backup/disabled.log` only on confirmed success, so
   `restore.ps1` can never be asked to re-enable something that was never
   disabled.
5. **No dependencies.** No Gradle, no AndroidX, no Maven coordinates in the
   launcher. If you need a library, write the forty lines instead — that's what
   `ColorSampler` and `Spring` are.
6. **Stay cheap.** The target has ~1 GB of RAM free. No blur, no video
   wallpaper, no background polling, no work on the main thread you wouldn't
   want to run at 60 fps.
7. **D-pad movement is deterministic.** Every `nextFocus*` edge is set by hand.
   If you add a focusable view, wire all four of its edges and clamp the ends
   so focus can't fall through somewhere surprising.

## Working on the launcher

```powershell
cd tools
copy device.example.ps1 device.local.ps1   # once
.\build-install.ps1 -Restart               # ~7 seconds, edit to running app
.\shot.ps1 whatever                        # pull a screenshot
.\key.ps1 DPAD_DOWN DPAD_RIGHT DPAD_CENTER # inject remote keys
```

**Look at the screenshot.** Every visual change in this repo was verified by
injecting keys, pulling a PNG and actually looking at it. Layout bugs on a TV
are invisible from the code — a clipped `PM`, a highlight that's 3dp tighter on
the left than the top. If you change something on screen, attach the before and
after to your PR.

**An injected key is not proof for anything the remote does.** `adb shell
input keyevent` bypasses layers that a real button press goes through. If your
change affects how the physical remote behaves, press the physical remote.

## Working on the debloat list

The package list in `debloat.ps1` is specific to a Xiaomi Mi Box S on Android 9.
If you're adding packages for another device:

- Check `dumpsys package <name>` before judging anything with a manufacturer
  prefix. Look at what permissions it holds and what it declares. Several
  Xiaomi packages on this box turned out to hold `INSTALL_PACKAGES` and
  `WRITE_SECURE_SETTINGS`; a couple of innocuous-looking Google ones held
  `RECORD_AUDIO`.
- Disable in small batches with a screenshot between them, so when something
  breaks you know which batch did it.
- Say *why* in the reason field. "redundant-services" is a reason;
  "bloat" is not.

## Commits and PRs

- Explain the *why* in the commit body, especially for a workaround. Half the
  value in this repo is the comments explaining why `d8` is not used and why
  assets aren't passed to `aapt2` with `-A`.
- Keep `docs/` in step with the code. If you add a knob, it goes in
  `config.xml` with a comment and in the table in `README.md`.
- Don't commit `backup/`, `build/`, `tools/device.local.ps1`,
  `tools/lib/r8.jar` or `launcher/keystore/`. `.gitignore` already covers them;
  check `git status` before you push anyway.

## Reporting a bug

Include: the box (model and Android version), `adb shell getprop ro.build.version.release`,
what you ran, what happened, and a screenshot if it's visual. If the launcher
crashed, `adb logcat -d | findstr tvhome` is worth a thousand words.
