@echo off
setlocal
rem Applies Claude's PixelPlayer changes (lyrics, search, video, App Lock, tabs) as 5 git commits.
cd /d "%~dp0"
set "GIT=git"
where git >nul 2>nul
if errorlevel 1 (
  rem No git on PATH: use the copy bundled with GitHub Desktop.
  for /d %%D in ("%LOCALAPPDATA%\GitHubDesktop\app-*") do if exist "%%D\resources\app\git\cmd\git.exe" set "GIT=%%D\resources\app\git\cmd\git.exe"
)
"%GIT%" --version >nul 2>nul
if errorlevel 1 (
  echo Could not find git. Install Git for Windows or GitHub Desktop, then run this again.
  pause
  exit /b 1
)
echo Applying changes with "%GIT%" ...
"%GIT%" am -3 --ignore-whitespace "Claude outputs\pixelplayer-all-changes.patch"
if errorlevel 1 (
  echo.
  echo Something did not apply cleanly. Nothing was left half-done:
  "%GIT%" am --abort
  echo Tell Claude what the messages above say.
  pause
  exit /b 1
)
echo.
echo Done. 5 commits added:
"%GIT%" log --oneline -5
echo.
echo Now rebuild the app in Android Studio.
pause
