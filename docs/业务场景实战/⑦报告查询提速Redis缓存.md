# ⑦ 报告查询提速——Redis 缓存 + 失效策略 + 压测

> **日期**：2026-10-11　**教材**：`CacheConfiguration`、`ResearchReportQueryService`、`ResearchReportAssemblyService`、`application.yml`
> **前置**：场景③（列表 SQL/索引）、④（幂等）、⑤（AOP/代理）｜**延伸**：G7（监控体系，按需衔接）
> **★ 本篇定位**：实测环节连爆多层"壳坑"，本篇按 **教科书 + 排障实录** 双线书写，细节从全——**供反复查阅/补课用**。

## 1. 开课动机——一个"每次都挖矿"的接口

**病人接口**（改造前）：

```text
GET /api/tasks/{taskId}/report
   → ResearchReportQueryService.getReportByTaskId(taskId)
       → findByTaskId（查库——拉出大对象：report_markdown 全文 + report_json）
       → toResearchReport（组装 Response）
   → 返回
```

**这个接口的三个"体质特征"**（合起来 = 缓存天生的好靶子）：

| 特征 | 说明 |
|------|------|
| **大** | 报告是 `TEXT`/`JSON` 大字段——每次从库里拉全量 |
| **读多写少** | 报告生成一次（很贵——LLM 写的），被看无数次 |
| **刷新频繁** | 报告页开着——前端反复请求 |

**缓存的思想一句话**：**矿挖出来放到门口（内存），下次直接拿**。

**前端锚点**（你比谁都熟）：

```javascript
useMemo(() => expensiveCompute(deps), [deps]);   // 记忆化：算一次、反复用
// React Query / SWR —— "前端有 React Query，后端有 Redis Cache"——同一个思想
// ★ 区别只有一样：Redis = 【跨进程、跨服务器】的 memo
```

## 2. 现状审计（三处证据）

| # | 证据 | 说明 |
|---|------|------|
| 1 | 查询链路**零缓存**（三行直连） | 每次请求 = 查库 + 组装 |
| 2 | `RedisTemplate` / `@Cacheable` 全项目 **0 处** | 但 `spring-boot-starter-data-redis` **依赖已在 pom**（放好了没用的牌）——**第 4 次"从零接入"** |
| 3 | **写路径就在附近**：`ResearchReportAssemblyService.buildReport` | 报告"组装保存"的唯一入口——**失效缓存的钩子点** |

## 3. ★ 知识课一：缓存三问（设计任何缓存，先答这三题）

### 3.1 三问三答

| 问 | 本题答案 |
|----|---------|
| **① 放什么？** | 报告 Response（读多写少 ✓、计算贵 ✓、能容忍"短暂旧" ✓——三条全中） |
| **② 什么时候失效？** | ★ **双保险**：写时主动踢（`@CacheEvict`）+ TTL 兜底（详见 §10 思考题 1） |
| **③ 怎么防坏？** | 三大经典问题（见 3.2） |

### 3.2 三大经典问题（缓存界的"三座大山"——先认脸）

| 问题 | 症状 | 解法（初认） |
|------|------|------------|
| **穿透** | 查**根本不存在**的 key——缓存永远不命中，每次打库 | 空值也缓存 / 布隆过滤器 |
| **击穿** | **热点 key 恰好过期的一秒**——大量请求同时涌向数据库 | 互斥锁（只放一个去查）/ 逻辑过期 |
| **雪崩** | **大量 key 同时过期**（或 Redis 宕机） | TTL 加随机尾巴 / 多级缓存 / 限流降级 |

（附：**Redis 为什么快**——内存操作 + 单线程免锁竞争 + IO 多路复用。）

## 4. ★ 知识课二：Spring Cache——"申请书 + 外包工"第 5 次现身

```java
// 读路径：
@Cacheable(value = "report", key = "#taskId")
public ResearchReportResponse getReportByTaskId(UUID taskId) { ... }

// 写路径：
@CacheEvict(value = "report", key = "#taskId")
public ResearchReportEntity buildReport(UUID taskId) { ... }
```

**机制拆解**（你现在的眼睛应该能一眼看穿）：

```text
谁来读 @Cacheable？ → CacheInterceptor（外包工）
你调用 service 时撞到谁？ → ★ 代理戏服（CGLIB——J2 的 $$SpringCGLIB$$0）

代理的方法体（概念版）：
   查缓存 → 命中 → 直接返回（★ 你的方法【根本不会被调用】——连事务都不开）
          → 未命中 → proceed() 查库 → 结果顺手塞进缓存 → 返回
```

