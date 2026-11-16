## Context

当前后端环境碎片化：应用/H2 在本地 Windows、Kafka 在本地 Docker 散装容器（场景⑥搭建）、Redis 在虚拟机 K8s NodePort 32136、Nacos 在虚拟机 K8s NodePort 30048（且本地 mvn 启动时一直默默注册到虚拟机）。跑完整链路需要同时开 Docker Desktop 与 VMware，环境知识散落在各课文档里，重建成本高。

已有资产：`backend/Dockerfile`（多阶段构建、非 root、DaoCloud 加速、预留 `SPRING_PROFILES_ACTIVE` 与 `JAVA_OPTS` 环境变量入口）与 `backend/docker/maven-settings.xml`（构建依赖）。`application.yml` 的占位符默认值全部指向 localhost（PG 5432 / Redis 6379 / Kafka 9092）——设计上本就为"全本地栈"留好了口子。

约束：用户明确要求**原配置零改动**（`application.yml`、`application-dev.yml`、`start-dev.ps1` 不动），以"追加新环境"的方式实现三环境并存：本地 dev（现状）、Docker 专属环境（新增）、虚拟机 K8s（现状，集群演练用）。

## Goals / Non-Goals

**Goals:**

- 一条 `docker compose up -d --build` 拉起完整环境：Nacos + PostgreSQL + Redis + Kafka + backend 应用
- Docker 环境在**虚拟机关机状态下**独立成立（场景⑥异步链路、场景⑦缓存链路可完整回归）
- backend 注册到本地 Docker Nacos（第三套独立注册中心，与虚拟机 Nacos 隔离）
- 敏感值（LLM API Key）通过 `.env` 注入，不进 git
- 对现有环境零侵入：dev 启动脚本、虚拟机集群原样可用

**Non-Goals:**

- 不迁移任何历史数据（Redis 缓存丢弃、Kafka 依赖 `earliest` 重放、PG 全新库由 Flyway 建表、H2 数据留在 dev 环境）
- 不规划前端容器化（前端本地 npm dev 直连 localhost:8080 天然兼容）
- 不动虚拟机 K8s 环境（含其上的 Nacos/Sentinel/Redis）
- 不做 Sentinel 本地化（backend 未配置 Sentinel，属集群栈组件）

## Decisions

### D1：配置注入走 profile 覆盖，而非纯环境变量

`application-docker.yml`（`docker` profile）直接写容器服务名，profile 属性覆盖主配置的同名属性：

```yaml
spring.datasource.url: jdbc:postgresql://postgres:5432/research_agent
spring.data.redis.host: redis
spring.kafka.bootstrap-servers: kafka:9092
spring.cloud.nacos.server-addr 体系: nacos:8848
```

**理由**：用户明确要求新增专属 application 文件；地址是 compose 网络的稳定约定，集中在 yml 里比散在 compose env 里更可读。
**替代方案**：纯 env 注入占位符（micro-lab K8s 的 `NACOS_ADDR` 模式）——零新文件，但 compose 里要挂一长串 env，且不满足用户诉求。两条路线共享主配置占位符机制，互不冲突。

**实施修正（2026-11-14 实测）**：profile 覆盖对 `spring.config.import`（Nacos 配置导入）**不生效**——config.import 的解析早于 profile 覆盖，实测 config 客户端仍按主配置默认值订阅虚拟机 `192.168.157.129:30048`。修复：compose 的 backend 环境变量补注入 `NACOS_ADDR: nacos:8848`（env 优先级高于一切配置文件，config.import 阶段即生效）——这正是主配置占位符 + K8s 集群 env 注入的同款机制，也解释了 micro-lab K8s 为何选 env 路线。修复后复验：虚拟机零连接。

### D2：数据源用 PostgreSQL + Flyway，不用 H2

docker profile 继承主配置的 PG + Flyway（`flyway.enabled=true`）——首次启动自动建表。
**理由**：Docker 环境的定位就是"完整生产形态的本地副本"，主配置默认即生产形态；H2 文件模式在容器里是反模式（容器删了数据丢）。
**代价**：PG 库初始为空，H2 里的历史任务数据不共享——两个环境数据天然隔离，dev 随便造数据，Docker 保持干净。
**实施发现（2026-11-14）**：首次 Flyway 全新建库暴露历史欠账——场景④⑤只改了 `schema-dev.sql` 没补 Flyway 迁移（`audit_log` 表、`idempotency_key`/`version` 列），`ddl-auto: validate` 连环报错。已补 `V3__add_audit_log.sql` 与 `V4__add_research_task_version_and_idempotency.sql` 修复，集群老库同样受益（全部用 `IF NOT EXISTS` 兼容已手工加过列的状态）。

### D3：Nacos 进 compose，作为独立第三注册中心

| Nacos 实例 | 消费者 |
|---|---|
| 虚拟机 129:30048 | micro-lab 三件套 + K8s backend（集群演练态） |
| Docker localhost:8848 | Docker 环境的 backend（本变更） |

**理由**：用户明确要求使用 Nacos；注册中心按环境隔离是正确语义（本地实例注册本地，集群实例注册集群），彻底解耦"本地跑"与"虚拟机开"。
**技术要点**（micro-lab 实测踩坑）：Nacos 2.x 客户端额外连接 `主端口+1000` 的 gRPC 端口，compose 必须同时映射 `8848` 与 `9848`。

