# Shared environment for every tool in this folder.
# Dot-source it, don't run it:   . "$PSScriptRoot\_env.ps1"

$ErrorActionPreference = 'Stop'

# --- where your box and your SDK are ----------------------------------------
# Machine-specific settings live in device.local.ps1, which is NOT in git.
# Copy device.example.ps1 to device.local.ps1 and edit it. Environment
# variables win if the file doesn't set a value.
$localCfg = Join-Path $PSScriptRoot 'device.local.ps1'
if (Test-Path $localCfg) { . $localCfg }

if (-not $SERIAL) { $SERIAL = $env:TVHOME_SERIAL }
if (-not $SERIAL) {
    throw "No device set. Copy tools\device.example.ps1 to tools\device.local.ps1 " +
          "and put your TV's address in it (e.g. '192.168.1.50:5555'), or set " +
          "`$env:TVHOME_SERIAL."
}

if (-not $SDK) { $SDK = $env:TVHOME_SDK }
if (-not $SDK) { $SDK = $env:ANDROID_HOME }
if (-not $SDK) { $SDK = $env:ANDROID_SDK_ROOT }
if (-not $SDK) { $SDK = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not (Test-Path $SDK)) {
    throw "Android SDK not found at '$SDK'. Set `$SDK in tools\device.local.ps1 " +
          "or set `$env:ANDROID_HOME."
}

# IMPORTANT: use the SDK's adb, never whatever happens to be first on PATH.
# This machine has a 2018-era adb (v28.0.3, shipped with Touch Portal) ahead of
# the SDK's v34.0.5, and the old client mishandles modern devices. Any adb you
# run by hand should be the full path below.
$ADB = Join-Path $SDK 'platform-tools\adb.exe'
if (-not (Test-Path $ADB)) { throw "adb not found at '$ADB' - install SDK platform-tools." }

# Newest installed build-tools and platform, unless device.local.ps1 pinned one.
function Resolve-Newest([string]$dir, [string]$what) {
    if (-not (Test-Path $dir)) { throw "No $what found under '$dir'." }
    $hit = Get-ChildItem $dir -Directory | Sort-Object {
        # 'android-34' -> '34' -> '34.0', because [version] needs two parts.
        $n = $_.Name -replace '^android-', ''
        if ($n -notmatch '\.') { $n = "$n.0" }
        try { [version]$n } catch { [version]'0.0' }
    } | Select-Object -Last 1
    if (-not $hit) { throw "No $what found under '$dir'." }
    return $hit.FullName
}
if (-not $BT)   { $BT   = Resolve-Newest (Join-Path $SDK 'build-tools') 'build-tools' }
if (-not $PLAT) { $PLAT = Join-Path (Resolve-Newest (Join-Path $SDK 'platforms') 'platform') 'android.jar' }
if (-not (Test-Path $PLAT)) { throw "android.jar not found at '$PLAT'." }

$ROOT         = Split-Path $PSScriptRoot -Parent
$BACKUP       = Join-Path $ROOT 'backup'
$SHOTS        = Join-Path $ROOT 'screenshots'
$LAUNCHER     = Join-Path $ROOT 'launcher'
$BUILD        = Join-Path $ROOT 'build'
$DISABLED_LOG = Join-Path $BACKUP 'disabled.log'

# Run an adb subcommand against the TV:        Adb pull /sdcard/x.png .
function Adb { & $ADB -s $SERIAL @args }

# Run ONE shell command on the TV. Deliberately one command per call - chained
# commands can silently stop after the first one on this box.
function Sh([string]$cmd) { & $ADB -s $SERIAL shell $cmd }

# Make sure the TV is reachable and authorised before doing anything.
function Connect-Tv {
    & $ADB start-server 2>&1 | Out-Null
    $state = Get-TvState
    if ($state -ne 'device') {
        & $ADB connect $SERIAL 2>&1 | Out-Null
        Start-Sleep -Seconds 2
        $state = Get-TvState
    }
    if ($state -ne 'device') {
        throw "TV not ready (state='$state'). If it says 'unauthorized', accept the " +
              "'Allow USB debugging?' prompt on the TV and tick 'Always allow from this computer'."
    }
    "connected: $SERIAL"
}

function Get-TvState {
    $line = & $ADB devices 2>&1 | Select-String ([regex]::Escape($SERIAL))
    if (-not $line) { return 'absent' }
    return (($line.ToString() -split '\s+')[1])
}

# True if the package exists on the device at all.
function Test-Package([string]$pkg) {
    $hit = Sh "pm list packages $pkg" | Select-String ("^package:" + [regex]::Escape($pkg) + "$")
    return [bool]$hit
}

# 'enabled', 'disabled' or 'absent'.
function Get-PackageState([string]$pkg) {
    if (-not (Test-Package $pkg)) { return 'absent' }
    if (Sh "pm list packages -d $pkg" | Select-String ("^package:" + [regex]::Escape($pkg) + "$")) { return 'disabled' }
    return 'enabled'
}