**家族清点**（同一个 AOP 工厂生产）：`@Transactional`（事务戏服）、`@AuditLog`（审计）、`@KafkaListener`（消费者）、**`@Cacheable`（缓存）**——**你已经能凭"谁读标签、谁被套戏服"读出每一种了**。

## 5. 设计抉择（五项决策表）

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 缓存键 | `report::{taskId}` | 应用级命名空间（**不会碰集群里其他服务的 key**）+ 直观可读 |
| 失效策略 | **双保险**：`@CacheEvict`（写时踢）+ TTL 30 分钟（兜底） | 主动失效保及时性、TTL 保正确性（详见 §10） |
| 值序列化 | **JSON**（`Jackson2JsonRedisSerializer`） | 人类可读 + 跨版本友好（详见 §10 深挖一） |
| 缓存后端 | Redis（集群现成的实例） | 不另起服务（用户决策：**直接连集群的**） |
| 观测窗 | `redis-cli KEYS/TTL/GET` | 实物验证 |

## 6. 编码实录（7 处接线 + 2 次修复）

| 文件 | 改动 | 角色 |
|------|------|------|
| `ResearchAgentApplication` | + `@EnableCaching` | 缓存总开关（外包工上岗证） |
| `application.yml` | + `spring.cache.type=redis` | 声明 Redis 后端（★ 不写空壳节点——见 §11） |
| `ResearchReportQueryService` | + `@Cacheable` | 读路径 |
| `ResearchReportAssemblyService` | + `@CacheEvict` | 写路径失效 |
| `CacheConfiguration`【新】 | RedisCacheConfiguration bean | ★ 值序列化（JSON）+ TTL 30 分钟接管 |
| `pom.xml` | （无需新增——redis 依赖早已在） | —— |

## 7. 验证记录（2026-10-11，全绿）

### 7.1 链路铁证（虚拟机 redis-cli 三连）

```text
KEYS "report::*"  →  1) "report::b58eecc8-a166-4cb3-873d-1970f6cffdaf"     ← key 出现在【集群 Redis】
TTL  report::b58eecc8…  →  (integer) 1754                                  ← 30 分钟倒计时进行中
GET  report::b58eecc8…  →  {"taskId":"b58eecc8…","summary":"…", …}          ← ★ 人类可读的 JSON + UTF-8 中文
```

**判读**：本地应用 → 把缓存写进了**虚拟机 K8s 集群里的 Redis**——跨机器缓存链路 ✓；JSON 可读（对比 JDK 序列化的二进制乱码）= **序列化选型的实物验收** ✓。

### 7.2 ★ 彩蛋：两次响应的 `generatedAt` 格式不同 = "命中缓存"的指纹

```text
第一次: "generatedAt":"2026-10-11T14:53:50.95529+08:00"    ← 刚从库读出、直接序列化
第二次: "generatedAt":"2026-10-11T06:53:50.95529Z"         ← ★ 来自缓存（ISO 规范化为 UTC）
```

——**同一时刻、不同表示**：缓存里的 JSON 是规范化格式，反序列化再输出时时区被规范化——**第二次响应没走原路，它走的是缓存**（顺带证明 `JavaTimeModule + ISO 文本` 配置在干活）。

### 7.3 ★ 直改库实验（复现工作中的"手动清缓存"）

```text
① H2 console 直改库：UPDATE research_report SET summary='【被直改的摘要】' …   ← 旁路写入
② 请求接口 → 返回的还是【旧 summary】
   ← 为什么？@CacheEvict 只挂在【应用的 buildReport】上——直改库没有 evict 触发点
③ 手动清缓存：redis-cli DEL report::<id>
④ 再请求 → 【新 summary】出现 ✓
```

——**"改完必须清缓存"的完整因果，在自己手里复现了**（呼应 §13 的实战追问）。

### 7.4 压测（可选未跑——命令留档）

```powershell
$times = 1..30 | ForEach-Object { (curl.exe -s -o $null -w "%{time_total}" http://localhost:8080/api/tasks/<id>/report) -as [double] }
"第一次（未命中）: $($times[0]) s"
"后 29 次平均（命中）: $(($times[1..29] | Measure-Object -Average).Average) s"
```

**如实预期**：本地 H2 + 小数据 = 差异毫秒级。**缓存的价值 = "被省掉的那段代价"**——本地小库省得少；生产（远程库 + 大对象 + 高并发）才显威力。

