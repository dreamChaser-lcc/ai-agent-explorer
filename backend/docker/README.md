# research-agent Docker 专属运行环境

> OpenSpec 变更：`add-docker-runtime-environment`（2026-11-14）
> 一条命令拉起「Nacos + PostgreSQL + Redis + Kafka + backend」的完整环境。

## 三环境分工

| 环境 | 启动方式 | 定位 |
|---|---|---|
| **Docker 环境（本目录）** | `docker compose up -d --build` | 完整容器化形态：链路验证、生产预演，虚拟机关机下独立成立 |
| **本地调试（dev-docker 模式）** | `backend/start-dev-docker.ps1` | **日常调试**：应用本地 mvn + 中间件全在 Docker |
| 虚拟机 K8s | 集群内部署（micro-lab 模式） | 集群演练态（第 3/5 课、G1 压测） |
| 本地 dev（H2 老模式） | `backend/start-dev.ps1` | 已被 dev-docker 模式取代，保留备查 |

## 本地调试：dev-docker 模式（完全独立，不依赖 dev）

`application-dev-docker.yml` 是**全量独立的 profile**（不叠加 dev）——数据源直连 Docker PG、Nacos 指向本地 Docker Nacos；Redis/Kafka 沿用主配置默认值（localhost，天然命中 Docker）。一个 profile 开关，四件套全部就位。

```powershell
# 一条命令（自动：确保中间件栈就绪 → 停容器化 backend 释放 8080 → 注入 profile 启动）
cd backend ; .\start-dev-docker.ps1
```

| 组件 | dev-docker 连的 | 配置来源 |
|---|---|---|
| PostgreSQL | `localhost:5432`（Docker） | 本 profile 覆盖（dev 的 H2 不再生效） |
| Redis | `localhost:6379`（Docker） | 主配置默认值命中 |
| Kafka | `localhost:9092`（Docker） | 主配置默认值命中 |
| Nacos | `localhost:8848`（Docker） | 本 profile + 脚本注入的 `NACOS_ADDR` 双保险 |

> **Nacos 为什么要双重保障**：`spring.config.import`（Nacos 配置导入）的解析发生在 profile 覆盖生效**之前**，profile 文件盖不住它（compose backend 实测踩坑）——所以启动脚本同时注入 `NACOS_ADDR` 环境变量（env 优先级最高，config.import 阶段即生效）。
>
> 原 dev 模式（H2 + `start-dev.ps1`）**保留未动**，不再使用时可无视；`application-dev.yml` 原样保留。

> ⚠️ **8080 互斥**：容器化的 backend 与本地 mvn 都监听 8080，不能同时跑。
> 本地调试前先 `docker compose stop backend`（中间件栈继续跑），调完 `docker compose up -d backend` 恢复。

> ⚠️ **8080 互斥**：容器化的 backend 与本地 mvn 都监听 8080，不能同时跑。
> 本地调试前先 `docker compose stop backend`（中间件栈继续跑），调完 `docker compose up -d backend` 恢复。

> ⚠️ 若 Windows 服务版 Redis 重新启用并抢回 6379，dev 的 Redis 会连回 Windows 版（功能等价），但 Docker 栈的 redis 容器端口映射会在下次 up 时失败——保持服务停用状态最干净。

## 首次启动

```powershell
# 1. 准备敏感值（.env 不进 git）
cd backend\docker
Copy-Item .env.example .env
# 编辑 .env，填入 OPENAI_API_KEY=sk-xxx

# 2. 处理端口冲突源（仅首次需要）
sc stop Redis                        # 停用 Windows 服务版 Redis（占 6379）
sc config Redis start= disabled      # 顺带禁用开机自启
docker ps                            # 若有场景⑥的散装 Kafka 容器占 9092：
docker stop <容器名> ; docker rm <容器名>

# 3. 一键启动（首次含 Maven 依赖下载与 Flyway 建表，约 3~5 分钟）
docker compose up -d --build
```

## 日常启停

```powershell
docker compose up -d        # 启动（镜像已构建时秒级）
docker compose up -d --build backend   # 改代码后只重建应用（中间件不动）
docker compose down         # 停止，数据保留
docker compose down -v      # 彻底重建：清空 PG/Kafka 数据，Flyway 重新建表
docker compose logs -f backend   # 看应用日志
```

> **不需要 mvn**：`--build` 会用仓库根的多阶段 Dockerfile 在容器里自动完成 Maven 打包（宿主机无需 Maven/JDK），profile 由 compose 注入 `SPRING_PROFILES_ACTIVE=docker` 自动激活——`mvn spring-boot:run` 是 dev 环境的启动方式，与这里无关。

## 就绪后的入口

| 项 | 地址 |
|---|---|
| 应用健康检查 | http://localhost:8080/api/health |
| Nacos 控制台 | http://localhost:8848/nacos（默认账号 nacos/nacos） |
| **PG 数据库控制台（Adminer）** | http://localhost:8081 —— 需先启用 tools profile（见下） |
| 前端 | `npm run dev` 零配置直连 localhost:8080 |

