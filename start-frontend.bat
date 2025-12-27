@echo off
echo 启动长安大学成绩管理系统前端...
echo.

cd web-frontend

echo 检查依赖...
if not exist node_modules (
    echo 安装依赖中...
    npm install
)

echo 启动开发服务器...
npm run dev

pause