## 8. ★ 固化规范：缓存接入 checklist（本场景产出）

1. **五勾判断法**（读多写少/访问聚焦/更新低频/容忍短暂陈旧/失效粒度清晰）——**勾不满别缓**；
2. **失效双保险**：`@CacheEvict`（主动）+ TTL（兜底）——"缓存可以暂时错，不能永远错"；
3. **删缓存而非更新缓存**（Cache-Aside：先更新库、再删缓存——避开双写不一致）；
4. **值序列化用 JSON**（指定目标类型 + JavaTimeModule）——**别用默认 JDK 序列化**（§10）；
5. **yml 别写空壳节点**（§11）；
6. **命名空间隔离**：`业务::key` 前缀（共享 Redis 实例时互不误伤）；
7. **监控**：key 存在性（`KEYS`——学习环境）`SCAN`（生产）+ TTL + 命中率。

## 9. 复盘与关联地图

| 今天的部件 | 哪课学过 |
|-----------|---------|
| `@Cacheable` / `@CacheEvict`（申请书+外包工） | **J7（注解）→ 场景⑤（AOP/代理）——第 5 次现身** |
| 代理拦截（命中时方法体不执行） | J2（CGLIB 戏服） |
| `REQUIRES_NEW` 式的"独立副作用隔离"思想 | 场景⑤（审计独立事务）——本课的"TTL 兜底"同属"防御纵深" |
| 大对象读多写少 → 缓存 | 场景③（列表走 SQL/索引——**列表不缓存**的判断见 §14 题 2） |
| 直改库不生效 | 场景④（旁路写入思想）+ 用户工作实战 |

**性能优化演进线**：`裸查库` → `SQL/索引优化（场景③）` → **`缓存（本课）`** → （未来）`读写分离 / 专门搜索层`

## 10. ★ 深挖一：序列化坑全解剖（"Cannot serialize"事件全记录）

### 10.1 事件还原

首次请求报错：

```text
org.springframework.data.redis.serializer.SerializationException: Cannot serialize
Caused by: java.lang.IllegalArgumentException:
   DefaultSerializer requires a Serializable payload
   but received an object of type [ResearchReportResponse]
```

### 10.2 根因

**Spring Cache + Redis 的出厂默认值序列化器 = `JdkSerializationRedisSerializer`（JDK 序列化）**——它的规矩：**对象必须实现 `java.io.Serializable`**——而 `ResearchReportResponse` 是 **record（没实现）**——缓存写入（put）时当场拒收。

**几乎每个团队第一次接 Redis 缓存都会撞它一次**——它的意义是逼你回答："**缓存的值用什么格式存进 Redis？**"

### 10.3 方案对比（为什么选 JSON）

| 方案 | 做法 | 问题 |
|------|------|------|
| A. 快速止血 | record 加 `implements Serializable`（一行） | Redis 里是**二进制乱码**（不可读）+ **类结构一变旧缓存反序列化就炸**（跨版本脆弱）——"能跑"≠"该跑" |
| ★ B. 正道 | **自定义 JSON 序列化**（配置类） | 无——redis-cli 直接可读、跨语言、跨版本友好 |

### 10.4 `CacheConfiguration` 的三个"细节心机"

```java
// 细节 ①：JSON 序列化 —— redis-cli 里直接可读（GET 出来就是 {"taskId":…}）
Jackson2JsonRedisSerializer<ResearchReportResponse> valueSerializer =
        new Jackson2JsonRedisSerializer<>(mapper, ResearchReportResponse.class);

// 细节 ②：★ 指定目标类型 ResearchReportResponse.class
//   读回来直接构造该类型——【不依赖 JSON 内嵌类型信息】
//   （为什么不用 GenericJackson2JsonRedisSerializer？它靠内嵌类型信息，
//     而 record 是 final 类、默认策略不给 final 类嵌类型 → 读回来变 Map → ClassCast！）

// 细节 ③：注册 JavaTimeModule
//   Response 里有 OffsetDateTime——不注册就是【二次爆炸】
//   （报错会变成：Java 8 date/time type not supported by default）
//   顺带 disable(WRITE_DATES_AS_TIMESTAMPS)——时间存 ISO 文本（人类可读）
```

**TTL 也一并接管**（`entryTtl(Duration.ofMinutes(30))`）——yml 里的 `time-to-live` **随之失效**（自定义 `RedisCacheConfiguration` bean 会覆盖 Boot 按 properties 构建的默认配置）——**故 yml 中该行已移除**（避免"配置两处、真假不明"）。

