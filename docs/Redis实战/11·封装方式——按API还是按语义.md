# 11 · 封装方式——按 API 还是按语义

> **定位**：从一段真实的企业 `RedisUtils` 说起，讲清「按 API 封装」与「按语义封装」的差别，并给出可直接落地的代码
> **前置**：[08 · Spring 实战整合](08·Spring实战整合——初学者实战篇.md)、[09 · 原子操作](09·原子操作实战——验证码限流与Lua.md)
> **本项目正例**：[`DistributedTaskLock`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)（按语义封装锁）、`MultiLevelCache`（按语义封装缓存）、[`deduct_stock.lua`](../../backend/src/main/resources/lua/deduct_stock.lua)（原子操作走脚本）
> **一句话预告**：`RedisUtils` 的价值是「统一」，不是「更好用」；它用「吞异常 + 非原子组合」换来的便利，在锁和限流场景是定时炸弹。

---

## 0. 起因：一段真实的企业 `RedisUtils`

很多项目里都有这么个类（简化后的骨架，问题都保留着）：

```java
public class RedisUtils {
    private final RedisTemplate<String, Object> redisTemplate;   // 注意：不是 StringRedisTemplate

    public boolean set(String key, Object value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            logger.error(e.toString(), e);
            return false;                    // ← 坑 1：吞异常
        }
    }

    public boolean hsset(String key, Map<Object, Object> map, Long time) {
        try {
            redisTemplate.opsForHash().putAll(key, map);   // ← 坑 2：写
            if (time != null && time > 0) {
                expire(key, time);                          //    再设过期，非原子
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getExpire(String key) {
        return redisTemplate.getExpire(key);   // ← 坑 3：Long 拆箱可能 NPE
    }

    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }
}
```

它出现的年代、动机、以及为什么现在看来越来越不合身，是这一篇要讲清的事。

---

## 1. 它其实做了三件事

```text
RedisTemplate<String,Object>
        ↓  包一层
RedisUtils
  ① 每个方法套 try-catch + 日志，失败返回 false / 0 / null
  ② 提供业务友好的重载（set 带 TTL、getInteger 带默认值、hsset 写 Hash 带过期）
  ③ 收敛用法（TTL 统一秒、key 为 null 直接返回 null、返回值统一成基本类型）
```

**注意它包的是 `RedisTemplate<String, Object>` 而不是 `StringRedisTemplate`**——前者能直接存 Java 对象（靠 JSON 或 JDK 序列化），后者只能存字符串。

---

## 2. 七个真实动机（为什么企业愿意多这一层）

| # | 动机 | 说明 |
|---|---|---|
| 1 | **序列化策略收口** | `RedisTemplate<String,Object>` 必须显式配序列化器。若各 Service 各自 `new` 一个 Template 并各配一套，会出现「A 写进去的 B 读不出来」。封装是**强制统一**的手段 |
| 2 | **异常降级** | 原生 API 抛 `RedisConnectionFailureException`。Redis 一挂，所有调它的接口全 500。封装后返回 `false`，业务能「缓存失败就走数据库」 |
| 3 | **TTL 单位统一** | 全项目统一「秒」。避免有人传 `30`（以为秒）有人传 `30000`（以为毫秒），TTL 差 1000 倍 |
| 4 | **组合操作** | `set(key, value, time)`、`getInteger(key, default)` 是业务高频，原生要两行 |
| 5 | **可替换 / 可测试** | 业务依赖自定义接口而非 Spring 具体 API：换客户端、加埋点、单测 mock 都只改一处 |
| 6 | **新人友好** | `set / hset / lGet` 比 `opsForHash().putAll(...)` 直观 |
| 7 | **历史包袱** | Boot 1.x 时代 `StringRedisTemplate` 只能存字符串，存对象必须 `RedisTemplate<String,Object>` + 自定义序列化，于是各项目都长出了这个 Utils，一代代传下来 |

---

## 3. 七个坑（逐条对应到代码）