## 数据库 UI：Adminer（H2 console 的 PG 等价物）

### 启用与登录

```powershell
# 启用（tools profile 里的服务默认 up 不启动，不占资源）
docker compose --profile tools up -d
# 浏览器打开 http://localhost:8081，按下面表格填写登录表单
```

登录表单共 5 个字段 + 1 个勾选，一个都不能错（尤其 Database）：

| 表单字段 | 填写值 | 说明 |
|---|---|---|
| 系统（System） | **PostgreSQL** | 下拉默认是 MySQL / MariaDB——**必须手动改**，不改必登录失败 |
| 服务器（Server） | **postgres** | compose 网络内的服务名（不是 localhost——Adminer 也在容器里，它的 localhost 是自己） |
| 用户名（Username） | **postgres** | 与 compose 的 `POSTGRES_USER` 一致 |
| 密码（Password） | **postgres** | 与 compose 的 `POSTGRES_PASSWORD` 一致 |
| 数据库（Database） | **research_agent** | 业务表所在库（Flyway 建表的目标库） |
| Permanent login | 勾选 | 登录会话持久化，刷新页面不掉登录 |

### ★ 最常见的坑：登录成功但「没有表」

Database 填了 `postgres`（PG 安装后自带的默认空库）**也能成功登录**——但里面一张业务表都没有，页面上就是「没有表」。**业务表全在 `research_agent` 库**。

已经连进去才发现连错库，两种补救（不用重新登录）：

- 左上角**「数据库」下拉框**直接切换 `postgres` → `research_agent`；
- 或浏览器地址栏把 URL 里的 `db=postgres` 改成 `db=research_agent` 后回车。

### 登录后应看到的 12 张表

`research_task` / `research_plan` / `research_step` / `step_execution` / `source_document` / `research_report` / `task_event_log` / `human_confirmation` / `audit_log` / `request_pipline` / `sp4_tx_demo` / `flyway_schema_history`

> 最后那张 `flyway_schema_history` 是 Flyway 的迁移账本——能看到 V1–V4 每条迁移的执行记录与耗时，排查"表结构为什么是这样"先查它。

### 命令行替代

```powershell
docker exec -it research-postgres psql -U postgres -d research_agent
# 常用：\dt 看表、\d research_task 看列、SELECT ... 查数据、\q 退出
```

## 端口约定（与主配置默认值对齐）

| 端口 | 服务 | 说明 |
|---|---|---|
| 8080 | backend | 与本地 mvn dev **互斥**：跑谁起谁 |
| 8848 / 9848 | nacos | 9848 是 2.x gRPC（主端口+1000），必须一并映射 |
| 5432 | postgres | dev 环境用 H2，无冲突 |
| 6379 | redis | 与 Windows 服务版 Redis **共存**（后者仅绑 127.0.0.1，Docker 走独立发布机制，实测不冲突）；容器内 backend 走 `redis:6379` 服务名，不受影响。宿主机 `redis-cli` 直连 localhost:6379 命中的是 Windows Redis——如需调试容器 Redis，用 `docker exec -it research-redis redis-cli` |
| 9092 | kafka | 宿主机映射 9092→容器 9094（EXTERNAL）；容器内走 `kafka:9092`（INTERNAL） |

## 设计要点（速记）

- **零侵入**：`application.yml` / `application-dev.yml` / `start-dev.ps1` 一行未改，新环境靠 `application-docker.yml`（docker profile）覆盖容器服务名
- **两套 Nacos 天然隔离**：本环境 backend 注册到 `nacos:8848`；虚拟机集群栈仍用 `129:30048`，互不干扰
- **敏感值**：只存在于 `.env`（已被 .gitignore 忽略），compose 引用 `${OPENAI_API_KEY}`
- **数据语义**：`down` 保留 / `down -v` 重建；Redis 缓存不持久化（丢了自动回源）；Nacos 录入的配置**重启即失**（standalone 内嵌存储，见附录说明）

## 附录：第 5 课配置中心实验模板（可选）

本环境 Nacos 是空库，`spring.config.import: optional:nacos:...` 的 `optional` 前缀保证空库也能启动。若要复现第 5 课「配置推送」实验：在 Nacos 控制台 → 配置管理 → 新建配置，Data ID 填 `research-agent-backend.yaml`，格式选 YAML，内容可用下面的最小模板（以虚拟机 Nacos 上第 5 课录入的版本为准，这里给出可演示热更的最小集）：

```yaml
# Data ID: research-agent-backend.yaml  Group: DEFAULT_GROUP
app:
  llm:
    temperature: 0.2        # 改这个值推送 → 结合 /actuator/env 观察配置来源变化
    max-retries: 2
```

> 注意：Docker 环境的 Nacos 未挂载数据卷，`down` 后录入的配置会丢失——这是 standalone 内嵌 Derby 的预期行为，重新录入即可；若希望持久化，后续可在 compose 中为 `/home/nacos/data` 挂卷。
