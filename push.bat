@echo off
title Pushing HyperAssistant to GitHub...
cd /d "%~dp0"
echo ========================================================
echo  HyperAssistant - Auto Push to GitHub
echo  Repository: https://github.com/bolano-2666/HyperAssistant
echo ========================================================
echo.
echo Connecting to GitHub and uploading files...
echo (If this is your first time, a browser window will pop up to sign in to GitHub)
echo.

git push -u origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo  SUCCESS! Push completed!
    echo  GitHub Actions cloud server is now building the APK.
    echo  Opening download page in your browser...
    echo ========================================================
    start https://github.com/bolano-2666/HyperAssistant/actions
) else (
    echo.
    echo  Push not completed. Please authorize in your browser and run again.
)

echo.
pause
