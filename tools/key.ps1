# Inject remote key presses.
#   .\key.ps1 DPAD_DOWN DPAD_RIGHT DPAD_CENTER
#   .\key.ps1 HOME -DelayMs 1500
#
# Remember: an injected key proves the app reacts, not that the real remote
# button works. Anything that depends on the accessibility helper has to be
# confirmed by pressing the physical button.
param(
    [Parameter(Mandatory, ValueFromRemainingArguments)][string[]]$Keys,
    [int]$DelayMs = 450
)

. "$PSScriptRoot\_env.ps1"
Connect-Tv | Out-Null

foreach ($k in $Keys) {
    $code = if ($k -match '^\d+$') { $k } else { "KEYCODE_$($k -replace '^KEYCODE_','')" }
    Sh "input keyevent $code" | Out-Null
    Start-Sleep -Milliseconds $DelayMs
}
"sent: $($Keys -join ' ')"
