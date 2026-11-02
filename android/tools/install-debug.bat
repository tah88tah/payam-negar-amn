@echo off
setlocal
if "%~1"=="" (
  echo Usage: tools\install-debug.bat path\to\app-debug.apk
  exit /b 2
)
adb devices
adb install -r -g "%~1"
if errorlevel 1 exit /b 1
echo Installed. Open the app and verify SMS and notification permissions on this phone.
