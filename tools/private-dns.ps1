# Optional extra: network-wide ad blocking via Android's Private DNS (DNS-over-TLS).
#
#   .\private-dns.ps1 status
#   .\private-dns.ps1 on                 # AdGuard DNS
#   .\private-dns.ps1 on -Hostname dns.nextdns.io
#   .\private-dns.ps1 off                # back to the default (opportunistic)
#
# WHAT THIS DOES AND DOESN'T DO
#   It blocks requests to known ad and tracker domains for every app on the box.
#   It does NOT block YouTube ads - those are served from the same hostnames as
#   the video itself, so no DNS blocklist can separate them. Expect it to quieten
#   trackers and banner ads in other apps, not to clean up YouTube.
#
# Requires Android 9 or newer. This box is Android 9, so it's supported.
param(
    [Parameter(Position = 0)]
    [ValidateSet('on', 'off', 'status')]
    [string]$Action = 'status',

    [string]$Hostname = 'dns.adguard-dns.com',

    # Launch an app afterwards and screenshot it, to prove playback still works.
    [string]$VerifyWith = 'com.google.android.youtube.tv'
)

. "$PSScriptRoot\_env.ps1"
Connect-Tv | Out-Null

function Show-Status {
    $mode = (Sh 'settings get global private_dns_mode') -join ''
    $spec = (Sh 'settings get global private_dns_specifier') -join ''
    "private_dns_mode      : $mode"
    "private_dns_specifier : $spec"
}

switch ($Action) {
    'status' { Show-Status }

    'on' {
        Sh "settings put global private_dns_specifier $Hostname" | Out-Null
        Sh "settings put global private_dns_mode hostname" | Out-Null
        Start-Sleep -Seconds 3
        Show-Status
        ""
        "Reminder: this does not block YouTube ads. Undo with:  .\private-dns.ps1 off"

        if ($VerifyWith) {
            ""
            "checking $VerifyWith still loads..."
            Sh "monkey -p $VerifyWith -c android.intent.category.LEANBACK_LAUNCHER 1" 2>&1 | Out-Null
            Start-Sleep -Seconds 12
            $shot = & "$PSScriptRoot\shot.ps1" "private-dns-on-$VerifyWith"
            "screenshot: $shot"
            "look at it - if the app can't reach the network, turn this back off."
            Sh "input keyevent KEYCODE_HOME" | Out-Null
        }
    }

    'off' {
        # 'opportunistic' is the Android default: encrypted DNS if the network
        # offers it, plain DNS otherwise. Not 'off', which disables DoT entirely.
        Sh "settings put global private_dns_mode opportunistic" | Out-Null
        Start-Sleep -Seconds 2
        Show-Status
    }
}
