# micro-lab 配置文件全景讲解

> 定位：这是《微服务实战复盘.md》的**配置专题**复习材料——把整个学习过程中出现过的所有配置文件（yaml / Dockerfile / pom.xml / 配置中心条目）集中到一处，讲清它们的**位置、归属、内容、生效方式与区别**。
> 阅读方式：先看第一章建立"分层地图"，再按需深挖对应章节。文档中所有配置文件**保留原始注释**，正文为新增讲解。
> **深化配套**：《SpringBoot配置读取方式全解.md》——本册回答"**文件在哪、怎么生效**"，那册回答"**值怎么流动**"（书写来源 6 种 / 读取方式 4 种 / 本地·K8s·Nacos 三场景横向对比）。

---

## 目录

- [一、先建立地图：五类文件 + 1 条优先级链](#一先建立地图五类文件--1-条优先级链)
- [二、应用世界：3 个 application.yml（工程内）](#二应用世界3-个-applicationyml工程内)
- [三、构建世界：Dockerfile 与 pom.xml（工程内）](#三构建世界dockerfile-与-pomxml工程内)
- [四、集群世界：K8s 清单（虚拟机节点上）](#四集群世界k8s-清单虚拟机节点上)
- [五、系统世界：K3s 的 registries.yaml](#五系统世界k3s-的-registriesyaml)
- [六、配置中心世界：Nacos Data ID](#六配置中心世界nacos-data-id)
- [七、漂移账：kubectl set env 注入的配置](#七漂移账kubectl-set-env-注入的配置)
- [八、速查：分辨口诀 / 生效路径决策树 / FAQ](#八速查分辨口诀--生效路径决策树--faq)
- [九、学习路径建议](#九学习路径建议)

---

## 一、先建立地图：五类文件 + 1 条优先级链

### 1.1 五类文件（按"谁在什么时候读它"排队）

| 类别 | 文件 | 存放位置 | 谁读它 | 改完怎么生效 |
|------|------|---------|--------|-------------|
| **构建类** | 3 个 `Dockerfile` + 4 个 `pom.xml` | Windows：`micro-lab/`（工程内） | Maven / Docker | 重建 jar → 重建镜像 → 重新部署 |
| **应用类** | 3 个 `application.yml` | Windows：`micro-lab/<服务>/src/main/resources/` | Spring Boot 进程 | 重新 `mvn package` → 重建镜像 → 重新部署 |
| **集群类** | `nacos.yaml`、`micro-lab.yaml`、`prometheus.yaml`、`grafana.yaml` 等 | 虚拟机节点：`~/k8s-lab/`、`~/monitor-lab/`、`~/sentinel-lab/` | K8s（kubectl） | `kubectl apply -f` → 立即滚动更新 |
| **配置中心类** | `user-service.yaml`（Data ID） | Nacos 控制台网页上（**不是文件**） | Spring Cloud Config 客户端 | 控制台发布 → 热更新（需 `@RefreshScope`） |
| **系统类** | `registries.yaml` | 节点：`/etc/rancher/k3s/` | K3s 本体（镜像拉取） | **重启 k3s 才生效** |

> 顺序即"生命线"：**先构建（pom/Dockerfile）→ 装进镜像（application.yml）→ 部署（K8s 清单）→ 运行期覆盖（env）→ 动态刷新（Nacos）→ 底层系统（K3s 配置）**。

### 1.2 一条优先级链（最重要的一张图）

同一个配置项（比如 Nacos 地址），最终生效值按下面的优先级叠加：

```
低优先级 ──────────────────────────────────────────────▶ 高优先级

jar 内 application.yml     K8s 环境变量（set env / Deployment env）     Nacos dataId
（应用自带的默认值）        （同一镜像适配不同环境的关键）              （动态共享、可热更新）

示例：
server-addr: 192.168.157.129:30048   →  被 SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR=nacos-svc:8848 覆盖
```

> 📌 **修正注（2026-09-26）**：上图把 Nacos dataId 画在最高位，是 bootstrap 方式的直觉；本项目用的是 Spring Boot 3.x 的 `spring.config.import` 方式——Nacos 配置进入 **config data 层**（与本地 yml 同族）：**能覆盖 jar 内 yml，但会被 OS 环境变量盖住**。精确位置见下方"补充一"的完整栈。

**记住这句话**：写死在 yml 里的是"默认值"，环境相关的值尽量外置（环境变量 / 配置中心），这就是 12-Factor 的"配置与代码分离"。

**补充一：完整优先级栈（高 → 低）**

```text
栈顶 = 优先级最高，取值从这里开始找
┌─────────────────────────────────────────────────────────────┐
│ ① 命令行参数     --spring.cloud.nacos...=xxx                 │ java -jar 时附加（调试利器）
│ ② Java 系统属性  -Dspring.cloud.nacos...=xxx                 │ 启动脚本 / JAVA_TOOL_OPTIONS 里的 -D
│ ③ OS 环境变量    SPRING_CLOUD_NACOS_...=nacos-svc:8848       │ ★ K8s env 注入（micro-lab.yaml / set env）
│ ④ jar 外部 yml   ./config/application.yml（jar 旁边）         │ 运维可改，不动 jar
│ ⑤ jar 内部 yml   BOOT-INF/classes/application.yml           │ ★ 源码里那份（最低兜底）
│ ⑥ 代码兜底       setDefaultProperties / @PropertySource      │ 了解即可
└─────────────────────────────────────────────────────────────┘
        取值方向：↓ 从上往下问，谁先答"我有"就听谁的（找到即停）
```

日常打交道最多的是 **③ vs ⑤** 这一对；①②少见，但"临时调试改一行"时特好用。

**补充二：环境变量名怎么推导（relaxed binding 三步法）**

拿到 yml 里的任意键，三步得到环境变量名：

```text
spring.cloud.nacos.discovery.server-addr
  │ ① 点（.）→ 下划线（_）       spring_cloud_nacos_discovery_server-addr
  │ ② 连字符（-）→ 下划线（_）    spring_cloud_nacos_discovery_server_addr
  │ ③ 全部大写                  SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR  ✓
```

（官方文档的规则是"连字符删除"，即 `...SERVERADDR` 也合法；绑定匹配时会先把两边"标准化"——转小写、去分隔符——再比对，所以"换下划线"同样 100% 有效，且可读性更好。）

> 📌 **现状注（2026-10-03 起）**：下表前两行是"标准键名式"的**机制教学样本**；micro-lab 与 backend 现已**全部统一为"占位符式"**（yml 里 `${NACOS_ADDR:…}` 显式引用、清单 env 名为 `NACOS_ADDR`）。relaxed binding 仍是通用底层机制（任何环境变量都能这样覆盖任何配置键），只是不再是本项目的首选书写风格。

| yml 里的键 | 对应环境变量 | 用在哪 |
|-----------|-------------|--------|
| `spring.cloud.nacos.discovery.server-addr` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | **（历史）** micro-lab.yaml 三服务——2026-10-03 已改 `NACOS_ADDR` 占位符式 |
| `spring.cloud.nacos.config.server-addr` | `SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR` | **（历史）** micro-lab.yaml user-service——2026-10-03 已并入 `NACOS_ADDR` |
| `spring.cloud.sentinel.transport.dashboard` | `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD` | set env 注入 Dashboard 地址 |
| `management.endpoints.web.exposure.include` | `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` | set env 放开端点（监控篇） |
| `management.endpoint.health.probes.enabled` | `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` | micro-lab.yaml v3（探针篇） |
| `server.shutdown` | `SERVER_SHUTDOWN` | micro-lab.yaml v4（优雅停机） |

⚠️ **名字写错不会报错——"静默不生效"**（悄悄退回 yml 默认值）。所以"配了没生效"第一反应先查现场：

```bash
kubectl exec <pod> -- printenv | grep 关键字     # 名字注入对了吗
kubectl describe deploy/<名字>                   # Environment 段声明了什么
```

**补充三："覆盖"的过程——不是改写，是查找短路**

心智模型：**覆盖 ≠ 修改文件，而是"取值顺序"问题**——栈里从顶往下找，**找到即停**。（前端类比：CSS 层叠——默认样式表 vs 行内 style，行内赢，但样式表本身没被改。）

过程三步：

```text
① 启动组装：Environment 把各来源按优先级"登记"进栈（此刻不读任何值）
② 取值审查：Nacos 客户端读 server-addr 时逐层问：
     问 ① 命令行 → 没有；问 ② 系统属性 → 没有；问 ③ 环境变量 → "有，nacos-svc:8848" ← 立即返回
     （④⑤ 根本轮不到问；jar 内 yml 的值从此与本进程无关）
③ 绑定落地：值注入 @ConfigurationProperties / @Value——应用代码对"值从哪层来"完全无感
```

落到本项目的时间线：

| 时刻 | 发生了什么 | 栈里发生什么 |
|------|-----------|-------------|
| Windows `mvn package` | yml 被复制进 jar（`BOOT-INF/classes/`） | ⑤ 层就位（`192.168.157.129:30048`） |
| `kubectl apply -f micro-lab.yaml` | Pod 重建，env 成为容器环境变量 | ③ 层就位（`nacos-svc:8848`） |
| 容器 `java -jar` | Spring Boot 组装栈、登记来源 | 栈成形 |
| Nacos 客户端读 `server-addr` | 逐层审查 | **③ 命中 → 连 `nacos-svc:8848`** |
| （对照）Windows 本地 `java -jar` | 没有 ③ 层 | **④⑤ 命中 → 连 IP 直连** |

> 一句话收拢：**同一份 jar、不同的"栈"，得到不同的连接目标**——这就是 12-Factor"配置与代码分离"的实现原理。

（可选实操）`/actuator/env` 端点能把栈摊开"看现场"：

```bash
# 临时暴露 env 端点（set env 会自动滚动更新；看完可把 env 从列表去掉）
kubectl set env deploy/order-service MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE="health,metrics,prometheus,env"

# 定向查单个属性的"审查现场"：列出每层来源及各自的值
kubectl exec deploy/order-service -- wget -qO- "localhost:8082/actuator/env/spring.cloud.nacos.discovery.server-addr"
```

> 📎 **本组补充的系统化扩展版**：见同目录《SpringBoot配置读取方式全解.md》——上面的"优先级栈 / 推导法 / 覆盖过程"在那册里展开为完整体系：**书写来源全谱（6 种）**、**读取方式全谱（Environment / @Value / @ConfigurationProperties 等 4 种，含本项目真实代码样本）**，以及同一个键在**本地开发 / K8s 部署 / Nacos** 三种场景的书写对照与"改值生效决策树"。

### 1.3 一次完整交付的链路（配置文件各就各位）

```text
pom.xml（定义依赖与版本，第 2 站）
   └─ mvn clean package -DskipTests
        └─ target/xxx-1.0.0-SNAPSHOT.jar（fat jar，内含 application.yml 的编译副本）
             └─ Dockerfile（COPY jar + 启动命令，第 8 站）
                  └─ docker build → 镜像 xxx:1.0.0
                       └─ docker save → xxx.tar → scp 到两台节点 → ctr images import
                            └─ kubectl apply -f micro-lab.yaml（Deployment 引用镜像）
                                 └─ 运行期：env 覆盖 yml → Nacos dataId 动态刷新
```

**看这条链记住两件事**：

1. 越靠左的文件改动，代价越大——改 yml / 代码 / Dockerfile 都要**重建镜像**；改 K8s 清单只需 `kubectl apply`；
2. "镜像"是构建类文件（Dockerfile）的产物，"配置"是应用类文件（application.yml）的内容——**两者是"出厂"与"出厂设置"的关系**。

---

## 二、应用世界：3 个 application.yml（工程内）

### 2.1 位置与"双副本"现象

```
micro-lab/
├── user-service/
│   ├── src/main/resources/application.yml   ← ① 源码版（你编辑、提交 Git 的）
│   └── target/classes/application.yml       ← ② 编译副本（mvn 编译时自动拷贝）
├── order-service/（同样两份）
└── gateway/（同样两份）
```

- **只有源码版需要改**，`target/` 下的副本由 Maven 自动生成；
- jar / Docker 镜像里打进的是 `target/classes` 的内容；
- 所以：改了 `src` 的 yml 后，**必须重新构建**（`mvn spring-boot:run` 会自动编译，`mvn package` 用于交付镜像），否则跑的还是旧配置。

### 2.2 user-service/application.yml（唯一接了配置中心的服务）

**路径**：`micro-lab/user-service/src/main/resources/application.yml`

```yaml
server:
  # 用户服务端口（order-service 用 8082，gateway 用 8080，避免冲突）
  port: 8081

spring:
  application:
    # 服务名：注册到 Nacos 后，其他服务靠这个名字找到它
    name: user-service

  # 配置中心（第 5 站）：启动时把 Nacos 上的 user-service.yaml 拉下来，合并进本地配置
  config:
    import:
      # optional: 前缀 = 该配置在 Nacos 上不存在时也照常启动（不加则启动直接失败）
      - optional:nacos:user-service.yaml

  cloud:
    nacos:
      discovery:
        # Nacos 服务器地址（K8s 里的 Nacos，经 node2 的 NodePort 暴露）
        # 注意：Nacos 2.x 客户端还会连接「本端口 + 1000」的 gRPC 端口（31048），
        #       所以 Nacos 的 Service 需同时映射 30048（HTTP）与 31048（gRPC）
        server-addr: 192.168.157.129:30048
      config:
        # 配置中心的 Nacos 地址（与注册中心是同一个 Nacos）
        server-addr: 192.168.157.129:30048
        # Data ID 扩展名：服务名 + .yaml → 读取 Nacos 里名为 user-service.yaml 的配置
        file-extension: yaml
```

**逐段讲解**：

| 配置段 | 含义 | 关键点 |
|--------|------|--------|
| `server.port: 8081` | 服务端口 | 三个服务端口互不冲突：8081 / 8082 / 9090 |
| `spring.application.name` | 服务名 | **注册到 Nacos 的名字**，也是 Feign / 网关 `lb://` 引用的名字 |
| `spring.config.import` | 配置中心接入（新方式） | 替代老版 `bootstrap.yml`；`optional:` 前缀表示 Nacos 上没有该配置也能启动 |
| `nacos.discovery.server-addr` | 注册中心地址 | 本机开发用 NodePort（`192.168.157.129:30048`）；K8s 内被环境变量覆盖为 `nacos-svc:8848` |
| `nacos.config.*` | 配置中心地址 | 与注册中心是同一个 Nacos；`file-extension: yaml` 决定 Data ID 后缀 |

> 注释里的历史课：第 3 站（服务注册）时该文件只有 `discovery` 一段；第 5 站（配置中心）补上了 `config` 段和 `spring.config.import`——最终版本是两次改动叠加的结果。

### 2.3 order-service/application.yml（唯一接了 Sentinel 的服务）

**路径**：`micro-lab/order-service/src/main/resources/application.yml`

```yaml
server:
  # 订单服务端口（user-service 用 8081，gateway 用 9090，避免冲突）
  port: 8082

spring:
  application:
    # 服务名：注册到 Nacos 后，其他服务靠这个名字找到它
    name: order-service
  cloud:
    nacos:
      discovery:
        # Nacos 服务器地址（K8s 里的 Nacos，经 node2 的 NodePort 暴露）
        # 注意：Nacos 2.x 客户端还会连接「本端口 + 1000」的 gRPC 端口（31048），
        #       所以 Nacos 的 Service 需同时映射 30048（HTTP）与 31048（gRPC）
        server-addr: 192.168.157.129:30048
    sentinel:
      # 应用启动即完成 Sentinel 初始化（不配的话是懒加载：第一次请求才初始化）
      eager: true
      transport:
        # Sentinel 客户端与本地通信的端口
        port: 8719
        # 若之后部署了 Sentinel 控制台，把地址填在这里（当前教学未部署，留作可选）
        # dashboard: 192.168.157.129:30050

# 开启 Feign 与 Sentinel 的整合：只有开启它，@FeignClient 上配置的 fallback 才会生效
feign:
  sentinel:
    enabled: true
```

**逐段讲解**：

| 配置段 | 含义 | 关键点 |
|--------|------|--------|
| `spring.cloud.nacos.discovery` | 注册中心 | 只有 discovery，**没有 config 段**——order-service 没接配置中心 |
| `sentinel.eager: true` | 启动即初始化 Sentinel | 默认懒加载（首次请求才初始化），演示时容易"看不到效果" |
| `sentinel.transport.port: 8719` | 客户端与控制台的通信端口 | 控制台通过它"推"规则、拉指标 |
| `feign.sentinel.enabled: true` | **Feign + Sentinel 整合开关** | ⚠️ 不开它，`@FeignClient(fallback = ...)` 的兜底**不生效**（复盘速查 #8） |

> ⚠️ **注释小历史**：文件里注释写的是 `# dashboard: 192.168.157.129:30050`，而实际部署 Sentinel 控制台后用的是 **30058**，并且是通过 `kubectl set env` 注入的（见第七章）。**30050 是早期占位值，以文档 §8.2 与集群实际为准**。

### 2.4 gateway/application.yml（唯一有路由配置的服务）

**路径**：`micro-lab/gateway/src/main/resources/application.yml`

```yaml
server:
  # 网关端口（本机 8080 已被其他进程占用，改用 9090；user=8081、order=8082 不直接对外）
  port: 9090

spring:
  application:
    name: gateway

  cloud:
    nacos:
      discovery:
        # Nacos 地址：网关靠它把"服务名"解析成实际实例地址
        server-addr: 192.168.157.129:30048

    gateway:
      # 关闭"按服务名自动路由"（默认就关）：自动路由不必写规则，
      # 路径会变成 /服务名/**，教学上先用显式路由把规则写法讲透。
      discovery:
        locator:
          enabled: false

      # ============ 路由规则：网关的核心配置 ============
      routes:
        # 路由 1：/api/users/** → 用户服务
        - id: user-service-route
          # lb = load balance：从注册中心（Nacos）按服务名找实例并负载均衡
          uri: lb://user-service
          predicates:
            # 匹配条件：请求路径以 /api/users/ 开头
            - Path=/api/users/**
          filters:
            # 转发前去掉路径的第 1 段（/api）：
            # 客户端请求 /api/users/1 → 实际转发给 user-service 的是 /users/1
            # （不加会转发 /api/users/1，而服务端只有 /users/1，导致 404）
            - StripPrefix=1

        # 路由 2：/api/orders/** → 订单服务
        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
```

**逐段讲解**：

| 配置段 | 含义 | 关键点 |
|--------|------|--------|
| `server.port: 9090` | 网关端口 | 原定 8080，被本机其他进程占用后改 9090（复盘 §7.5 踩坑） |
| `gateway.discovery.locator.enabled: false` | 关闭自动路由 | 显式路由教学更清晰（自动路由路径会变成 `/服务名/**`） |
| `routes[].uri: lb://服务名` | **核心**：按服务名路由 | `lb` = 从 Nacos 拿实例列表 + 负载均衡；写成 IP 就失去意义 |
| `predicates: Path=/api/**` | 匹配条件（断言） | 决定"什么样的请求走这条路由" |
| `filters: StripPrefix=1` | 转发前去掉路径第 1 段 | 不加会 404——`/api/users/1` 要变成 `/users/1` 才对得上服务端接口 |

### 2.5 三份 yml 横向对照（背下来）

| 配置项 | user-service (8081) | order-service (8082) | gateway (9090) |
|--------|:---:|:---:|:---:|
| `spring.application.name` | user-service | order-service | gateway |
| Nacos `discovery` | ✅ | ✅ | ✅ |
| Nacos `config` + `config.import` | ✅ **唯一** | ❌ | ❌ |
| Sentinel（`eager` / `transport`） | ❌ | ✅ **唯一** | ❌ |
| `feign.sentinel.enabled` | ❌ | ✅ | ❌ |
| `gateway.routes` | ❌ | ❌ | ✅ **唯一** |
| actuator / prometheus 配置 | ❌ | ❌ | ❌（K8s 环节用 env 注入） |

**规律**：每个服务只在自己"角色相关"的配置上动手——这个"按角色分文件"的设计，正是微服务配置管理的直观体现。

---

## 三、构建世界：Dockerfile 与 pom.xml（工程内）

> 提示：这两类文件**不是"运行时读"的配置**，而是"把程序造出来、装进盒子"的配置。之所以必须搞懂它们，是因为它们决定了"为什么改一行 yml 要等十分钟才生效"。

### 3.1 工程内的构建类文件分布

```
micro-lab/
├── pom.xml                     ← 父 pom（版本治理：三个 BOM）
├── user-service/
│   ├── pom.xml                 ← 模块 pom（声明本服务需要哪些依赖）
│   ├── Dockerfile              ← 镜像构建配方
│   └── src/main/resources/application.yml   ← 应用配置（第二章）
├── order-service/（pom.xml + Dockerfile + application.yml）
└── gateway/（pom.xml + Dockerfile + application.yml）
```

### 3.2 三个 Dockerfile

**路径**：`micro-lab/<服务>/Dockerfile`
三份除"注释标题 + COPY 的 jar 名"外**完全一致**，以 user-service 为例（原文，注释保留）：

```dockerfile
# ============================================================
# user-service 镜像构建文件（单阶段简化版）
# 构建前提：已在 micro-lab 根目录执行 mvn clean package -DskipTests（产出 jar）
# 构建命令（在 micro-lab 根目录）：
#   docker build -t user-service:1.0.0 user-service
# ============================================================

# 基础镜像：JRE 21 精简版。直写 daocloud 加速源域名，绕开拉取被墙的问题
FROM docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine

# 时区：让容器内日志时间与本地一致
ENV TZ=Asia/Shanghai

WORKDIR /app

# 拷贝本地已构建的 jar（本地构建 + 镜像只装运，比容器内编译快得多）
COPY target/user-service-1.0.0-SNAPSHOT.jar app.jar

# 启动命令
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**逐行讲解**：

| 行 | 内容 | 含义 / 注意点 |
|----|------|--------------|
| `# 注释块` | 构建前提与命令 | **必须先在 micro-lab 根目录 `mvn clean package -DskipTests`**，否则 COPY 的文件不存在或还是旧 jar |
| `FROM` | `docker.m.daocloud.io/library/eclipse-temurin:21-jre-alpine` | ① 直写 daocloud 加速源域名（绕开 docker.io 被墙）；② **JRE 不是 JDK**——运行只需要运行时，镜像更小；③ alpine 精简版 |
| `ENV TZ` | `Asia/Shanghai` | 容器默认 UTC，不设的话日志时间比本地差 8 小时 |
| `WORKDIR` | `/app` | 容器内工作目录，jar 最终落在 `/app/app.jar` |
| `COPY` | `target/xxx-SNAPSHOT.jar app.jar` | 从**构建上下文**取文件；改名为 `app.jar` 让 ENTRYPOINT 固定不变 |
| `ENTRYPOINT` | `java -jar /app/app.jar` | 前台运行（作为 PID 1），容器靠它活着；**内存参数不写死在这里**——K8s 用 `JAVA_TOOL_OPTIONS` 注入（见第四章） |

**三份文件的差异**（只有这一处 + 顶部注释）：

| 服务 | COPY 的 jar | 构建命令（在根目录执行） |
|------|------------|------------------------|
| user-service | `target/user-service-1.0.0-SNAPSHOT.jar` | `docker build -t user-service:1.0.0 user-service` |
| order-service | `target/order-service-1.0.0-SNAPSHOT.jar` | `docker build -t order-service:1.0.0 order-service` |
| gateway | `target/gateway-1.0.0-SNAPSHOT.jar` | `docker build -t gateway:1.0.0 gateway` |

### 3.3 Dockerfile 的三个关键认知

1. **单阶段 vs 多阶段**（复盘速查 #5）：本项目是**单阶段**——本地先 `mvn package`，镜像只负责"装运"jar（快）；多阶段则是容器内编译（更独立但慢）。单阶段的坑：**忘了本地 package，就会把旧 jar 打进镜像**。
2. **镜像里到底装了什么**：JRE + fat jar。而 jar 里已经含 `application.yml` 的编译副本（`target/classes`）——所以**改 yml = 改镜像内容**，必须重建镜像才能生效。
3. **构建上下文**：`docker build -t xxx <服务目录>` 的上下文是**服务目录本身**，所以 `COPY target/xxx.jar` 是相对服务目录的路径（先去根目录 mvn 产出 target）。上下文写错会报 `COPY failed: no source files were specified`。

### 3.4 四个 pom.xml（依赖与站点对照）

**父 pom**（`micro-lab/pom.xml`）是版本治理核心：`packaging: pom`（只管理不产出代码）+ 三个版本属性 + 三个 BOM `import`（子模块写依赖永远不带版本号）。全文见复盘 §3.3，此处只记结论：

| 属性 | 值 |
|------|-----|
| `java.version` | 21 |
| `spring-boot.version` | 3.2.4 |
| `spring-cloud.version` | 2023.0.1 |
| `spring-cloud-alibaba.version` | 2023.0.1.0 |

**三个模块 pom 的依赖对照表**（每行对应一站的知识点）：

| 依赖 | user-service | order-service | gateway | 对应站点 / 备注 |
|------|:---:|:---:|:---:|------|
| `spring-boot-starter-web` | ✅ | ✅ | ❌ **禁忌** | 第 2 站；Gateway 是 WebFlux，引入会报 `Spring MVC found on classpath, which is incompatible with Spring Cloud Gateway` |
| `spring-cloud-starter-alibaba-nacos-discovery` | ✅ | ✅ | ✅ | 第 3 站（注册发现） |
| `spring-cloud-starter-alibaba-nacos-config` | ✅ | ❌ | ❌ | 第 5 站（配置中心，仅 user-service 接入） |
| `spring-cloud-starter-openfeign` | ❌ | ✅ | ❌ | 第 4 站（声明式调用，仅调用方需要） |
| `spring-cloud-starter-loadbalancer` | ❌ | ✅ | ✅ | 第 4/6 站；Feign 缺它会报 loadBalancing 相关错误；gateway 的 `lb://` 也依赖它 |
| `spring-cloud-starter-alibaba-sentinel` | ❌ | ✅ | ❌ | 第 7 站（熔断限流，装在调用方） |
| `spring-boot-starter-actuator` | ✅ | ✅ | ✅ | 探针篇（`/actuator/health` 及 liveness/readiness 子端点） |
| `io.micrometer:micrometer-registry-prometheus` | ✅ | ✅ | ✅ | 监控篇（`/actuator/prometheus` 文本格式） |
| `spring-cloud-starter-gateway` | ❌ | ❌ | ✅ | 第 6 站（网关本体） |

> 三模块 pom 的 `<build>` 都配了 `spring-boot-maven-plugin`——这是 fat jar 能生成、能 `java -jar` 直接跑的前提（复盘速查 #15：自建父 POM 必须显式配 repackage，否则 `no main manifest`）。

### 3.5 完整操作链：改配置后的正确姿势（构建视角）

```powershell
# ① 在 micro-lab 根目录重建 jar
mvn clean package -DskipTests

# ② 重建镜像（改的是哪个服务就 build 哪个）
docker build -t user-service:1.0.0 user-service

# ③ 导出 + 分发（两台节点都要，镜像 store 是节点级的）
docker save user-service:1.0.0 -o user-service-1.0.0.tar
scp user-service-1.0.0.tar lcc@192.168.157.128:/tmp/
scp user-service-1.0.0.tar lcc@192.168.157.129:/tmp/

# ④ 两台节点分别导入
sudo k3s ctr -n k8s.io images import /tmp/user-service-1.0.0.tar

# ⑤ 重启 Pod 让新镜像生效（rollout restart 不重建镜像，只重建 Pod！）
kubectl rollout restart deployment/user-service
```

**两个反复踩过的坑**（详见复盘十章 #19 / #23）：同名旧 tar 冒充新镜像（靠 `dir` 时间戳识破）；镜像只导入了一台节点（靠 `ctr images ls` 的 digest 逐台核对）。

---

## 四、集群世界：K8s 清单（虚拟机节点上）

> ⚠️ 重要：以下文件**不在 Windows 工程里**，全部存放于虚拟机节点（node1/node2）的 `~/k8s-lab/`、`~/monitor-lab/`、`~/sentinel-lab/` 目录下，用 `kubectl apply -f` 提交给集群。
> 判断依据：文件以 `apiVersion:` / `kind:` 开头——这是 K8s 资源清单的标志（对比 application.yml 以 `server:` / `spring:` 开头）。

### 3.1 nacos.yaml —— Nacos 部署清单（第 1 站）

**路径**：节点 `~/k8s-lab/nacos.yaml`
**对象**：Deployment（怎么跑） + Service（怎么访问）

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nacos
  labels:
    app: nacos
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nacos
  template:
    metadata:
      labels:
        app: nacos
    spec:
      containers:
        - name: nacos
          image: nacos/nacos-server:v2.3.2
          env:
            - name: MODE
              value: "standalone"        # 单机模式（学习环境）
            - name: JVM_XMS
              value: "256m"              # 限制 JVM 内存
            - name: JVM_XMX
              value: "512m"
          ports:
            - containerPort: 8848        # 控制台 + HTTP API
            - containerPort: 9848        # Nacos 2.x 的 gRPC 端口（=8848+1000，客户端通信必需）
---
apiVersion: v1
kind: Service
metadata:
  name: nacos-svc
spec:
  type: NodePort
  selector:
    app: nacos
  ports:
    - name: http
      port: 8848
      targetPort: 8848
      nodePort: 30048
    - name: grpc
      port: 9848
      targetPort: 9848
      nodePort: 31048               # ★ 必须是 30048+1000，否则客户端 gRPC 连不上
```

**逐块讲解**：

1. **它本质是"一条 docker run 的 K8s 翻译版"**：
   `docker run -d --name nacos -e MODE=standalone -e JVM_XMS=256m -e JVM_XMX=512m -p 30048:8848 -p 31048:9848 nacos/nacos-server:v2.3.2`
2. **`app: nacos` 出现三遍**：Deployment 自身标签 / `selector.matchLabels`（认领规则）/ `template.metadata.labels`（出厂标签）——认领规则与出厂标签必须一致，Deployment 靠标签认领 Pod，从不记 Pod 名字；
3. **三层端口**（最容易混）：

   | 层 | 值 | 使用者 |
   |---|---|---|
   | `nodePort: 30048` | `<节点IP>:30048` | **集群外**：Windows 浏览器 / 本地开发服务 |
   | `port: 8848` | `nacos-svc:8848` | **集群内**微服务（yml 里填的地址） |
   | `targetPort: 8848` | Pod 的 8848 | Service 转发目标 |

4. **gRPC 端口 +1000 铁律**（核心坑）：Nacos 2.x 客户端是"两条腿"通信（HTTP 8848 + gRPC 9848）。客户端算 gRPC 端口的规则是「配置端口 + 1000」——配置 `30048` 就会去连 `31048`。**最初误配成 30049，导致 Windows 本地服务注册时 gRPC 超时**，最终修正为 31048；
5. `MODE=standalone` 是 Nacos 应用层的"单机模式"，与 K8s 无关；`cluster` 模式有硬性要求：3 节点起 + 外置 MySQL。代价：数据存 Pod 内嵌存储，**Pod 一重建数据全丢**（待办：换 StatefulSet + PVC）。

### 3.2 micro-lab.yaml —— 微服务部署清单（第 8 站，改了 6 版）

**路径**：节点 `~/k8s-lab/micro-lab.yaml`
**对象**：3 个 Deployment（user / order / gateway） + 3 个 Service

#### v1（2026-09-17 首版）：基础部署，无探针

```yaml
cat > ~/k8s-lab/micro-lab.yaml <<'EOF'
# micro-lab 部署清单：3 个 Deployment + 3 个 Service

# ---------------- user-service ----------------
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
  labels:
    app: user-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: user-service:1.0.0
          env:
            # 环境变量覆盖 jar 内 application.yml 的 Nacos 地址（配置外置）
            - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
              value: "nacos-svc:8848"
            - name: SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR
              value: "nacos-svc:8848"
            # 限制 JVM 内存
            - name: JAVA_TOOL_OPTIONS
              value: "-Xms128m -Xmx256m"
          ports:
            - containerPort: 8081
---
apiVersion: v1
kind: Service
metadata:
  name: user-service-svc
spec:
  type: ClusterIP          # 内部服务：不对外
  selector:
    app: user-service
  ports:
    - port: 8081
      targetPort: 8081
---
# ---------------- order-service ----------------
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  labels:
    app: order-service
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: order-service:1.0.0
          env:
            - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
              value: "nacos-svc:8848"
            - name: JAVA_TOOL_OPTIONS
              value: "-Xms128m -Xmx256m"
          ports:
            - containerPort: 8082
---
apiVersion: v1
kind: Service
metadata:
  name: order-service-svc
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
    - port: 8082
      targetPort: 8082
---
# ---------------- gateway ----------------
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway
  labels:
    app: gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: gateway
  template:
    metadata:
      labels:
        app: gateway
    spec:
      containers:
        - name: gateway
          image: gateway:1.0.0
          env:
            - name: SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR
              value: "nacos-svc:8848"
            - name: JAVA_TOOL_OPTIONS
              value: "-Xms128m -Xmx256m"
          ports:
            - containerPort: 9090
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-svc
spec:
  type: NodePort            # 唯一对外入口
  selector:
    app: gateway
  ports:
    - port: 9090
      targetPort: 9090
      nodePort: 30090
EOF
```

**v1 讲解要点**：

| 点 | 说明 |
|----|------|
| 环境变量覆盖 yml | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` 来自 **relaxed binding**：`spring.cloud.nacos.discovery.server-addr` 中的 `.` 和 `-` 全换 `_`、全大写。环境变量优先级高于 jar 内 yml，实现"同一镜像、不同环境连不同 Nacos"（2026-10-03 注：micro-lab 三服务与 backend 现已统一为占位符式 `NACOS_ADDR`；relaxed binding 作为机制认知保留） |
| 只有网关对外 | user/order 是 `ClusterIP`（集群内部），gateway 是 `NodePort`（唯一入口，30090）——安全原则"只暴露网关" |
| JAVA_TOOL_OPTIONS | 限制 JVM 堆内存，防止小集群被 Java 吃满 |
| nodePort 30090 | 就是"30090 端口"的唯一定义处；不写则 K8s 从 30000~32767 随机分配，重建会变号 |

#### v2（2026-09-19）：加 tcpSocket 探针

```yaml
          readinessProbe:              # 就绪探针：端口能连上才进流量名单（失败不重启，只摘流量）
            tcpSocket:
              port: 8081
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 6
          livenessProbe:               # 存活探针：连续失败才重启
            tcpSocket:
              port: 8081
            initialDelaySeconds: 40    # ★ 容忍时间必须 > 应用最长启动时间
            periodSeconds: 10
            failureThreshold: 5
```

**为什么要加**：Pod `1/1 Running` ≠ 应用就绪。无探针时 K8s 只判"容器进程存活"，滚动更新时新 Pod 在"端口还没监听"的窗口期就被加进流量名单 → `connection refused`。

**参数设计黄金法则**：

| 探针 | 容忍时间设计 | 要点 |
|------|-------------|------|
| readiness | ~35s 没就绪则持续重试 | 失败**不重启**（安全），宁可宽松 |
| liveness | 40s 后开始查，连挂 5 次（~90s）才重启 | ⚠️ **必须大于应用最长启动时间**，否则"没起完就被杀 → CrashLoopBackOff" |

#### v3（2026-09-19）：升级 httpGet + actuator

```yaml
env:
  - name: MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED   # relaxed binding：management.endpoint.health.probes.enabled
    value: "true"                                     # 打开 /actuator/health/{liveness,readiness} 子端点
# ...容器片段：
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8081
  initialDelaySeconds: 5
  periodSeconds: 5
  failureThreshold: 6
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8081
  initialDelaySeconds: 40
  periodSeconds: 10
  failureThreshold: 5
```

**tcpSocket vs httpGet 的本质区别**：tcpSocket 只知道"端口通不通"（网络层）；httpGet 走 actuator 端点，能读到应用内部的**状态语义**（HTTP 200=就绪、503=未就绪），还能聚合依赖健康（DB、注册中心等）。

**三个端点分工**：

| 端点 | 含义 | 适合用途 |
|------|------|---------|
| `/actuator/health` | 聚合健康（diskSpace / ping / livenessState / readinessState…） | 人工排查 / 监控 |
| `/actuator/health/liveness` | 应用自身是否还能活（**不含外部依赖**） | livenessProbe |
| `/actuator/health/readiness` | 应用是否可接流量 | readinessProbe |

> 需先在三个模块 pom 加 `spring-boot-starter-actuator` 依赖并重建镜像。

#### v4（2026-09-19）：零中断"完全体"

v4 新增/调整（按复盘 §9.10 描述整理，等价写法）：

```yaml
spec:
  template:
    spec:
      terminationGracePeriodSeconds: 30     # 终止总预算 = preStop(5s) + 优雅停机窗口
      containers:
        - name: user-service
          env:
            - name: SERVER_SHUTDOWN
              value: "graceful"             # 出口侧保险②：把手头的活干完再退
          lifecycle:
            preStop:                        # 出口侧保险①："延迟死刑"，等摘牌传播完
              exec:
                command: ["sh", "-c", "sleep 5"]
          startupProbe:                     # 启动期保护：通过前压制 liveness/readiness
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            periodSeconds: 5
            failureThreshold: 30            # 5s × 30 = 容忍 155s 启动
          livenessProbe:                    # 启动期交给 startup 扛后，liveness 解放为"敏感模式"
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            periodSeconds: 5
            failureThreshold: 3             # 假死约 15s 被发现
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 5
            periodSeconds: 5
            failureThreshold: 6
```

**为什么需要出口侧保护**：老 Pod 退场时，`Terminating → 从流量名单摘牌`的传播有延迟（毫秒~数秒），期间新流量还会打到它；`preStop: sleep 5` 让容器多活 5 秒继续应酬"迟到的客人"，`SERVER_SHUTDOWN=graceful` 保证在途请求处理完再退。

**"零中断发布"完整拼图**：

| 环节 | 机制 | 版本 |
|------|------|------|
| 入口侧 | readinessProbe（新 Pod 就绪才切流量） | v2 / v3 |
| 出口侧 | preStop + 优雅停机（在途请求善后） | v4 |
| 启动期 | startupProbe（慢启动保护） | v4 |
| 节奏器 | 滚动策略（maxSurge / maxUnavailable，默认够用） | — |
| 保险绳 | 多副本（replicas ≥ 2 抗单点，可选待做） | — |

#### 版本演化总表

| 版本 | 日期 | 改了什么 |
|------|------|---------|
| v1 | 09-17 | 首版：3 Deployment + 3 Service，env 覆盖 Nacos 地址，gateway 唯一 NodePort 30090 |
| v2 | 09-19 | 加 tcpSocket readiness + liveness |
| v3 | 09-19 | 探针改 httpGet + actuator 端点 + `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` |
| v4 | 09-19 | + startupProbe、liveness 敏感模式、+ preStop、+ `SERVER_SHUTDOWN=graceful`、+ `terminationGracePeriodSeconds: 30` |
| **v5（✅ 2026-10-01）** | 10-01 | 4 笔 `kubectl set env` 漂移写回文件固化（见第七章）——apply 后**零滚动**归位；order-service dashboard 旧值（30058）核账时更新为 `sentinel-dashboard-svc:8858` |
| **v6（✅ 2026-10-03）** | 10-03 | **env 双风格统一（清账日 2.0）**：user/order 的 `server-addr` 占位符化（`${NACOS_ADDR:…}`）——清单环境变量由 `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` ×2 + `SPRING_CLOUD_NACOS_CONFIG_SERVER_ADDR` ×1 **归并为单个 `NACOS_ADDR`**；镜像 user/order → **1.1.0**（详见《微服务实战复盘.md》7.3 演进记录 2） |

### 3.3 prometheus.yaml —— 监控抓取配置（含参考骨架）

**路径**：节点 `~/monitor-lab/prometheus.yaml`
**构成**（按复盘 §9.13-B 描述）：ConfigMap（`prometheus.yml`：15s 抓取 + `metrics_path: /actuator/prometheus` + targets 三个 Service DNS） + Deployment（`prom/prometheus:v2.54.1`，`--web.enable-lifecycle`） + Service（NodePort **30091**）。

> ⚠️ 以下为**按文档描述整理的参考骨架**（复盘未展开原文），字段名以节点实际文件为准：

```yaml
# 参考骨架（非原文）
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
    scrape_configs:
      - job_name: micro-lab
        metrics_path: /actuator/prometheus
        static_configs:
          # 教学点：targets 写 Service DNS 而非 Pod IP——Pod 重建不影响抓取
          - targets:
              - user-service-svc:8081
              - order-service-svc:8082
              - gateway-svc:9090
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
spec:
  # ... image: prom/prometheus:v2.54.1，启动参数含 --web.enable-lifecycle
---
apiVersion: v1
kind: Service
metadata:
  name: prometheus-svc
spec:
  type: NodePort
  ports:
    - port: 9090
      targetPort: 9090
      nodePort: 30091
```

**关键认知**：
- `targets` 写 **Service DNS**（`user-service-svc:8081`），不写 Pod IP——Pod 重建换 IP 也不影响抓取；
- `metrics_path: /actuator/prometheus` 是 micrometer-registry-prometheus 提供的机器可读格式（对比 `/actuator/metrics` 的 JSON 是人看格式）。

### 3.4 grafana.yaml —— 可视化（含参考骨架）

**路径**：节点 `~/monitor-lab/grafana.yaml`
**构成**：Deployment（`grafana/grafana:10.4.5`，admin/admin） + Service（NodePort **30092**）。

> ⚠️ 参考骨架（非原文）：

```yaml
# 参考骨架（非原文）
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
spec:
  # ... image: grafana/grafana:10.4.5，初始密码 admin/admin
---
apiVersion: v1
kind: Service
metadata:
  name: grafana-svc
spec:
  type: NodePort
  ports:
    - port: 3000
      targetPort: 3000
      nodePort: 30092
```

**使用三步**：① 登录（30092）→ ② 数据源 Prometheus URL = `http://prometheus-svc:9090`（集群内 Service DNS）→ ③ Import 大盘 ID **4701**（JVM Micrometer）。

### 3.5 sentinel-lab —— Sentinel 控制台（第 9.11 节）

**路径**：节点 `~/sentinel-lab/`
**部署形态**：自建 Dockerfile（基础镜像 `eclipse-temurin:8-jre-alpine`，因公共源镜像被拒、maven 直链下载到"假 jar"，改用 GitHub Release 下载），构建镜像后在 K8s 部署，**Service 端口 8858**，NodePort 暴露 → 访问 `http://<任意节点IP>:30058`。

**接入方式**（不是 yml，是命令注入）：

```bash
kubectl set env deployment/order-service SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD=192.168.157.129:30058
#   ※ 该值后续又升级为 sentinel-dashboard-svc:8858（集群内 Service 名）
```

> 这也是一笔"漂移账"——**✅ 2026-10-01 v5 已固化**（写回 micro-lab.yaml，现值 `sentinel-dashboard-svc:8858`）。

---

## 五、系统世界：K3s 的 registries.yaml

**路径**：节点 `/etc/rancher/k3s/registries.yaml`
**作用**：配置镜像加速源（国内网络直连 Docker Hub 会被拒/DNS 污染）。

> ⚠️ 参考骨架（非原文，字段以节点实际为准）：

```yaml
# 参考骨架（非原文）
mirrors:
  docker.io:
    endpoint:
      - https://docker.m.daocloud.io
```

**真相揭示（2026-10-01 清账日收官）**：本条曾名为"最大的坑"——旧记录称"配置对但没重启，所以 `ctr pull` 仍直连被拒"（复盘 §9.13-E）。**收官日破案**，真相分三层：

1. **配置链路一直是通的**——k3s 每次启动会把 `registries.yaml` 渲染成新版 containerd 的"改道牌"：`/var/lib/rancher/k3s/agent/etc/containerd/certs.d/docker.io/hosts.toml`（`config.toml` 的 `config_path` 指向该目录）。**改配置后重启 k3s 仍然是必要动作**（为了重新渲染），但……
2. **`ctr` 命令不走 CRI 的 mirror 配置**——它是底层直连客户端，**任何时候都直连 registry**（重启与否都一样）——"重启就能让 ctr 走 mirror"的推论**是误判**；真因是**测试工具与消费路径不匹配**；
3. **正确验证姿势（金标准）**：让 **kubelet（走 CRI）拉"裸名"镜像**：

```bash
kubectl run t --image=docker.io/library/hello-world:latest --restart=Never
```

成功即 mirror 生效；想用 ctr 验证则要显式"导航"：`sudo k3s ctr images pull --hosts-dir /var/lib/rancher/k3s/agent/etc/containerd/certs.d <镜像>`。

**2026-10-01 双节点实测**：裸名 `docker.io/library/hello-world` **577ms 拉取成功**（image size 15077 bytes；同节点 `ctr` 直连仍被拒——完美对照组）——镜像加速线正式收官 ✓

**临时替代方案**（对 ctr 等不走 mirror 的路径依然有效）：点名加速源全名 + retag 回标准名：

```bash
sudo k3s ctr -n k8s.io images pull docker.m.daocloud.io/prom/prometheus:v2.54.1
sudo k3s ctr -n k8s.io images tag docker.m.daocloud.io/prom/prometheus:v2.54.1 docker.io/prom/prometheus:v2.54.1
```

---

## 六、配置中心世界：Nacos Data ID

### 5.1 它长什么样

| 要素 | 值 |
|------|-----|
| Data ID | `user-service.yaml` |
| Group | `DEFAULT_GROUP` |
| Namespace | `public`（默认） |
| 格式 | YAML |

**内容**：

```yaml
microlab:
  welcome-message: "欢迎回来！我是 10-01 重建的欢迎语——杀 Pod 也带不走我（下一幕见）"
```

> 📌 该数据命运备注：**曾因 Nacos 无持久化而丢失**（重启 5 次后蒸发，接口退回代码兜底值）；2026-10-01 升级 PVC 持久化后**重建**——"杀 Pod 存活"验证通过（详见《虚拟机DockerK8s实战复盘.md》状态清单 Nacos 行）。

> ⚠️ 它**不是磁盘文件**，只存在于 Nacos 控制台（配置管理 → 配置列表）。工程里找不到它是正常的。

### 5.2 三个世界如何协作（读配置的完整链路）

```
① 本地 application.yml 声明"我要去 Nacos 拉 user-service.yaml"
        （spring.config.import: optional:nacos:user-service.yaml）
② 启动时 Nacos 客户端按 Data ID（服务名 + .yaml）拉取远端配置，合并进本地
③ 运行时 Nacos 客户端长轮询监听变更 → 发布 RefreshEvent → @RefreshScope 的 Bean 重建 → 新值生效
```

### 5.3 动态刷新实验（第 5 站高潮）

```bash
# ① Nacos 控制台创建配置（Data ID: user-service.yaml）
# ② 重启 user-service 后：
curl.exe http://localhost:8081/users/config/welcome
# → 返回 Nacos 配置的内容
# ③ 控制台把文案改掉 → 发布 → 不重启服务 → 再次 curl
# → 立即返回新文案！
```

**关键**：读取配置的 Bean 必须加 `@RefreshScope`，否则 `@Value` 不会更新（"改了配置没反应"的常见根因）。

---

## 七、漂移账：kubectl set env 注入的配置

以下配置**曾存在于运行中的集群里、但没写进 micro-lab.yaml**（"漂移"形态）——**✅ 2026-10-01 已全部写回文件固化（见 3.2 版本演化表 v5）**。留档学习：它们也是"配置"的一部分，只是当时的形态是集群里的环境变量。

| 注入项 | 目标服务 | 用途 | 来源 |
|--------|---------|------|------|
| `MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=always` | user-service | 打开 `/actuator/health` 明细（components 全公开） | §9.9 实验一 |
| `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics,prometheus` | 三服务 | 暴露 Prometheus 抓取端点 | §9.13-A |
| `MANAGEMENT_METRICS_TAGS_APPLICATION=<服务名>` | 三服务 | 所有指标统一带 `application=xxx` 标签（Grafana 按应用过滤的前提） | §9.13-A |
| `SPRING_CLOUD_SENTINEL_TRANSPORT_DASHBOARD`（**核账发现：实际值已升级为 `sentinel-dashboard-svc:8858`**，旧值 30058 过时） | order-service | 接入 Sentinel 控制台 | §9.11-B |

**为什么这是"账"**：`set env` 是命令式操作，与声明式的 yml 文件产生漂移——**下次 `kubectl apply -f micro-lab.yaml` 不会带这些变量**（apply 只调 yml 里声明过的字段），重建后配置就丢了。所以生产上要"写回文件"（声明式）或用 ConfigMap 管理。**（本账目 2026-10-01 已结清：4 笔写回 micro-lab.yaml v5，apply 后零滚动归位——"对账"还当场发现 dashboard 值已演进而文档滞后。）**

---

## 八、速查：分辨口诀 / 生效路径决策树 / FAQ

### 7.1 一眼分辨（看首行/首段）

| 开头 | 是什么 | 例子 |
|------|--------|------|
| `server:` / `spring:` | **Spring Boot 应用配置** | application.yml |
| `apiVersion:` / `kind:` | **K8s 清单** | nacos.yaml、micro-lab.yaml、prometheus.yaml |
| `mirrors:` / `endpoints:` 且在 `/etc/rancher/k3s/` | **K3s 系统配置** | registries.yaml |
| 不在文件系统、在 Nacos 网页上 | **配置中心 Data ID** | user-service.yaml |

### 7.2 改配置后怎么生效（决策树）

```
你想改一个配置，先问：它在哪一层？
├─ application.yml（jar 内）
│    → 本地开发：mvn spring-boot:run 自动编译生效
│    → 跑在 K8s：必须重打包 → 重建镜像 → 重新部署（rollout restart 不够！）
├─ micro-lab.yaml 等 K8s 清单
│    → kubectl apply -f → 立即滚动更新
├─ Nacos Data ID
│    → 控制台发布 → 热更新（读配置的 Bean 需 @RefreshScope）
└─ registries.yaml
     → 重启 k3s 才生效
```

### 7.3 高频 FAQ

**Q1：为什么工程里只有 3 个 application.yml，没有 k8s 的 yaml？**
因为 K8s 清单属于"集群世界"，存放在虚拟机节点的 `~/k8s-lab/` 等目录，用 `kubectl apply` 提交；工程仓库只装"应用世界"的配置。

**Q2：order-service yml 里写着 `30050`，文档为什么写 `30058`？**
`30050` 是早期占位注释；实际 Sentinel 控制台部署在了 **30058**（NodePort），并通过 `kubectl set env` 注入。**以集群实际与文档 §8.2 为准**。

**Q3：src 和 target 下两份 application.yml，改哪份？**
只改 `src/main/resources/` 下的。`target/classes/` 是编译副本，打包/镜像用的是它——所以改完必须重新构建才会进 jar。

**Q4：环境变量凭什么能覆盖 yml？**
Spring Boot 的 relaxed binding（`spring.cloud.nacos.discovery.server-addr` → `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR`）+ 环境变量优先级天然高于配置文件。这是"同一镜像、不同环境"的实现基础。

**Q5：为什么 nacos.yaml 里 30048 和 31048 必须成对？**
Nacos 2.x 客户端按「配置端口 + 1000」计算 gRPC 端口。配 30048 就会连 31048——Service 少映射任何一个，注册都会失败（gRPC 超时）。

**Q6：`rollout restart` 改完代码为什么不生效？**
它只重建 Pod，**不重建镜像**；镜像不变，jar 不变，代码永远不生效。必须走完整链路：package → build → 分发 → import → 部署。

**Q7：镜像里到底装了什么？为什么改 yml 也要重建镜像？**
镜像 = JRE + fat jar；而 jar 里已经含 `application.yml` 的编译副本（`target/classes`）——**改 yml 等于改镜像内容**，所以必须重新 package → build → 分发 → 部署。这也是它和 K8s 清单（apply 即生效）最大的区别。

**Q8：Dockerfile 改了要重建镜像吗？**
要。Dockerfile 只在 `docker build` 时被读取，改完必须重新 build，且新镜像要**分发到每台节点**。另外本项目是**单阶段**写法：镜像只装运本地构建好的 jar——所以顺序永远是"先 `mvn package`，再 `docker build`"，反过来就会把旧 jar 打进镜像。

### 8.4 端口管理速查：谁定义、去哪查、怎么统一

**先记结论**：Pod IP 每次重建都会变；**端口不会变**（前提是写死）。唯一例外：Service 不写 `nodePort` 时，K8s 会从 30000~32767 随机分配——所以本项目的做法是**永远显式写死**。

**端口的五层模型（每层有各自的"权威定义处"）**：

```text
① 应用监听端口 ── application.yml 的 server.port（代码层，打进镜像）
② 容器端口    ── Deployment 的 containerPort（文档性声明，相当于注释）
③ Service 端口 ── port / targetPort（集群内访问：xxx-svc:port）
④ NodePort    ── nodePort（集群外访问：节点IP:3xxxx，写死才不变）
⑤ 节点/网络层  ── 防火墙、安全组（放行 NodePort）
```

**micro-lab 端口台账**（单一事实来源，改端口先改这里）：

| 组件 | ① 应用/容器端口 | ③ 集群内访问 | ④ 集群外访问 | 权威定义处（改哪里） |
|------|:---:|:---:|:---:|------|
| user-service | 8081 | `user-service-svc:8081` | 不对外 | `application.yml` + `micro-lab.yaml` |
| order-service | 8082 | `order-service-svc:8082` | 不对外 | 同上 |
| gateway | 9090 | `gateway-svc:9090` | **节点IP:30090** | `application.yml` + `gateway-svc.nodePort` |
| Nacos（HTTP/控制台） | 8848 | `nacos-svc:8848` | **节点IP:30048** | `nacos.yaml` |
| Nacos（gRPC） | 9848 | `nacos-svc:9848` | **节点IP:31048** | `nacos.yaml`（+1000 铁律） |
| Sentinel Dashboard | **8858** | `sentinel-dashboard-svc:8858` | **节点IP:30058** | `~/sentinel-lab/` 部署文件 |
| Prometheus | 9090 | `prometheus-svc:9090` | **节点IP:30091** | `~/monitor-lab/prometheus.yaml` |
| Grafana | 3000 | `grafana-svc:3000` | **节点IP:30092** | `~/monitor-lab/grafana.yaml` |
| research-agent-svc | 8080 | `research-agent-svc:8080` | **节点IP:30081** | **其他项目**（非 micro-lab），早期部署 |
| Sentinel 客户端 transport | 8719 | 仅本机 | — | `order-service/application.yml` |
| actuator 端点 | 同应用端口 | — | — | （不需要额外端口） |

> 规划规律：对外一律 NodePort；`300xx` 给业务与中间件、`3009x` 给监控栈；业务 NodePort 用"应用端口前加 3 万"的记法（9090 → 30090），好记且避让常用端口。

**去哪查（三个环境各自的命令）**：

| 环境 | 查什么 | 命令 |
|------|--------|------|
| 集群（权威） | 所有 Service 的 port/nodePort | `kubectl get svc`（输出 `9090:30090/TCP`，读作 **port:nodePort**） |
| | 单个 Service 的完整定义 | `kubectl describe svc gateway-svc` / `kubectl get svc gateway-svc -o yaml` |
| | Pod 实际 IP（**参考，别依赖**） | `kubectl get pods -o wide` |
| | 全命名空间扫一遍 | `kubectl get svc -A` |
| 节点（Linux） | 谁在监听哪些端口 | `ss -lntp`（或 `netstat -lntp`） |
| Windows 本机 | 应用端口是否被占 | `netstat -ano \| findstr :8080` → `tasklist \| findstr <PID>` |
| 应用自身 | 端口最终生效值 | 启动日志 `Tomcat started on port 8081`；`curl localhost:8081/actuator/health` |

> 以上是速查版；**更完整的 6 层查询路径（含 Nacos 控制台、容器内 netstat、实测验证）见 §8.7**。

**统一管理 4 条实践**：

1. **建立端口台账作为单一事实来源**：改端口时"先改台账，再改代码"，把四个联动点一次改全（见下方连锁图）；
2. **NodePort 永远显式写死**（不写会随机分配），并按区间分段规划（业务 / 中间件 / 监控）；
3. **配置里只写名字不写 IP**：服务互访写 `xxx-svc:port`；数据库等外部资源地址放环境变量 / ConfigMap / Nacos 配置中心；
4. **减少暴露面**：进阶用 **Ingress 统一 80/443**（替代一堆 NodePort，待办"迁移真实项目"会遇到）；临时调试用 `kubectl port-forward svc/gateway-svc 9090:9090`（用完即走，不占 NodePort）。

**端口变更的连锁点（最小同步集合）**：

```text
application.yml（server.port）
   → micro-lab.yaml（Service 的 port / targetPort）
        → micro-lab.yaml（Service 的 nodePort）
             → 本文档端口台账（记录）
                  → 调用方配置（Feign / 网关路由 / 探针端口 / Prometheus targets）
```

> 最容易漏的两处：**探针端口**（9.9 节 httpGet 里写死 `port: 8081`）与 **Prometheus targets**（`user-service-svc:8081`）——改端口时只改 Service 不改这两个，就会"服务正常但探针 404 / 监控掉线"。

### 8.5 实战示例：`kubectl get svc` 输出逐列逐行讲解

**命令**（在任意节点执行）：

```bash
kubectl get svc
```

**真实输出（2026-09-25 集群实录）**：

```text
NAME                     TYPE        CLUSTER-IP      EXTERNAL-IP   PORT(S)                         AGE
gateway-svc              NodePort    10.43.201.107   <none>        9090:30090/TCP                  7d19h
grafana-svc              NodePort    10.43.133.48    <none>        3000:30092/TCP                  5d19h
kubernetes               ClusterIP   10.43.0.1       <none>        443/TCP                         16d
nacos-svc                NodePort    10.43.163.231   <none>        8848:30048/TCP,9848:31048/TCP   11d
order-service-svc        ClusterIP   10.43.24.236    <none>        8082/TCP                        7d19h
prometheus-svc           NodePort    10.43.218.45    <none>        9090:30091/TCP                  5d19h
research-agent-svc       NodePort    10.43.135.43    <none>        8080:30081/TCP                  16d
sentinel-dashboard-svc   NodePort    10.43.48.189    <none>        8858:30058/TCP                  5d22h
user-service-svc         ClusterIP   10.43.99.184    <none>        8081/TCP                        7d19h
```

**① 先学读表的 6 列**：

| 列 | 含义 | 关键读法 |
|----|------|---------|
| `NAME` | Service 名称 | 命名规范 `<服务名>-svc` |
| `TYPE` | 类型 | `NodePort` = 集群外可访问；`ClusterIP` = 仅集群内部；`LoadBalancer` = 云环境才有 EXTERNAL-IP |
| `CLUSTER-IP` | 集群内虚拟 IP | `10.43.x.x` 是 K3s 的 **Service 网段**（10.43.0.0/16）；它是虚拟 IP，不出现在任何网卡上；长期不变，但实际访问用名字 |
| `EXTERNAL-IP` | 外部 IP | `<none>` 是**正常现象**——NodePort 类型不分配外部 IP，访问方式 = `任意节点IP + nodePort` |
| `PORT(S)` | 端口映射 | ClusterIP 只有 `port`；NodePort 是 `port:nodePort`（**冒号的含义详见 §8.6**）；多端口用逗号分隔 |
| `AGE` | 创建至今 | 可用来对照学习时间线（见下） |

**② 逐行讲解（9 行，分三类）**：

**A. micro-lab 业务三件套**（AGE `7d19h` ≈ 09-17 第 8 站部署当天）：

| 行 | 解读 |
|----|------|
| `user-service-svc 8081/TCP`（ClusterIP） | 只有 `port` 没有 `nodePort` → 不对外，内部用 `user-service-svc:8081` 访问 |
| `order-service-svc 8082/TCP`（ClusterIP） | 同上 |
| `gateway-svc 9090:30090/TCP`（NodePort） | 唯一业务入口：外部走 `节点IP:30090` |

**B. 中间件与治理/监控栈**：

| 行 | 解读 |
|----|------|
| `nacos-svc 8848:30048/TCP,9848:31048/TCP`（AGE 11d ≈ 09-14 第 1 站） | 多端口写法：HTTP 30048 + gRPC 31048——**"+1000 铁律"的实证**（31048 = 30048 + 1000） |
| `sentinel-dashboard-svc 8858:30058/TCP`（AGE 5d22h ≈ 09-19） | 容器/Service 端口是 **8858**（不是默认 8080），对外 NodePort 30058 |
| `prometheus-svc 9090:30091/TCP`、`grafana-svc 3000:30092/TCP`（AGE 5d19h ≈ 09-19） | 监控双件套；注意 Prometheus 的 `9090` 与 gateway 的 `9090` 是**两个 Service 各自的 port，互不冲突**（port 只在所属 Service 内生效） |

**C. 其他与系统**：

| 行 | 解读 |
|----|------|
| `kubernetes 443/TCP`（ClusterIP，10.43.0.1） | K8s 系统自带：集群内访问 API Server 的入口；`10.43.0.1` 是 Service 网段的第一个 IP |
| `research-agent-svc 8080:30081/TCP`（AGE 16d） | **另一个项目**的服务（非 micro-lab），早期部署在同一集群；同样遵循 NodePort 规则 |

**③ 从这张表还能读出 4 条"隐藏信息"**：

1. **类型分布体现"只暴露必要"原则**：对外全是 NodePort（gateway + 中间件 + 监控 + 其他项目），业务内部服务清一色 ClusterIP；
2. **两套网段不要混**：`10.43.x.x` = **Service 虚拟网段**（本表 CLUSTER-IP）；`10.42.x.x` = **Pod 网段**（你 `ip addr` 里的 `cni0 10.42.0.1` / `flannel.1`）——Pod IP 会变，Service 名不变；
3. **AGE 是学习时间线的化石**：Nacos `11d`（第 1 站）→ micro-lab 三件套 `7d19h`（第 8 站，09-17）→ 监控/Sentinel `5d19h`（09-19 探针与监控）——与复盘文档记录完全吻合；
4. **`9090:30090` 的读法**：冒号**前**是"集群内用的 port"，冒号**后**是"集群外用的 nodePort"，两个数字各管一边。

**④ 下一步查证（由浅入深）**：

```bash
# ① 看权威定义（nodePort 是否写死在 YAML 里）
kubectl get svc gateway-svc -o yaml

# ② 看后端是谁在承接流量
kubectl describe svc user-service-svc

# ③ 看所有 Service 背后的 Pod IP（对比：Pod IP 会随重建变化）
kubectl get endpoints

# ④ 宽格式查看（含 selector 等更多列）
kubectl get svc -o wide
```

> 记住这个对照：**`kubectl get svc` 的 CLUSTER-IP 长期不变；`kubectl get endpoints` 的 Pod IP 每次重建都变**——前者是"门牌号"，后者是"屋里的人"。

### 8.6 `PORT(S)` 列详解：冒号前后到底是什么

**问题**：为什么 `9090:30090/TCP` 里有两个数字、中间用冒号隔开？

**答案**：因为 Service 是"端口映射器"——**内外两扇门，外加一个隐藏的"屋里门"**：

```text
集群外：节点IP:30090     ─┐
                         ├─► Service（gateway-svc） ─► Pod:9090（应用真实监听）
集群内：gateway-svc:9090 ─┘

Service 内部登记了三个端口：
  nodePort   30090   ← "外门"：集群外入口（开在节点上、冒号右边那个）
  port       9090    ← "内门"：集群内入口（gateway-svc:9090、冒号左边那个）
  targetPort 9090    ← "屋里门"：转发终点（Pod 真正监听的端口，命令里不显示）
```

**三个端口的分工**：

| 顺序 | 字段名 | 本项目示例 | 谁在用 | `get svc` 里是否显示 |
|:---:|--------|:---:|--------|:---:|
| 外门 | `nodePort` | 30090 | **集群外**：浏览器 / Windows / 其他机器 | 冒号**右边** ✓ |
| 内门 | `port` | 9090 | **集群内**：其他 Pod（`gateway-svc:9090`） | 冒号**左边** ✓ |
| 屋里门 | `targetPort` | 9090 | Service 转发的最终目标（容器端口） | ❌ **不显示**，要 `-o yaml` |

**完整流量路径**：

```text
外部访问：192.168.157.129:30090
   →（kube-proxy 转发规则）→ Service:9090（ClusterIP 10.43.201.107）
        →（按标签找到 Pod）→ Pod IP:9090（targetPort，应用真正监听的端口）

集群内访问：gateway-svc:9090 ──→ 直接走 port，不经过节点端口
```

**为什么"对外"还要另开一扇门（设计动机）**：

1. **ClusterIP 是虚拟 IP**——`10.43.x.x` 只存在于集群内的转发规则里，外部机器**根本路由不到**，所以必须在每个节点上开一个真实端口（nodePort）；
2. **分层访问**：内部调用直接走 `port`，不绕节点端口，路径更短；
3. **避免冲突**：外部端口统一放 30000~32767 区间，与节点上已有服务错开；
4. **按需开放**：内部服务（user/order）只留 ClusterIP，连 nodePort 都不给——"只暴露必要"。

**各种写法的读法（结合 §8.5 实录）**：

| 输出 | 类型 | 含义 |
|------|------|------|
| `9090:30090/TCP` | NodePort | 内门 9090 + 外门 30090 |
| `8081/TCP` | ClusterIP | **只有内门**，没开外门 → 不对外 |
| `8848:30048/TCP,9848:31048/TCP` | NodePort | 多端口：两组"内门:外门"，逗号分隔 |
| `443/TCP` | ClusterIP | 系统 Service（API Server） |

> **判据一句话**：显示两个数字 ⇔ 配了 `nodePort`（对外标志）；只显示一个 ⇔ 只有 ClusterIP。

**怎么看到隐藏的第三个数（targetPort）**：

```bash
# ① 权威字段（port / targetPort / nodePort 三兄弟都在）
kubectl get svc gateway-svc -o yaml
#   ports:
#   - port: 9090          ← 内门
#     targetPort: 9090    ← 屋里门
#     nodePort: 30090     ← 外门

# ② 人类可读格式（字段示意）
kubectl describe svc gateway-svc
#   Port: 9090/TCP    TargetPort: 9090/TCP    NodePort: 30090/TCP
```

**两个高频困惑**：

1. **从 Windows `curl 10.43.201.107:9090` 不通**：ClusterIP 是虚拟 IP，只在本集群网络内可路由——外部只能走 `节点IP:30090`；想在集群内验证，可 `kubectl exec` 进某个 Pod 里 curl；
2. **节点上 `ss -lntp` 看不到 30090 的进程**：nodePort 的"监听"不是应用进程，而是 **kube-proxy 下发的转发规则**（K3s 默认 iptables 模式）。想亲眼看规则：

```bash
sudo iptables -t nat -L KUBE-NODEPORTS -n | grep 30090
```

**一句话口诀**：

> **冒号左 = 门内号**（集群内：`服务名:port`）；**冒号右 = 门外号**（集群外：`节点IP:nodePort`）；**隐藏的 targetPort**（要 `-o yaml` 查）= 屋里真正开门的地方。

### 8.7 端口查询的 6 种方式 + kubectl 简称表

**一、先答"`svc` 是什么"：它是 `service` 的缩写**

kubectl 支持资源类型简称，`svc` 就是 `service`：

```bash
kubectl get svc        # 等价于
kubectl get services
```

常用简称速查：

| 简称 | 全称 | 是什么 |
|------|------|--------|
| `svc` | service | **一组 Pod 的稳定访问入口**（固定名字 + 固定端口 + 负载均衡） |
| `po` | pod | 最小运行单元（IP 会变） |
| `deploy` | deployment | 管 Pod 的"工头"（副本数、滚动更新） |
| `cm` / `secret` | configmap / secret | 配置 / 密钥 |
| `ns` | namespace | 命名空间（资源隔离） |
| `no` | node | 集群节点 |
| `ing` | ingress | 七层入口（进阶） |

查全集：`kubectl api-resources`。

> 注意：Service 是 K8s 的网络**抽象对象**，不是"业务微服务"的意思——一个 Deployment 可以对应一个 Service，也可以多个，甚至可以没有。

**二、澄清一个误区："没暴露到全局"不影响查端口**

| 概念 | 决定什么 |
|------|---------|
| **暴露范围**（NodePort vs ClusterIP） | 能不能从**集群外**访问 |
| **可查性**（`kubectl get svc`） | 能否看到端口的**定义**——与暴露无关 |

- ClusterIP 的服务（user/order）照样能查到 `8081/TCP`、`8082/TCP`；
- ClusterIP 在**集群内**也是"全局"的——任何 Pod 都能用 `服务名:port` 访问它，只是集群外进不来；
- **唯一会让你"查不到"的原因是命名空间**：`kubectl get svc` 默认只显示当前 namespace（本项目都是 `default`）；服务在别的 ns 时要带参数：

```bash
kubectl get svc -A                # 所有命名空间
kubectl get svc -n default        # 指定命名空间
```

**三、查端口的 6 个层次（除 `kubectl get svc` 之外的路径）**

| 层次 | 想知道什么 | 命令 / 位置 |
|------|-----------|------------|
| ① 应用配置层 | 应用"设计"监听哪个端口 | `application.yml` 的 `server.port`；启动日志 `Tomcat started on port 8081` |
| ② 容器实际层 | 容器里"真实"监听的端口 | `kubectl exec deploy/user-service -- netstat -lntp`（alpine 有 busybox netstat，`ss` 不一定有） |
| ③ Service 层 | 集群内外怎么访问、转发到哪 | `kubectl get svc -o wide`、`kubectl describe svc user-service-svc`、`kubectl get endpoints` |
| ④ 工作负载层 | Pod 声明了哪些 containerPort | `kubectl get deploy user-service -o yaml`（ports 段） |
| ⑤ 注册中心层 | 实例"自己上报"的 IP:端口 | **Nacos 控制台** → 服务管理 → 服务列表 → 详情 → 实例（能看到 `192.168.157.x:8081`） |
| ⑥ 网络/节点层 | nodePort 的转发规则 | `sudo iptables -t nat -L KUBE-SERVICES -n`、节点上 `ss -lntp` |

**四、几条"一网打尽"的命令（强烈推荐）**：

```bash
kubectl get all                       # 当前 ns 的 pod/svc/deploy/rs 一把抓
kubectl get svc,pods -o wide          # Service + Pod（含 Pod IP 与所在节点）
kubectl get deploy,svc,endpoints      # 从"谁在跑"到"谁在接流量"一条线
kubectl describe pod <pod名>          # 看 Ports 段（容器声明端口）
kubectl get svc -A                    # 跨命名空间全量
```

**五、验证"端口到底通不通"（查不到就实测）**：

```bash
# 集群内：直接问服务
kubectl exec deploy/order-service -- wget -qO- http://user-service-svc:8081/actuator/health

# 容器内自查监听
kubectl exec deploy/user-service -- netstat -lntp

# 外部（Windows）：只能测 NodePort
curl.exe http://192.168.157.129:30090/api/orders/1001
```

**六、一句话总结**：

> `svc` 就是 `service` 的缩写；**"暴露范围"和"能否查询"是两件事**——ClusterIP 一样能查、一样是集群内全局可达，只是外面进不来；查端口最全的组合是 **`kubectl get svc -A`（服务视角）+ `kubectl describe pod`（容器视角）+ Nacos 控制台（应用上报视角）**；查不到时先怀疑"命名空间不对"，再看 `-o yaml` 看权威定义。

---

## 九、学习路径建议

按"从内到外、从简到繁"的顺序复习：

1. **第一遍：看懂 3 个 application.yml**（第二章）
   目标：能说出每个服务"为什么只配了这些"——端口、服务名、角色相关配置。
2. **第二遍：看懂构建链路（Dockerfile + pom.xml）**（第三章）
   目标：能解释"为什么改一行 yml 要重建镜像"、Dockerfile 每一行在干什么、三个模块的依赖分别对应哪一站。
3. **第三遍：看懂优先级链**（第一章 §1.2 / §1.3 + 第七章）
   目标：能解释"为什么本地 yml 写 192.168.157.129:30048，K8s 里却连 nacos-svc:8848"。
4. **第四遍：看懂 nacos.yaml**（第四章 4.1）
   目标：三层端口 + gRPC +1000 铁律能背下来。
5. **第五遍：micro-lab.yaml 的六个版本**（第四章 4.2）
   目标：理解每一版解决的"什么故障"——这是一条"从能跑到跑得稳、再到账实归位、最后风格统一"的演进线：
   `能部署（v1）→ 发布不中断（v2/v3 入口侧）→ 退场不丢请求（v4 出口侧）→ 慢启动不误杀（v4 启动期）→ 漂移归位（v5）→ env 风格统一（v6）`。
6. **第六遍：配置中心与动态刷新**（第六章）
   目标：能讲清"本地 yml / 环境变量 / Nacos dataId"三者的分工。
7. **收尾：认领漂移账**（第七章）
   目标：理解声明式 vs 命令式的差异，知道为什么要把 set env 写回文件——**✅ 2026-10-01 已实操完成（v5，零滚动归位）**。

> 学习心法：**配置的每一个"文件"背后，都对应一个"谁在什么时候读它"**。搞清楚"读者"和"生效时机"，这一堆 yaml 就不会再乱。

---

*本文档由《微服务实战复盘.md》与 micro-lab 工程实际文件（3 个 `application.yml`、3 个 `Dockerfile`、4 个 `pom.xml`）对照整理；标注"参考骨架"的片段为按文档描述复原的示意写法，节点实际文件以 `~/k8s-lab/`、`~/monitor-lab/`、`~/sentinel-lab/` 为准。*