### D4：端口与主配置默认值对齐，冲突源停用

| 服务 | 宿主机端口 | 冲突源与处理 |
|---|---|---|
| backend | 8080 | 与本地 mvn dev 互斥（两种运行形态二选一） |
| nacos | 8848 + 9848 | 无冲突 |
| postgres | 5432 | 无冲突（dev 用 H2） |
| redis | 6379 | **实施实测修正（2026-11-14）**：Windows 服务版 Redis 仅绑定 `127.0.0.1:6379`，与 Docker Desktop 的端口发布机制**可共存**（探针容器验证通过）——停用从"必须"降级为"可选清理"（需管理员权限）；dev 环境继续用 Windows Redis，docker 环境走容器网络 `redis:6379`，语义互不干扰 |
| kafka | 9092 | **退役场景⑥散装容器**（数据不迁，`auto-offset-reset: earliest` 支持重放） |

### D5：敏感值走 `.env`，不复刻明文 key

`backend/docker/.env.example` 提交模板（`OPENAI_API_KEY=` 占位），`.env` 本体进 `.gitignore`；compose 中 `backend.environment.OPENAI_API_KEY: ${OPENAI_API_KEY}` 引用。
**理由**：start-dev.ps1 里明文硬编码 API Key 是已知教训（脚本注释自认"提交前务必确认"），新环境从第一天就规范。

### D6：复用现有 Dockerfile，零改动

compose 的 `environment.SPRING_PROFILES_ACTIVE: docker` 覆盖镜像 ENV（Docker 标准优先级），`build: { context: .., dockerfile: Dockerfile }` 指向现有构建链（含 maven-settings.xml 分层缓存）。

### D7：Kafka 用 KRaft 单节点镜像

与场景⑥本地形态保持一致（无 ZooKeeper），选 `bitnami/kafka:3.x`（环境变量配置最简）。卷持久化 topic 与消费位点，`down -v` 才清空。

### D8：启动顺序用 depends_on + healthcheck

`backend.depends_on` 挂 postgres（pg_isready）、nacos、redis、kafka（healthcheck 或 `service_started`）；Spring 自带连接重试兜底首次建库/建 topic 的窗口期。

## Risks / Trade-offs

- [宿主机端口冲突导致 backend 起不来] → 实施步骤里"停旧源"列为 compose 启动的前置检查；compose 起前脚本化检测 6379/9092 占用
- [Nacos 空库：第 5 课配置中心内容不在此环境] → `spring.config.import: optional:nacos:...` 的 optional 前缀保证空库可启动；提供配置录入模板（见 tasks 可选项）
- [首次 up 时 Nacos/PG 未就绪导致 backend 启动失败] → depends_on + Spring 重试；验收清单含"重启 backend 容器即恢复"
- [compose 栈内存占用约 3GB] → 对比现状（虚拟机 VMware 2~4GB 必须开机）为净收益；`down` 后资源全释放
- [backend 容器改代码需 rebuild] → 语义正确：日常改码调试用 dev 环境（mvn），Docker 环境做完整链路验证，二者分工互补

## 实施后记（2026-11-16 实测发现的两个环境级坑）

### 坑 A：Flyway 历史欠账（已修复，V3/V4）
场景④⑤只改了 `schema-dev.sql` 没补 Flyway 迁移——dev（H2 建表）从未暴露，Docker 环境首次全量建库连环报错（`missing table [audit_log]` → `missing column [idempotency_key]`）。修复：V3 + V4 迁移，集群老库同样受益（`IF NOT EXISTS` 兼容手工状态）。

### 坑 B：wslrelay 的 IPv6 回环干扰（影响本地 mvn 连 Docker 端口映射）
本机 `wslrelay.exe`（WSL 端口中继）在**全部中间件端口的 `[::1]`（IPv6 回环）**上监听。Java 连 `localhost` 时 Windows 优先解析 IPv6 → 撞上 wslrelay → 连接被丢给 WSL 侧（无对应服务）→ `Connection refused`。
- **症状**：`netstat` 显示 `0.0.0.0:5432 LISTENING`（com.docker.backend）且 `Test-NetConnection 127.0.0.1:5432` 成功，但 Java 连 `localhost:5432` 仍 refused（连到了 `[::1]` 的 wslrelay）
- **修复**：`start-dev-docker.ps1` 的 `JAVA_TOOL_OPTIONS` 加 `-Djava.net.preferIPv4Stack=true`，强制 JVM 解析 `localhost` 为 `127.0.0.1`，直接命中 Docker 的 IPv4 监听
- **遗留**：验证过程中发现 Docker Desktop 长时间运行后端口转发链路本身不稳定（netstat LISTENING 但 TCP 握手失败）——完整重启 Docker Desktop 后恢复。若本地 mvn 仍连不上，先完整重启 Docker Desktop 再跑脚本。

## Migration Plan

1. 新增三个文件 + `.gitignore` 一行（见 proposal Impact）
2. 停用 Windows 服务 Redis、退役场景⑥ Kafka 容器（运行时操作）
3. `cp .env.example .env` 并填入 API Key
4. `docker compose up -d --build` 验收
5. **回滚**：`docker compose down -v` + 恢复 Windows Redis 服务（`sc start Redis`）+ 重启场景⑥容器——原环境完全不受影响，因为没有任何文件被修改