## 11. ★ 深挖二：空壳节点坑（"ConverterNotFound"事件全记录）

### 11.1 事件还原

```text
Failed to bind properties under 'spring.cache.redis' to CacheProperties$Redis:
Reason: No converter found capable of converting from
        type [java.lang.String] to type [CacheProperties$Redis]
```

### 11.2 根因

我把 TTL 挪去配置类后，yml 里留下的 `redis:` 节点**下面只剩注释、没有任何有效键值**：

```yaml
  cache:
    type: redis
    redis:          # ← 空壳：只有注释
      # 注释注释注释……
```

——**"只有注释的键" = 一个值为 null 的空壳节点**——Spring Boot 把它绑定到 `CacheProperties.Redis`（对象类型）时，以"诡异方式"失败（ConverterNotFound）。

### 11.3 规则

> **YAML 里"只挂注释、无键值"的节点 = 定时炸弹——要么删掉整个节点，要么给它至少一个有效键值。**

（已在 application.yml 里加了警示注释。）

## 12. ★ 深挖三：六层壳排障实录（本场景完整战报）

今天从"写代码"到"应用起来"，**连穿六层壳**——每层的报错都"看似无关"，**逐层剥、每层拿证据**才走到底：

| 层 | 现象 | 根因 | 解法 |
|----|------|------|------|
| 1 | `curl: (3) URL rejected` | `-D"{\"..\"}"` 是 cmd 转义、PowerShell 不认 → 参数拆碎 | 单引号包 JSON |
| 2 | 仍 400（fields:[]） | **中文编码**：PS 把中文按 GBK 字节传给 curl → 服务端按 UTF-8 解析失败 | **文件法**（UTF-8 无 BOM + `-d @文件`） |
| 3 | 英文含空格仍 400 | **PowerShell re-quoting**（向原生程序传参的引号重组在"空格+引号"组合下不可靠） | 文件法（**固化习惯**：body 一律文件法/改用 IDEA HTTP Client） |
| 4 | `Unknown lifecycle phase ".run.profiles=dev"` | **PowerShell 把 `-Dspring-boot.run.profiles=dev` 拆成两半** | `--%`（停止解析符）或引号包参数 |
| 5 | `No plugin found for prefix 'spring-boot'` | **在根目录跑了 mvn**（根目录无 pom） | `cd backend` |
| 6 | `Failed to bind properties under 'spring.cache.redis'` | **空壳节点**（§11） | 删节点 |

**排障心法总结**：

```text
① 分层怀疑：传输层（编码/引号）→ 参数层（shell 拆分）→ 配置层（绑定）→ 代码层
② 每层拿证据，不靠猜（describe / git diff / 读文件 / 对质 src 与 target）
③ 工具固化：body 走文件、参数加引号、日志落文件（*> + 我来读）
④ 报错看"最根上的 Caused by"，别只看"事后通报"
```

**基建配套**（今天顺带打通的）：**集群 Redis 的 NodePort 接入**——`kubectl patch svc redis-svc -p '{"spec":{"type":"NodePort"}}'` → `6379:32136` → Windows 预检 `Test-NetConnection` ✓ → **`$env:REDIS_HOST/PORT` 覆盖**（yml 的 `${REDIS_HOST:localhost}` 伏笔派上用场——**本地开发连集群中间件，零代码改动**）。

## 13. ★ 深挖四：实战追问——"为什么工作里总要'手动清缓存'？"

**你问的**（后台菜单配置场景）："既然写库会自动删缓存，为什么后端老说'配置有缓存，需要清缓存'？是 Redis 吗还是别的？"

**核心判读**：**"需要手动清缓存"这件事本身，就说明"自动失效链路"有缺口**。六种缺口：

| # | 缺口 | 场景 |
|---|------|------|
| 1 | **旁路写入** | 配置直接 SQL 改库/别的平台改——没走应用写路径——**没有 evict 触发点**（§7.3 复现的就是它） |
| 2 | ★ **缓存不在 Redis——在【每台机器的 JVM 内存】** | 菜单/权限类数据**极常用本地缓存**（Caffeine/启动加载/手写 Map）——快、数据小——**但"每台机器一份"**：清了 Redis 别的实例本地还在——**本地缓存的分布式失效没有"一键 DEL"**——只能重启/刷新接口/广播（pub-sub/MQ） |
| 3 | 缓存没接失效机制 | 手写 `redisTemplate.set` 完事——没配"写时删除" |
| 4 | **永不过期** | 极致性能（或怕击穿）——"永不过期 + 没接失效 = 必须人肉清" |
| 5 | 失效粒度模糊 | 菜单树交叉引用——"踢哪个 key"难定义——干脆全清 |
| 6 | 多写入口 | 后台/脚本/DBA——不可能人人记得清——人肉兜底 |

