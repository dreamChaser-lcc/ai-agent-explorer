# 09 · 原子操作实战——验证码限流、Redis 事务与 Lua

> **定位**：从"发送验证码"这个真实需求出发，把**原子性**这件事讲透——为什么"分两步写"会出事、Redis 事务为什么救不了、Lua 到底解决了什么
> **前置**：[08 · Spring 实战整合](08·Spring实战整合.md)（先会 `StringRedisTemplate`）
> **配套源码**：[`RedisPlaygroundController`](../../backend/src/main/java/com/aiexplorer/researchagent/api/controller/redisDemo/RedisPlaygroundController.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/controller/redisDemo/RedisPlaygroundController.java)）——本章讨论的 `/api/redis-playground/sms/*` 端点就在这里
> **一句话预告**：能一条命令解决的，绝不用两条；必须两条以上的，用 Lua；**Redis 给不了回滚**。

---

## 0. 起因：两个真实疑问

学习项目里的发送验证码实现时，冒出了两个问题：

1. "每调一次接口都会往 Redis 写一次，实际场景不该是'有缓存就直接返回'吗？"
2. "Redis 也有事务吧？原子性问题用事务就能解决，为什么还要 Lua？Lua 和事务一样吗？"

这两个问题串起来，正好覆盖 Redis 实战里最容易含糊的一块：**状态存储 vs 缓存、原子性、事务边界**。

---

## 1. 先分清：缓存 ≠ 状态存储

项目里有两个长得像、性质完全相反的东西：

| 维度 | `report::<taskId>`（场景⑦ 报告缓存） | `demo:captcha:<phone>`（验证码） |
|---|---|---|
| 本质 | **算出来的结果**（省一次计算） | **业务事实**（记一个状态） |
| 同一个 key 反复写 | 不应该，只在回源后写一次 | **应该**，每次发送都覆盖 |
| 命中后怎么做 | 直接返回缓存值（read-through） | 必须读出来**比对**，不能"有就返回" |
| TTL 的含义 | 缓解数据库压力，过期后回源 | **业务有效期**（5 分钟内有效） |
| 出错的后果 | 性能问题 | **安全事故**（旧码可重复使用） |

### 如果把验证码写成"有缓存就返回"会发生什么

```text
用户点「发送验证码」 → 生成 123456 存入，返回
用户再点一次         → 命中"缓存" → 返回 123456，但短信根本没发出去（提示却说已发送）
用户 5 分钟后点       → TTL 过期，才生成新码

攻击者视角：5 分钟内反复请求都返回同一个码 → 无限次可用
```

所以"发送验证码"的正确语义是**无条件覆盖写**——这正是 `RedisPlaygroundController#sendSmsCode` 现在的写法：

```java
redisTemplate.opsForValue().set("demo:captcha:" + phone, smsCode, Duration.ofMinutes(5));
```

### 判断口诀

> **同一个问题问多次、答案可以一样 → 缓存**
> **每次操作都在改变状态 → 直接操作存储**

验证码、库存、余额、订单状态、分布式锁，全属于第二类。

---

## 2. demo 版的验证码，离生产还差 5 步

| 步骤 | 做什么 | 用什么 | 现状 |
|---|---|---|---|
| ① 发送限流 | 同手机号 **60 秒内只能发一次** | `SET key 1 EX 60 NX` | ✅ 已实现 `/sms/send-guarded` |
| ② 每日上限 | 一个号码一天最多 N 条 | `INCR` + 条件 `EXPIRE` | ✅ 已实现 `/sms/send-daily-limited` |
| ③ 安全随机 | 用 `SecureRandom`，不用 `Math.random()` | `Math.random()` 可预测，安全场景禁用 | demo 用的是后者 |
| ④ 覆盖写入 | 新码覆盖旧码，TTL 5 分钟 | `SET` + `Duration` | ✅ 已有 |
| ⑤ 不返回验证码 | **生产环境绝不把 code 放进响应** | demo 的 `devCode` 字段仅在 dev 存在 | ⚠️ 仅限教学 |
| ⑥ 校验侧加固 | 成功即删除（✅ 已有）+ 错误次数上限 | `verifySmsCode` 已做一次性作废 | 缺错误计数 |

