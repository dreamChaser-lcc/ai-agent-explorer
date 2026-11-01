# T6 · Caffeine 本地缓存（两讲完整版）

> **状态**：✅ 完成（2026-11-01，两讲：三种用法/W-TinyLFU + 多层缓存/脏读）
> **前置**：[Redis 实战 04·缓存三件套](../Redis实战/04·缓存三件套.md)（穿透/击穿/雪崩）/ [场景⑦](../业务场景实战/⑦报告查询提速Redis缓存.md)（Redis 缓存已落地）
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单：Caffeine 缺失，纠偏 2 天）

---

## 涉及代码文件（点击阅读）

| 文件 | 本课中的作用 |
|---|---|
| [`CaffeineBasicsTest.java`](../../backend/src/test/java/com/aiexplorer/researchagent/cache/CaffeineBasicsTest.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/test/java/com/aiexplorer/researchagent/cache/CaffeineBasicsTest.java) | 第一讲：三种用法 + 扫描污染两场景实测 |
| [`MultiLevelCache.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cache/MultiLevelCache.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cache/MultiLevelCache.java) | 第二讲：L1 Caffeine + 可插拔 L2 |
| [`MultiLevelCacheTest.java`](../../backend/src/test/java/com/aiexplorer/researchagent/infrastructure/cache/MultiLevelCacheTest.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/test/java/com/aiexplorer/researchagent/infrastructure/cache/MultiLevelCacheTest.java) | 两实例脏读 + 广播失效演示（3 测试全过） |
| [`pom.xml`](../../backend/pom.xml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/pom.xml) | caffeine 依赖（Spring Boot BOM 管版本） |

---

## 一、为什么 Redis 之外还要本地缓存（数字差）

```text
Redis 取一次：    ~0.2~1ms（网络往返 + 序列化/反序列化）
Caffeine 取一次： ~50~100ns（纯内存哈希表）
→ 差 1 万倍

三个真实动机：
① 热 key：单 key QPS 几十万时 Redis 也可能打满——本地挡在最前面
② 网络抖动/Redis 故障：本地是 L1 兜底（读可用性）
③ 成本：每次请求省 1ms × 百万请求 = 实打实的延迟收益
```

## 二、第一讲：三种用法（CaffeineBasicsTest 实测，3/3 过）

### 用法 1：手动 Cache

```java
Cache<String, String> cache = Caffeine.newBuilder()
        .maximumSize(2).expireAfterWrite(Duration.ofMinutes(5))
        .recordStats().build();
cache.put("k3", "v3");   // 容量 2 放第 3 个 → 淘汰一个
cache.cleanUp();         // ★ 淘汰/统计是异步维护的，断言前强制同步
```

### 用法 2：LoadingCache（未命中自动加载 = 天然防穿透）

```java
LoadingCache<String, String> cache = Caffeine.newBuilder()
        .maximumSize(100).recordStats()
        .build(key -> { loadCount++; return "db-" + key; });   // loader

cache.get("report-1");   // miss → loader 执行
cache.get("report-1");   // hit  → 不执行
// 实测：loader 执行 1 次（同 key 并发请求合并），命中率 0.50
```

### 用法 3：扫描污染两场景（本次最有价值的实测）

| 场景 | 构造 | 热数据存活 |
|---|---|---|
| A：容量内扫描 | 容量 1000，热 50 + 冷 500 | **50/50 全存活** |
| B：极端扫描 | 容量 1000，热 50 + 冷 3000（3 倍） | **仅 5/50** |

### 第一讲踩的三个坑（实测留档）

```text
① Cache.get(key, fn) 不是"读"！它内部是 computeIfAbsent——
   已存在的 key 直接返回，【不累积 TinyLFU 频率】。
   模拟真实读必须 getIfPresent（用它预热 → 热 key 被扫描冲光 0/50）

② 淘汰/统计异步：put 后 evictionCount 可能还是 0，断言前 cache.cleanUp()

③ 别硬断言内部策略数值（存活率随版本/模式浮动）
   测确定性事实：容量内全存活（50/50）+ 极端扫描有淘汰（<50）
```

## 三、W-TinyLFU 原理

```text
LRU 的问题：扫描污染（一次性遍历把热数据全挤掉）
LFU 的问题：历史热点永不淘汰 + 计数维护贵

W-TinyLFU = Window（新数据缓冲，1%）+ TinyLFU 准入过滤器（主区 99%）
  · 新数据先进 Window（小 LRU）——吸收突发
  · 挤出 Window 的候选要进主区：必须比"即将淘汰的受害者"更热
    （Count-Min Sketch 记频率：4-bit 计数器极省内存 + 定期老化防历史包袱）
  · 主区再分 protected(80%) / probation(20%)

一句话：新数据不是想进就进——先证明你比被淘汰者更热（抗扫描的本质）
```
## 四、第二讲：多层缓存（L1 Caffeine → L2 Redis → DB）

### 读路径（MultiLevelCache.get 的实现）

```text
请求 → ① L1（Caffeine，~100ns）
        命中 → 直接返回（99% 的请求止步于此）
        未命中 ↓
      ② L2（Redis，~1ms）
        命中 → 回填 L1 → 返回
        未命中 ↓
      ③ DB（~10ms）→ 回填 L2 → 回填 L1 → 返回

MultiLevelCache 的抽象：L2 用 Function/Consumer 注入
  生产：loader = 先 RedisTemplate 再 Repository；l2Put = RedisTemplate.set
  测试：loader = Map.get（零 Redis 依赖，纯 JVM 可测）
```

### 写路径与失效

```text
put：双写 L1 + L2（本实例）
evict：清 L1 + L2（本实例）
★ 其他实例的 L1 谁也够不着 → 脏读（下一节）
```

## 五、多实例脏读：问题与三个解法

### 问题（MultiLevelCacheTest 实测演示）

```text
部署两个实例（A、B），各自有独立 L1，共享 Redis（L2）：

① A、B 都读 key=r1 → 两个 L1 都缓存 v1
② 数据变更：A 更新 → A 的 L1/L2 都是 v2 ✓
③ ★ B 读 r1 → B 的 L1 命中 v1（旧值！）——L2 已是 v2 也救不了
④ 在 B 的 L1 TTL（30s）内，用户会反复读到脏数据

测试断言：instanceB.get = "v1"（脏）→ invalidateLocal 后 → "v2"（一致）
```

### 三个解法（按成本排序）

```text
① 短 TTL（最简单，脏读窗口 = TTL）
   Caffeine expireAfterWrite 30s~60s
   → 接受"最多旧 1 分钟"，适合容忍度高的数据（列表/统计）

② 广播失效（精准，需引入消息机制）
   A 写后 publish "cache-invalidate:report::123"
   B/C subscribe → 收到后 invalidateLocal
   → 本项目已有 Kafka（场景⑥），可用 Kafka 广播；小项目 Redis pub/sub 也够

③ 版本号/时间戳进 key（彻底，改造成本高）
   key = report:v{数据版本}::123 —— 版本变 = 天然换 key，旧值自然过期
   适合强一致要求的核心数据
```

### 本项目的务实选择

```text
当前：@Cacheable Redis 单层（TTL 30min + @CacheEvict）——单实例无脏读问题
若将来多实例 + 上 L1：L1 TTL 收短到 60s + 场景⑥ 的 Kafka 做广播失效
—— 先有问题再上方案，不过度设计
```

## 六、Spring Cache 共存问题（一个应用两个 CacheManager）

```text
Spring 的 @Cacheable 走【唯一的 primary CacheManager】：
  CaffeineCacheManager 和 RedisCacheManager 不能同时当默认

三种取舍：
① 全换 Caffeine：丢失分布式能力（多实例各存一份 + 不共享）→ 单实例小应用可
② 保持 Redis：现状（本项目选择）
③ 多级：自定义 CacheManager 包装（L1+L2，如上面 MultiLevelCache 的思路）
   或用现成方案（jitpack 的 multilevel-cache / jcache 组合）
—— 规范差距里"Caffeine 缺失"的最终建议：先落常量与习惯（T5 式渐进），
   真有热 key 压力再上多级（带脏读预案）
```

## 七、面试速答

```text
Q：有了 Redis 为什么还要本地缓存？
A：网络往返 vs 内存访问差 1 万倍；热 key 压力、可用性兜底。代价是多实例一致性。

Q：Caffeine 为什么快/淘汰算法是什么？
A：W-TinyLFU——Window 吸收突发 + TinyLFU（Count-Min Sketch）频率准入，
   抗扫描污染；命中率显著高于纯 LRU/LFU。

Q：多实例本地缓存不一致怎么办？
A：①短 TTL（脏读窗口=TTL）②写后广播失效（pub/sub / Kafka）
   ③版本号进 key。按数据容忍度选，别过度设计。

Q：本地缓存和 Redis 缓存怎么分工？
A：L1 放"极热 + 容忍短暂脏"的小数据（配置/字典/权限）；
   L2 放"共享 + 较大"的数据；L1 容量小 TTL 短，L2 容量大 TTL 长。
```

## 一句话

```text
本地缓存 = 用"1 万倍的速度差"换"多实例一致性"的复杂度
Caffeine 的 W-TinyLFU 让它尽可能只存"真的热"的数据
多层缓存（L1→L2→DB）读得快，但写失效必须想清楚"别人的 L1"
——先有热 key 压力再上多级，上了就必须配广播失效或短 TTL
```
## 附录：调用链路全景——显式与隐式（2026-11-01 学员问："java 调用关系不明确，什么时间调用什么"）

### 一、MultiLevelCache 内部（显式，顺着代码读）

```java
cache.get("r1")
  ① l1.getIfPresent("r1")            Caffeine ~100ns
  ② loader.apply("r1")               ★ 回调：构造时传入的 lambda 在这一行被执行
       ②-1 redisTemplate.get(...)    Redis ~1ms（L2）
       ②-2 repository.findById(...)  DB ~10ms
  ④ l1.put("r1", value)              回填 L1
```

loader 什么时候执行：不是框架调的——是【构造时你传进去、get() 未命中时这一行调用】。
Function<K,V> 是接口，lambda 是它的实现（回调模式：谁持有函数，谁在需要时调用）。

### 二、真正的隐式：@Cacheable 的代理魔法

```java
@Cacheable(value = "report", key = "#taskId")
public ResearchReportResponse getReportByTaskId(UUID taskId) { ... }   // 方法体无缓存代码
```

真实调用链（运行时织入，源码里不存在）：

```text
调用方: reportQueryService.getReportByTaskId(123)
   ↓ 你拿到的其实是【代理】（类名打印：...$$SpringCGLIB$$0）
代理拦截 → CacheInterceptor.invoke()
   ① 解析注解 → cacheName/key
   ② CacheManager.getCache("report").get("123")     ← 走 Redis
      命中 → 直接返回（方法体【根本不执行】）
   ③ 未命中 → 反射调真实方法（查 DB）
   ④ 结果写缓存 → 返回
```

### 三、隐式调用全景表（以后按这张表追）

| 隐式机制 | 触发方式 | 本项目实例 | 追踪方法 |
|---|---|---|---|
| AOP 代理 | @Cacheable/@Transactional/@Async | 场景⑦缓存/⑤审计/②事务 | debug 看栈帧；搜 XxxInterceptor |
| 回调/函数注入 | 构造传 Function/Consumer/Listener | MultiLevelCache 的 loader | 找"谁 new、传了什么 lambda" |
| 反射注册 | 启动时建映射表 | @GetMapping / MyBatis Mapper | DispatcherServlet 的 HandlerMapping / MapperProxy |
| 事件发布 | publishEvent → @EventListener | （将来缓存广播失效用它） | 搜 @EventListener / publishEvent |
| 生命周期回调 | Bean 阶段自动调 | @PostConstruct / InitializingBean | Bean 生命周期图 |

### 四、完整时间线（启动到请求）

```text
T0 启动：Spring 扫描注解 → 实例化 Bean（MultiLevelCache 的 @Bean 方法在此 new + 传 lambda 定稿）
T1 请求：DispatcherServlet（总入口）→ HandlerMapping 查 @GetMapping 表
       → Controller → Service【代理拦截】→ 显式的 cache.get 三层
```

### 五、三板斧（遇到隐式调用就这么追）

```text
① Debug 断点看调用栈——代理会现形（CglibAopProxy / CacheInterceptor / TransactionInterceptor）
② 打印对象类名——出现 $$SpringCGLIB$$ 就是有代理拦截
③ 按注解搜框架的处理器（命名规律：@Cacheable→CacheInterceptor，@Transactional→TransactionInterceptor）
```

### 一句话

```text
显式的（MultiLevelCache 三层）：顺着 get() 读就是链路；loader 是构造时注入、未命中时触发
隐式的（@Cacheable 等）：调用发生在运行时代理里——断点看栈、打印类名、搜 Interceptor
```
## 附录：CacheInterceptor Debug 实操指南（复习时照着做，约 15 分钟）

> 目标：亲眼验证"@Cacheable 的调用发生在代理里"——
> ① 断点先停在 CacheInterceptor（不是你的方法）② 第二次调用时【方法体根本不执行】
> ③ Redis 里的 key 长什么样 ④ @CacheEvict 的删除时机

### 准备（5 分钟）

```text
① Redis 跑起来（localhost:6379），redis-cli 能连
② backend 正常启动（mvn spring-boot:run 或 IDE F5）
③ 拿一个真实 taskId（数据库里存在的研究任务）
④ （可选）给依赖下源码，debug 能看到 Spring 源码：
   IDEA：Maven 面板 → Download Sources
   VSCode：settings 加 "java.maven.downloadSources": true
```

### 步骤 1：打三个断点

```text
断点 A【你的方法体】：ResearchReportQueryService.java 第 35 行附近
    ResearchReportEntity report = researchReportRepository.findByTaskId(taskId)

断点 B【写路径】：ResearchReportAssemblyService 的 @CacheEvict 方法体第一行

断点 C【框架内部】：Ctrl+双击/@Cacheable 追进 jar → 找 CacheInterceptor 类
    → 在 public Object invoke(MethodInvocation invocation) 第一行打断点
    （搜不到就搜 CacheAspectSupport 的 execute 方法——同一个拦截链）
    ★ jar 里的类也能打断点（IDE 会提示 attach sources，跳过也行，
      断在"反编译视图"同样有效）
```

### 步骤 2：Debug 模式启动应用

```text
IDEA：Run 面板 → Debug 启动 main
VSCode：F5（Spring Boot 启动配置）或 mvn spring-boot:run 后 Remote Attach
```

### 步骤 3：第一次请求（未命中 → 方法体执行 → 写缓存）

```powershell
curl http://localhost:8080/api/reports/{taskId}
```

```text
预期断点顺序：
  ① 停在【断点 C：CacheInterceptor.invoke】← ★ 方法体还没进！
     看 IDE 的 Call Stack（调用栈），从下往上会看到：
       CacheInterceptor.invoke
       CacheAspectSupport.execute
       CglibAopProxy$DynamicAdvisedInterceptor.intercept     ← 代理现形！
       ResearchReportQueryService$$SpringCGLIB$$0.getReportByTaskId
       ResearchTaskController.getReport
       ...DispatcherServlet 一路往上
     ↑ 这就是"你拿到的 Service 是代理"的实证

  ② F5 放行 → 停在【断点 A：方法体】← 未命中才会进这里
  ③ F5 放行 → 响应返回

请求后看 Redis：
  redis-cli > KEYS report::*
  → 1) "report::<你的taskId>"      ← key 出现了
  redis-cli > TYPE report::<taskId>   → string（JSON 内容）
