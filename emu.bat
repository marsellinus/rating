@echo off
REM RATIG - helper harian untuk emulator Android (emulator-5554)
REM Pakai: emu.bat start | emu.bat install | emu.bat clear | emu.bat log | emu.bat shot

set ADB=adb -s emulator-5554
set APK=%~dp0app\build\outputs\apk\debug\app-debug.apk

if "%1"=="start" (
  %ADB% shell am force-stop com.ratig.app
  %ADB% shell am start -n com.ratig.app/.MainActivity
  echo [OK] aplikasi dibuka
  goto :eof
)

if "%1"=="install" (
  %ADB% install -r "%APK%"
  echo [OK] APK terpasang
  goto :eof
)

if "%1"=="clear" (
  %ADB% shell pm clear com.ratig.app
  echo [OK] data aplikasi dihapus (setup ulang)
  goto :eof
)

if "%1"=="log" (
  %ADB% logcat -c
  echo [INFO] logcat dibersihkan. Buka aplikasi, lalu jalankan: emu.bat logcat
  goto :eof
)

if "%1"=="logcat" (
  %ADB% logcat -v time | findstr /I "RATIG AndroidRuntime Exception FATAL"
  goto :eof
)

if "%1"=="shot" (
  %ADB% exec-out screencap -p > "%~dp0_emu.png"
  echo [OK] screenshot: _emu.png
  goto :eof
)

echo Pakai: emu.bat start ^| install ^| clear ^| log ^| logcat ^| shot