③④⑤⑥ 属于常规工程规范，**①② 才是本章主题**——因为它们涉及原子性，写错了会在并发下静默失效。

> ①② 已落成三个可直接对比的接口（一个裸接口 + 两个受保护版本），实测数据与调用命令见 **§9.1**。

---

## 3. 60 秒发送限流：一条命令就能原子

### ❌ 错误写法：先 SETNX 再 EXPIRE

```java
// 两步操作，中间存在窗口期
Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1");     // 成功
// ← 这里的任何中断（进程崩溃 / 网络断 / Redis 重启未持久化）
redisTemplate.expire(key, Duration.ofSeconds(60));                  // 没执行
// 结果：key 永不过期 → 该手机号被永久限流，再也发不出验证码
```

### ✅ 正确写法：一条命令内嵌过期

```java
// 等价于 SET demo:sms:limit:<phone> 1 EX 60 NX —— 单条命令，原子
Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent("demo:sms:limit:" + phone, "1", Duration.ofSeconds(60));

if (Boolean.FALSE.equals(acquired)) {
    Long remainingSeconds = redisTemplate.getExpire("demo:sms:limit:" + phone);
    return Map.of("sent", false,
                  "message", "操作过于频繁，请 " + remainingSeconds + " 秒后再试");
}
// 走到这里才真正发短信
```

**关键区别**：`setIfAbsent(key, value, timeout)` 底层是 `SET key value EX seconds NX`——**判断、写入、设置过期在一条命令里完成**，不存在"写进去了但没设过期"的中间态。

### redis-cli 现场验证

```bash
SET demo:sms:limit:13800000000 1 EX 60 NX    # 第一次 → OK
SET demo:sms:limit:13800000000 1 EX 60 NX    # 60 秒内第二次 → (nil)
TTL demo:sms:limit:13800000000               # → 剩余秒数，比如 43
```

`(nil)` 就是"已存在、拒绝"的信号——整个过程**没有先 GET 查询**，一条命令完成"检查 + 占用"。

> **前端类比**：这是一次原子的 `if (!map.has(k)) { map.set(k, v) }`——"检查+写入"不可分割。

---

## 4. 每日上限：INCR + 条件过期

这个比 60 秒限流复杂，因为 `INCR` 和 `EXPIRE` 天然是两条命令。

```java
private static final int DAILY_SMS_LIMIT = 5;

String dailyKey = "demo:sms:daily:" + phone;
Long count = redisTemplate.opsForValue().increment(dailyKey);   // INCR，原子自增

// ★ 只有第一次创建这个 key 时才设过期（count == 1）
if (count != null && count == 1L) {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime tomorrowMidnight = LocalDate.now().plusDays(1).atStartOfDay();
    redisTemplate.expire(dailyKey, Duration.between(now, tomorrowMidnight));
}

if (count != null && count > DAILY_SMS_LIMIT) {
    return Map.of("sent", false, "message", "今日发送次数已达上限（" + DAILY_SMS_LIMIT + " 条）");
}
```

### 三个必须理解的细节

**① 为什么只在 `count == 1` 时设过期？**

如果每次 INCR 都刷一次 `EXPIRE`，就变成"以最后一次发送为起点算 24 小时"——用户每发一条，过期时间往后推，**永远到不了"次日 0 点"，每日限制形同虚设**。

**② "次日 0 点"的 TTL 怎么算**

```java
LocalDate.now().plusDays(1).atStartOfDay()       // 明天 00:00:00
Duration.between(LocalDateTime.now(), 那个时刻)    // 现在到明天 0 点还剩多久
```

下午 3 点发第一条 → TTL ≈ 9 小时；晚上 23:59 发 → TTL ≈ 1 分钟。所有计数器的"死亡时间"都对齐在 0 点。

**③ INCR 原子，但 INCR + EXPIRE 这个组合不原子**

