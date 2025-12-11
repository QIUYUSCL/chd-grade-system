# CHD 成绩管理系统

## 项目简介
这是一个功能全面的学生成绩管理系统，旨在简化学生成绩的录入、管理和查询流程。系统为管理员、教师和学生提供了不同的操作界面，确保了功能的针对性和安全性。

## 主要功能
-   **用户认证与授权**: 为管理员、教师和学生提供安全登录及基于角色的访问控制。
-   **学生成绩管理**:
    -   学生可以查看自己的各科成绩。
    -   教师可以录入和分析学生成绩。
-   **用户管理**:
    -   管理员可以管理系统中的所有用户账户。
-   **审计日志**: 记录关键操作，便于追踪和审计。
-   **数据安全**: 使用 AES 加密算法保护敏感数据。

## 系统架构
本系统采用经典的前后端分离架构，确保了前后端开发的独立性和灵活性。

### 前端 (`web-frontend`)
-   使用 **Vue.js** 框架开发，提供动态、交互式的用户界面。
-   负责处理用户交互、展示数据，并通过API与后端通信。
-   根据用户角色（`admin`, `student`, `teacher`）组织视图，提供定制化的用户体验。

### 后端 (`web-backend`)
-   基于 **Java Spring Boot** 构建，采用多模块项目结构:
    -   **客户端模块 (Client)**: 充当API网关或BFF（服务于前端的后端）。它处理来自前端的请求，管理用户认证 (JWT)，并在将请求转发到服务器模块之前进行鉴权。
    -   **服务端模块 (Server)**: 包含核心业务逻辑和数据访问层。它提供RPC风格的接口用于成绩操作、审计日志和数据查询，并直接与数据库交互。

## 技术栈
-   **前端**: Vue.js, Vite
-   **后端**: Java, Spring Boot, Maven
-   **安全**: JWT, BCrypt, AES 加密
-   **数据库**: (待指定 - 详情请查阅 `数据库说明文档.md`)

## 快速开始
请按照以下说明在本地设置并运行本系统。

### 环境准备
-   Java 开发工具包 (JDK) 8 或更高版本
-   Maven 3.x
-   Node.js 和 npm (或 yarn)
-   数据库系统 (例如 MySQL, PostgreSQL)

### 后端启动 (`web-backend`)
1.  进入 `web-backend` 目录。
2.  在 `web-backend/Client/src/main/resources/application.yml` 和 `web-backend/Server/src/main/resources/application.yml` 中配置你的数据库连接。
3.  构建并运行客户端和服务端模块:
    ```bash
    # 在 web-backend/Client 目录下
    mvn clean install
    mvn spring-boot:run

    # 在 web-backend/Server 目录下
    mvn clean install
    mvn spring-boot:run
    ```
    (请确保客户端和服务端都已成功启动并正在运行)

### 前端启动 (`web-frontend`)
1.  进入 `web-frontend` 目录。
2.  安装依赖:
    ```bash
    npm install
    # 或者 yarn install
    ```
3.  启动开发服务器:
    ```bash
    npm run dev
    # 或者 yarn dev
    ```
    启动后，你可以在浏览器中访问前端应用，地址通常是 `http://localhost:5173`。

## 相关文档
-   `数据库说明文档.md`: 详细的数据库表结构设计。
-   `项目结构.txt`: 项目的目录结构概览。