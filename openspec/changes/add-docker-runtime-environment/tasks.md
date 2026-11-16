## 1. 配置文件新增

- [x] 1.1 新增 `backend/src/main/resources/application-docker.yml`：docker profile，覆盖数据源为 `postgres:5432`、Redis 为 `redis`、Kafka 为 `kafka:9092`、Nacos 为 `nacos:8848`（discovery 与 config 同源），继承主配置的 PG + Flyway 与全部业务配置
- [x] 1.2 新增 `backend/docker/.env.example`：提供 `OPENAI_API_KEY=` 空占位模板与填写说明注释
- [x] 1.3 修改 `.gitignore`：追加 `backend/docker/.env` 忽略规则

## 2. Compose 编排

- [x] 2.1 新增 `backend/docker/docker-compose.yml` 基础骨架：project 命名、具名卷（pg-data、kafka-data）、默认网络
- [x] 2.2 编写 `nacos` 服务：standalone 模式（内嵌存储）、映射 8848 与 9848（gRPC，主端口+1000）、healthcheck
- [x] 2.3 编写 `postgres` 服务：postgres:16、环境变量初始化 `research_agent` 库与 postgres 用户、pg-data 卷、pg_isready healthcheck、5432 端口映射
- [x] 2.4 编写 `redis` 服务：redis:7-alpine、6379 端口映射（不带密码，与主配置默认一致）
- [x] 2.5 编写 `kafka` 服务：bitnami/kafka KRaft 单节点、9092 端口映射、kafka-data 卷、 advertised-listeners 配置保证宿主机与容器网络双可达
- [x] 2.6 编写 `backend` 服务：build 指向仓库根的现有 Dockerfile、`SPRING_PROFILES_ACTIVE=docker`、`OPENAI_API_KEY: ${OPENAI_API_KEY}` 引用 .env、8080 端口映射、depends_on 挂全部中间件
- [x] 2.7 为 compose 起草 `README.md`：一条命令启停、.env 准备、验收步骤、与 dev/虚拟机环境的分工说明

## 3. 运行时环境整理（零文件改动的一次性操作）

- [x] 3.1 Redis 端口整理：实测 Docker 端口发布与 Windows Redis 服务（仅绑 127.0.0.1:6379）**可共存**——dev 环境继续用 Windows Redis（零影响），docker 环境走容器网络。原"停用服务"降级为可选清理项（需管理员权限手动执行 `sc stop Redis` + `sc config Redis start= disabled`）
- [x] 3.2 退役场景⑥散装 Kafka 容器：`docker rm kafka` 完成，9092 已释放（topic 数据不迁移，消费组靠 `earliest` 重放）
- [x] 3.3 复制 `.env.example` 为 `.env` 并填入真实 API Key（复用 start-dev.ps1 既有 Key），`git check-ignore` 验证通过

## 4. 启动与验收

- [x] 4.1 `docker compose up -d --build`：确认五服务全部健康（首次含 Flyway 自动建表）
- [x] 4.2 验收 `/api/health` 返回 200，Nacos 控制台（localhost:8848/nacos）服务列表出现 backend 实例
- [x] 4.3 验收场景⑥异步链路：创建异步任务，Kafka 消费与执行推进正常
- [x] 4.4 验收场景⑦缓存链路：报告查询两次，第二次命中 Redis 缓存（日志或耗时对照）
- [x] 4.5 验收环境独立性：确认虚拟机处于关机状态下 4.1~4.4 全部通过；执行 `docker compose down && up -d` 验证数据保留

## 5. 可选项（第 5 课配置中心在 Docker 环境的复现）

- [x] 5.1 将 `research-agent-backend.yaml` 配置模板（源自虚拟机 Nacos 上的第 5 课录入内容）整理进 `backend/docker/README.md` 附录，供手动录入本地 Nacos 后做配置推送实验

## 6. dev-docker 调试模式（本地 mvn 连 Docker 栈，2026-11-16 增补）

- [x] 6.1 新增 `application-dev-docker.yml`：组合 profile（dev,dev-docker），覆盖数据源为 Docker PG、Nacos 为本地 Docker Nacos；Redis/Kafka 由 dev 默认值自动命中
- [x] 6.2 新增 `backend/start-dev-docker.ps1`：自动拉起中间件栈、停容器化 backend 释放 8080、API Key 从 .env 读取、保留 LLM 代理配置
- [x] 6.3 修复 wslrelay IPv6 回环干扰：`JAVA_TOOL_OPTIONS` 加 `-Djava.net.preferIPv4Stack=true`（Java 连 localhost 解析到 `[::1]` 会撞上 wslrelay 的端口中继导致 refused）
- [ ] 6.4 实测复跑验证：需先**完整重启 Docker Desktop**（实测长运行后端口转发失效：netstat LISTENING 但 TCP 握手失败），再执行 `start-dev-docker.ps1` 确认应用启动连上 Docker PG/Nacos