极端情况下（INCR 成功后进程崩溃、没走到 EXPIRE），key 会永不过期 → 该号码永久达到上限。彻底解决要靠 Lua，见 §7。

---

## 5. ★ 什么叫"非原子"：三种失效模式

**原子（atomic）= 一组操作对外表现为"要么全部生效，要么全部没发生"，不存在"做了一半"的可见中间状态。**

Redis 的原子性层级：

| 层级 | 原子？ | 原因 |
|---|---|---|
| 单条命令（`SET`/`INCR`/`SETNX`） | ✅ | Redis 单线程执行，命令内部不会被打断 |
| 多条命令的组合 | ❌ | 命令之间可以插进：崩溃、网络断、其他客户端的命令 |

### 形态 1：中断（最直观）

```text
① SETNX demo:sms:limit:138 1   → OK，key 存在，但 TTL = -1（永不过期）
   ← 进程崩溃 / 网络断 / Redis 重启（未持久化）
② EXPIRE demo:sms:limit:138 60 → 没执行
结果：key 永远活着 → 该手机号被永久限流
```

### 形态 2：并发交错（不需要崩溃也会错）

```text
进程 A: INCR daily:138 → 1     ← A 认为是"今天第一条"，准备设过期
进程 B: INCR daily:138 → 2     ← B 认为是第二条，不设过期
进程 A: 崩溃，EXPIRE 没执行
结果：key 从未设置过期 → 计数永不清零
```

**即使没有任何故障**，"判断 == 1"和"设置过期"之间，别的请求也可能已经改掉了计数。

### 形态 3：部分成功（最危险）

```text
扣 A 账户 100 元   ✅
加 B 账户 100 元   ❌ 失败
→ 钱凭空消失
```

在缓存/计数器语境里，表现为"限流永久生效""计数不清零"这类静默故障。

### 一句话记法

> **"先判断、再写入"永远是竞态；"一次写入里带上所有条件"才是原子。**
> Redis 能保证原子的粒度是**一条命令**或**一个 Lua 脚本**。

---

## 6. Redis 事务：MULTI/EXEC 为什么救不了

Redis 确实有事务，但语义和关系型数据库不同。

### 基本用法

```text
MULTI                    # 开始，后续命令只入队（不执行）
SET demo:a 1             # → QUEUED
INCR demo:b              # → QUEUED
EXEC                     # 一次性连续执行，返回结果数组

WATCH demo:a             # 乐观锁：EXEC 前 demo:a 被改过 → 整个事务作废（返回 nil）
DISCARD                  # 放弃事务
```

它保证的是：**队列里的命令连续执行，中间不被别的客户端插队**。

### 硬伤 1：运行时错误**不回滚**

Redis 官方明确声明 "Redis does not support rollbacks"：

```text
MULTI
SET demo:k "abc"         → +QUEUED
LPUSH demo:k "x"         → +QUEUED（语法合法，对 string 做 list 操作会在执行时报错）
EXEC
→ 1) OK
   2) WRONGTYPE Operation against a key holding the wrong kind of value

结果：SET 的效果保留，不会撤销
```

只有"入队阶段就被判非法"的语法错误会让整个事务作废；**执行期错误只影响那一条，其他照常生效**。

> 为什么 Redis 不做回滚？回滚需要额外的日志/快照机制，与"简单、快"的设计目标冲突。Redis 的判断是：**编程错误该在开发期发现，不必靠运行时回滚兜底**。

### 硬伤 2：**不能根据中间结果做判断**（这才是关键）

回到每日上限的需求：

```text
INCR daily:138     → 得到 count
if (count == 1) 才 EXPIRE
```

在 `MULTI` 里做不到——命令是**提前排队**的，执行时看不到前一条的结果，无法写"如果 INCR 结果是 1 那就 EXPIRE"。

能勉强绕过（`GET` 预判 + `WATCH` 乐观锁），但并发时会 `EXEC` 失败需要重试循环，复杂且低效。

---

