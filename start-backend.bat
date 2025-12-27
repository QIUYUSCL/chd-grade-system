@echo off
echo 启动长安大学成绩管理系统后端...
echo.

echo 启动 Server 模块...
start "CHD-Server" cmd /k "cd web-backend/Server && mvn spring-boot:run"

echo 等待 Server 模块启动...
timeout /t 10

echo 启动 Client 模块...
start "CHD-Client" cmd /k "cd web-backend/Client && mvn spring-boot:run"

echo 后端服务启动中...
echo Server 模块: http://localhost:8081
echo Client 模块: http://localhost:8080
echo.
pause