# Redis 使用策略与实战手册

> 基于 AI Agent Explorer 项目，讲解 Redis 的核心概念、使用策略，以及项目内的落地示例。

---

## 一、项目现状

| 项 | 状态 | 位置 |
|---|---|---|
| 依赖 `spring-boot-starter-data-redis` | ✅ 已引入 | `pom.xml` |
| 连接配置 `host/port/password` | ✅ 已预留 | `application.yml` |
| 缓存管理器 + 注解配置 | ✅ 已实现 | `RedisCacheConfig.java` |
| 业务缓存（任务详情） | ✅ 已实现 | `ResearchTaskQueryService.getTaskDetail` |

本地开发默认未启动 Redis；只有连上 Redis 时缓存才生效。dev 模式若未装 Redis，需注意连接失败的处理（详见第八节）。

---

## 二、Redis 是什么、为什么用它

Redis 是一个**基于内存的键值存储数据库**：

- **极快**：读写微秒级，比 MySQL/PG 快几个数量级
- **核心价值**：把"查得多、变得少"的热点数据放进内存，挡住数据库压力，提升响应速度

常见用途：缓存、分布式锁、计数器/限流、会话存储、消息队列。

---

## 三、缓存的核心策略

### 1. 缓存热点数据（最常用）

```
请求 → 先查 Redis（命中直接返回）
        ↓ 未命中
      查数据库 → 结果写入 Redis（设过期时间）→ 返回
```

适用：任务详情、字典/配置、读多写少的数据。

### 2. 缓存三大经典问题及对策

| 问题 | 现象 | 对策 |
|---|---|---|
| **穿透** | 查一个根本不存在的 key，每次都打到数据库 | 空结果也缓存（短过期）；布隆过滤器 |
| **击穿** | 热点 key 过期瞬间，大量请求同时打到数据库 | 热点 key 不过期 / 加互斥锁重建 |
| **雪崩** | 大量 key 同时过期，数据库瞬间被打爆 | 过期时间加随机值，避免集中失效 |

### 3. 分布式锁

多实例同时操作同一数据时，用 `SET key value NX EX` 实现互斥，防止并发重复处理。
适用：防止同一任务被并发重复执行。

### 4. 计数器 / 限流

`INCR` 原子自增，做访问计数、接口限流。
适用：统计步骤执行次数、接口调用频次限制。

### 5. 会话 / 临时状态存储

存登录 token、验证码、临时进度等带 TTL 的短数据。
适用：SSE 进度推送时的临时状态快照。

---

## 四、Spring Boot 中使用 Redis 的两种方式

### 方式 A：声明式注解（简单，推荐入门）

```java
@Cacheable(value = "task:detail", key = "#taskId")   // 查缓存，未命中则执行方法并缓存结果
public TaskDetailResponse getTaskDetail(UUID taskId) { ... }

@CacheEvict(value = "task:detail", key = "#taskId")  // 更新/删除时清缓存
public void updateTask(ResearchTask task) { ... }
```

### 方式 B：直接操作 RedisTemplate（灵活）

```java
@Autowired
private StringRedisTemplate redisTemplate;

redisTemplate.opsForValue().set("key", value, Duration.ofMinutes(10));  // 写
String v = redisTemplate.opsForValue().get("key");                      // 读
```

---

## 五、项目落地实现（已完成的代码）

### 1. 配置类 `RedisCacheConfig.java`

```java
@Configuration
@EnableCaching   // 开启 @Cacheable 等注解
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))                    // 默认 30 秒过期
                .serializeKeysWith(... StringRedisSerializer ...)     // key 用字符串，可读
                .serializeValuesWith(... GenericJackson2JsonRedisSerializer ...) // value 用 JSON
                .disableCachingNullValues();                          // 不缓存 null

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}
```

**为什么用 JSON 而不是默认 JDK 序列化？**
- 默认 JDK 序列化要求对象实现 `Serializable`，且 key 带 `\xAC\xED` 乱码
- JSON 序列化可读、无需改造实体类、方便跨语言/工具查看

### 2. 缓存注解 `ResearchTaskQueryService.java`

```java
@Transactional(readOnly = true)
@Cacheable(value = "task:detail", key = "#taskId")
public TaskDetailResponse getTaskDetail(UUID taskId) { ... }
```

**执行效果：**
- 首次调用：查数据库，结果以 `task:detail::<UUID>` 为 key 存进 Redis
- 30 秒内再次调用：直接读缓存，不碰数据库
- 30 秒后：缓存过期，重新查库

---

## 六、缓存更新的两种策略

### 策略 1：Cache Aside（旁路缓存，最常用）

- 读：先缓存 → 未命中再查库并写缓存
- 写：**先更新数据库，再删除缓存**（不是更新缓存）

```java
public void updateTask(ResearchTask task) {
    researchTaskRepository.save(task);     // 1. 先写库
    // 2. 删缓存（让下次读重新加载，避免缓存与库不一致）
}
```

### 策略 2：Write Through（写穿透）

写入时同步更新缓存，保证缓存与库一致，但写性能略降，适合一致性要求高的场景。

---

## 七、缓存一致性要点

- **先更新库、再删缓存**，顺序不能反（反了会读到脏数据）
- 删除缓存用 `@CacheEvict`，比手动 `redisTemplate.delete` 更简洁
- 过期时间（TTL）是兜底：即使漏删，数据到点也会失效重载

---

## 八、注意事项与坑

1. **dev 模式未装 Redis**：本地 H2 开发未启动 Redis 时，缓存连接会失败。两种处理：
   - 本地装 Redis 并启动（默认 `localhost:6379`）
   - 或 dev 配置临时关闭缓存（Spring Cache 退化为不缓存，但需避免连接报错）
2. **TTL 要贴合数据变化频率**：任务详情状态变化快，设短 TTL（30 秒）；字典类数据可设长 TTL
3. **不缓存 null**：`disableCachingNullValues()` 防止缓存穿透放大
4. **序列化一致性**：key/value 序列化方式一旦上线，不要随意改（会导致旧缓存读不出来）
5. **缓存只适合读多写少**：写频繁的数据用缓存反而增加复杂度

---

## 九、本项目可继续扩展的缓存场景

| 场景 | 建议 |
|---|---|
| 任务详情 | ✅ 已实现 `@Cacheable` |
| 任务列表摘要 | 可加 `@Cacheable`，但列表会随新任务变化，需配合失效 |
| 步骤执行记录 | 读多写少，可缓存 |
| 报告内容 | 生成后基本不变，适合长 TTL 缓存 |
| 分布式锁 | 防任务并发重复执行（进阶，需引入 Redisson 或手写 Lua） |

---

## 十、一句话总结

- **Redis = 内存缓存，把热点数据从数据库搬进内存**
- **读缓存：`@Cacheable`；删缓存：`@CacheEvict`**
- **更新顺序：先写库、再删缓存**
- **TTL 是兜底，序列化用 JSON 更友好**
- **缓存只适合读多写少的数据**