## 7. Lua：原子块里可以"做判断"

```lua
-- KEYS[1] = demo:sms:daily:<phone>，ARGV[1] = 到次日 0 点的秒数
local count = redis.call('INCR', KEYS[1])
if count == 1 then                 -- ★ 能读到 INCR 的结果再决定
    redis.call('EXPIRE', KEYS[1], ARGV[1])
end
return count
```

Lua 脚本被 Redis 当成**一个命令**执行：原子，且**里面能写 if/else**——这是它和 MULTI 的本质差别。

Spring 里用 `DefaultRedisScript` 执行：

```java
private static final RedisScript<Long> INCR_WITH_EXPIRE_SCRIPT = new DefaultRedisScript<>(
        "local count = redis.call('INCR', KEYS[1]) " +
        "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
        "return count",
        Long.class);

Long count = redisTemplate.execute(INCR_WITH_EXPIRE_SCRIPT,
        List.of("demo:sms:daily:" + phone),
        String.valueOf(ttlSeconds));
```

### 但注意：Lua **也不回滚**

如果脚本里第二个 `redis.call` 报错，第一个的效果同样保留。Lua 解决的不是"回滚"，而是"**在同一个原子块里完成判断 + 多次写入**"。

> 脚本的**原子性**（要么全做要么全不做）和**持久性**（做了会不会丢）是两件事：
> 前者靠单线程执行保证，后者看 `appendfsync` 策略——详见 [03 · 持久化](03·持久化RDB与AOF.md) 第三节「一条命令的完整旅程」。

### 7.1 实战：秒杀扣库存（"读-判断-写"三件套）

**场景**：库存只剩 1 件，两个人同时下单。如果拆成三次往返 Redis：

```text
请求 A: GET stock → 1
请求 B: GET stock → 1              ← 两个请求都看到"还有 1 件"
请求 A: 判断 1 >= 1 → DECRBY → 0
请求 B: 判断 1 >= 1 → DECRBY → -1   ← 超卖！
```

Lua 把三步收进一个原子块（`backend/src/main/resources/lua/deduct_stock.lua`）：

```lua
local stockKey = KEYS[1]
local deductQuantity = tonumber(ARGV[1])

local currentStock = tonumber(redis.call('GET', stockKey))
if currentStock == nil then
    return -1                                              -- 库存未初始化
end
if currentStock < deductQuantity then
    return -2                                              -- 库存不足
end
return redis.call('DECRBY', stockKey, deductQuantity)      -- 扣减并返回剩余库存
```

Java 侧只需一行（`RedisLuaScriptController`）：

```java
Long result = redisTemplate.execute(deductStockScript, List.of(key), String.valueOf(quantity));
```

**实测结果（本项目跑出来的）**：

| 测试 | 结果 |
|---|---|
| 库存 3，连续扣 4 次 | 剩余 2 → 1 → 0，第 4 次返回 `-2`（库存不足） |
| 库存 5，**10 个并发扣减** | 成功 **5** 次、失败 **5** 次（全 `OUT_OF_STOCK`），最终库存 **0**，**零超卖** |

> 端点：`POST /api/redis-lua/stock/init`、`POST /api/redis-lua/stock/deduct`（`dev` / `dev-docker` profile）

### 7.2 脚本怎么组织：内嵌字符串 vs 独立 `.lua` 文件

上面的脚本我写了两种放法，实际项目里两者并存：

| | 内嵌字符串 | 独立 `.lua` 文件 |
|---|---|---|
| 可读性 | 差（拼字符串、无语法高亮） | 好（高亮 + 可 lint） |
| 可否单独测试 | ❌ 只能通过应用调用 | ✅ `redis-cli --eval` 直接跑 |
| 复用 | 只能在定义它的类里 | ✅ 多服务、多模块共享 |
| 改动成本 | 改一行要重新编译打包 | 改资源文件即可（多数情况） |
| Code Review | 夹在 Java 代码里容易漏看 | ✅ 可单独 diff |
| 适合 | **3~5 行的极短脚本** | **10 行以上、含分支逻辑、多处复用** |

