# NoteX

NoteX 是一个面向个人使用场景的私人笔记云仓，后端基于 Spring Boot 3.5.13 与 Spring AI 1.1.4，前端基于 Vue 3 与 Tailwind CSS，提供笔记管理、AI 对话、RAG 检索与静态资源管理能力。

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite
- Tailwind CSS
- md-editor-v3

### 后端

- Java 21
- Spring Boot 3.5.13
- Spring AI 1.1.4
- MyBatis-Plus
- PostgreSQL 16 + pgvector
- Redis 8.6.1

### 部署

- Docker Compose
- Nginx

## 项目特点

- 支持私有化部署
- 前后端分离，统一通过 Nginx 对外提供入口
- PostgreSQL 集成 pgvector，用于向量检索相关能力
- Redis 用于缓存与运行期辅助存储
- 提供初始化、重建、启动三套脚本，便于快速部署
- 构建产物与运行配置分离，方便迁移与维护

## 目录说明

```text
.
├─ notex_backend/              # Spring Boot 后端源码
├─ notex_frontend/             # Vue 3 前端源码
├─ notex_config/               # 运行配置目录
│  ├─ application-local.yaml   # 后端本地运行配置
│  ├─ nginx.conf               # Nginx 配置
│  └─ postgres/init/           # PostgreSQL 初始化脚本
├─ notex_storage/              # 构建产物、数据与运行期文件
├─ docker-compose.yaml         # 运行编排
├─ docker-compose.build.yaml   # 构建编排
├─ init.sh                     # 首次初始化脚本
├─ rebuild.sh                  # 全量重建脚本
└─ start.sh                    # 正常启动脚本
```

## 部署前准备

### 1. 修改后端配置

编辑 `notex_config/application-local.yaml`，至少确认以下内容：

- 数据库连接配置
- Redis 连接配置
- 图片存储路径
- JWT 密钥

> 强烈建议将默认 JWT 密钥替换为你自己的安全随机值，不要直接使用仓库内默认配置。

### 2. 准备环境变量

复制 `.env.example` 为 `.env`，并根据实际环境修改：

- 数据库用户名
- 数据库密码
- 数据库名称
- 前端对外端口
- 时区

### 3. 赋予脚本执行权限

在 Linux 环境执行：

```sh
chmod +x init.sh rebuild.sh start.sh
```

## 构建与运行

### 首次初始化

首次部署时执行：

```sh
./init.sh
```

该脚本会完成以下工作：

- 自动创建 `.env`（若不存在）
- 初始化 `notex_storage/` 所需目录
- 构建前端与后端产物
- 停止构建容器
- 启动完整运行环境

### 重新构建

当你修改了前端或后端源码，需要重新编译产物时执行：

```sh
./rebuild.sh
```

该脚本会：

- 清空已有构建产物
- 重新构建前后端
- 重新拉起运行环境

### 正常启动

如果构建产物已经存在，只需要启动服务时执行：

```sh
./start.sh
```

该脚本会先检查以下产物是否存在：

- `notex_storage/build/backend/notex-core.jar`
- `notex_storage/build/frontend/index.html`

检查通过后再启动服务。

## Docker 编排说明

### 构建编排

`docker-compose.build.yaml` 只负责：

- 构建前端静态资源
- 构建后端可执行 Jar
- 将构建结果输出到 `notex_storage/build/`

### 运行编排

`docker-compose.yaml` 只负责：

- 启动 PostgreSQL、Redis、后端、Nginx
- 将 `notex_config/` 内配置复制到运行目录
- 检查构建产物是否存在
- 仅暴露前端访问端口，其余服务仅在内部网络通信

## 数据库初始化

PostgreSQL 首次启动时会自动执行 `notex_config/postgres/init/001-migration.sql`，其中包含：

- `CREATE EXTENSION IF NOT EXISTS vector`
- 业务表初始化
- 索引初始化
- 外键约束初始化

## 默认访问方式

服务启动后，通过以下地址访问前端：

```text
http://服务器IP:8080
```

如果你修改了 `.env` 中的前端端口，请以实际配置为准。

## Credits

- md-editor-v3
- Harmony Sans 字体

## 建议补充

为了更安全、稳定地运行，建议在正式环境中额外完成以下操作：

- 修改默认 JWT 密钥
- 使用强密码替换数据库默认密码
- 定期备份 `notex_storage/data/` 与 `notex_storage/files/`
- 为 Nginx 配置 HTTPS
- 在公网环境中配合反向代理或防火墙限制访问范围

## License
MIT
