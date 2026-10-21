<#
.SYNOPSIS
    Snapshot the TV before you change anything.

.DESCRIPTION
    Dumps the package lists, all three settings namespaces, build properties,
    the HOME and LEANBACK_LAUNCHER claimants, TV input services and a few
    device facts into backup\. Nothing is modified on the device - every
    command here is a read.

    Run this FIRST, before debloat.ps1. It is what you fall back on if you
    ever need to work out what a package was doing before you touched it.

    backup\ is git-ignored: it contains your serial number, Wi-Fi MAC and
    android_id. Keep it, don't publish it.

.EXAMPLE
    .\backup.ps1
.EXAMPLE
    .\backup.ps1 -Tag before-debloat
    Writes into backup\before-debloat\ instead of backup\.
#>
[CmdletBinding()]
param(
    # Optional subfolder name, for taking a second snapshot without
    # overwriting the first.
    [string] $Tag
)

. "$PSScriptRoot\_env.ps1"

Connect-Tv

$dest = if ($Tag) { Join-Path $BACKUP $Tag } else { $BACKUP }
New-Item -ItemType Directory -Force -Path $dest | Out-Null

# name -> one shell command. Deliberately one command per call: chained
# commands can silently stop after the first one on this box.
$dumps = [ordered]@{
    'packages-all'         = 'pm list packages'
    'packages-enabled'     = 'pm list packages -e'
    'packages-disabled'    = 'pm list packages -d'
    'packages-system'      = 'pm list packages -s'
    'packages-thirdparty'  = 'pm list packages -3'
    'packages-uninstalled' = 'pm list packages -u'
    'packages-with-paths'  = 'pm list packages -f'
    'getprop'              = 'getprop'
    'settings-global'      = 'settings list global'
    'settings-secure'      = 'settings list secure'
    'settings-system'      = 'settings list system'
    'users'                = 'pm list users'
    'features'             = 'pm list features'
    'meminfo'              = 'cat /proc/meminfo'
    'home-activities'      = 'cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME'
    'leanback-launchers'   = 'cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER'
    'tv-input-services'    = 'cmd package query-services -a android.media.tv.TvInputService'
}

foreach ($name in $dumps.Keys) {
    $out  = Join-Path $dest "$name.txt"
    $text = Sh $dumps[$name]
    $text | Out-File -FilePath $out -Encoding utf8
    $n = @($text).Count
    "{0,-22} {1,5} lines" -f $name, $n
}

# Two-liners that take more than one command.
(@(Sh 'wm size'), @(Sh 'wm density')) |
    ForEach-Object { $_ } |
    Out-File -FilePath (Join-Path $dest 'display.txt') -Encoding utf8
"{0,-22} {1,5} lines" -f 'display', 2

(@(Sh 'settings get secure enabled_accessibility_services'),
 @(Sh 'settings get secure accessibility_enabled')) |
    ForEach-Object { $_ } |
    Out-File -FilePath (Join-Path $dest 'accessibility.txt') -Encoding utf8
"{0,-22} {1,5} lines" -f 'accessibility', 2

""
"snapshot written to $dest"

# disabled.log is NOT written here. It is the debloat journal: debloat.ps1
# appends to it only after it has verified a package really is disabled, and
# restore.ps1 will re-enable nothing that isn't in it. Creating it here with
# the box's pre-existing disabled packages would make restore.ps1 offer to
# re-enable things this project never touched.
if (-not (Test-Path $DISABLED_LOG)) {
    "(no disabled.log yet - debloat.ps1 creates it)"
}