| 坑 | 代码位置 | 后果 |
|---|---|---|
| **吞异常** | 几乎所有方法 `catch (Exception e) { log; return false }` | 缓存写失败**静默**返回 false，而调用方通常不看返回值 → 问题被永久隐藏 |
| **「写 + 过期」非原子** | `hsset` = `putAll` 再 `expire` | 中间失败 → **永久 key**（与 09 篇的 INCR+EXPIRE 是同一个坑） |
| **`getExpire` 拆箱** | `return redisTemplate.getExpire(key)` | key 不存在时可能返回 null → **NPE** |
| **泛型逃逸** | `RedisTemplate<String,Object>` → `opsForHash()` 的 field 也是 `Object` | 类型不安全，field 拼错也能编译 |
| **缺原子能力** | 没有 `setIfAbsent`、没有带 TTL 的 INCR、没有 Lua 入口 | 做锁/限流还得绕过它直接注入 Template → **封装成了障碍** |
| **命名混乱** | `hashKey` / `hHashKey` / `sHashKey` 三种前缀；`hsset`、`haIncr`、`haDenr`、`lRomve` | 明显是复制粘贴手工维护、没有 review |
| **无意义注解** | `@SuppressWarnings("unchecked")` 挂在 `del` 上 | 方法里根本没有泛型转换 |

---

## 4. 两种封装哲学

| 维度 | 按 API 封装（RedisUtils） | 按语义封装（CacheHelper / LockHelper） |
|---|---|---|
| 命名依据 | Redis 的数据结构（string/hash/set/zset/list） | **业务动作**（读缓存、抢锁、扣库存） |
| 失败处理 | 一律吞掉返回 false | **按语义分流**：缓存可降级、锁必须抛 |
| 原子性 | 组合操作混在一起（非原子） | 需要原子 → 单命令或 Lua，封装里显式说明 |
| 类型 | `Object` 泛型到处跑 | 各自明确（`Optional<T>` / `boolean` / `long`） |
| 调用方可读性 | 「我调了什么命令」 | 「我在做什么业务」 |
| 演进 | 改一处影响全项目 | 各自独立演进 |

> 前端类比：`RedisUtils` ≈ 一个包了所有 HTTP 方法的 `request.ts`；`CacheHelper` / `LockHelper` ≈ 按领域拆的 `userApi.ts` / `orderApi.ts`。后者更好维护，原因是一样的。

---

## 5. 代码对照

### 5.1 反面：`RedisUtils`（精简版，坑已标注）

```java
/** ⚠️ 反面示例：仅用于对照，不建议在新项目使用 */
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 坑 1：吞掉所有异常，调用方拿到 false 也不知道是网络问题还是序列化问题 */
    public boolean set(String key, Object value, long seconds) {
        try {
            if (seconds > 0) {
                redisTemplate.opsForValue().set(key, value, seconds, TimeUnit.SECONDS);
            } else {
                redisTemplate.opsForValue().set(key, value);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 坑 2：putAll 与 expire 是两条命令，中间失败会留下永久 key */
    public boolean hsset(String key, Map<Object, Object> map, Long seconds) {
        try {
            redisTemplate.opsForHash().putAll(key, map);
            if (seconds != null && seconds > 0) {
                redisTemplate.expire(key, seconds, TimeUnit.SECONDS);   // 非原子
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 坑 3：返回 long，内部 Long 为 null 时直接 NPE */
    public long getExpire(String key) {
        return redisTemplate.getExpire(key);
    }

    /** 坑 4：Object 泛型，调用方要自己强转，转错只在运行时炸 */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }
}
```

### 5.2 正面一：`CacheHelper`——缓存允许降级