**目录约定**：

```text
backend/src/main/resources/
└── lua/
    ├── deduct_stock.lua
    └── （以后：rate_limit.lua、unlock.lua ……）
```

**加载方式**（Spring Data Redis 原生支持，无需额外依赖）：

```java
@Configuration
@Profile({"dev", "dev-docker"})
public class RedisScriptConfig {

    @Bean
    public RedisScript<Long> deductStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/deduct_stock.lua"));
        script.setResultType(Long.class);   // 决定 Lua number → Java Long
        return script;
    }
}
```

**本项目两种写法并存（正好对照）**：

| 端点 | 脚本来源 | 规模 |
|---|---|---|
| `POST /api/redis-playground/sms/send-daily-limited` | 内嵌 `INCREMENT_WITH_DAILY_TTL_SCRIPT` | 3 行 |
| `POST /api/redis-lua/stock/deduct` | `classpath:lua/deduct_stock.lua` | 10 行 + 注释 |

**文件版最大好处：能脱离应用调试**

```bash
redis-cli --eval lua/deduct_stock.lua demo:stock:p9 , 2
#                                  ↑ KEYS        ↑ 逗号   ↑ ARGV
```

### 7.3 Lua 最小语法（前端视角速查）

Redis 脚本用到的 Lua 只有一小部分，够用就行：

| Lua 写法 | 含义 | 前端对应物 |
|---|---|---|
| `local x = 1` | 声明局部变量 | `const x = 1` |
| 不加 `local` | 变成全局变量（污染脚本环境） | 不加 `var/let` 的隐式全局 |
| `tonumber(x)` | 转数字 | `Number(x)` |
| `tostring(x)` | 转字符串 | `String(x)` |
| `redis.call('GET', k)` | 执行一条 Redis 命令 | 同步版 `await redis.get(k)` |
| `redis.pcall(...)` | 同上，但捕获错误不中断脚本 | `try/catch` |
| `nil` | 空值 | `null` / `undefined` |
| `~=` / `==` | 不等于 / 等于（不做类型转换） | `!==` / `===` |
| `#t` | 取表长度 | `arr.length` |
| `..` | 字符串拼接 | `+` |
| `--` / `--[[ ]]` | 单行 / 多行注释 | `//` / `/* */` |

**三个必须记住的差异**：

1. **数组下标从 1 开始**——`KEYS[1]` 才是第一个 key，写 `KEYS[0]` 得到 `nil`
2. **只有一种复合类型 `table`**——数组、对象、队列全是它
3. **没有 `null`，只有 `nil`**

**返回值怎么映射到 Java**（决定 `setResultType`）：

| Lua 返回 | Redis 协议类型 | Java 侧（`setResultType`） |
|---|---|---|
| `return 3` | integer | `Long 3` |
| `return "ok"` | bulk string | `String "ok"` |
| `return {1, 2}` | array | `List<Long>` |
| `return false` / `return nil` | nil | `null` |

### 7.4 去哪学 Lua（省时间版）

| 资源 | 地址 | 用途 |
|---|---|---|
| Redis 官方脚本入门 | `redis.io/docs/latest/develop/programmability/eval-intro/` | 权威：KEYS/ARGV 约定、原子性、集群限制 |
| `EVAL` 命令文档 | `redis.io/docs/latest/commands/eval/` | 返回值映射、超时、`SCRIPT KILL` |
| Programming in Lua（第 1 版，免费在线） | `lua.org/pil/` | Lua 作者写的，公认最好的教程 |
| Lua 5.1 手册 | `lua.org/manual/5.1/` | **Redis 内置的就是 Lua 5.1**，语法以它为准 |
| 菜鸟教程 Lua（中文） | `runoob.com/lua/lua-tutorial.html` | 中文快速入门，2 小时能过一遍 |
| Spring Data Redis 脚本章节 | `docs.spring.io/spring-data/redis/reference/redis/scripting.html` | `DefaultRedisScript` / `RedisScript` 官方用法 |

