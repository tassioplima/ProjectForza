<#
.SYNOPSIS
    Builds and installs the Forza Gallery APK on a connected Android device.

.DESCRIPTION
    This script:
      1. Locates the Android SDK / ADB on the machine
      2. Checks that at least one Android device is connected via USB (debug mode)
      3. Optionally builds the APK with Gradle (skip with -SkipBuild)
      4. Installs the APK with "adb install -r"

.PARAMETER SkipBuild
    Skip the Gradle build and use whatever APK already exists in the output folder.

.EXAMPLE
    # Full build + install
    powershell -ExecutionPolicy Bypass -File .\install.ps1

    # Install only (APK already built)
    powershell -ExecutionPolicy Bypass -File .\install.ps1 -SkipBuild

.NOTES
    Requirements:
      - Android SDK installed (ANDROID_HOME or ANDROID_SDK_ROOT set, or default path)
      - USB Debugging enabled on the Android device
      - Java 17+ in PATH (or JAVA_HOME pointing to Android Studio's JBR)
#>

param(
    [switch]$SkipBuild
)

# Self-relaunch with Bypass if execution policy would block us
if ($PSVersionTable -and -not ($ExecutionContext.SessionState.LanguageMode -eq 'FullLanguage' -and $PSScriptRoot)) {
    # Running inline; this is fine
}

$ErrorActionPreference = "Stop"

# -- Colour helpers ------------------------------------------------------------
function Write-Step  { param($msg) Write-Host "  >> $msg" -ForegroundColor Cyan }
function Write-OK    { param($msg) Write-Host "  OK  $msg" -ForegroundColor Green }
function Write-Fail  {
    param($msg)
    Write-Host " ERR  $msg" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=======================================" -ForegroundColor Blue
Write-Host "   Forza Gallery - Install Script      " -ForegroundColor Blue
Write-Host "=======================================" -ForegroundColor Blue
Write-Host ""

# -- 1. Locate Android SDK -----------------------------------------------------
Write-Step "Locating Android SDK..."

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot) { $sdkRoot = "$env:LOCALAPPDATA\Android\Sdk" }

if (-not (Test-Path $sdkRoot)) {
    Write-Fail "Android SDK not found. Set ANDROID_HOME to your SDK path."
}
Write-OK "SDK: $sdkRoot"

$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    Write-Fail "ADB not found at '$adb'. Make sure platform-tools are installed."
}
Write-OK "ADB: $adb"

# -- 2. Check device is connected ---------------------------------------------
Write-Step "Checking connected devices..."

$devices = & $adb devices 2>&1 | Select-String "device$"
if (-not $devices) {
    Write-Fail @"
No Android device found.

To fix:
  1. Enable Developer Options on your phone
     Settings -> About phone -> tap Build number 7 times
  2. Enable USB Debugging
     Settings -> Developer options -> USB debugging -> ON
  3. Connect the phone via USB and accept the 'Allow USB debugging?' prompt
"@
}
Write-OK "Device connected: $($devices -join ', ')"

# -- 3. Locate / build the APK ------------------------------------------------
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not $SkipBuild) {
    Write-Step "Building APK with Gradle (this may take a minute)..."

    # Ensure Java is available
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        $jbrPath = "C:\Program Files\Android\Android Studio\jbr"
        if (Test-Path $jbrPath) {
            $env:JAVA_HOME = $jbrPath
            $env:PATH = "$jbrPath\bin;$env:PATH"
            Write-OK "Using Android Studio JBR at $jbrPath"
        } else {
            Write-Fail "Java not found. Add Java 17+ to PATH or set JAVA_HOME."
        }
    }

    $gradlew = ".\gradlew.bat"
    if (-not (Test-Path $gradlew)) {
        Write-Fail "gradlew.bat not found. Run this script from the project root directory."
    }

    & $gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) { Write-Fail "Gradle build failed (exit code $LASTEXITCODE)." }
    Write-OK "Build successful."
} else {
    Write-Step "Skipping build (-SkipBuild flag set)."
}

if (-not (Test-Path $apkPath)) {
    Write-Fail "APK not found at '$apkPath'. Run without -SkipBuild to build first."
}
Write-OK "APK: $apkPath"

# -- 4. Install the APK -------------------------------------------------------
Write-Step "Installing APK on device..."

$result = & $adb install -r $apkPath 2>&1
if ($LASTEXITCODE -ne 0 -or ($result -match "FAILED")) {
    Write-Fail "Installation failed:`n$result"
}

Write-OK "Installed successfully!"
Write-Host ""
Write-Host "  Forza Gallery is now on your device." -ForegroundColor Green
Write-Host "  Open it and log in with your Microsoft account." -ForegroundColor Green
Write-Host ""
