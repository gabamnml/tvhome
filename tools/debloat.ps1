# Disable packages for user 0, one at a time, verifying each one.
#
#   .\debloat.ps1 -Label telemetry -Packages com.miui.tv.analytics, tv.alphonso.alphonso_eula
#
# Rules this enforces, so they can't be forgotten:
#   * ONE `pm` command per adb shell call - chained commands can silently stop
#     after the first on this box.
#   * disable-user only. Never `pm uninstall` a system package.
#   * A package is only written to disabled.log once the device has CONFIRMED
#     it is disabled. restore.ps1 is generated from that log, so it can only
#     ever re-enable things we actually turned off.
param(
    [Parameter(Mandatory)][string[]]$Packages,
    [string]$Label = 'batch',
    [switch]$WhatIf
)

. "$PSScriptRoot\_env.ps1"
Connect-Tv | Out-Null

# Load-bearing on this hardware. Refuse to touch them whatever the caller says.
$PROTECTED = @(
    'android', 'com.android.systemui', 'com.android.settings.intelligence',
    'com.android.tv.settings',            # Settings + FallbackHome safety net
    'com.droidlogic',                     # Amlogic: display, audio, playback
    'com.droidlogic.ble',                 # Bluetooth remote
    'com.droidlogic.overlay',
    'com.android.bluetooth', 'com.android.inputdevices',
    'com.android.companiondevicemanager', # Bluetooth pairing
    'com.google.android.tv.remote.service',
    'com.google.android.inputmethod.latin', # on-screen keyboard
    'com.android.vending', 'com.google.android.gms', 'com.google.android.gsf',
    'com.google.android.packageinstaller', 'com.android.captiveportallogin',
    'com.google.android.webview', 'com.android.shell'
)

$results = @()
foreach ($pkg in $Packages) {
    $pkg = $pkg.Trim()
    if (-not $pkg) { continue }

    if ($PROTECTED -contains $pkg) {
        $results += [pscustomobject]@{ Package = $pkg; Result = 'REFUSED (protected)' }
        continue
    }

    $state = Get-PackageState $pkg
    if ($state -eq 'absent')   { $results += [pscustomobject]@{ Package = $pkg; Result = 'skip (not installed)' }; continue }
    if ($state -eq 'disabled') { $results += [pscustomobject]@{ Package = $pkg; Result = 'skip (already disabled)' }; continue }

    if ($WhatIf) { $results += [pscustomobject]@{ Package = $pkg; Result = 'would disable' }; continue }

    Sh "pm disable-user --user 0 $pkg" | Out-Null

    if ((Get-PackageState $pkg) -eq 'disabled') {
        Add-Content -Path $DISABLED_LOG -Value ("{0}`t{1}`t{2}" -f $pkg, $Label, (Get-Date -Format 's'))
        $results += [pscustomobject]@{ Package = $pkg; Result = 'disabled' }
    } else {
        $results += [pscustomobject]@{ Package = $pkg; Result = 'FAILED (still enabled)' }
    }
}

$results | Format-Table -AutoSize | Out-String -Width 80
