@echo off
chcp 65001 >nul
title HyperAssistant - 推送到 GitHub 自动打包
echo ========================================================
echo       正在将 HyperAssistant 推送到 GitHub 云端打包...
echo       目标仓库: https://github.com/bolano-2666/HyperAssistant
echo ========================================================
echo.

cd /d "C:\Users\Administrator\.gemini\antigravity\scratch\HyperAssistant"

echo 正在连接 GitHub 并上传代码...
echo (如果是第一次推送，系统会弹出网页让你点击授权 GitHub 登录)
echo.

git push -u origin main

echo.
if %ERRORLEVEL% equ 0 (
    echo ========================================================
    echo ✅ 推送成功！
    echo 🚀 GitHub 云端服务器已经开始自动为你打包 APK！
    echo 📱 请在浏览器打开以下网址查看进度并下载 APK：
    echo    https://github.com/bolano-2666/HyperAssistant/actions
    echo.
    echo    等待 1~2 分钟绿勾出现后，点进任务页面最下方下载：
    echo    👉 HyperAssistant-apk
    echo ========================================================
) else (
    echo.
    echo ❌ 推送未完成。常见原因：
    echo 1. 弹出了浏览器登录窗口尚未点击「Authorize」授权
    echo 2. 电脑尚未开启加速代理连接 GitHub
    echo.
    echo 授权完成后再次双击本脚本即可！
)

echo.
pause
