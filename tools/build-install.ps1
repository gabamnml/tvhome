# Build an APK with nothing but the installed Android SDK - no Gradle, no Maven.
#   aapt2 compile -> aapt2 link -> javac -> d8 -> zipalign -> apksigner -> pm install
#
#   .\build-install.ps1                       # build ..\launcher and install it
#   .\build-install.ps1 -Src ..\build\gate    # build something else
#   .\build-install.ps1 -NoInstall            # build only
#
# -Src must contain AndroidManifest.xml and java\, and may contain res\.
param(
    [string]$Src,
    [string]$Name = 'tvhome',
    [switch]$NoInstall,
    # Relaunch the launcher after installing, so you see the change immediately
    # instead of having to press HOME on the remote.
    [switch]$Restart
)

. "$PSScriptRoot\_env.ps1"

if (-not $Src) { $Src = $LAUNCHER }
$Src = (Resolve-Path $Src).Path
$manifest = Join-Path $Src 'AndroidManifest.xml'
if (-not (Test-Path $manifest)) { throw "no AndroidManifest.xml in $Src" }

# The work dir gets wiped on every build, so make very sure it is not the
# source tree (or a parent of it) before deleting anything.
$work = Join-Path $BUILD "_work\$Name"
foreach ($pair in @(@($work, $Src), @($Src, $work))) {
    $a = $pair[0].TrimEnd('\') + '\'
    $b = $pair[1].TrimEnd('\') + '\'
    if ($b.StartsWith($a, [StringComparison]::OrdinalIgnoreCase)) {
        throw "refusing to build: work dir '$work' and source dir '$Src' overlap"
    }
}
Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work, "$work\flat", "$work\gen", "$work\classes" | Out-Null

$aapt2    = Join-Path $BT 'aapt2.exe'
$zipalign = Join-Path $BT 'zipalign.exe'
$apksign  = Join-Path $BT 'apksigner.bat'

# Dexing deliberately does NOT use build-tools' own d8. The d8 bundled with
# build-tools 34.0.0 (R8 8.2.2-dev) throws
#     NullPointerException: Cannot invoke "String.length()"
# on any class that implements a generic interface - an anonymous
# Comparator<T>, a named `implements Comparator<T>`, all of it. AGP never hits
# this because it pulls a current R8 from Maven, which is what we do here too.
# Downloaded once, then cached in tools\lib\.
$r8jar = Join-Path $PSScriptRoot 'lib\r8.jar'
if (-not (Test-Path $r8jar)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $r8jar) | Out-Null
    $r8url = 'https://maven.google.com/com/android/tools/r8/8.7.18/r8-8.7.18.jar'
    Write-Host "fetching R8 (one time) from $r8url"
    Invoke-WebRequest -Uri $r8url -OutFile $r8jar -TimeoutSec 180 -UseBasicParsing
    if (-not (Test-Path $r8jar)) { throw "could not download R8 to $r8jar" }
}

# Oracle's javapath shim on PATH only exposes java/javac - keytool and jar are
# not there. Resolve the real JDK bin from java.home instead.
$javaHome = (& java -XshowSettings:properties -version 2>&1 |
             Select-String 'java\.home' | Select-Object -First 1) -replace '.*=\s*',''
$javaHome = $javaHome.Trim()
$keytool  = Join-Path $javaHome 'bin\keytool.exe'
$jar      = Join-Path $javaHome 'bin\jar.exe'
foreach ($t in @($keytool, $jar)) {
    if (-not (Test-Path $t)) { throw "missing $t - JDK bin not found under '$javaHome'" }
}

# --- a debug keystore, created once and kept so upgrades stay signature-compatible
$ksDir = Join-Path $LAUNCHER 'keystore'
$ks    = Join-Path $ksDir 'tvhome.jks'
$ksPass = 'tvhome'
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path $ksDir | Out-Null
    & $keytool -genkeypair -v -keystore $ks -storepass $ksPass -keypass $ksPass `
        -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=TV Home, OU=Dev, O=Local, L=., S=., C=CL" 2>&1 | Out-Null
    if (-not (Test-Path $ks)) { throw "keytool failed to create $ks" }
    "created keystore $ks"
}

# --- 1. resources (optional)
$resArgs = @()
$res = Join-Path $Src 'res'
if (Test-Path $res) {
    & $aapt2 compile --dir $res -o (Join-Path $work 'flat\res.zip')
    if ($LASTEXITCODE) { throw "aapt2 compile failed ($LASTEXITCODE)" }
    $resArgs = @((Join-Path $work 'flat\res.zip'))
}

# --- 2. link -> an APK with resources + a generated R.java
$unsigned = Join-Path $work "$Name-unsigned.apk"
$linkArgs = @(
    'link', '-o', $unsigned, '-I', $PLAT,
    '--manifest', $manifest,
    '--java', (Join-Path $work 'gen'),
    '--min-sdk-version', '28', '--target-sdk-version', '28',
    '--no-version-vectors'
)
$assets = Join-Path $Src 'assets'
# NOTE: assets are deliberately NOT passed to aapt2 with -A. On Windows aapt2
# writes the OS separator into the zip entry ("assets/fonts\outfit.ttf"), and
# AssetManager then can't find the file - the launcher silently fell back from
# Outfit to Roboto. They're added with jar below instead, which always uses "/".
& $aapt2 @linkArgs @resArgs
if ($LASTEXITCODE) { throw "aapt2 link failed ($LASTEXITCODE)" }

# --- 3. compile java (sources + generated R.java)
$srcs = @()
$srcs += (Get-ChildItem (Join-Path $Src 'java') -Recurse -Filter *.java | Select-Object -Exp FullName)
$srcs += (Get-ChildItem (Join-Path $work 'gen') -Recurse -Filter *.java -EA SilentlyContinue | Select-Object -Exp FullName)
$srcList = Join-Path $work 'sources.txt'
Set-Content -Path $srcList -Value $srcs -Encoding utf8

& javac -nowarn -source 8 -target 8 -bootclasspath $PLAT -encoding UTF-8 `
        -d (Join-Path $work 'classes') "@$srcList" 2>&1 |
    Where-Object { $_ -notmatch 'bootstrap class path|source value 8|target value 8|deprecat' }
if ($LASTEXITCODE) { throw "javac failed ($LASTEXITCODE)" }

# --- 4. dex
$classFiles = Get-ChildItem (Join-Path $work 'classes') -Recurse -Filter *.class | Select-Object -Exp FullName
& java -cp $r8jar com.android.tools.r8.D8 --min-api 28 --lib $PLAT --output $work @classFiles
if ($LASTEXITCODE) { throw "D8 failed ($LASTEXITCODE)" }
if (-not (Test-Path (Join-Path $work 'classes.dex'))) { throw "D8 produced no classes.dex" }

# --- 5. drop classes.dex into the apk
Push-Location $work
try {
    & $jar uf $unsigned classes.dex
    if ($LASTEXITCODE) { throw "adding classes.dex failed ($LASTEXITCODE)" }
} finally { Pop-Location }

# --- 5b. assets, with forward-slash entry names (see the note above)
if (Test-Path $assets) {
    Push-Location $Src
    try {
        $rel = Get-ChildItem $assets -Recurse -File |
               ForEach-Object { $_.FullName.Substring($Src.Length + 1).Replace('\', '/') }
        foreach ($f in $rel) {
            & $jar uf $unsigned $f
            if ($LASTEXITCODE) { throw "adding asset $f failed ($LASTEXITCODE)" }
        }
        "assets: $($rel -join ', ')"
    } finally { Pop-Location }
}

# --- 6. align + sign
$aligned = Join-Path $work "$Name-aligned.apk"
& $zipalign -f -p 4 $unsigned $aligned
if ($LASTEXITCODE) { throw "zipalign failed ($LASTEXITCODE)" }

$final = Join-Path $BUILD "$Name.apk"
& $apksign sign --ks $ks --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
    --min-sdk-version 28 --out $final $aligned
if ($LASTEXITCODE) { throw "apksigner failed ($LASTEXITCODE)" }

"built: $final  ({0:N0} bytes)" -f (Get-Item $final).Length

# --- 7. install. Push + `pm install` rather than `adb install`: on some builds
#     `adb install` sits waiting for an on-screen confirmation that never comes.
if (-not $NoInstall) {
    Connect-Tv | Out-Null
    Adb push $final /data/local/tmp/$Name.apk 2>&1 | Out-Null
    $out = Sh "pm install -r -t /data/local/tmp/$Name.apk"
    Sh "rm -f /data/local/tmp/$Name.apk" | Out-Null
    "pm install: $out"
    if ($out -notmatch 'Success') { throw "install failed: $out" }

    if ($Restart) {
        Sh "am force-stop com.binario.tvhome" | Out-Null
        Sh "am start -n com.binario.tvhome/.HomeActivity" | Out-Null
        "relaunched"
    }
}
