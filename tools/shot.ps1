# Grab a screenshot off the TV and drop it in ../screenshots.
#   .\shot.ps1 dock-focused
param([string]$Name = 'shot')

. "$PSScriptRoot\_env.ps1"
Connect-Tv | Out-Null

New-Item -ItemType Directory -Force -Path $SHOTS | Out-Null
$out = Join-Path $SHOTS ("{0}-{1}.png" -f $Name, (Get-Date -Format 'HHmmss'))

Sh 'screencap -p /sdcard/_shot.png' | Out-Null
Adb pull /sdcard/_shot.png $out 2>&1 | Out-Null
Sh 'rm -f /sdcard/_shot.png'  | Out-Null

if (-not (Test-Path $out)) { throw "screenshot failed - nothing landed at $out" }
$out