**只学这 5 点就够用**：

1. `local` 变量 + `if/for` 控制流
2. `table`（数组从 1 开始）
3. `redis.call` 与 `redis.pcall` 的区别
4. `KEYS[n]` / `ARGV[n]` 约定 + 返回值类型映射
5. 原子性边界：单线程执行、`lua-time-limit` 默认 5 秒，超时其他客户端收到 `BUSY`

**不用学**：元表、协程、GC 调优——Redis 脚本场景基本用不上。

---

## 8. 选型决策表

| 需求 | 方案 | 说明 |
|---|---|---|
| 单条件写入（不存在才写、带过期、自增） | **单条命令** | `SET NX EX` / `INCR` / `SETEX`，优先选这个 |
| 需要"读完结果再决定"的多步操作 | **Lua** | 每日上限、库存扣减、分布式锁续期 |
| 一批互不依赖的命令，只为减少 RTT | MULTI/EXEC | 注意不回滚 |
| 版本号校验更新（乐观锁） | WATCH + MULTI | 冲突时重试 |
| 需要真正回滚 | **Redis 给不了** | 靠业务补偿或改用关系库 |

| 能力 | 单条命令 | MULTI/EXEC | Lua |
|---|---|---|---|
| 不被打断（串行） | ✅ | ✅ | ✅ |
| 减少网络往返 | — | ✅ | ✅ |
| **根据中间结果分支** | — | ❌ | ✅ |
| 运行时错误回滚 | — | ❌ | ❌ |

### 一句话区分

> **Redis 事务 = "这一串命令别插队"；Lua = "这一串命令别插队，而且我能边执行边做判断"**。
> 两者都**不提供回滚**——回滚是关系库的能力，Redis 为了性能主动放弃。

> ⚠️ 常见误解：Spring 的 `@Transactional` **管不了 Redis**（它绑定 JDBC/JPA 的事务管理器），加在 Redis 操作上不会回滚。Redis 侧要强一致只能靠 Lua。

---

## 9. 两个限制的组合顺序

顺序会影响业务正确性：

```text
1. SETNX 60 秒锁        ← 先做，轻量、能挡住 90% 的刷
2. INCR 每日计数        ← 通过第 1 步才计数
3. 都通过 → 发短信 + 写验证码
```

**为什么要先 SETNX 再 INCR**：反过来会让每次"60 秒内重复点击"都白白吃掉一次每日额度——用户点 5 次就被锁一整天。

**为什么不担心"SETNX 成功、INCR 超限"**：代价只是 60 秒内不能再点，不消耗额度，可接受。

---

### 9.1 本项目已落地的三个对照接口

上面讲的限制，项目里已经实现成三个端点（`dev` / `dev-docker` 生效），可以直接跑起来对比：

| 端点 | 保护 | 关键参数 | 返回字段 |
|---|---|---|---|
| `POST /api/redis-playground/sms/send` | **无**（对照组） | `phone` | `devCode`（仅教学环境返回） |
| `POST /api/redis-playground/sms/send-guarded` | 窗口限流 | `phone`、`windowSeconds`（默认 60） | `sent` / `remainingSeconds` |
| `POST /api/redis-playground/sms/send-daily-limited` | 每日上限 | `phone`、`dailyLimit`（默认 5） | `todayCount` / `remainingQuota` / `resetInSeconds` |

**实测对比**（连续调用，同一环境）：

```text
① /sms/send ×2                → 两次都成功，devCode 分别 361246、913179（完全没限制）
② /sms/send-guarded ×2        → 第 1 次 sent=true；第 2 次 RATE_LIMITED，remainingSeconds=60
③ /sms/send-daily-limited?dailyLimit=2 ×3
     1: todayCount=1, remainingQuota=1  → 成功
     2: todayCount=2, remainingQuota=0  → 成功
     3: DAILY_LIMIT_EXCEEDED, resetInSeconds=28792（≈ 到次日 0 点）
```

Redis 里对应的 key 与 TTL（同一时刻实测）：

