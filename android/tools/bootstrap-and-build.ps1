$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root
Write-Host "[1/6] Static audit"
python .\tools\audit_project.py
if ($LASTEXITCODE -ne 0) { throw "Static audit failed" }

Write-Host "[2/6] Java"
java -version

# Locate SDK
if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
if (-not $env:ANDROID_SDK_ROOT) {
  $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk"
  if (Test-Path $candidate) { $env:ANDROID_SDK_ROOT = $candidate }
}
if (-not $env:ANDROID_SDK_ROOT -or -not (Test-Path $env:ANDROID_SDK_ROOT)) {
  throw "Android SDK not found. Install Android Studio/SDK and set ANDROID_SDK_ROOT."
}
Write-Host "[3/6] Android SDK: $env:ANDROID_SDK_ROOT"

# Install required SDK packages when sdkmanager exists
$sdkmanager = Join-Path $env:ANDROID_SDK_ROOT "cmdline-tools\latest\bin\sdkmanager.bat"
if (Test-Path $sdkmanager) {
  Write-Host "Installing/checking required SDK packages..."
  & $sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
  if ($LASTEXITCODE -ne 0) { throw "SDK installation failed" }
} else {
  Write-Warning "sdkmanager.bat not found under cmdline-tools\latest. Assuming SDK packages are installed by Android Studio."
}

Write-Host "[4/6] Gradle wrapper"
$wrapperJar = Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
  $gradle = Get-Command gradle -ErrorAction SilentlyContinue
  if ($gradle) {
    gradle wrapper --gradle-version 9.6.0
  } else {
    Write-Host "System Gradle not found. Downloading official Gradle 9.6.0..."
    $tools = Join-Path $Root ".build-tools"
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools "gradle-9.6.0-bin.zip"
    $gradleHome = Join-Path $tools "gradle-9.6.0"
    if (-not (Test-Path $zip)) {
      Invoke-WebRequest -UseBasicParsing "https://services.gradle.org/distributions/gradle-9.6.0-bin.zip" -OutFile $zip
    }
    if (-not (Test-Path $gradleHome)) {
      Expand-Archive -Path $zip -DestinationPath $tools -Force
    }
    $localGradle = Join-Path $gradleHome "bin\gradle.bat"
    if (-not (Test-Path $localGradle)) { throw "Downloaded Gradle is incomplete: $localGradle" }
    & $localGradle wrapper --gradle-version 9.6.0
    if ($LASTEXITCODE -ne 0) { throw "Gradle wrapper generation failed" }
  }
}

Write-Host "[5/6] Assemble Debug APK"
& .\gradlew.bat --no-daemon --stacktrace clean :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }

Write-Host "[6/6] Collect artifact"
$apk = Join-Path $Root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "Build reported success but APK not found: $apk" }
New-Item -ItemType Directory -Force -Path (Join-Path $Root "artifacts") | Out-Null
$out = Join-Path $Root "artifacts\LocalLS-v0.9.1-debug.apk"
Copy-Item $apk $out -Force
python .\tools\verify_apk.py $out --sdk $env:ANDROID_SDK_ROOT
if ($LASTEXITCODE -ne 0) { throw "APK validation failed" }
$hash = (Get-FileHash $out -Algorithm SHA256).Hash.ToLower()
$size = (Get-Item $out).Length
@"
# BUILD REPORT

- Status: BUILD SUCCESSFUL
- APK: artifacts/LocalLS-v0.9.1-debug.apk
- Size: $size bytes
- Gradle: 9.6.0
- Android Gradle Plugin: 9.4.1
- compileSdk / minSdk / targetSdk: 37 / 24 / 37
- Build Tools: 36.0.0
- JDK: $(java -version 2>&1 | Select-Object -First 1)
- Validation: tools/verify_apk.py; static audit; assembleDebugAndroidTest; lintDebug
- Device tests: not executed by this script
- SHA-256: $hash
- Built: $(Get-Date -Format o)
"@ | Set-Content -Encoding UTF8 (Join-Path $Root "BUILD_REPORT.md")
Write-Host "DONE: $out"
Write-Host "SHA-256: $hash"