```java
/**
 * 缓存专用封装（语义：读缓存 / 写缓存 / 失效缓存）。
 *
 * <p>设计要点：
 * ① 任何 Redis 异常都降级为「未命中」，绝不打断业务——缓存本来就是可丢的；
 * ② 值统一存 JSON，读取时反序列化到目标类型，类型安全；
 * ③ 只暴露缓存语义，不暴露 hash/list 等结构操作（那些属于具体业务）。</p>
 */
@Component
public class CacheHelper {

    private static final Logger log = LoggerFactory.getLogger(CacheHelper.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CacheHelper(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /** 读缓存：未命中或读失败都返回 empty，调用方回源 */
    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, type));
        } catch (Exception e) {
            // 读失败 = 降级为未命中（这里没有业务异常可吞，只是缓存不可用）
            log.warn("[cache] 读取失败，降级回源 key={}", key, e);
            return Optional.empty();
        }
    }

    /** 写缓存：失败只记日志，不影响主流程 */
    public void put(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("[cache] 写入失败 key={}", key, e);
        }
    }

    /** 失效缓存：失败也不要紧，有 TTL 兜底 */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("[cache] 删除失败 key={}", key, e);
        }
    }
}
```

### 5.3 正面二：`LockHelper`——锁禁止降级

```java
/**
 * 分布式锁封装（语义：抢锁 / 释放锁）。
 *
 * <p>与 CacheHelper 的关键区别：<b>不吞异常</b>。
 * 锁服务不可用时如果静默返回 false，业务会误判为「别人正持有锁」而跳过执行，
 * 表现为「任务莫名其妙不跑」——比直接报错难排查得多。
 * 所以这里让异常向上冒泡，由调用方决定重试 / 告警 / 降级为单机锁。</p>
 */
@Component
public class LockHelper {

    private final StringRedisTemplate redisTemplate;

    public LockHelper(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试抢锁（不等待）。
     *
     * @return true=抢到；false=别人持有
     * @throws org.springframework.dao.DataAccessException Redis 不可用（故意不捕获）
     */
    public Optional<String> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    /** 释放：只删自己持有的锁（token 校验走 Lua，见 09 篇） */
    public void unlock(String key, String token) {
        // 具体实现见项目 DistributedTaskLock：Lua 比对 token 再 DEL
    }
}
```

### 5.4 正面三：`RedisKeys`——key 命名收口

```java
/**
 * key 命名唯一出口（语义：一个业务概念一个方法）。
 * 好处：改前缀只改这里；key 拼错在编译期就能发现。
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 场景⑦：任务报告缓存 */
    public static String report(UUID taskId) {
        return "report::" + taskId;
    }

    /** 场景⑨：任务执行锁 */
    public static String taskExecLock(UUID taskId) {
        return "lock:task:exec:" + taskId;
    }

    /** 场景⑩：定时清理锁 */
    public static String cleanupLock() {
        return "lock:cleanup";
    }

    /** 演示：库存 */
    public static String stock(String productId) {
        return "demo:stock:" + productId;
    }
}
```

### 5.5 正面四：`AtomicStockOps`——原子操作要有自己的入口

```java
/**
 * 原子操作封装（语义：扣库存）。
 *
 * <p>设计要点：失败必须显式——库存不足抛业务异常，
 * 而不是像 RedisUtils 那样返回 false 让调用方猜。</p>
 */
@Component
public class AtomicStockOps {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> deductStockScript;   // classpath:lua/deduct_stock.lua

    public AtomicStockOps(StringRedisTemplate redisTemplate,
                          @Qualifier("deductStockScript") RedisScript<Long> deductStockScript) {
        this.redisTemplate = redisTemplate;
        this.deductStockScript = deductStockScript;
    }

    /**
     * 扣减库存（原子）。
     *
     * @return 扣减后的剩余库存
     * @throws OutOfStockException    库存不足（脚本返回 -2）
     * @throws IllegalStateException  库存未初始化或脚本无返回（属于程序错误）
     */
    public long deductStock(String productId, long quantity) {
        Long result = redisTemplate.execute(deductStockScript,
                List.of(RedisKeys.stock(productId)), String.valueOf(quantity));

        if (result == null) {
            throw new IllegalStateException("扣减脚本无返回，检查 Redis 连接");
        }
        if (result == -1L) {
            throw new IllegalStateException("库存未初始化: " + productId);
        }
        if (result == -2L) {
            throw new OutOfStockException(productId);
        }
        return result;
    }
}
```

### 5.6 两者差异一览

