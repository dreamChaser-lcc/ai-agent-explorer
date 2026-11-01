# T7 · Redisson 分布式锁（技术栈补齐收官）

> **状态**：✅ 完成（2026-11-01，依赖 + 对照实现 + 能力矩阵）
> **前置**：[场景⑨ 并发控制实验](../业务场景实战/⑨并发控制实验.md)（手写 SET NX EX + Lua 四步进化）/ [T5](T5·RedisKey常量化.md)（锁 key 常量）
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单：Redisson 缺失）

---

## 涉及代码文件（点击阅读）

| 文件 | 本课中的作用 |
|---|---|
| [`RedissonTaskLock.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/RedissonTaskLock.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/RedissonTaskLock.java) | ★ 对照实现（app.lock.redisson 默认关） |
| [`DistributedTaskLock.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java) | 手写版（生产在用，场景⑨） |
| [`pom.xml`](../../backend/pom.xml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/pom.xml) | redisson-spring-boot-starter 3.30.0 |

---

## 一、先回顾：手写锁的四步进化（场景⑨ 的成果）

```text
① SET NX          基础互斥，但进程挂了 → 死锁
② SET NX EX       加 TTL 兜底 → 不死锁，但任务超时锁先过期
③ token + Lua     释放校验持有者 → 防误删他人锁
④ TTL 5min        覆盖完整执行 → 但任务真超 5min 还是会并发

四步之后仍缺：任务执行时间不可控时的【续期】、同线程【可重入】、
公平排队、读写分离——这些就是 Redisson 的开箱能力。
```

## 二、手写 vs Redisson 能力矩阵（本课核心）

| 能力 | 手写 DistributedTaskLock | Redisson RLock |
|---|---|---|
| 基础互斥 | SET NX EX ✓ | ✓ |
| TTL 兜底 | 固定 5min ✓ | ✓（默认 30s） |
| token 防误删 | 手写 Lua ✓ | 内置（等价实现） |
| **看门狗续期** | ✗ 超时锁被夺 | ★ TTL 30s，每 10s 自动续 |
| **可重入** | ✗ 同线程再锁失败 | ★ Hash 计数，同线程可重入 |
| 公平锁 | ✗ | fairLock（排队） |
| 读写锁 | ✗ | RReadWriteLock |
| 代码量 | ~150 行（含 Lua） | 调 API，3 行核心 |

## 三、看门狗（watchdog）原理

```text
lock.tryLock(0, -1, TimeUnit.SECONDS)   ← leaseTime=-1 = 启用看门狗

流程：
① 加锁成功：key TTL = 30s（默认 lockWatchdogTimeout）
② 后台定时任务（每 TTL/3 = 10s）检查：这把锁还是我持有的？
   是 → PEXPIRE 续回 30s（任务跑多久锁都不过期）
   否（进程崩溃，续期线程死了）→ 无人续期 → 30s 后锁自动过期 ✓ 兜底仍在
③ unlock() → 取消看门狗 + 删除 key

对照手写版核心痛点：固定 TTL 5min 是拍脑袋猜时长——
猜短了并发击穿、猜长了崩溃恢复慢；
看门狗 = 活着就一直续，死了自然过期——两个问题一起解决。
```
## 四、可重入原理（Hash 结构，为什么手写版做不到）

```text
手写版：SET lock:task:exec:123 token NX EX 300
  → key 只有一个 string——同线程再 SET 会失败（NX）→ 不可重入

Redisson：HSET lock:task:exec:123 <线程标识:UUID> 1   （Hash！）
  同线程再 lock → field 相同 → 计数 +1 = 2（重入成功）
  unlock → 计数 -1 → 1（锁还在！）
  再 unlock → 计数 0 → DEL（真正释放）

使用场景：方法 A 持锁后调用方法 B，B 也需要同一把锁
  （递归/公共方法被持锁路径调用）——手写版会死锁自己
```

## 五、对照实现要点（RedissonTaskLock.java）

```java
@Component
@ConditionalOnProperty(name = "app.lock.redisson", havingValue = "true")  // 默认关
@ConditionalOnBean(RedissonClient.class)
public class RedissonTaskLock {

    public Optional<RLock> tryLock(UUID taskId) {
        RLock lock = redisson.getLock(RedisConstant.lockTaskExec(taskId)); // T5 常量
        boolean ok = lock.tryLock(0, -1, TimeUnit.SECONDS);  // -1 = 看门狗接管
        return ok ? Optional.of(lock) : Optional.empty();
    }

    public void unlock(UUID taskId, RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) lock.unlock();
    }
}

设计取舍：
① 不动生产：手写锁仍是默认路径，Redisson 版开关式并存（渐进式）
② key 复用 T5 的 RedisConstant（两版锁 key 相同，可无缝切换）
③ tryLock(0, -1)：waitTime=0 不等待（与手写语义一致）；leaseTime=-1 启用看门狗
```