**"是 Redis 吗"**——按概率：**JVM 本地缓存（菜单场景最常见）> Redis > 配置中心客户端快照 > 网关/浏览器**。

**金句**：

> **"写库自动删缓存"是理想架构的最优路径；现实中每一次"手动清缓存"，几乎都来自那条路径之外——直改库、多入口、本地缓存、历史包袱。**
> 那句话的真实含义 = **"这个系统的缓存失效链路有缺口，目前靠人肉兜底。"**

**下次对接反问三连**（显得专业）：① 缓存在哪一层（Redis/本地）？② 为什么要手动清（是不是直改库）？③ 清了多久生效（有无 TTL 兜底）？

## 14. ★ 补充：思考题与答案

### 思考题 1（失效设计）：光有"主动踢"够吗？

**答**：**双保险**——① 写时主动踢（`@CacheEvict` 挂在 `buildReport`——"数据变了旧缓存立刻踢、下次读自动重建"）；② **TTL 兜底**（30 分钟）。**为什么 TTL 必须**（主动失效会"漏"的三途径）：(a) **新写路径忘加 evict**（后台修数据/定时任务——"忘"是人之常情）；(b) **绕过应用直接改库**（运维 SQL——应用全然不知——§7.3 复现过）；(c) evict 执行异常（Redis 抖动）。

> **金句："缓存可以暂时错，不能永远错"**——主动失效保及时性、TTL 保正确性兜底。**"永不过期"比"过期"危险得多**。

**附**：为什么是"删缓存"而非"更新缓存"——更新有**双写并发坑**（A、B 两个写者乱序 → 缓存留旧值）；"删"让下次读从库重建，天然避开。**Cache-Aside 标准 = 先更新库、再删缓存**（"延迟双删"为进阶）。

### 思考题 2（缓存选型）：任务列表接口能不能也缓存？

**答**：**能但难得多——"详情随便缓、列表要三思"**：

| # | 维度 | 报告（详情） | 任务列表 |
|---|------|-------------|---------|
| 1 | key 数量 | `report::{taskId}` 一物一键 | `list::{status}::{page}::{size}::…` **笛卡尔积** |
| 2 | 命中率 | 每个 key 反复访问（热点集中） | 大多组合只被访问一两次（长尾）——**白建 + 白吃内存** |
| 3 | 失效粒度 | 精确踢那一个 | 任何任务变化都让整页过期——**key 爆炸"想踢踢不全"**——只能秒级短 TTL |

**外加**：分页 + 缓存 = **"位移漂移"**（新增一条 → 所有页内容错位）。

> **判断口诀五勾**（读多写少/访问聚焦/更新低频/容忍短暂陈旧/失效粒度清晰）——**报告满分全勾；列表三处不及格**。
> **升华**：**"缓存不是唯一解"**——列表的正解可能是：优化 SQL+索引（场景③ 已做）/ 只缓存热门第一页 / 短 TTL + 前端 SWR 容忍 / 大流量上专门设施（ES）。

---

## 集群 Redis 接线备忘（部署时用）

```bash
# 集群 Redis 已打通（开发机 → 集群）：
kubectl patch svc redis-svc -p '{"spec":{"type":"NodePort"}}'   # ClusterIP → NodePort（兼具内网能力）
# NodePort = 32136；Windows 预检：Test-NetConnection 192.168.157.129 -Port 32136

# 本地开发连集群 Redis（env 覆盖——不改代码）：
$env:REDIS_HOST = "192.168.157.129"; $env:REDIS_PORT = "32136"; mvn spring-boot:run "-Dspring-boot.run.profiles=dev"

# ★ 部署 research-agent 进集群时：清单 env 必须配 REDIS_HOST=<集群内服务名>
#   （此前从不连 Redis 所以从没配过——现在开始缓存，不配会在 Pod 里连 localhost 失败）
```

---

> **下一场景预告：⑧ 下游调用治理——LLM 调用的超时 / 重试 / 降级（Resilience4j 或 Sentinel）**：容错模式、重试语义（衔接场景⑥ 的"至少一次"思想）。
