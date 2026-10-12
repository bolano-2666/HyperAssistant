@echo off
chcp 65001 >nul
echo ========================================================
echo       HyperAssistant 超灵助手 - GitHub 云端自动打包推送工具
echo ========================================================
echo.
echo 【第 1 步】请先在浏览器打开 https://github.com/new 创建一个新仓库
echo          （仓库名称随意，比如填 HyperAssistant，设置为 Public 或 Private 均可，不要勾选 Initialize with README）
echo.
set /p REPO_URL="【第 2 步】请复制并在此粘贴你的 GitHub 仓库地址 (回车确认): "

if "%REPO_URL%"=="" (
    echo [错误] 仓库地址不能为空！
    pause
    exit /b
)

echo.
echo 正在连接你的 GitHub 仓库并推送代码...
git remote remove origin 2>nul
git remote add origin %REPO_URL%
git branch -M main
git push -u origin main

if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo ✅ 推送成功！
    echo 🚀 GitHub 云端服务器已经开始自动为你烘焙打包 APK！
    echo 📱 请打开你的 GitHub 仓库页面，点击顶部的 "Actions" 标签页
    echo    等待 1~2 分钟绿勾出现后，点击进去即可直接下载打包好的 app-debug.apk！
    echo ========================================================
) else (
    echo.
    echo ❌ 推送失败，请检查网络或是否已登录 GitHub 账号凭据。
)

echo.
pause