| 维度 | `RedisUtils`（按 API） | `CacheHelper` / `LockHelper` / `AtomicStockOps`（按语义） |
|---|---|---|
| 失败怎么办 | 一律 `return false` | 缓存降级 / 锁抛异常 / 库存抛业务异常 |
| 返回值类型 | `Object`、`long`（可能 NPE） | `Optional<T>`、`long`、异常 |
| 原子性 | 组合操作埋坑 | 单命令或 Lua，注释写明 |
| key 从哪来 | 调用方随手拼字符串 | `RedisKeys.xxx()` 统一出口 |
| 新人理解成本 | 需要懂 Redis 命令 | 看方法名就懂业务 |
| 加监控埋点 | 改一处影响全项目 | 各封装独立演进 |

---

## 6. 什么时候该封装（决策表）

| 判断 | 结论 |
|---|---|
| 项目里 15+ 处直接调 `opsForXxx()`，已出现 TTL 单位混用、序列化不一致 | ✅ **该封装**，但要封在基础设施层、**按语义分** |
| 只有 3~5 处调用 | ❌ 直接注入 `StringRedisTemplate`，多一层抽象只是噪音 |
| 想「存对象自动 JSON」 | ✅ 封装有意义（`RedisTemplate<String,Object>` + Jackson，或 CacheHelper） |
| 做锁 / 限流 / 幂等 | ❌ 绝不塞进通用 Utils —— 这类必须**显式失败** |
| 想「少写代码」 | ❌ 用封装隐藏原子性细节（`hsset` 的两步写）只会埋坑 |
| 想统一埋点（命中率、慢命令） | ✅ 封装是唯一能收口的地方 |

---

## 7. 本项目当前的做法（对照）

| 组件 | 封装方式 | 说明 |
|---|---|---|
| `DistributedTaskLock` | **按语义** | 方法叫 `tryLock(UUID)` / `unlock(UUID, token)`，不暴露 Template |
| `MultiLevelCache` | **按语义** | 本地 + Redis 两级，对外只有 `get/put/evict` |
| `RedisPlaygroundController` | 直接注入 Template | 教学用途，故意展示原生 API |
| `RedisLuaScriptController` | 直接注入 Template + 脚本 Bean | 对照内嵌 vs 文件版 |
| key 命名 | `RedisConstant` 常量类 | 与 `RedisKeys` 同思路 |

没有 `RedisUtils` 那种大而全的工具类——因为**每个使用点都清楚自己要什么语义**。

---

## 8. 老项目已有 `RedisUtils` 怎么渐进改造

```text
第 1 步（零风险）：新代码不再往 Utils 里加方法，改用 CacheHelper / LockHelper
第 2 步（优先迁危险区）：把锁 / 限流 / 幂等 三类调用从 Utils 迁到语义封装
        —— 这三类最怕「吞异常」，也最容易被误用
第 3 步（最后收拾）：Utils 剩下的纯缓存方法收敛成 CacheHelper，
        key 拼接统一挪进 RedisKeys，然后删掉 Utils
```

**不要一次性重写**：`RedisUtils` 往往被几十处调用，批量替换的风险远大于收益。

---

## 9. 面试速答

```text
Q：为什么不直接用 RedisTemplate，要封装一层？
A：为了统一——序列化策略、TTL 单位、异常处理、key 命名、监控埋点。
   但封装应按【业务语义】（CacheHelper/LockHelper）而不是按【Redis 数据结构】（RedisUtils）。

Q：RedisUtils 吞异常有什么问题？
A：缓存场景可以接受（降级回源），但锁/限流/幂等场景会把「Redis 不可用」
   伪装成「业务条件不满足」，表现为任务莫名不跑、限流莫名不生效，极难排查。

Q：hsset(key, map, time) 这种「写 + 过期」有什么风险？
A：putAll 与 expire 是两条命令，中间失败会留下永不过期的 key，内存慢慢被吃掉。
   要么用 Pipeline/Lua，要么接受 TTL 由业务自己兜底。
```

## 10. 一句话

> **`RedisUtils` 解决的是「统一」，`CacheHelper` / `LockHelper` 解决的是「语义清晰 + 失败可控」。**
> 前者让所有人写法一致，后者让每个人知道失败意味着什么——生产系统里，后者更重要。
