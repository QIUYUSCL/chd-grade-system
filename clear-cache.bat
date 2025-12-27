@echo off
echo 清理前端缓存和重新启动...
echo.

cd web-frontend

echo 停止开发服务器...
taskkill /f /im node.exe 2>nul

echo 清理缓存...
if exist node_modules\.vite rmdir /s /q node_modules\.vite
if exist dist rmdir /s /q dist
if exist .vite rmdir /s /q .vite

echo 清理 npm 缓存...
npm cache clean --force

echo 重新安装依赖...
npm install

echo 启动开发服务器...
npm run dev

pause