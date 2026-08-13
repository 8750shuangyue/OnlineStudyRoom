@echo off
chcp 65001 >nul
title 网页版自习室 - 一键启动
cd /d "%~dp0"

echo ============================================
echo   网页版自习室 一键启动
echo   (后端 8081 + 前端 5173)
echo ============================================
echo.

rem ---------- 后端 8081 ----------
netstat -ano | findstr ":8081" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo [OK] 后端已在运行  http://localhost:8081
) else (
    echo [..] 后端未运行，正在启动 mvn spring-boot:run ...
    start "study-room-backend" cmd /k "cd /d %~dp0 && mvn -B spring-boot:run"
)

echo.

rem ---------- 前端 5173 ----------
netstat -ano | findstr ":5173" | findstr "LISTENING" >nul
if %errorlevel%==0 (
    echo [OK] 前端已在运行  http://localhost:5173
) else (
    echo [..] 前端未运行，正在启动 npm run dev ...
    start "study-room-frontend" cmd /k "cd /d %~dp0client && npm run dev"
)

echo.
echo 等待服务就绪（首次启动后端可能需要 20-40 秒）...
timeout /t 10 /nobreak >nul
start http://localhost:5173/
echo.
echo 浏览器已打开 http://localhost:5173/  (接口代理到后端 8081)
echo 提示：关闭本窗口不会停止服务；需要停止时，关闭标题为
echo       study-room-backend / study-room-frontend 的窗口即可。
echo.
pause