## 六、何时选 Redisson vs 手写（务实结论）

```text
选手写：锁场景简单（就任务互斥）、想完全掌控实现、教学/面试深挖原理
选 Redisson：需要可重入/公平/读写锁、任务时长不可控（要看门狗）、
            团队不想维护 Lua、接受额外依赖（netty 一族）

本项目结论：生产保持手写（场景⑨ 已验证、够用）；
Redisson 对照实现开关并存——哪天需要可重入/看门狗，打开开关即换。
```

## 七、面试速答

```text
Q：Redisson 看门狗怎么工作？
A：lock 不指定 leaseTime 时，TTL 默认 30s；后台任务每 10s（TTL/3）检查
   持有状态并续期；进程崩溃续期停止，30s 后自动过期（不会死锁）。

Q：可重入怎么实现？
A：锁用 Redis Hash 存储：field=客户端+线程标识，value=重入计数；
   同线程加锁计数+1，解锁-1，减到 0 才 DEL——Lua 保证原子。

Q：Redisson 和手写 SET NX EX 的本质区别？
A：手写是"一次性命令"，Redisson 是"有生命周期的锁对象"——
   续期、重入、公平性、读写分离都是围绕这个锁对象的持续管理。
```

## 一句话

```text
手写锁 = 你自己搭的四层护栏（NX→EX→token→TTL）
Redisson = 把这些护栏 + 你没搭的（看门狗/可重入/公平/读写）全部交钥匙
本项目：手写为生产默认，Redisson 开关并存——按需切换，互为注脚
```
## 追问：Redis 和 Redisson 是什么关系？为什么有"Redis 分布式锁"又有"Redisson 分布式锁"？（2026-11-01）

### 一句话：Redis 是数据库，Redisson 是操作它的 Java 高级客户端——两把锁底层是同一个 Redis

```text
你的 Java 代码
  ├─ 方案一（手写）：DistributedTaskLock
  │     ↓ StringRedisTemplate
  │   发命令：SET key token NX EX 300 / Lua
  │
  ├─ 方案二（现成）：Redisson RLock
  │     ↓ Redisson（内部也在发 Redis 命令，只是被封装）
  │   发命令：EVAL Lua（加锁/续期/释放）
  │
  └──→ 都到同一个 Redis 服务器（localhost:6379）

Redis 全程只做"存字节 + 执行命令"——它不知道什么是锁；
SET NX 只是"不存在才设置"，锁的语义是命令组合被赋予的。
```

### 类比

```text
Redis          = MySQL（数据库）
Jedis/Lettuce  = JDBC 驱动（能发命令，一切手写）
Redisson       = MyBatis-Plus（驱动之上的高级封装，开箱即用）

分布式锁：
  基于 Redis 手写 = 用 JDBC 手写一套查询
  Redisson 锁     = MyBatis-Plus 里一行 selectById()
  （底层都是 SQL/Redis 命令，封装层次不同）
```

### 两个命令的真实对比（同 key 不同结构）

```text
手写版：    SET lock:task:exec:123 <token> NX EX 300       （String，一次性）
Redisson版：HSET lock:task:exec:123 <线程标识> 1            （Hash，支持可重入）
            + 后台看门狗每 10s PEXPIRE 续期
```

### 命名澄清（面试别说错）

```text
"Redis 分布式锁"不严谨：Redis 本身没有锁功能，只有 SET NX 原语
  → 准确说法：【基于 Redis 实现的】分布式锁（一种方案）

"Redisson 分布式锁"准确：Redisson 库提供的 RLock（该方案的产品化封装）

面试模板：
  "分布式锁我手写过完整实现（SET NX EX + Lua 释放校验 + TTL 兜底），
   也用过 Redisson（看门狗续期、可重入）——理解底层原理，
   所以知道什么时候可以手写、什么时候该换现成库。"
```
## 追问二：项目里为什么看不到 SET NX？锁是怎么实现的？我怎么才能用 SET NX？（2026-11-01）

### 一句话：SET NX 被方法名"翻译"了——setIfAbsent 就是 NX 的英文直译

项目真实代码（DistributedTaskLock.acquire 第一行）：

```java
Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
//                                 ↑↑↑↑↑↑↑↑↑↑↑↑↑ 这就是 SET NX！
```