```

### 步骤 4：第二次请求（命中 → 方法体【不执行】）★ 最有说服力

```powershell
curl http://localhost:8080/api/reports/{taskId}   # 同一个 taskId 再发一次
```

```text
预期：
  ① 又停在【断点 C：CacheInterceptor.invoke】✓（拦截器每次都工作）
  ② F5 放行后——【断点 A 不会再触发！】
     响应直接从缓存返回
     ↑ 这就是"命中时方法体根本不执行"的铁证
```

### 步骤 5：写路径验证 @CacheEvict

```powershell
curl -X PUT http://localhost:8080/api/reports/{taskId}/confirm   # 触发报告重建（写路径）
```

```text
预期：
  ① 停在【断点 B】（写方法体）
  ② CacheInterceptor 走 evict 分支 → Redis 里 report::<taskId> 被删除
     （redis-cli KEYS report::* 确认消失）
  ③ 再 GET 一次 → 又从未命中开始（重建缓存）——完整闭环
```

### 观察点总结表（对照记忆）

| 操作 | CacheInterceptor | 你的方法体（断点 A） | Redis |
|---|---|---|---|
| 第 1 次 GET | ✓ invoke | ✓ 进（未命中→反射调用） | 写入 report::key |
| 第 2 次 GET | ✓ invoke | **✗ 不进**（命中短路） | 不变 |
| 写路径 | ✓ evict 分支 | ✓ 进 | key 被删 |

### 常见问题

```text
Q：断点 C 打不上（类找不到）？
A：依赖源码没下载——IDEA：Maven 面板 Download Sources；
   VSCode：加 "java.maven.downloadSources": true 后 Reload。

Q：根本不停断点 C？
A：@EnableCaching 没生效或缓存管理器不是 Redis——
   在 Service 里 System.out.println(getClass()) 看是不是 $$SpringCGLIB$$；
   不是代理 = 注解没被处理（查 spring-boot-starter-cache / spring.cache.type=redis）。

Q：第二次还进了断点 A？
A：key 没对上——看 redis-cli 里实际 key，
   和 SpEL "#taskId" 求值结果是否一致（taskId 传的类型要匹配）。

Q：步进时跳进一堆框架代码晕了？
A：用 Step Over（F8/F10）只在关键行停；想跳过框架用 Run to Cursor。
```

### 一句话

```text
三个断点（拦截器/方法体/写路径）+ 三次请求（首查/再查/写）=
亲眼看到 代理拦截 → 未命中进方法 → 命中短路 → 写时失效 的全部真相
```