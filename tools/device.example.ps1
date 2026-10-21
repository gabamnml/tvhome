# Copy this file to  device.local.ps1  and edit it. That copy is git-ignored,
# so your IP address and SDK path never leave your machine.
#
#     copy device.example.ps1 device.local.ps1
#
# Every script in this folder dot-sources _env.ps1, which dot-sources this.

# --- REQUIRED ---------------------------------------------------------------
# Your TV's ADB address. Find it on the box under
#   Settings -> Network & Internet -> (your network) -> IP address
# Port 5555 is the default for ADB over network.
$SERIAL = '192.168.1.50:5555'

# A USB-connected device works too - use the serial from `adb devices`:
# $SERIAL = 'ABCDEF0123456789'

# --- OPTIONAL ---------------------------------------------------------------
# Where your Android SDK lives. Leave commented and _env.ps1 will try, in order:
#   $env:TVHOME_SDK, $env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
#   %LOCALAPPDATA%\Android\Sdk
# $SDK = "C:\Users\you\AppData\Local\Android\Sdk"

# Pin a specific build-tools / platform. Leave commented to use the newest
# installed, which is what the build was developed against (34.0.0 / android-34).
# $BT   = Join-Path $SDK 'build-tools\34.0.0'
# $PLAT = Join-Path $SDK 'platforms\android-34\android.jar'