### 逐词对照：Redis 命令 ↔ Java 方法

```text
Redis 原语：   SET  key  value  NX  EX  300
Java 方法：    setIfAbsent( key,  value,  Duration.ofMinutes(5) )
               set + IfAbsent = 不存在才设置 = SET + NX（NX = Not eXists）
                                        Duration → 自动翻译成 EX/PX
执行时发给 Redis 的真实命令：
  SET lock:task:exec:123 550e8400-... NX PX 300000
```

### 为什么看不到——三层封装（和 JDBC/SQL 同构）

```text
你写的：      setIfAbsent(...)                        业务层
   ↓ Spring Data Redis 参数翻译
驱动：        Lettuce/Jedis → set(key, val, nx, px)   驱动层
   ↓ RESP 协议编码
网络上发的：  "SET" "lock:task:exec:123" "token" "NX" "PX" "300000"
```

### 亲手用 SET NX 的三种方式

```bash
① redis-cli 直接敲（体验原语）：
   SET lock:test token123 NX EX 60     → OK（拿到锁）
   SET lock:test token999 NX EX 60     → (nil)（NX：已存在即失败）
   GET lock:test                        → "token123"
   DEL lock:test                        → 释放

② redis-cli MONITOR（看应用发的真命令，最有说服力）：
   终端1：redis-cli MONITOR
   终端2：触发一次加锁（跑任务）
   终端1 会打印：
     "SET" "lock:task:exec:xxx" "token" "NX" "PX" "300000"     ← 就是它
     "EVAL" "if redis.call('get',...)==ARGV[1]..." ...          ← 释放的 Lua

③ Java：就是项目 acquire 第一行的 setIfAbsent（已经在用）
```

### 顺带：同方法的"释放"解读

```java
redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
// EVAL 执行 Lua：GET 比对 token → 一致才 DEL（两步合成原子）
// 否则 GET 与 DEL 之间锁过期 → 会误删别人的锁（场景⑨ 第③步进化的原因）
```

### 一句话

```text
setIfAbsent(key, value, Duration) ≡ SET key value NX EX/PX <秒>——
方法名是 NX 的英文直译；每一把锁都是这一行 Java 在向 Redis 发 SET NX。
想亲眼看 → redis-cli MONITOR；想亲手摸 → redis-cli 敲 SET ... NX。
```
## 追问三：去哪查 Redis 在 Java 中的方法？（2026-11-01）

### 结构认知：一个 Template，六个操作视图

```java
stringRedisTemplate.opsForValue()   // String 类型（SET/GET/INCR）
stringRedisTemplate.opsForHash()    // Hash（HSET/HGET）
stringRedisTemplate.opsForList()    // List（LPUSH/LPOP）
stringRedisTemplate.opsForSet()     // Set（SADD）
stringRedisTemplate.opsForZSet()    // ZSet（ZADD/ZRANGE）
stringRedisTemplate.delete(k)       // 键级操作（DEL/EXPIRE/TTL）
stringRedisTemplate.execute(script) // Lua（EVAL）

记忆法：Redis 5 种数据类型 → 5 个 opsForXxx() 一一对应
```

### 速查表（Redis 命令 → Java 方法）

| Redis 命令 | Java 方法 |
|---|---|
| SET k v NX EX 60 | `opsForValue().setIfAbsent(k, v, Duration)` |
| SET / GET | `opsForValue().set(k,v)` / `.get(k)` |
| INCR k | `opsForValue().increment(k)` |
| DEL k | `template.delete(k)` |
| EXPIRE / TTL | `template.expire(k, Duration)` / `.getExpire(k)` |
| HSET / HGET / HGETALL | `opsForHash().put / get / entries` |
| LPUSH / RPOP / LRANGE | `opsForList().leftPush / rightPop / range` |
| SADD / SMEMBERS | `opsForSet().add / members` |
| ZADD / ZRANGE | `opsForZSet().add / range` |
| EVAL lua | `template.execute(script, keys, args)` |
| KEYS（生产禁用） | `template.keys(p)` / `template.scan()` |

### 四个查询途径

```text
① IDE 自动补全：opsForValue(). 点号后列全部方法；悬停看 Javadoc（含对应命令）
② 官方 Javadoc：ValueOperations 的 setIfAbsent 原文即 "using NX and EX"
③ Redis 命令表：redis.io/commands（命令 → 反查方法）
④ Spring Data Redis 参考文档：docs.spring.io/spring-data/redis/reference/

Redisson（对象化 API）：redisson.getLock/getMap/getBucket/getRateLimiter
文档：github.com/redisson/redisson/wiki
```