| key | TTL | 说明 |
|---|---|---|
| `demo:sms:limit:<phone>` | 49s | 60 秒窗口，已走 11 秒 |
| `demo:sms:daily:<phone>` | 28780s | 到次日 0 点归零 |
| `demo:captcha:<phone>` | 288s | 5 分钟有效期，被新码覆盖 |

**自己动手验证**：

```bash
# ① 裸接口：连点两次都成功（这就是"没做限流"的样子）
curl -X POST "http://localhost:8080/api/redis-playground/sms/send?phone=13900000001"
curl -X POST "http://localhost:8080/api/redis-playground/sms/send?phone=13900000001"

# ② 窗口限流：第二次直接被拒
curl -X POST "http://localhost:8080/api/redis-playground/sms/send-guarded?phone=13900000002"
curl -X POST "http://localhost:8080/api/redis-playground/sms/send-guarded?phone=13900000002"

# ③ 每日上限：把 dailyLimit 设小，快速看到边界
curl -X POST "http://localhost:8080/api/redis-playground/sms/send-daily-limited?phone=13900000003&dailyLimit=2"

# ④ 想验证 1 秒窗口（不用等 60 秒）：
curl -X POST "http://localhost:8080/api/redis-playground/sms/send-guarded?phone=13900000004&windowSeconds=1"
```

**代码对应**（都在 `RedisPlaygroundController`）：

```java
// ① 窗口限流：SET NX EX 一条命令，原子占用
Boolean acquired = redisTemplate.opsForValue()
        .setIfAbsent(limitKey, "1", Duration.ofSeconds(windowSeconds));

// ② 每日上限：INCR 自增，仅首次创建时设"到次日 0 点"的过期
Long todayCount = redisTemplate.opsForValue().increment(dailyKey);
if (todayCount == 1L) {
    LocalDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay();
    redisTemplate.expire(dailyKey, Duration.between(LocalDateTime.now(), nextMidnight));
}
```

> 两个接口都还留着一个**已知缺口**：`INCR` 与 `EXPIRE` 之间仍非原子（§5 形态 1 描述的场景）。要彻底消除，把这两步换成 Lua —— 思路与 §7 的 `lua/deduct_stock.lua` 完全一致，只是业务从"扣库存"换成"发验证码计数"。

## 10. 常见坑速查

| 写法 | 问题 | 正确写法 |
|---|---|---|
| `SETNX` 然后 `EXPIRE` | 中断/崩溃 → key 永久存在 → 永久限流 | `SET key value EX 60 NX`（`setIfAbsent(key, value, timeout)`） |
| 每次 INCR 都刷新 `EXPIRE` | TTL 无限续期 → 每日限制失效 | **只在 `count == 1` 时设过期** |
| 用 `MULTI` 做"条件性 EXPIRE" | 事务里看不到中间结果，做不到 | Lua 脚本 |
| 以为 `MULTI` 出错会回滚 | 运行时错误不回滚，产生半成品状态 | 用 Lua 保证"判断+操作"同一原子块，或用业务补偿 |
| 用 `Math.random()` 生成验证码 | 伪随机可预测，可被暴力枚举 | `SecureRandom` |
| 把验证码放进 API 响应 | 生产环境等于把验证码告诉任何人 | 只在 dev 返回，或用短信/邮件真实下发 |
| 在 Redis 方法上加 `@Transactional` | Spring 事务管理器不管 Redis | 用 Lua 保证原子性 |

---

## 11. 一句话总结

> **能一条命令搞定的绝不拆成两条；必须两步的用 Lua；`MULTI` 只解决"别插队"，不解决"做判断"和"回滚"。**

验证码这个例子里：

- 60 秒限流 → **一条 `SET NX EX`** 就够了（事务和 Lua 都用不上）
- 每日上限 → **Lua**（因为要看 INCR 的结果再决定 EXPIRE）
- 忘记"原子性只到命令粒度"这件事 → 就是线上最典型的"偶发失效、重启后恢复、查不出原因"的那类 bug
