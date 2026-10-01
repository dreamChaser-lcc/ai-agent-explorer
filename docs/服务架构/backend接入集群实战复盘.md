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
- [五、踩坑记录（B 编号）](#五踩坑记录b-编号)
- [六、进度与待办](#六进度与待办)

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
第 2 课  backend 进集群（新镜像 + PG env + Flyway 建表）    ⏳ 下一课
第 3 课  Nacos 接入（配置中心先行；注册中心待消费方）         ⏸
第 4 课  更远：配置中心深化 / 微服务化                       ⏸
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
- 已埋好的伏笔：micro-lab 的 **gateway** 接进 Nacos 后，即可路由到 `research-agent-backend`——"真实项目微服务化"的第一步（改天开工）。

---

## 五、踩坑记录（B 编号）

| # | 坑 | 现象 | 解法 |
|---|-----|------|------|
| **B1** | 直连 docker.io 被拒 | `ctr pull docker.io/library/postgres:16-alpine` → `dial tcp ...: connect: connection refused` | 走 Daocloud 全名（`docker.m.daocloud.io/library/...`），且 **yaml 的 `image:` 与拉取名字字面一致**。※副产品认知：镜像名写成"可达仓库全名"后，kubelet 可在**任意节点**自动拉取——本次 PG Pod 被调度到未预拉的 node1，也自举成功 |
| **B2** | Flyway 缺 PG 支持模块 | Pod 启动失败：`FlywayException: Unsupported Database: PostgreSQL 16.15`（bean `flywayInitializer` 创建失败 → 应用上下文回滚） | Flyway 10+ 起数据库支持模块化——pom 补 `flyway-database-postgresql`（版本由 Boot BOM 管理）。※深层教训：dev profile 下 `flyway.enabled=false`，Flyway 从未真正运行过——**H2 demo 掩盖了"生产依赖缺一块"**；接真实 DB 的第一课就把这类问题逼出来了——这正是走生产路径的价值 |
| **B3** | 实验表挡路（Flyway"非空库保护"） | `FlywayException: Found non-empty schema(s) "public" but no schema history table`（Pod 崩溃循环） | 第 1 课的 `persist_check` 实验表使 schema"非空"、又没有 `flyway_schema_history` 记账本 → Flyway 拒绝动工（防误伤有数据的库） | `DROP TABLE persist_check;`（实验表功成身退）→ 删 Pod 加速重试。※知识点：交给迁移工具的库应"干净出生"；接管旧数据用 `baselineOnMigrate` 认领 |
| **B4** | （工具操作）sed 捕获组在粘贴中丢失 | `sed: invalid reference \1 on 's' command's RHS`——粘贴时 `\(`、`\)` 被吞成普通字符，替换却引用 `\1` | 改用 **nano 手改**（眼见为实、零转义风险）+ `kubectl apply --dry-run=client` 语法体检。※教训：复杂 sed 在"人肉复制"链路上很脆弱——服务器上改配置文件，nano 是更稳的默认选择 |

---

## 六、进度与待办

- [x] **第 1 课**：数据层进集群（PG 16 + Redis 7 + PVC + 持久化验证）——2026-09-26
- [x] **第 2 课（✅ 2026-09-28 完成）**：backend 进集群——镜像 1.3（B2 修复版）双节点上线 + env/反亲和/resources + **Flyway 建表成功（9 张）** + 双副本 Running + 接口 200 + 端到端任务落库验证。途中趟过：B2（Flyway 模块缺失）、B3（实验表挡路）、node1 掉线大营救（见《虚拟机……》E.10/P22）
- [x] **第 3 课（✅ 2026-09-28 完成）**：Nacos 接入——三件套 2025 版本线（Cloud 2025.0.0 / Alibaba 2025.0.0.0）+ 注册成功（2 实例 healthy）+ 动态实验 2→1→2；镜像 1.4。插曲：B4（sed → nano）
- [ ] **第 4 课（下一目标）**：gateway 接 Nacos，路由到 `research-agent-backend`（"真实项目微服务化第一步"）
- [ ] 关联小课：`registries.yaml` 修复（K3s 自动走加速源，一劳永逸；需重启 k3s——独立安排，别打断主线）
