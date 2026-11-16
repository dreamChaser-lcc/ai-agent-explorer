## ADDED Requirements

### Requirement: 一条命令启停完整环境
系统 SHALL 支持在 `backend/docker/` 目录下执行 `docker compose up -d --build` 一条命令启动包含 Nacos、PostgreSQL、Redis、Kafka 与 backend 应用的完整环境，并 SHALL 支持 `docker compose down` 一条命令停止。

#### Scenario: 一键启动全家桶
- **WHEN** 在 `backend/docker/` 目录执行 `docker compose up -d --build` 且所有镜像构建拉取完成
- **THEN** 五个服务（nacos、postgres、redis、kafka、backend）全部进入运行状态，`http://localhost:8080/api/health` 返回 200

#### Scenario: 一键停止
- **WHEN** 执行 `docker compose down`
- **THEN** 五个服务全部停止且数据卷保留（再次 up 后数据仍在）

### Requirement: docker 专属配置不修改现有文件
系统 SHALL 通过新增 `application-docker.yml`（docker profile）承载 Docker 环境的全部地址覆盖，且 SHALL NOT 修改 `application.yml`、`application-dev.yml`、`start-dev.ps1` 与前端任何文件。

#### Scenario: 激活 docker profile 后按容器服务名寻址
- **WHEN** backend 以 `SPRING_PROFILES_ACTIVE=docker` 启动
- **THEN** 数据源连接 `postgres:5432`、Redis 连接 `redis:6379`、Kafka 连接 `kafka:9092`、Nacos 连接 `nacos:8848`（均为 compose 内部服务名）

#### Scenario: 现有环境零影响
- **WHEN** 本变更全部文件就位后，不启动 docker compose，直接执行 `backend/start-dev.ps1`
- **THEN** 本地 dev 环境按原有行为正常启动（H2 数据库、原有中间件寻址），与变更前完全一致

### Requirement: backend 注册到本地 Docker Nacos
Docker 环境中的 backend SHALL 注册到 compose 内的 Nacos 实例，且 SHALL NOT 与虚拟机 K8s 上的 Nacos 产生任何交互。

#### Scenario: 注册中心控制台可见实例
- **WHEN** `docker compose up -d` 后 backend 启动完成
- **THEN** 访问 `http://localhost:8848/nacos` 控制台服务列表出现 backend 服务实例，且虚拟机关机不影响该注册

#### Scenario: gRPC 端口显式映射
- **WHEN** backend 容器内 Nacos 客户端与 Nacos 服务端建立连接
- **THEN** 宿主机 8848（HTTP）与 9848（gRPC，即 8848+1000）端口均已映射，连接不因端口缺失而失败

### Requirement: 环境在虚拟机关机状态下独立成立
Docker 环境 SHALL 在虚拟机完全关机的状态下支撑完整的业务链路回归，包括 Kafka 异步执行链路与 Redis 缓存链路。

#### Scenario: 虚拟机关机下全链路可用
- **WHEN** 虚拟机处于关机状态，Docker 环境启动，创建一个异步研究任务并等待执行完成，随后连续两次查询报告
- **THEN** 任务经 Kafka 链路异步执行成功，第二次报告查询命中 Redis 缓存，全程无任何指向 192.168.157.x 网段的连接失败日志

### Requirement: 数据持久化与重建语义
系统 SHALL 通过具名数据卷持久化 PostgreSQL、Kafka 的数据，`docker compose down` 后数据保留；`docker compose down -v` 为环境的完全重建入口。

#### Scenario: 普通重启数据保留
- **WHEN** 执行 `docker compose down` 后再次 `docker compose up -d`
- **THEN** PostgreSQL 中的表结构与历史任务数据、Kafka 的 topic 均仍然存在

#### Scenario: 完全重建
- **WHEN** 执行 `docker compose down -v` 后再次 `docker compose up -d`
- **THEN** 所有数据卷删除，PostgreSQL 由 Flyway 重新建表，环境回到初始状态

### Requirement: 敏感值经 .env 注入且不入库
系统 SHALL 将 LLM API Key 等敏感值通过 `backend/docker/.env` 注入 compose，SHALL 提交 `.env.example` 作为模板，且 `.env` SHALL 被 git 忽略。

#### Scenario: 模板就位且真实值被忽略
- **WHEN** 仓库克隆完成后查看 `backend/docker/` 目录并检查 `git status`
- **THEN** 存在 `.env.example`（含空占位符），且开发者自建的 `.env`（含真实 API Key）不出现在 git 跟踪列表中

#### Scenario: 缺失 .env 时启动可诊断
- **WHEN** 未创建 `.env` 直接执行 `docker compose up -d`
- **THEN** backend 以空 API Key 启动，LLM 相关功能进入配置的失败降级路径，应用本身与其他链路不受影响

### Requirement: 端口约定与现有消费方兼容
宿主机端口映射 SHALL 与主配置默认值及前端默认行为对齐（8080/5432/6379/9092/8848），保证前端 `npm run dev` 无任何配置改动即可调用 Docker 环境的 backend。

#### Scenario: 前端零配置接入
- **WHEN** Docker 环境启动后，前端以默认配置 `npm run dev` 启动并访问首页
- **THEN** 前端通过 `http://localhost:8080` 成功调用 Docker 环境的 backend，任务列表正常渲染
