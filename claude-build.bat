@echo off
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Android\Android Studio2\jbr"
echo Building... > "Claude outputs\build.log"
echo Writing the build log to "Claude outputs\build.log" ...
call gradlew.bat assembleDebug --console=plain >> "Claude outputs\build.log" 2>&1
echo EXIT=%ERRORLEVEL% >> "Claude outputs\build.log"
type "Claude outputs\build.log" | findstr /C:"e: " /C:"EXIT=" /C:"BUILD"
pause
