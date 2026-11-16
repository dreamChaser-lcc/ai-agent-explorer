## Why

当前后端运行环境碎片化：H2/应用在本地 Windows、Kafka 在本地 Docker 散装容器、Redis 在虚拟机 K8s（NodePort 32136）、Nacos/Sentinel 在虚拟机 K8s——日常跑一个完整链路需要同时开 Docker Desktop 和 VMware 虚拟机，且各中间件位置散落在多份课程文档里，环境重建成本高。

需要一个"一条命令就能跑起来的 Docker 专属环境"：中间件全家桶 + 应用本身全部在本地 Docker，与现有 dev 环境、虚拟机集群环境**并存且零侵入**（原配置一行不改），作为日常完整链路验证的默认环境；虚拟机 K8s 留作集群演练。

## What Changes

- 新增 `backend/docker/docker-compose.yml`：五个服务（nacos、postgres、redis、kafka、backend），一条 `docker compose up -d --build` 拉起完整环境
- 新增 `backend/src/main/resources/application-docker.yml`：`docker` profile，用容器服务名覆盖主配置的中间件地址
- 新增 `backend/docker/.env.example`：敏感值（LLM API Key）注入模板，`.env` 进 gitignore（吸取 start-dev.ps1 明文 key 的教训）
- 复用现有 `backend/Dockerfile`（多阶段构建，零改动）：通过 compose 环境变量 `SPRING_PROFILES_ACTIVE=docker` 激活新 profile
- 运行时操作（零文件改动）：停用 Windows 服务版 Redis（6379 冲突）、退役场景⑥散装 Kafka 容器（9092 冲突）
- 明确不改：`application.yml`、`application-dev.yml`、`start-dev.ps1`、前端

## Capabilities

### New Capabilities

- `docker-runtime`: Docker 专属运行环境的完整定义——compose 服务拓扑、docker profile 配置约定、端口映射、敏感值注入方式、一条命令启停

### Modified Capabilities

（无——本变更是纯新增，不触碰任何现有能力的行为约定）

## Impact

- **新增文件**：`backend/docker/docker-compose.yml`、`backend/docker/.env.example`、`backend/src/main/resources/application-docker.yml`
- **修改文件**：`.gitignore`（追加 `backend/docker/.env`）
- **运行时影响**：本地 6379/9092 端口归属变化（Windows 服务 Redis 停用、场景⑥ Kafka 容器退役）；backend 8080 与本地 mvn 启动互斥（两种运行形态二选一）
- **不受影响**：本地 dev 环境（start-dev.ps1 原样可用）、虚拟机 K8s 集群（原样可用）、前端（默认连 localhost:8080，天然兼容）
- **依赖镜像**：nacos-server v2.x（需映射 8848 + 9848 gRPC）、postgres:16、redis:7、Kafka KRaft 单节点、backend 现有 Dockerfile 构建链
