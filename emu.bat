@echo off
REM RATIG - helper emulator (emulator-5554)
set ADB=adb -s emulator-5554
if "%1"=="start" (%ADB% shell am force-stop com.ratig.app & %ADB% shell am start -n com.ratig.app/.MainActivity & goto :eof)
if "%1"=="install" (%ADB% install -r app\build\outputs\apk\debug\app-debug.apk & goto :eof)
if "%1"=="clear" (%ADB% shell pm clear com.ratig.app & goto :eof)
if "%1"=="shot" (%ADB% exec-out screencap -p > _emu.png & goto :eof)
echo Pakai: emu.bat start ^| install ^| clear ^| shot
