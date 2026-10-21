# Undo the debloat.
#
# This re-enables exactly what tools\debloat.ps1 recorded in backup\disabled.log
# - nothing more, nothing less. It is generated from a log of CONFIRMED disables,
# so it can't touch anything we didn't turn off.
#
#   .\restore.ps1                                  # put everything back
#   .\restore.ps1 -WhatIf                          # show what it would do
#   .\restore.ps1 -Packages com.netflix.ninja      # just one app
#   .\restore.ps1 -StockLauncher                   # also hand HOME back to Xiaomi/Google
#   .\restore.ps1 -All -StockLauncher              # full rollback to how the box shipped
param(
    [string[]]$Packages,
    [switch]$All,
    [switch]$StockLauncher,
    [switch]$WhatIf
)

. "$PSScriptRoot\_env.ps1"
Connect-Tv | Out-Null

if (-not (Test-Path $DISABLED_LOG)) {
    throw "no $DISABLED_LOG - nothing was recorded as disabled, so there's nothing to restore"
}

$logged = Get-Content $DISABLED_LOG |
          ForEach-Object { ($_ -split "`t")[0] } |
          Where-Object { $_ } |
          Select-Object -Unique

if ($Packages) {
    $unknown = $Packages | Where-Object { $logged -notcontains $_ }
    foreach ($u in $unknown) {
        Write-Warning "$u is not in disabled.log - we never disabled it, skipping"
    }
    $targets = $Packages | Where-Object { $logged -contains $_ }
} else {
    $targets = $logged
}

$results = foreach ($pkg in $targets) {
    $state = Get-PackageState $pkg
    if ($state -eq 'absent')  { [pscustomobject]@{ Package=$pkg; Result='skip (not installed)' }; continue }
    if ($state -eq 'enabled') { [pscustomobject]@{ Package=$pkg; Result='already enabled' };      continue }
    if ($WhatIf)              { [pscustomobject]@{ Package=$pkg; Result='would enable' };         continue }

    Sh "pm enable $pkg" | Out-Null
    [pscustomobject]@{
        Package = $pkg
        Result  = if ((Get-PackageState $pkg) -eq 'enabled') { 'enabled' } else { 'FAILED' }
    }
}

$results | Format-Table -AutoSize | Out-String -Width 80

if ($StockLauncher -and -not $WhatIf) {
    "handing HOME back to the stock launcher..."
    Sh "pm enable com.google.android.tvlauncher" | Out-Null
    Sh "cmd package set-home-activity com.google.android.tvlauncher/.MainActivity" | Out-Null
    # On this box set-home-activity alone is not enough - whichever launcher is
    # enabled with the highest priority wins - so take ours out of the running.
    Sh "pm disable-user --user 0 com.binario.tvhome" | Out-Null
    Sh "input keyevent KEYCODE_HOME" | Out-Null
    Start-Sleep -Seconds 3
    Sh "dumpsys activity activities" | Select-String 'mResumedActivity' | Select-Object -First 1
    ""
    "TV Home is now disabled, not uninstalled. To remove it completely:"
    "    adb -s $SERIAL uninstall com.binario.tvhome"
    "To bring it back:"
    "    adb -s $SERIAL shell pm enable com.binario.tvhome"
}

if (-not $WhatIf -and -not $Packages -and -not $StockLauncher) {
    ""
    "Everything we disabled is back. The stock launcher is still disabled -"
    "re-run with -StockLauncher if you also want HOME handed back to it."
}
