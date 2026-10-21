# Building without Gradle

An Android APK is a zip with a particular structure. Gradle is a very large
program for producing one. For a launcher with no dependencies, the whole
pipeline is six tools in a row, and running them directly turns a 40-second
build into a **7-second edit → install → relaunch cycle**.

```
res/  ──aapt2 compile──▶ res.zip ──aapt2 link──▶ unsigned.apk + R.java
                                                       │
java/ + R.java ──javac──▶ .class ──R8/D8──▶ classes.dex │
                                                       ▼
               assets ──jar──▶ zipalign ──▶ apksigner ──▶ tvhome.apk
                                                       │
                                            pm install -r -t
```

All of it is [`tools/build-install.ps1`](../tools/build-install.ps1).

```powershell
.\build-install.ps1            # build + install
.\build-install.ps1 -Restart   # ...and force-stop + relaunch
```

---

## The three traps

Each of these cost real time. They're worked around in the script and
commented there; this is the longer version.

### 1. build-tools 34's `d8` is broken

It throws

```
NullPointerException: Cannot invoke "String.length()" because "<local3>" is null
```

on **any class that implements a generic interface**. An anonymous
`Comparator<String>`, a named `implements Comparator<T>`, a nested one — all of
it. A raw `Comparator` is fine. An anonymous `Runnable` is fine.

This was proved with a five-shape probe and confirmed not to be a JDK problem
by re-running on Android Studio's bundled JBR 17. The bundled version is R8
`8.2.2-dev`; the bug is fixed upstream. AGP never hits it because AGP pulls a
current R8 from Maven — which is what the script does too:

```powershell
$r8jar = Join-Path $PSScriptRoot 'lib\r8.jar'
if (-not (Test-Path $r8jar)) {
    Invoke-WebRequest 'https://maven.google.com/com/android/tools/r8/8.7.18/r8-8.7.18.jar' -OutFile $r8jar
}
& java -cp $r8jar com.android.tools.r8.D8 --min-api 28 --lib $PLAT --output $work @classFiles
```

17 MB, downloaded once, cached in `tools/lib/` (git-ignored). **Don't switch
back to `$BT\d8.bat`.**

### 2. `aapt2 -A` writes Windows path separators into the zip

Assets passed with `-A` came out as `assets/fonts\outfit.ttf` — with a
backslash — which `AssetManager` cannot find. The launcher silently fell back
from Outfit to Roboto, with no error in logcat, no exception, nothing. It took
listing the APK's zip entries to find.

Assets are therefore added with `jar`, which always writes `/`. Verify after a
build:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [IO.Compression.ZipFile]::OpenRead("..\build\tvhome.apk")
$z.Entries | Where-Object { $_.FullName -match 'outfit' } | ForEach-Object { $_.FullName }
$z.Dispose()
```

It must print `assets/fonts/outfit.ttf`, with a forward slash.

### 3. Oracle's `javapath` shim only exposes `java` and `javac`

`keytool` and `jar` are not on `PATH` even when Java clearly is. Resolve the
real JDK bin from `java.home` instead of trusting `PATH`:

```powershell
$javaHome = (& java -XshowSettings:properties -version 2>&1 |
             Select-String 'java\.home' | Select-Object -First 1) -replace '.*=\s*',''
$keytool  = Join-Path $javaHome 'bin\keytool.exe'
$jar      = Join-Path $javaHome 'bin\jar.exe'
```

---

## The work directory guard

The script wipes its work directory with `Remove-Item -Recurse` before each
build. An early version put that directory *inside* a source tree and deleted
the sources it was about to compile. There is now an explicit guard that throws
rather than let the two overlap:

```powershell
$work = Join-Path $BUILD "_work\$Name"
foreach ($pair in @(@($work, $Src), @($Src, $work))) {
    # ...throw "work dir and source dir overlap"
}
```

If you repoint `-Src` or `$BUILD`, leave that guard in.

---

## Signing

A debug keystore is generated on the first build at
`launcher/keystore/tvhome.jks` (password `tvhome`) and kept, so later builds
stay signature-compatible and `pm install -r` works as an upgrade.

**It is git-ignored**, and it should stay that way — a signing key in a public
repository means every clone signs with the same key.

Keep your copy. Rebuilding with a different key means uninstalling first,
because Android refuses a signature change on upgrade:

```powershell
Sh "pm uninstall com.binario.tvhome"
```

---

## Setting the default launcher

`cmd package set-home-activity` alone is **not enough**. It reports `Success`
and HOME still resolves to whichever enabled launcher has the highest priority.
`pm clear-package-preferred-activities` doesn't help either. The stock launcher
has to be disabled as well — both steps are needed:

```powershell
Sh "cmd package set-home-activity com.binario.tvhome/.HomeActivity"
Sh "pm disable-user --user 0 com.google.android.tvlauncher"
```

This is why disabling `com.google.android.tvlauncher` is mandatory rather than
a matter of taste.

It is safe because `com.android.tv.settings` registers a `FallbackHome` at
priority **−1000**. The box can never be left with no home screen at all, even
if tvhome fails to start.

---

## Proving the box accepts a sideloaded HOME first

[`gate/`](../gate) is a throwaway APK: one activity, one `TextView`, declaring
`CATEGORY_HOME` and `CATEGORY_LEANBACK_LAUNCHER`. It exists to answer two
questions before any real work is done —

1. does this box accept an APK signed with a debug key at all?
2. can a sideloaded app become HOME here?

— because finding out *after* writing a launcher would be a bad afternoon. It
also smoke-tests the whole build pipeline on something small.

```powershell
.\build-install.ps1 -Src ..\gate -Name gate
```

---

## Verifying a change on the real device

The loop this project was built with:

```powershell
.\build-install.ps1 -Restart
.\key.ps1 DPAD_UP DPAD_RIGHT          # drive the remote
.\shot.ps1 whatever-changed           # pull a 1080p PNG
```

Then **look at the PNG**. Layout bugs on a TV are invisible from the code.

One warning: `adb shell input keyevent` is not proof of anything a *physical*
remote button does. It bypasses layers a real press goes through. If a change
affects the remote, press the remote.
