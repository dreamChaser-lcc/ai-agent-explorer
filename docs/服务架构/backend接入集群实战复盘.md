# backend 接入集群实战复盘

> **定位**：真实项目（`research-agent` backend，Spring Boot 单体）从"H2 单机演示"走向"**K3s 集群 + 真实数据库**"的实战记录——生产式接入路线。
> **配套资料**：基础设施原理见《虚拟机DockerK8s实战复盘.md》；micro-lab 微服务教学线见《微服务实战复盘.md》；配置文件专题见《micro-lab配置文件全景讲解.md》。
> **起点**：2026-09-26。

---

## 📖 目录

- [一、路线规划（为什么这么走）](#一路线规划为什么这么走)
- [二、第 1 课：数据层进集群（PG + Redis）](#二第-1-课数据层进集群pg--redis)
- [三、第 2 课：backend 进集群（2026-09-28 完成）](#三第-2-课backend-进集群2026-09-28-完成)
- [四、第 3 课：Nacos 接入（2026-09-28 完成）](#四第-3-课nacos-接入2026-09-28-完成)
- [五、第 4 课：gateway 路由到 backend（2026-10-01 完成）](#五第-4-课gateway-路由到-backend2026-10-01-完成)
- [六、第 5 课：配置中心接入（2026-10-03 完成）](#六第-5-课配置中心接入2026-10-03-完成)
- [七、踩坑记录（B 编号）](#七踩坑记录b-编号)
- [八、进度与待办](#八进度与待办)

---

## 一、路线规划（为什么这么走）

### 1.1 关键认知：四件事彼此独立

| 事 | 依赖关系 | 结论 |
|----|---------|------|
| ① 接真实数据库（PostgreSQL + Flyway） | 只关乎应用自身 | 与集群无关，随时可做 |
| ② 单体进 K8s 集群 | 只要有镜像 | **不需要微服务**（单体住 K8s 是标准操作） |
| ③ 接 Nacos 注册中心 | 需引 Spring Cloud 依赖 | 技术可行，但"没有消费方时价值为零" |
| ④ 拆微服务 | 真需要才做 | 最后一步 |

**核心结论**：注册中心不是"进集群的门票"——单体部署进 K8s 后，用 **Service DNS**（`xxx-svc:端口`）即可自洽；Nacos 等有消费方（网关 / 第二个服务）时再接最自然。

（另一个成本考量：backend 是 Spring Boot **3.5.13**，与 micro-lab 的 3.2.4 不同——引 Cloud 系前必须重新查"三件套"兼容矩阵，有真实的适配工作量。相关概念见《micro-lab配置文件全景讲解.md》与《微服务实战复盘.md》1.3 节。）

### 1.2 决策记录（2026-09-26）

| 决策 | 内容 | 理由 |
|------|------|------|
| **全 VM 路线** | PG / Redis / backend 全部放虚拟机集群，**Windows 本地不开服务** | 数据与服务集中服务器侧（开发环境标准做法之一）；开发机轻装 |
| **数据层进 K8s**（而非节点裸 Docker） | PG/Redis 直接以集群原生形态部署 | backend 连 `postgres-svc` 不依赖节点 IP（复用 micro-lab 的 Service 互访模式）；顺路学 PVC/有状态服务 |
| **Nacos 后置** | 微服务化时再接 | 单体注册无消费方；引 Cloud 系有版本适配成本 |

> **【企业级标注】** "数据库住 K8s"在生产是有争议的话题——大厂更常用云托管数据库（RDS）或独立服务器（存储/备份/运维复杂度）。本环境为学习用途：赚到"有状态服务"一整套训练；但这份数据是实验数据（local 卷、无备份、无主从），别当生产。

### 1.3 路线图

```text
第 1 课  数据层进集群（PG + Redis）                        ✅ 2026-09-26
第 2 课  backend 进集群（新镜像 + PG env + Flyway 建表）    ✅ 2026-09-28
第 3 课  Nacos 接入（注册中心）                             ✅ 2026-09-28
第 4 课  gateway 路由到 backend（真实项目微服务化第一步）    ✅ 2026-10-01
第 5 课  配置中心接入（Nacos Server 升级 3.0.3，双年代客户端同台） ✅ 2026-10-03
第 6 课+ 更远：Sentinel 接入 / 监控进阶 / 鉴权与集群模式 / 挂账升级… ⏸
```

---

## 二、第 1 课：数据层进集群（PG + Redis）✅ 2026-09-26

### 2.1 目标形态

```text
        （下一课）                    （本课）
  backend Pod ──→ postgres-svc:5432 ──→ postgres Pod ──→ PVC ★数据落盘
              └─→ redis-svc:6379    ──→ redis Pod    ──→ PVC ★数据落盘
   （无论 backend 被调度到哪台节点，都只连 Service 名）
```

### 2.2 体检记录（动手前）

| 检查 | 结果 | 判读 |
|------|------|------|
| node1 `docker ps` | 空 | 旧容器已清空，环境干净 |
| node2 集群 | 9 个 Pod 全 Running | micro-lab + Nacos/Sentinel/Prometheus/Grafana 在线；research-agent 老副本 ×2 仍跑（17d，近期稳定，暂不动） |
| 资源 | node1 可用 ~4.0Gi / node2 可用 ~2.6Gi | PG+Redis 实际吃 <200Mi，宽裕 ✓ |

### 2.3 镜像获取（踩坑见 B1）

```bash
# node2 执行
sudo k3s ctr -n k8s.io images pull docker.m.daocloud.io/library/postgres:16-alpine
sudo k3s ctr -n k8s.io images pull docker.m.daocloud.io/library/redis:7-alpine
```

要点：直连 `docker.io` 被拒 → 走 Daocloud 加速源；**yaml 里 `image:` 必须写与本地镜像"字面一致"的名字**（K8s 按字面名找本地镜像，对不上就当作没有、去网上拉）。

### 2.4 部署清单（`~/data-lab/postgres-redis.yaml`，node2 上创建）

```yaml
# ============================================================
# data-lab：PostgreSQL + Redis（第 1 课：数据层进集群）
# 与 micro-lab 同集群、同 namespace；集群内访问：
#   postgres-svc:5432  /  redis-svc:6379
# ============================================================

# ---------- 1) Secret：集中存密码（不散落在 Deployment 明文里） ----------
apiVersion: v1
kind: Secret
metadata:
  name: pg-secret
type: Opaque
stringData:                 # stringData：可直接写明文，K8s 存成 base64（注意：base64 不是加密，只做"分离管理"）
  POSTGRES_PASSWORD: "pg123456"
---
# ---------- 2) PostgreSQL：PVC 持久化 ----------
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes: ["ReadWriteOnce"]     # RWO：同一时刻只被一个节点挂载
  storageClassName: local-path       # K3s 自带供给器：首次调度时在 Pod 所在节点上开目录
  resources:
    requests:
      storage: 2Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  labels:
    app: postgres
spec:
  replicas: 1
  strategy:
    type: Recreate                    # ★ 有状态单副本必须 Recreate：滚动更新会让新旧 Pod 抢同一个 RWO 卷，卡死
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: docker.m.daocloud.io/library/postgres:16-alpine   # ★ 与 ctr pull 的名字字面一致（见 B1）
          env:
            - name: POSTGRES_DB          # 首次启动自动创建此库
              value: research_agent
            - name: POSTGRES_USER
              value: postgres
            - name: POSTGRES_PASSWORD    # 从 Secret 注入
              valueFrom:
                secretKeyRef:
                  name: pg-secret
                  key: POSTGRES_PASSWORD
            - name: PGDATA               # ★ 数据放子目录：规避挂载根目录的 lost+found 权限问题
              value: /var/lib/postgresql/data/pgdata
          ports:
            - containerPort: 5432
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
          resources:
            requests: {cpu: 100m, memory: 128Mi}
            limits: {memory: 512Mi}
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgres-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: postgres-svc                 # ★ 集群内的"数据库地址"
spec:
  type: ClusterIP                    # 不对集群外暴露（安全原则）
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
---
# ---------- 3) Redis：同一套持久化套路 ----------
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: redis-pvc
spec:
  accessModes: ["ReadWriteOnce"]
  storageClassName: local-path
  resources:
    requests:
      storage: 1Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  labels:
    app: redis
spec:
  replicas: 1
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
        - name: redis
          image: docker.m.daocloud.io/library/redis:7-alpine    # ★ 同上
          args: ["redis-server", "--appendonly", "yes"]   # AOF 持久化：每条写命令落盘
          ports:
            - containerPort: 6379
          volumeMounts:
            - name: data
              mountPath: /data
          resources:
            requests: {cpu: 50m, memory: 64Mi}
            limits: {memory: 128Mi}
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: redis-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: redis-svc
spec:
  type: ClusterIP
  selector:
    app: redis
  ports:
    - port: 6379
      targetPort: 6379
```

**四块速览**：

| 块 | 作用 | 关键点 |
|----|------|--------|
| Secret | 存 PG 密码 | `stringData` 可写明文（K8s 存 base64——**base64 不是加密**，防护靠 RBAC） |
| PVC ×2 | 数据落盘 | `storageClassName: local-path`（K3s 自带供给器）；绑定模式 **WaitForFirstConsumer**——"卷跟着 Pod 走" |
| Deployment ×2 | 跑容器 | `strategy: Recreate`（★ 有状态单副本必须：滚动更新会让新旧 Pod 抢同一个 RWO 卷而卡死）；PG 数据放子目录（`PGDATA=.../pgdata`）；Redis 开 AOF |
| Service ×2 | 集群内固定地址 | `postgres-svc:5432` / `redis-svc:6379`（ClusterIP，不对外——安全原则） |

### 2.5 部署与观察

```bash
kubectl apply -f ~/data-lab/postgres-redis.yaml

kubectl get pvc            # ★ 第一眼可能是 Pending（WaitForFirstConsumer：没人用先不分配）→ Pod 调度成功后 Bound
kubectl get pods -o wide
```

实测结果：两个 PVC 均 **Bound**（2Gi / 1Gi）；PG/Redis Pod 都落在 **node1**（`10.42.1.53 / 10.42.1.52`）。

### 2.6 ★ 杀 Pod 持久化实验（数据复活）

```bash
# ① 自检 + 写测试数据
kubectl exec -it deploy/postgres -- psql -U postgres -d research_agent -c "SELECT version();"
kubectl exec -it deploy/postgres -- psql -U postgres -d research_agent \
  -c "CREATE TABLE IF NOT EXISTS persist_check(id int primary key, note text);
      INSERT INTO persist_check VALUES (1,'杀Pod实验') ON CONFLICT (id) DO NOTHING;"
kubectl exec -it deploy/redis -- redis-cli SET demo "hello-pvc"

# ② ★ 杀 Pod（Deployment 自动重建）
kubectl delete pod -l app=postgres
kubectl delete pod -l app=redis

# ③ 重建后数据复活验证
kubectl exec -it deploy/postgres -- psql -U postgres -d research_agent -c "SELECT * FROM persist_check;"
#  id |   note
# ----+-----------
#   1 | 杀Pod实验     ← 还在！
kubectl exec -it deploy/redis -- redis-cli GET demo
# "hello-pvc"        ← 还在！
```

**对照**：同样"杀掉等重建"——Nacos 的数据会全丢（没挂 PVC），而 PG/Redis 完好无损。**一正一反 = "有状态服务必须持久化"的实证**。

### 2.7 知识点提炼

| 知识点 | 一句话 |
|--------|--------|
| **PVC + local-path** | WaitForFirstConsumer："卷跟 Pod 走"；数据落在 Pod 所在节点的本地目录（`/var/lib/rancher/k3s/storage/`） |
| **Secret** | 密码与清单分离管理（base64 非加密，只做"分离"） |
| **Recreate 策略** | 有状态单副本必备（防新旧 Pod 抢卷） |
| **Service DNS 数据层** | `postgres-svc:5432` / `redis-svc:6379`——屏蔽"数据到底在哪台节点" |
| **卷钉节点** | 卷绑定后，Pod 重建**必回原节点**（否则因卷不可用而 Pending）——实验后观察 `-o wide` 可验证 |

> **【企业级标注】** local 卷钉在节点上——**该节点故障时数据不可用**（卷无法漂移）。这就是生产用网络存储（NFS/Ceph/云盘）或托管数据库的原因。

---

## 三、第 2 课：backend 进集群（2026-09-28 完成）

### 3.1 已完成（2026-09-26 夜）

- **镜像就绪**：Windows 构建 `research-agent:1.2` → save/scp/import **双节点**（digest 一致 `4a62fe78...` ✓）；
- **清单修订并 apply**（`~/k8s-lab/research-agent.yaml`，5 处变更）：
  1. `image: 1.0 → 1.2`（顺手拉正两笔历史漂移：文件停在 1.0、live 是 set image 的 1.1）；
  2. 新增 **env 块（7 项）**：`SPRING_PROFILES_ACTIVE=pg`（覆盖 Dockerfile 的 dev/H2——"别让它等于 dev"是关键）、`DB_URL=jdbc:postgresql://postgres-svc:5432/research_agent`、`DB_USERNAME`、`DB_PASSWORD`（← `pg-secret` 的 secretKeyRef）、`REDIS_HOST=redis-svc`、`JAVA_OPTS=-Xms128m -Xmx256m`、`OPENAI_API_KEY=sk-placeholder`（防 langchain4j 启动校验）；
  3. 补回**软反亲和**（E.8 成就曾失效：两副本挤在 node2）；
  4. 新增 **resources**（requests 256Mi / limits 512Mi）；
  5. 补 Deployment 自身 labels。

### 3.2 ⚠️ 首轮上线暴露 B2：Flyway 缺 PG 模块（已修复 ✅）

- 现象：Pod 启动失败——`error creating bean 'flywayInitializer' ... FlywayException: Unsupported Database: PostgreSQL 16.15`；
- 根因：Flyway 10+ 数据库支持模块化，pom 缺 **`flyway-database-postgresql`**（只有 flyway-core）；**本地从未暴露**——dev profile 下 `flyway.enabled=false`（H2 + `sql.init` 模式），Flyway 今天才第一次真正运行（"demo 路径掩盖生产依赖缺失"的教材级案例）；
- 兜底：滚动更新 maxUnavailable=0——新版卡住不杀旧版（"零中断"机制现场实证）；
- **修复**：`backend/pom.xml` 补 `flyway-database-postgresql`（版本由 Boot BOM 管理）→ 重建 **1.3** 镜像 → 双节点导入（digest 一致 `ca36e176...`）→ 上线。修复后日志：`Database: jdbc:postgresql://postgres-svc:5432/research_agent (PostgreSQL 16.15)` + `Successfully validated 1 migration`——Flyway 正式"认得 PG"。

### 3.3 ⚠️ 二次上线暴露 B3：实验表挡路（已修复 ✅）

- 现象：`FlywayException: Found non-empty schema(s) "public" but no schema history table.`（应用启动失败、Pod 崩溃循环）；
- 根因：**第 1 课的 `persist_check` 实验表**还在库里 → `public` schema "非空"、又没有 `flyway_schema_history` 记账本 → 触发 **Flyway 的"非空库保护"**，拒绝动工（防误伤"有数据的库"）；
- 修复：`DROP TABLE persist_check;`（实验表功成身退）→ 删 Pod 加速重试 → 迁移成功；
- 知识点：**交给迁移工具的库应"干净出生"**；若真要接管"已有数据的库"，用 `baselineOnMigrate` + `baseline-version` 认领基线（生产里"遗留系统接入迁移工具"的标准议题）。

### 3.4 🏁 通关验收（2026-09-28）

| 判据 | 结果 |
|------|------|
| 双副本状态 | `1/1 Running`（重启 0 次，一次起飞） ✓ |
| Flyway 迁移 | `Successfully applied`；且 `ddl-auto: validate` 通过（应用能启动 = 表结构与 JPA 实体全对齐） ✓ |
| 接口健康 | `{"service":"research-agent-backend",...,"status":"ok"}` ✓ |
| PG 表清单 | **9 张**：`flyway_schema_history`（Flyway 记账本）+ 8 张业务表（`human_confirmation` / `research_plan` / `research_report` / `research_step` / `research_task` / `source_document` / `step_execution` / `task_event_log`） ✓ |

> **优美的闭环**：启动日志 "Found **8 JPA repository interfaces**" 与 8 张业务表**一一对应**——从 Java 实体到数据库表的全链路自动对齐。

> **本课插曲**：执行途中遭遇 node1 掉线（IP 冲突复发），先完成了一场完整的集群营救——全录见《虚拟机DockerK8s实战复盘.md》**E.10** / 踩坑 **P22**。（连带副产物：PG/Redis 被节点驱逐后**重建回卷所在节点**——"卷亲和"获得节点级实证。）

---

## 四、第 3 课：Nacos 接入（2026-09-28 完成）

> **目标**：把 backend（单体）注册进集群内的 Nacos——**先接注册中心、暂不拆微服务**（四件事解耦见 1.1）。

### 4.1 版本矩阵（"三件套"首次实战）

| 件 | 版本 | 说明 |
|---|------|------|
| Spring Boot | 3.5.13（既有） | 不动 |
| Spring Cloud | **2025.0.0** | 新增 BOM |
| Spring Cloud Alibaba | **2025.0.0.0** | 新增 BOM + `nacos-discovery` starter |

> 来源：阿里云 SCA 官网版本说明（Boot 3.5.x ↔ Cloud 2025.0.x ↔ Alibaba 2025.0.0.0）。**注意与 micro-lab 的 2023.0.1.0 那套完全不同**——真实项目在更新的版本线上，"三件套必须配套"在这里第一次真实落地。

### 4.2 改造清单（2 个文件）

- `pom.xml`：properties 加 `spring-cloud.version / spring-cloud-alibaba.version` 两个属性；`dependencyManagement` 双 BOM import；新增依赖 `spring-cloud-starter-alibaba-nacos-discovery`；
- `application.yml`：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_ADDR:192.168.157.129:30048}
```

**L2 占位符写法**（本地直连默认值 / 集群由 env 覆盖为 `nacos-svc:8848`）——理论来源见《micro-lab配置文件全景讲解.md》1.2 补充。

### 4.3 上线（沿用第 2 课链路）

镜像 `1.4` → 双节点导入 → yaml 更新（`image: 1.4` + env 新增 `NACOS_ADDR=nacos-svc:8848`）→ apply → `successfully rolled out` ✓
（插曲：sed 正则捕获组在粘贴中丢失导致翻车，改用 **nano 手改**成功——见踩坑 **B4**。）

### 4.4 验证（注册成功）

Nacos API 查询（`/nacos/v1/ns/instance/list?serviceName=research-agent-backend`）：

```json
"hosts": [
    { "ip": "10.42.0.12", "port": 8080, "healthy": true, "ephemeral": true,
      "metadata": { "preserved.register.source": "SPRING_CLOUD" } },
    { "ip": "10.42.0.11", "port": 8080, "healthy": true, "ephemeral": true, "...": "同结构" }
]
```

**关键解读**：
- **2 个实例**（两副本都注册）——"一个服务名 ↔ 多个实例"是注册中心的完整意义；
- `ephemeral: true`（临时实例）+ 心跳三档参数：**每 5s 心跳 → 15s 无心跳标"不健康" → 30s 摘除**——"服务下线自动感知"的全部秘密；
- `register.source: SPRING_CLOUD`——注册者身份（nacos-discovery starter）。

### 4.5 ★ 动态实验："名字跳舞"（2 → 1 → 2）

```bash
# 杀掉一个副本，连查实例数
kubectl delete pod <一个副本名>
for i in 1 2 3; do curl -s ".../instance/list?serviceName=research-agent-backend" | grep -o 'instanceId' | wc -l; sleep 6; done
# 输出：1 → 1 → 2
```

**解读**：旧实例**秒级消失**——优雅停机触发**主动 deregister**（比 30s 心跳超时快得多）；新 Pod 启动完成后自动注册，名单满血复活。**与 micro-lab"摘牌传播"（preStop sleep 5 等的就是它）互为印证**。

### 4.6 小结与下一步

- 单体注册本身不改变架构（"没有消费方"的局限仍在）——它的价值是：**将来网关接入时即刻可用**（按服务名路由 + 负载均衡）；
- 已埋好的伏笔：micro-lab 的 **gateway** 接进 Nacos 后，即可路由到 `research-agent-backend`——"真实项目微服务化"的第一步。**✅ 已于 2026-10-01 兑现 → 见第五章第 4 课。**

---

## 五、第 4 课：gateway 路由到 backend（2026-10-01 完成）

### 5.1 目标：让 micro-lab 网关成为真实项目的入口

**目标链路**：

```text
浏览器 / curl
    ↓ http://192.168.157.129:30090        （gateway NodePort，集群外唯一入口）
  gateway（Spring Cloud Gateway，按服务名 lb:// 解析）
    ↓ 查 Nacos："research-agent-backend 的实例在哪？"
  backend Pod ×2（两节点各一，由网关负载均衡）
    ↓
  postgres-svc / redis-svc
```

**意义**：4.6 伏笔兑现——**"真实项目微服务化"第一步**。单体从"直接对外（NodePort 30081）"升级为"经网关统一入口"：服务名解析、负载均衡交给网关 + Nacos；将来鉴权、限流、灰度也在网关层发生。

### 5.2 gateway 配置改造（`micro-lab/gateway/application.yml`）

**① 新增路由 3**：

```yaml
- id: research-agent-route
  uri: lb://research-agent-backend
  predicates:
    - Path=/api/tasks/**,/api/health
  # ★ 不加 StripPrefix（判据见下）
```

**② Nacos 地址升级为 L2 占位符**（与 backend 风格统一）：

```yaml
server-addr: ${NACOS_ADDR:192.168.157.129:30048}
```

#### ★ 核心教学点 1：StripPrefix 增删的判断法

**判据 = "目标服务实际的路径长什么样"，而不是网关的习惯**：

| | micro-lab 服务（user/order） | backend（research-agent） |
|---|---|---|
| 客户端请求 | `/api/users/1` | `/api/tasks` |
| 目标服务实际路径 | `/users/1`（**不带** /api） | `/api/tasks`（**自带** /api） |
| 网关动作 | `StripPrefix=1`：剥掉第 1 段 | **原样转发**（不加 filter） |

#### ★ 核心教学点 2：两套 env 风格的"汇合现场"

本次把 gateway 从"标准键名式"升到"占位符式"——当时形成两套风格并存的过渡态；**该过渡态已于 2026-10-03 清账日结束**（gateway / backend / user / order **全部统一为占位符式**）：

| | 标准键名式（历史形态，保留作教学对照） | 占位符式（**现状：四者全用**） |
|---|---|---|
| yml 写法 | `server-addr: 192.168.157.129:30048`（写死） | `server-addr: ${NACOS_ADDR:192.168.157.129:30048}` |
| env 名 | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`（必须可推导） | `NACOS_ADDR`（自定义，两边对齐即可） |
| 不注入 env 时 | 用写死值 | 用冒号后的默认值 |

**★ 配套铁律（安全知识）**：yml 占位符与清单 env 名**必须成对升级**，顺序应为"**先升 yml（打包上线）→ 再升清单 env 名**"。若反过来（清单先改 `NACOS_ADDR` 而 yml 未升）会**静默降级**——没人接住新变量名、退回写死 IP，集群内碰巧还能通（NodePort 从集群内可达），表面正常、机制变脏。这类错误**不报错**，是"名字写错=静默不生效"的又一变种。

> **★ 精确边界（2026-10-03 清账日补充）**：铁律的**真正含义**是"不要让任何 Pod 处于 env 名与 yml 不配套的**稳态**"——因此"**镜像 + 清单同批交付**"（一次 apply）**同样安全**：滚动混合期里旧 Pod（旧 env+旧 yml）与新 Pod（新 env+新 yml）**各自自洽**。铁律防的是"清单单方面先改、而镜像没跟上"的稳态故障，而非"必须分两轮"。**理解原理 > 背口诀**。

### 5.3 交付链（完整五步演练）

```powershell
# ---- Windows ----
mvn clean package -DskipTests -pl gateway -am     # -pl：只构建 gateway 模块；-am：连带其依赖
docker build -t gateway:1.1.0 gateway              # 新 tag（与集群里的 1.0.0 区分，杜绝旧 tar 冒充）
docker save gateway:1.1.0 -o gateway-1.1.0.tar
scp gateway-1.1.0.tar lcc@192.168.157.128:/tmp/    # 惯例：中转目录 /tmp（自动清理、不占家目录）
scp gateway-1.1.0.tar lcc@192.168.157.129:/tmp/
```

```bash
# ---- 双节点分别执行 ----
tar -tf /tmp/gateway-1.1.0.tar > /dev/null && echo "tar 完整 ✓"   # 先验完整性再导入（P18 惯例）
sudo k3s ctr -n k8s.io images import /tmp/gateway-1.1.0.tar
sudo k3s ctr -n k8s.io images ls | grep gateway                    # 核对：两台 digest 一致
rm /tmp/gateway-1.1.0.tar                                          # 用完即删（P21 惯例）
```

**清单更新（`~/k8s-lab/micro-lab.yaml`）——先备份，再两条 sed**：

```bash
cp ~/k8s-lab/micro-lab.yaml ~/k8s-lab/micro-lab.yaml.bak-1001

# ① 镜像 tag（"image: gateway:1.0.0" 全文件唯一）
sed -i 's|image: gateway:1.0.0|image: gateway:1.1.0|' ~/k8s-lab/micro-lab.yaml

# ② env 名——★ 行号限定第 166 行！
#    全文件替换会误伤 user-service（26 行）/ order-service（97 行）的同名 env——
#    它们的 yml 还是旧风格，改了没人接住（静默降级）
sed -i '166s|SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR|NACOS_ADDR|' ~/k8s-lab/micro-lab.yaml

# 复查（三处 env 状态 + 两处镜像 tag）
grep -n 'image:\|SPRING_CLOUD\|NACOS_ADDR' ~/k8s-lab/micro-lab.yaml
```

```bash
# ---- 提交 + 观察 ----
kubectl apply -f ~/k8s-lab/micro-lab.yaml     # apply 幂等：只有 gateway 模板变了，只有它滚动
kubectl rollout status deploy/gateway
kubectl get pod -l app=gateway -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[0].image,STATUS:.status.phase'
```

### 5.4 通关证据（2026-10-01）

| 验证 | 命令 | 结果 |
|------|------|------|
| **改前基线** | `curl 30090/api/tasks` | **404**（无匹配路由） |
| **核心判据** | `curl 30090/api/health` | **200** `{"status":"ok","service":"research-agent-backend"}` ← 网关→真实项目回执 |
| **数据链路** | `curl 30090/api/tasks` | **200** 任务列表（09-28 端到端任务 `TASK-F86D065F`「集群端到端验证」在列） |
| **老路由回归** | `curl 30090/api/users/1` | **200** `{"userId":1,"userName":"张三","userPhone":"13800000001","userLevel":"黄金会员"}`（micro-lab 链路无损） |

### 5.5 小结

- 网关成为"统一入口"：**同一入口按路径分流**——`/api/users|orders` 走教学服务、`/api/tasks|health` 走真实项目；
- 一次交付两件事：路由到真实项目 + gateway 的 env 风格统一（"顺路优化"的范例）；
- ~~遗留选项（挂账）：user/order 的占位符升级（"B 升级"）——等下次有别的改动时顺路做~~ → **✅ 2026-10-03 清账日 2.0 已结清**（镜像 1.1.0 ×2 + 清单 env 统一 `NACOS_ADDR`，四服务占位符式全统一——见《微服务实战复盘.md》7.3 演进记录 2）；
- **课后彩蛋（2026-10-01）**：滚动重启 backend（两终端：一个循环打网关、一个 `rollout restart deploy/research-agent`）——**全程零失败**（循环 curl 无一个非 200）——"preStop + 优雅摘牌"的零中断理想在**真实项目**上验证成真。

---

## 六、第 5 课：配置中心接入（2026-10-03 完成）

> **课程结构**：本章分上下半场——**上半场**（2026-10-02 凌晨）：镜像 1.5 改造 + 撞上"客户端-服务端版本错配"（6.1~6.4）；**下半场**（2026-10-03）：升级 Nacos Server 3.0.3，配置中心正式点亮（6.5~6.6）。

### 6.1 目标与改造（镜像 1.5，已上线）

**目标**：backend 接入 Nacos config——业务配置搬进配置中心、支持热更新（承接第 3 课"注册"之后的下一站）。

| 层 | 改动 | 说明 |
|----|------|------|
| **pom** | +`spring-cloud-starter-alibaba-nacos-config`、+`spring-boot-starter-actuator` | 配置通道 + 观测窗（版本由 BOM 管） |
| **yml** | +`spring.config.import: optional:nacos:research-agent-backend.yaml`；+`spring.cloud.nacos.config.server-addr: ${NACOS_ADDR:...}`；+`management` 暴露 `health,info,env` | `optional:` = Nacos 无此配置也照常启动 |
| **清单** | +`MANAGEMENT_ENDPOINT_ENV_SHOW_VALUES=always`（观测窗开真值）；+`SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR=nacos-svc:8848`（引导配置补注入） | 两次 env 补注入均**无效**——成为诊断链的一环 |
| **镜像** | 1.5（双节点 import + 上线） | actuator 观测窗验证可用（`/actuator/health` 200、`/actuator/env` 可查） |

### 6.2 现象：配置"读空"

- Nacos 控制台已发布 `research-agent-backend.yaml`（`app.llm.temperature: 0.8`），开放 API 可读 ✓；
- 应用侧持续报 `NacosConfigDataLoader: config[dataId=research-agent-backend.yaml, group=DEFAULT_GROUP] is empty`（首发 + 重启 ×3 稳定复现）；
- `/actuator/env/app.llm.temperature` 永远为 `0.2` + 来源 = jar 内 yml——**Nacos 源从未出现在 propertySources**。

### 6.3 诊断链（"证据链排障"方法论现场）

| # | 动作 | 结果 | 结论 |
|---|------|------|------|
| 1 | 等待 + 重启 ×3 | 仍 `is empty` | 排除"没等够 / 未建立监听" |
| 2 | namespace 测试（带 `tenant=public` / 不带） | 均可读到内容 | 排除命名空间语义差异 |
| 3 | 连接日志普查 | **只有 naming 一组连接**，config 客户端无连接痕迹 | config 模块从未成功连上 |
| 4 | env 注入 `CONFIG_SERVER_ADDR` | 无效 | 排除"地址未送达"——问题不在地址 |
| 5 | **版本取证（Maven 仓库）** | **`nacos-client 3.0.3`**（+`nacos-client-basic` 3.0.3，SCA 2025.0.0.0 传递）vs 服务端 **Nacos Server 2.3.2** | ★ 破案线索 |
| 6 | 官方口径 + 日志特征 | 官方 FAQ："2.X 服务端兼容 1.2.0~2.X 客户端"（**不含 3.x**）；启动日志出现 3.x 特征 `AbstractAbilityControlManager ... support modes: [SDK_CLIENT]`（能力协商） | 佐证 |

### 6.4 结论与路线

**结论（高置信）**：**客户端 3.0.3 ↔ 服务端 2.3.2 跨大版本错配**——这是第 1 课"三件套配套铁律"的**镜像版**：**不只是 Boot / Cloud / Alibaba 要配套，Client ↔ Server 也要看配套表**。

**表现特征（排查陷阱）**：naming（注册）半可用——注册 / 心跳 / 摘牌均正常；**config（配置）静默空读**——**不报错**，只在日志里留下一句歧义的 `is empty`（"配置不存在"与"读取失败"共用同一句话）。

**修复路线**：**升级 Nacos Server → 3.0.3**（与 SCA 2025 的客户端配套）——**✅ 已于 2026-10-03 执行，见 6.5。**

| 要点 | 说明 |
|------|------|
| 有利条件 | PVC 持久化已就绪（10-01）；registries 加速已通（10-01）；数据量极小（两条配置，重建即可） |
| 注意点 | 3.x 的 Server/Console 拆分（控制台端口变化）待查；升级前快照；micro-lab 旧客户端（2.3.2）兼容性当场验证 |
| 验证三连 | ① backend config 点亮（0.8 + Nacos 源）② 旧客户端兼容 ③ 全链路回归 |

**1.5 镜像不回退**——actuator / show-values / config-import 均为"等升级即生效"的伏笔。

### 6.5 下半场：Nacos Server 升级 3.0.3（2026-10-03 完成）

**① 升级前核实（官方兼容表 + 部署规格）**：

| 查证项 | 结论 |
|--------|------|
| micro-lab 客户端（2.3.2）会被"升级坏了"吗 | **不会**——官方兼容表：0.x 不兼容 / 1.x 兼容（v3.2 停）/ **2.x 兼容** / 3.x 兼容 |
| 客户端-服务端 vs 三件套的"绑定"性质 | 三件套 = **硬绑定**（同进程代码耦合，必须同升）；Nacos C-S = **软绑定（单向兼容）**（协议层：服务端兼容老客户端、不兼容比自己新的客户端——上半场撞的正是后者） |
| 3.x 镜像硬性要求 | **鉴权三变量必填**（`NACOS_AUTH_TOKEN`（Base64、原文 >32 字符）/ `NACOS_AUTH_IDENTITY_KEY` / `NACOS_AUTH_IDENTITY_VALUE`）——缺则拒启 |
| 3.x 运营形态变化 | Server/Console 拆分（**控制台独立 8080**，首启初始化 admin 密码）；API v3 化（admin 需 token） |

**② 三个决策**：

| # | 决策 | 理由 |
|---|------|------|
| ① | **干净重建**：新卷 `nacos-pvc-v3`（旧卷 `nacos-pvc` 保留观察） | 2.x→3.x 的 Derby 表结构迁移无官方路径（手册只覆盖 MySQL）；数据仅两条配置，重建零风险 |
| ② | 设三变量、**不开客户端鉴权**（不设 `NACOS_AUTH_ENABLE=true`） | 官方 quickstart 默认形态；⚠️ 学习环境简化路径——企业级需开启并给所有客户端配 creds（留作"鉴权课"） |
| ③ | 控制台 Service + `8080 → NodePort 30080` | 3.x 控制台独立后的访问入口 |

**③ 交付链（本课特有动作）**：

```bash
# 升级前：VMware 快照 + 导出两条配置（趁 v1 API 可用）+ 控制台人眼核对条数
# 预拉镜像：★ 走 kubelet 通道（ctr pull 不读 mirror 配置——清账日破案结论）
kubectl run pull-v303-node1 --image=docker.io/nacos/nacos-server:v3.0.3 --restart=Never \
  --overrides='{"spec":{"nodeName":"lccserver"}}' --command -- sh -c "echo pull-ok"
kubectl run pull-v303-node2 --image=docker.io/nacos/nacos-server:v3.0.3 --restart=Never \
  --overrides='{"spec":{"nodeName":"lccserver-node2"}}' --command -- sh -c "echo pull-ok"
# 清单四处变更（nacos.yaml）：镜像 tag / +鉴权三 env / 新卷 nacos-pvc-v3 / Service +8080
# apply（Recreate：先杀后建——短暂中断）
```

**④ 现场新知识（实测）**：

| 项 | 结果 | 说明 |
|----|------|------|
| 启动形态 | 日志**双横幅**：`Nacos Server API`（8848）+ `Nacos Console 3.0.3 ... Port: 8080` | Server/Console 拆分亲眼所见 |
| `/nacos/actuator/health` | **404** | 3.x 健康路径变化（2.x 老路径退役） |
| **v1 API 兼容层** | **实测活着**：`/nacos/v1/ns/service/list` 与 `/nacos/v1/cs/configs` 均正常返回 | 社区"v1 默认禁用"说法对 3.0.3 读接口不成立——我们的老验证命令继续可用 |
| 控制台 | 首访初始化管理员（用户 `nacos`；学习环境，文档不落密码明文） | 8080 → NodePort 30080 |

### 6.6 通关证据（2026-10-03）

| 验证 | 命令 | 结果 |
|------|------|------|
| **★ 配置中心点亮**（判据一） | `/actuator/env/app.llm.temperature` | `property.source = DEFAULT_GROUP@research-agent-backend.yaml, value 0.8`；**propertySources 中 Nacos 源（0.8）排在 jar 内 yml（0.2）之前**——优先级栈实战证据 |
| **旧客户端读配置**（判据二） | 经网关 `/api/users/config/welcome` | 新欢迎语（micro-lab 2.3.2 → 3.0.3 服务端读配置成功，零重启） |
| **全链路**（判据三） | `/api/health` + `/api/tasks` | 200 + 200（`TASK-F86D065F` 在列） |
| **双年代同台** | Nacos 服务列表 | gateway / user-service / order-service（**2.3.2**）+ research-agent-backend（**3.0.3**）同注册于 3.0.3 服务端 |
| 配置重建 | 控制台（从升级前导出文件粘贴） | 两条配置原文核对 + 新库 v1 API 读回一致 |

**小结**：上半场挖出的"版本代际问题"在下半场闭环——**服务端换代、两端客户端零改动无感**（自动重连重注册）；"客户端-服务端也是配套表，但**服务端单向兼容**"这条认知，由 live 现场立此存照。

### 6.7 课后彩蛋：热更新闭环 + 健康检查形态定案（2026-10-03）

#### ★ 热更新实验：改值立变（零重启）

**实验**：控制台把 `app.llm.temperature` 0.8 → 0.5——**三点对照一次对齐**：

| 视角 | 命令 | 结果 |
|------|------|------|
| **服务端**（Nacos 存的） | v1 API 读配置 | `temperature: 0.5` ✓ |
| **客户端**（backend 生效的） | `/actuator/env/app.llm.temperature` | Nacos 源 `value: 0.5` ✓ |
| **日志**（推送→刷新链路） | `kubectl logs … \| grep -E "refresh\|push"` | 新时间戳 `13:24:18` 五连 ✓（md5 `9f840a27…` → `049a7b49…`） |

**客户端日志全链路拆解**（13:24:18 那次，从推送到刷新 **307ms**）：

```text
13:24:18.217  Receive server push request (ConfigChangeNotifyRequest)  ← 服务端推送到达
13:24:18.218  [server-push] config changed → Ack                       ← 确认回执
13:24:18.234  [notify-listener] … md5=049a7b49…                        ← MD5 比对（变了）→ 派发
13:24:18.235  [Nacos Config] Receive Nacos config change               ← SCA 收到变更
13:24:18.541  Refresh keys changed: [app.llm.temperature]              ← ★ Spring 刷新事件（精确到键）
13:24:18.542  [notify-ok] job run cost=307 millis                       ← 全链路闭环
```

（前一组 `12:41:25` 的对照实验同样成功，510ms——两次实验互为复现。）**结论**：配置中心"**改值立变、零重启**"闭环成立——**第 5 课存在的最终意义**。

#### 健康检查形态定案（3.0.3 实测）

| 探测 | 结果 | 定论 |
|------|------|------|
| `8848/nacos/actuator/prometheus` | 200（JVM 指标文本） | ★ 8848 暴露的唯一 actuator 端点 = **prometheus** |
| `8848/nacos/actuator`（索引） | `{"_links":{…"prometheus":…}}` | 只有 self + prometheus——"Exposing 1 endpoint"之谜钉死 |
| `8848/nacos/actuator/health` | **404** | **3.0.3 无 health 端点**（2.x 形态退役） |
| `8080/actuator/health` | 500（`No static resource`） | Console 无 actuator；**Spring Boot 3.2 的"伪装 500"** |
| `/nacos/v3/console/health/readiness` | 404 / 500 | 3.0.3 尚无此接口（更高版本才有） |

**探针结论**：将来给 Nacos 加 K8s 探针——用 **TCP 探针（8848）** 或届时查最新官方示例——**别照抄 2.x 的 `/nacos/actuator/health`**。

**方法论沉淀**：① **"500 可能是伪装成 500 的 404"**——状态码必须结合**响应体**读（本轮一次推断被现场修正——"证据链"方法论自我示范）；② **"记忆里的路径"在新版本上失效是常态**——查证 > 记忆。

---

## 七、踩坑记录（B 编号）

| # | 坑 | 现象 | 解法 |
|---|-----|------|------|
| **B1** | 直连 docker.io 被拒 | `ctr pull docker.io/library/postgres:16-alpine` → `dial tcp ...: connect: connection refused` | 走 Daocloud 全名（`docker.m.daocloud.io/library/...`），且 **yaml 的 `image:` 与拉取名字字面一致**。※副产品认知：镜像名写成"可达仓库全名"后，kubelet 可在**任意节点**自动拉取——本次 PG Pod 被调度到未预拉的 node1，也自举成功 |
| **B2** | Flyway 缺 PG 支持模块 | Pod 启动失败：`FlywayException: Unsupported Database: PostgreSQL 16.15`（bean `flywayInitializer` 创建失败 → 应用上下文回滚） | Flyway 10+ 起数据库支持模块化——pom 补 `flyway-database-postgresql`（版本由 Boot BOM 管理）。※深层教训：dev profile 下 `flyway.enabled=false`，Flyway 从未真正运行过——**H2 demo 掩盖了"生产依赖缺一块"**；接真实 DB 的第一课就把这类问题逼出来了——这正是走生产路径的价值 |
| **B3** | 实验表挡路（Flyway"非空库保护"） | `FlywayException: Found non-empty schema(s) "public" but no schema history table`（Pod 崩溃循环） | 第 1 课的 `persist_check` 实验表使 schema"非空"、又没有 `flyway_schema_history` 记账本 → Flyway 拒绝动工（防误伤有数据的库） | `DROP TABLE persist_check;`（实验表功成身退）→ 删 Pod 加速重试。※知识点：交给迁移工具的库应"干净出生"；接管旧数据用 `baselineOnMigrate` 认领 |
| **B4** | （工具操作）sed 捕获组在粘贴中丢失 | `sed: invalid reference \1 on 's' command's RHS`——粘贴时 `\(`、`\)` 被吞成普通字符，替换却引用 `\1` | 改用 **nano 手改**（眼见为实、零转义风险）+ `kubectl apply --dry-run=client` 语法体检。※教训：复杂 sed 在"人肉复制"链路上很脆弱——服务器上改配置文件，nano 是更稳的默认选择 |

---

## 八、进度与待办

- [x] **第 1 课**：数据层进集群（PG 16 + Redis 7 + PVC + 持久化验证）——2026-09-26
- [x] **第 2 课（✅ 2026-09-28 完成）**：backend 进集群——镜像 1.3（B2 修复版）双节点上线 + env/反亲和/resources + **Flyway 建表成功（9 张）** + 双副本 Running + 接口 200 + 端到端任务落库验证。途中趟过：B2（Flyway 模块缺失）、B3（实验表挡路）、node1 掉线大营救（见《虚拟机……》E.10/P22）
- [x] **第 3 课（✅ 2026-09-28 完成）**：Nacos 接入——三件套 2025 版本线（Cloud 2025.0.0 / Alibaba 2025.0.0.0）+ 注册成功（2 实例 healthy）+ 动态实验 2→1→2；镜像 1.4。插曲：B4（sed → nano）
- [x] **第 4 课（✅ 2026-10-01 完成）**：gateway 路由到 `research-agent-backend`——镜像 1.1.0（新路由 3：`Path=/api/tasks/**,/api/health`、不加 StripPrefix）+ gateway yml 占位符化 + 清单 env 改 `NACOS_ADDR`；通关证据：网关 404→200（health/tasks/users 三连 200）
- [x] **关联小课 ✅（2026-10-01 清账日完成）**：`registries.yaml` 修复——重启 k3s 渲染 `certs.d` 改道牌后，**双节点实测"裸名镜像"577ms 拉取成功**；途中破案历史误判（"ctr 不走 mirror 配置"，正确验证姿势 = kubelet/CRI 路径——详见《micro-lab配置文件全景讲解.md》第五章"真相揭示"）
- [x] **第 5 课（✅ 2026-10-03 完成）**：配置中心接入——镜像 1.5（config-import + actuator 观测窗）；上半场诊断"客户端 3.0.3 ↔ 服务端 2.3.2 版本错配"（第六章）；下半场 **升级 Nacos Server 3.0.3**（新卷 `nacos-pvc-v3` + 鉴权三变量 + 控制台 8080→NodePort 30080）→ **配置中心点亮**（temperature 0.8 / Nacos 源）+ **双年代客户端同台**（2.3.2 与 3.0.3）+ 全链路 200
