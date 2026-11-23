# 08 · Spring 实战整合（前端视角）

> **定位**：01~07 讲的是"原理和面试"；本篇讲"**在 Spring 里怎么用、项目里怎么落**"——按前端工程师的习惯，每个概念都配前端类比 + 可运行代码 + 项目真实场景。
> **前置**：不需要任何 Redis 基础（01~07 可以之后再读）
> **创建**：2026-11-13
> **关联**：[场景⑦·报告查询提速Redis缓存](../业务场景实战/⑦报告查询提速Redis缓存.md) / [场景⑨·并发控制实验](../业务场景实战/⑨并发控制实验.md) / [场景⑩·定时清理](../业务场景实战/⑩定时清理.md)

---

## 目录

- [0. 学习地图：一张表看懂"概念 ↔ Spring ↔ 项目场景"](#0-学习地图一张表看懂概念--spring--项目场景)
- [1. 第 0 步：把 Redis 跑起来](#1-第-0-步把-redis-跑起来)
  - [1.1 一条命令（Docker）](#11-一条命令docker) ｜ [1.2 项目的连接配置](#12-项目的连接配置)
- [2. 第 1 课：`StringRedisTemplate`——你的"JS 对象操作台"](#2-第-1-课stringredistemplate你的js-对象操作台)
  - [2.1 它是什么](#21-它是什么) ｜ [2.2 五大类型 × 前端类比 × Spring API](#22-五大类型--前端类比--spring-api) ｜ [2.3 可运行片段](#23-可运行片段每类-3-行) ｜ [2.4 实战：真实场景怎么用（演示控制器）](#24-实战这些类型在真实场景里怎么用含可运行的演示控制器)
- [3. 第 2 课：TTL——会自动消失的"临时变量"](#3-第-2-课ttl会自动消失的临时变量)
  - [3.1 用法](#31-用法) ｜ [3.2 项目的两处 TTL](#32-项目的两处-ttl) ｜ [3.3 回链概念课](#33-回链概念课)
- [4. 第 3 课：实战①——给接口加缓存（场景⑦，共 4 步）](#4-第-3-课实战①给接口加缓存场景⑦共-4-步)
  - [4.1 打开缓存开关](#41-第-1-步打开缓存开关) ｜ [4.2 声明 Redis 后端](#42-第-2-步声明用-redis-当缓存后端) ｜ [4.3 两个注解](#43-第-3-步两个注解读缓存--写失效) ｜ [4.4 自定义序列化](#44-第-4-步自定义序列化-初学者必踩的坑)
  - [4.5 ★ 深挖：序列化会"丢"什么？](#45--深挖序列化会丢什么js-心智-vs-java-心智2026-11-13-补) ｜ [4.6 运行时验证](#46-运行时发生了什么亲手验证) ｜ [4.7 ★ 疑问①：只用 String？缓存什么？](#47--高频疑问①spring-cache-能用五大类型吗缓存的内容到底是什么) ｜ [4.8 ★ 疑问②：真实项目用手写还是注解？](#48--高频疑问②真实项目会显式调-getset-吗还是只用注解)
- [5. 第 4 课：实战②——分布式锁（场景⑨ / ⑩）](#5-第-4-课实战②分布式锁场景⑨--⑩)
  - [5.1 为什么需要](#51-为什么需要) ｜ [5.2 逐行解读 DistributedTaskLock](#52-逐行解读-distributedtasklock网页版) ｜ [5.3 ★ 疑问：为什么没有 get](#53--高频疑问抢锁只有-setifabsent没有-get它怎么知道有没有锁) ｜ [5.4 调用方套路](#54-调用方怎么用固定套路) ｜ [5.5 概念展开](#55-概念展开)
- [6. 第 5 课：Key 规范——别让 Redis 变成"全局变量垃圾场"](#6-第-5-课key-规范别让-redis-变成全局变量垃圾场)
- [7. Redis 的四大角色：存储 / 协调 / 广播 / 计算](#7-redis-的四大角色存储--协调--广播--计算2026-11-14-补)
  - [7.1 总览](#71-总览一张表看懂四大角色) ｜ [7.2 广播型](#72-广播型消息通信进程之间喊话) ｜ [7.3 计算型](#73-计算型边存边算数据进去统计结果出来) ｜ [7.4 决策总表](#74-需求--角色--实现决策总表) ｜ [7.5 项目角色地图](#75-项目角色地图现状--扩展方向)
- [8. 概念课导航（01~07 什么时候读）](#8-概念课导航0107-什么时候读)
- [9. 动手实验清单（按顺序做）](#9-动手实验清单按顺序做)
- [10. 常见坑速查](#10-常见坑速查)
- [11. 一句话总结](#11-一句话总结)

---

## 0. 学习地图：一张表看懂"概念 ↔ Spring ↔ 项目场景"

| Redis 概念 | Spring 里的样子 | 项目场景 | 本篇 |
|---|---|---|---|
| String 类型 | `StringRedisTemplate.opsForValue()` | 分布式锁的 token | §2 |
| TTL 过期 | `set(key, value, Duration)` | 锁 5 分钟自动释放 / 缓存 30 分钟 | §3 |
| SET NX EX（原子抢占） | `setIfAbsent(key, token, ttl)` | 场景⑨ 任务执行互斥 | §5 |
| Lua 脚本（原子复合操作） | `redisTemplate.execute(script, ...)` | 锁的"校验后释放" | §5 |
| 缓存读写 | `@Cacheable` / `@CacheEvict` | 场景⑦ 报告查询提速 | §4 |
| 缓存序列化 | `Jackson2JsonRedisSerializer` | record 对象存 Redis 的坑 | §4.4 |
| Key 规范 | `RedisConstant` 常量类 | `lock:task:exec:<uuid>` | §6 |
| 持久化 / 单线程 / 淘汰策略 / 主从集群 | 概念层（项目暂未直接触达） | 面试 + 上生产前必读 | §8 导航 |

---

## 1. 第 0 步：把 Redis 跑起来

### 1.1 一条命令（Docker）

```bash
docker run -d --name redis -p 6379:6379 redis:7
```

验证（进入自带的命令行客户端）：

```bash
docker exec -it redis redis-cli
127.0.0.1:6379> SET hello world
127.0.0.1:6379> GET hello
127.0.0.1:6379> TTL hello          # -1 = 永不过期
127.0.0.1:6379> DEL hello
```

**前端类比**：Redis 就是一个**常驻内存的"全局大字典"服务**——`Map<string, string>`，但它是独立进程，能被**多个应用、多台机器共享**（浏览器的 `localStorage` 只属于一个页面域，Redis 是"全公司共用的那份"）。

### 1.2 项目的连接配置

[`application.yml`](../../backend/src/main/resources/application.yml)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml)）里已经配好：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cache:
    type: redis     # 场景⑦：用 Redis 做缓存后端
```

> 两个配置坑（都写在 yml 注释里，初学者必看）：
> ① 本地 Redis 无密码时**不要配空 `password:`**——空串会发 `AUTH ""` 直接报错；
> ② **不要写空的 `redis:` 节点**（只挂注释没键值）——空壳节点会让属性绑定以诡异方式失败。

### 1.3 怎么看 Redis 里的数据（命令速查）

§1.1 那 4 条只是热身。真正开发时最常问的是"**Redis 里现在到底存了什么**"——记住下面这一组就够了（进 `redis-cli` 后直接敲）：

| 目的 | 命令 | 说明 |
|---|---|---|
| key 总数 | `DBSIZE` | 当前 db 有多少个 key |
| 列出全部 key | `KEYS *` | 学习环境够用；**生产禁用**（O(N) 全库扫描，会阻塞） |
| 安全遍历 | `SCAN 0 COUNT 100` | 游标分批迭代，生产推荐（返回新游标 + 一批 key） |
| 过期时间 | `TTL report::xxx` | 剩余秒数；`-1` 永不过期，`-2` 已不存在 |
| 数据类型 | `TYPE report::xxx` | string / hash / list / set / zset |
| 看值 | `GET` 看 string；`HGETALL` 看 hash；`LRANGE key 0 -1` 看 list；`SMEMBERS` 看 set；`ZRANGE key 0 -1 WITHSCORES` 看 zset | **先 `TYPE` 再选读法**，读错类型会直接报错 |
| 各 db 概览 | `INFO keyspace` | 每个 db 的 key 数与带 TTL 的 key 数 |
| 单 key 内存 | `MEMORY USAGE report::xxx` | 字节数（排查大 key 用） |
| 谁在连接 | `CLIENT LIST` | 判断应用是否真的连到**这个**实例 |
| 实时命令流 | `MONITOR` | 看应用正在发什么命令；调试完 `Ctrl+C`，生产禁用 |

**前端类比**：

- `KEYS *` ≈ `Object.keys(obj)`——一次拿全量，key 多时会卡住事件循环，所以生产禁用
- `SCAN` ≈ 分页拉取（`for await` 一页页读），不阻塞
- `TTL` ≈ 给每个 key 配的 `setTimeout` 的剩余时间，`-1` 就是没配定时器
- `TYPE` ≈ `typeof`——Redis 的值有 5 种类型，读之前先确认

#### 一次性查询（不进交互式终端）

```bash
# 容器名以实际为准：compose 环境是 research-redis；按 §1.1 单独 docker run 起的是 redis
docker exec research-redis redis-cli DBSIZE
docker exec research-redis redis-cli KEYS "report::*"
docker exec research-redis redis-cli TTL "report::<你的taskId>"
```

> **Docker Desktop 用户**：打开容器详情页的 **Exec** 标签，输入 `redis-cli` 回车，就得到一个交互式终端，和本地装 `redis-cli` 的体验完全一致（Logs 标签则可以看到 Redis 服务端日志，包括 RDB 落盘记录）。

#### 本项目的 key 长什么样

| key 形态 | 来源 | 典型 TTL |
|---|---|---|
| `report::<taskId>` | 场景⑦ 报告查询缓存（`@Cacheable`） | 30 分钟 |
| `lock:*` | 场景⑨ / ⑩ 分布式锁 | 按业务设定（通常很短） |
| `demo:*` | 教学演示 `/api/redis-playground/**`（§2.4 的控制器） | 5 ~ 10 分钟 |

> `demo:*` 那批每访问一次演示端点就生成一类数据，配合 `KEYS demo:*` 能直观看到五种结构在 Redis 里的真实形态——这是理解"五大类型"最快的方式，比看 API 文档强。

#### ⚠️ 查不到缓存？先确认连的是哪个 Redis

本机若同时存在 **Windows 服务版 Redis**（占 `127.0.0.1:6379`）与 **Docker 的 Redis**，本地 `mvn` 启动的应用连 `localhost:6379` 时会命中前者——此时在 Docker 里查是空的，缓存其实在 Windows 那个实例里。

判断方法：在任一侧执行 `CLIENT LIST`，**能看到应用的连接条目**才说明缓存写在这里。

统一到 Docker（推荐）：管理员 PowerShell 执行

```powershell
sc stop Redis
sc config Redis start= disabled
```

---

## 2. 第 1 课：`StringRedisTemplate`——你的"JS 对象操作台"

### 2.1 它是什么

Spring 提供的 Redis 客户端门面。注入后，所有操作就像**操作一个跨进程共享的大 Map**：

```java
@Component
public class RedisDemo {
    private final StringRedisTemplate redisTemplate;   // ★ Spring Boot 自动装配，直接注入

    public RedisDemo(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }
}
```

**为什么选 `StringRedisTemplate`**：它对 key 和 value 都用**字符串序列化**——你在 `redis-cli` 里看到的就是原文，排查问题直观。（默认的 `RedisTemplate` 用 JDK 序列化，存出来是乱码字节，见 §4.4 的坑。）

#### ★ 高频疑问：名字里的 `String` 是什么意思？只能存字符串吗？

**名字的含义**：`StringRedisTemplate` ≈ `RedisTemplate<String, String>`——`String` 描述的是 **key 和 value 在 Java 侧的类型**（都是字符串），并内置了字符串序列化器。

**破除误区**：它**不是**"只能操作 Redis 的 String 类型"——五大类型（String/Hash/List/Set/ZSet）全能玩。§2.4 的演示控制器就是活证据：注入的是 `StringRedisTemplate`，五种类型全部演示了一遍。

**为什么要有这个专门的类**：直接注入默认 `RedisTemplate` 会用 **JDK 序列化**——存个 `"hello"` 进去，redis-cli 里看到的是二进制乱码（`\xac\xed\x00\x05...`，人类不可读、其他语言也解不开）。`StringRedisTemplate` 换成字符串序列化器——存什么就是什么原文。

三种注入方式对照：

| 注入方式 | key 序列化 | value 序列化 | redis-cli 里 | 适用 |
|---|---|---|---|---|
| `RedisTemplate`（默认） | JDK | JDK | 乱码 ❌ | 不推荐 |
| **`StringRedisTemplate`** | String | String | **原文可读** ✅ | 值为字符串/JSON（本项目用法） |
| 自定义 `RedisTemplate<String, Object>` + JSON 序列化器 | String | JSON | JSON 可读 | 想直接存取 Java 对象 |

**"值是 String" ≠ "只能存文本"**——value 的 Java 类型是 String，但**内容可以是任何东西的字符串形式**：

```java
// 存对象：先转 JSON 字符串（≈ 前端的 JSON.stringify）
redisTemplate.opsForValue().set("user:1001", objectMapper.writeValueAsString(userDto));

// 读对象：JSON 反序列化（≈ JSON.parse）
UserDto user = objectMapper.readValue(redisTemplate.opsForValue().get("user:1001"), UserDto.class);

// 计数器：存的是 "123"，increment 直接返回 long
Long views = redisTemplate.opsForValue().increment("views:article:1");
```

> **前端类比**：`localStorage` 只能存字符串——存对象前 `JSON.stringify`、读出来 `JSON.parse`。`StringRedisTemplate` 就是同一套逻辑。

**想"直接存对象"不手动转 JSON？三条路**：

| 方式 | 做法 | 项目里 |
|---|---|---|
| A. 手动 JSON | 上面那样转换 | 零散的小 key |
| B. Spring Cache | `@Cacheable` + `CacheConfiguration` 的 JSON 序列化器，**框架自动转换** | ★ 场景⑦ 用的就是这个 |
| C. 自定义 RedisTemplate | 配 `GenericJackson2JsonRedisSerializer` | 存取对象多且杂时 |

**一句话**：`StringRedisTemplate` = **"键值都用字符串形式"的 RedisTemplate**；五大类型照玩不误；存对象要么自己转 JSON，要么交给 Spring Cache 的 JSON 序列化器。

### 2.2 五大类型 × 前端类比 × Spring API

| Redis 类型 | 前端心智 | Spring API | 典型场景 |
|-----------|---------|-----------|---------|
| **String** | 一个字段 `map.set/get` | `opsForValue().set / get / setIfAbsent` | token、缓存 JSON、计数器 |
| **Hash** | 一个对象 `{field: value}` | `opsForHash().put / get / entries` | 用户资料（一次读一个字段） |
| **List** | 数组（两端进出） | `opsForList().leftPush / rightPop` | 简易消息队列、最新 N 条 |
| **Set** | `new Set()` | `opsForSet().add / members / isMember` | 黑名单、去重、共同关注 |
| **ZSet** | 带 score 的排行榜 | `opsForZSet().add / incrementScore / reverseRange` | 排行榜、延迟队列 |

### 2.3 可运行片段（每类 3 行）

```java
// ---- String：最常用 ----
redisTemplate.opsForValue().set("greeting", "hello redis", Duration.ofMinutes(10));
String value = redisTemplate.opsForValue().get("greeting");   // "hello redis"

// ---- Hash：像 JS 对象，按字段读写 ----
redisTemplate.opsForHash().put("user:1001", "name", "lcc");
redisTemplate.opsForHash().put("user:1001", "level", "gold");
Object name = redisTemplate.opsForHash().get("user:1001", "name");

// ---- List：简易队列 ----
redisTemplate.opsForList().leftPush("task:queue", "taskId-1");
String first = redisTemplate.opsForList().rightPop("task:queue");   // FIFO

// ---- Set：去重 + 判断 ----
redisTemplate.opsForSet().add("blacklist", "13800000000");
Boolean blocked = redisTemplate.opsForSet().isMember("blacklist", "13800000000");

// ---- ZSet：排行榜（score 排序）----
redisTemplate.opsForZSet().incrementScore("rank:search", "redis", 1);   // 每搜一次 +1
```

> **存对象怎么办**：`StringRedisTemplate` 只存字符串——先把对象 `objectMapper.writeValueAsString(dto)` 转成 JSON 字符串再 `set`，读出来 `readValue` 反序列化。前端视角：**自己负责 `JSON.stringify` / `JSON.parse`**。（用 `@Cacheable` 则由 Spring 的 JSON 序列化器自动做，见 §4.4。）

### 2.4 实战：这些类型在真实场景里怎么用（含可运行的演示控制器）

**先说清楚**：§2.3 的代码不在项目里——它是教学示例，需要你亲手跑起来。为此项目里新增了一个 dev 演示控制器（教学脚手架，`@Profile("dev")` 保证生产不加载，学完可删）：

[`RedisPlaygroundController`](../../backend/src/main/java/com/aiexplorer/researchagent/api/controller/redisDemo/RedisPlaygroundController.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/controller/redisDemo/RedisPlaygroundController.java)）

#### 全部端点速查（7 个）

| 端点 | 演示 |
|------|------|
| `GET /api/redis-playground/string?key=greeting` | String + TTL（返回值里带剩余秒数） |
| `GET /api/redis-playground/hash` | Hash（像 JS 对象按字段读写） |
| `GET /api/redis-playground/list` | List 队列（FIFO） |
| `GET /api/redis-playground/set?phone=...` | Set 去重 |
| `GET /api/redis-playground/zset?keyword=...` | ZSet 排行榜（访问一次 +1 分） |
| `POST /api/redis-playground/sms/send?phone=...` | ★ **验证码发送**（TTL 5 分钟，dev 环境直接返回验证码） |
| `GET /api/redis-playground/sms/verify?phone=...&code=...` | ★ **验证码校验**（过期/错误/一次性 三种路径） |

#### 需求 → 类型 决策表（背这张就够了）

| 你的需求长这样 | 用什么类型 | 真实产品例子 |
|---|---|---|
| 存一个值，可能还要过期 | **String** | 验证码、登录 token、限流计数、缓存 JSON、分布式锁 |
| 一个对象，字段要**单独**改 | **Hash** | 购物车（改商品 A 数量不影响 B）、用户资料 |
| 有先后顺序 / 排队 | **List** | 简易任务队列、保留最新 N 条动态 |
| 去重 / 判断"在不在" | **Set** | 手机号黑名单、文章点赞（一人一赞）、活跃用户去重 |
| 要按分数**排序** | **ZSet** | 搜索热词榜、积分排行榜、延迟任务（score 存时间戳） |

#### 演示端点清单（启动后端后逐个访问，每访问一个就去 redis-cli 执行 `KEYS demo:*` 对照）

| 端点 | 演示类型 | 观察点 |
|---|---|---|
| `GET /api/redis-playground/string?key=greeting` | String | 返回值 + 剩余 TTL（600s 在倒数） |
| `GET /api/redis-playground/hash` | Hash | 返回 `{name=lcc, level=gold}`——像 JS 对象 |
| `GET /api/redis-playground/list` | List | 第 1 次访问 `popped=task-1`（左进右出 = FIFO：先 push 的先出）；第 2 次 `popped=task-2`；队列空后再访问返回 `popped=null` |
| `GET /api/redis-playground/set?phone=13800000000` | Set | 再访问一次同一手机号——集合里依然只有一个（去重） |
| `GET /api/redis-playground/zset?keyword=redis` | ZSet | 多访问几次不同关键词，看 score 排序的 Top 榜 |

> 注意 List 那一行的细节：`leftPush` 从左进、`rightPop` 从右出 → **先 push 的 task-1 会先被 pop**（FIFO 队列）——动手跑一次比背十遍记得牢。

#### ★ 真实场景完整闭环：短信验证码（String + TTL 的最佳教材）

```
用户点"发送验证码"
  → 后端生成 6 位随机码 → SET demo:captcha:<phone> <code> EX 300
      （★ TTL 5 分钟 = "5 分钟内有效"，不需要写任何定时清理代码）
用户输入验证码提交
  → GET demo:captcha:<phone> → 比对
      ├─ key 不存在   → "验证码已过期"（TTL 到点自动删了）
      ├─ 值不一致     → "验证码错误"
      └─ 一致         → DEL 该 key（用后作废，防止重复使用）→ 校验通过
```

端点（`RedisPlaygroundController` 里）：

| 端点 | 说明 |
|---|---|
| `POST /api/redis-playground/sms/send?phone=13800000000` | 生成验证码写入 Redis（TTL 5 分钟），dev 环境直接返回 `devCode` 方便自测 |
| `GET /api/redis-playground/sms/verify?phone=...&code=...` | 校验：过期/错误/通过三种路径全覆盖 |

**五步实验（强烈建议亲手走一遍）**：

```text
① POST /sms/send?phone=138...           → 拿到 devCode
② redis-cli：GET demo:captcha:138...    → 亲眼看它存在；TTL demo:captcha:138... → 剩余秒数在倒数
③ 等 5 分钟再 GET                        → (nil)——TTL 到点自动消失，没写一行清理代码
④ 重新 send，用正确 code 调 verify       → success=true，且 key 被 DEL
⑤ 再 verify 一次                         → "验证码已过期"（一次性使用）
```

> 这一个场景覆盖了 Redis 最核心的三个能力：**快（内存读写）**、**TTL（自动过期）**、**跨进程共享**（验证码在服务端生成，多个请求实例都能读到）。面试里"为什么验证码放 Redis 不放 MySQL"，答这三点即可。

---

## 3. 第 2 课：TTL——会自动消失的"临时变量"

### 3.1 用法

```java
// 写入并同时设置过期时间（原子操作）
redisTemplate.opsForValue().set("captcha:13800000000", "882913", Duration.ofMinutes(5));

// 抢占式写入（不存在才设置 + 过期时间）—— §5 分布式锁的基石
Boolean ok = redisTemplate.opsForValue().setIfAbsent("lock:demo", "token-1", Duration.ofMinutes(5));
```

**前端类比**：`set` 带 TTL ≈ 写 cookie 时带 `max-age`——到点自动消失，**不用你写 setTimeout 去清理**。这个"到点自动消失"是 Redis 最有用的特性之一：验证码、限流计数、分布式锁全靠它兜底。

### 3.2 项目的两处 TTL

| 位置 | TTL | 为什么是这个值 |
|------|-----|---------------|
| 分布式锁（场景⑨） | 5 分钟 | 必须覆盖一次完整任务执行（含多次 LLM 调用，数十秒到几分钟） |
| 报告缓存（场景⑦） | 30 分钟 | 兜底职责——`@CacheEvict` 万一漏了，最多"旧 30 分钟" |

### 3.3 回链概念课

TTL 到期后 Redis 怎么删？为什么不是定时器扫全表？→ 见 [06·过期与内存淘汰](06·过期与内存淘汰.md)（惰性删除 + 定期抽样，两招配合）。

---

## 4. 第 3 课：实战①——给接口加缓存（场景⑦，共 4 步）

**需求背景**：报告查询接口要现场组装大对象（17KB 的 Service），每次都查库 + 计算 → 太慢。目标：**第一次查库，之后 N 次直接读 Redis**；数据重建后**自动失效**。

### 4.1 第 1 步：打开缓存开关

[`ResearchAgentApplication.java`](../../backend/src/main/java/com/aiexplorer/researchagent/ResearchAgentApplication.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/ResearchAgentApplication.java)）：

```java
@EnableCaching   // ★ 打开 Spring Cache——CacheInterceptor 开始上岗
@SpringBootApplication
public class ResearchAgentApplication { ... }
```

### 4.2 第 2 步：声明用 Redis 当缓存后端

`application.yml` 一行：`spring.cache.type: redis`（见 §1.2）。

### 4.3 第 3 步：两个注解（读缓存 / 写失效）

**读路径**——[`ResearchReportQueryService`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportQueryService.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportQueryService.java)）：

```java
@Cacheable(value = RedisConstant.CACHE_REPORT, key = "#taskId")
// ★ 语义：先查缓存，命中 → 直接返回（★ 方法体不执行）；未命中 → 执行方法体，结果自动回填 Redis
public ResearchReportResponse queryReport(UUID taskId) { ... }
```

**写路径**——[`ResearchReportAssemblyService`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java)）：

```java
@CacheEvict(value = RedisConstant.CACHE_REPORT, key = "#taskId")
// ★ 语义：报告重建后，把旧缓存踢掉——下次读自动重建（"写后失效"模式）
public ... assembleReport(...) { ... }
```

**前端类比**：这就是服务端版的 **SWR（stale-while-revalidate）**——读走缓存、写完把缓存作废，下一读自然回源刷新。

### 4.4 第 4 步：自定义序列化（★ 初学者必踩的坑）

光有注解还不行。Spring Cache + Redis **默认用 JDK 序列化**，两个问题：
1. 要求缓存对象实现 `java.io.Serializable`——我们缓存的 `ResearchReportResponse` 是 **record**，直接抛异常；
2. 存进 Redis 的是二进制乱码，`redis-cli` 里没法看。

所以项目里有 [`CacheConfiguration`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)）——**改用 JSON 序列化 + 统一 TTL 30 分钟**：

```java
@Bean
public RedisCacheConfiguration redisCacheConfiguration() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());                        // 支持 OffsetDateTime 等时间类型
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);     // 时间存 ISO 文本（可读）

    Jackson2JsonRedisSerializer<ResearchReportResponse> valueSerializer =
            new Jackson2JsonRedisSerializer<>(mapper, ResearchReportResponse.class);

    return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))                           // ★ TTL 在这里接管
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                    .fromSerializer(valueSerializer));                  // ★ 值用 JSON
}
```

> **前端类比**：JSON 序列化 ≈ `JSON.stringify` / `JSON.parse`——存进去人类可读，跨版本/跨语言友好。这是业界主流，不要用 JDK 二进制序列化。

### 4.5 ★ 深挖：序列化会"丢"什么？——JS 心智 vs Java 心智（2026-11-13 补）

> 灵魂拷问：`JSON.stringify` 在 JS 里会丢方法、丢 `#field`、丢 `undefined`——那 Java 对象序列化进 Redis，会不会也丢东西？**会丢一些，但"方法"恰恰是唯一不用担心丢的。** 这背后是两种语言对象模型的根本差异。

#### 4.5.1 先破最大的心智差异：Java 的方法根本"不在对象里"

| | JavaScript | Java |
|---|---|---|
| 对象本质 | **属性袋**——方法也是属性，可以动态挂上去 | **字段数据 + 指向类元数据的引用** |
| 方法存在哪 | 对象自身属性 / 原型链上（函数也是值） | **类的元数据里**（方法区），所有实例共享一份 |
| 序列化范围 | 遍历对象的自有可枚举属性 | 遍历对象的**字段**（方法压根不在遍历范围） |

```text
JS 的对象 = { name: "lcc", sayHi: function(){...} }   ← 方法是属性之一，stringify 时被跳过 → 丢
Java 的对象 = 字段值们（name="lcc"...） + 一个隐藏指针 → 指向 Class（方法全在 Class 里）
```

**序列化 = 导出"数据快照"（字段值）；反序列化 = 按某个 Class 图纸，把数据填回一个新对象。**

方法属于"图纸"（Class），不属于"快照"（数据）——只要反序列化时图纸还在（JVM 里类一直加载着），方法自动全在。**JS 会丢方法，是它的对象模型决定的；Java 没有这个模型，问题不存在。**

#### 4.5.2 但 JSON 只能表达 6 种东西——这些 Java 类型会"变形/丢失"

JSON 全部表达能力：string / number / boolean / null / array / object。Java 类型系统比它丰富得多，逐项盘点：

| 内容 | JSON 序列化后 | 丢/变形？ | 应对 |
|---|---|:---:|---|
| **方法** | 不参与 | ✅ **不丢**（属于类） | 无需处理 |
| **private 字段** | 通过 getter/反射正常输出 | ✅ 不丢（JS 的 `#field` 才会丢） | 无需处理 |
| **static 字段** | 不参与 | ⚠️ 不存（static 本就是类级别，合理） | — |
| **transient 字段** | 不参与 | ⚠️ 会丢（语义就是"不序列化"） | 知晓即可 |
| **接口/多态字段** | 只写实际值，**类名不写** | ⚠️ **丢类型**——`Animal a = new Dog()` 读回来变 Animal，Dog 特有字段丢失 | `@JsonTypeInfo` 或指定目标类型 |
| **枚举** | 变成名字字符串（`"SUCCESS"`） | ⚠️ 枚举名改了，旧数据就对不上 | 枚举名当不可变对待 |
| **时间类型** | 时间戳或 ISO 文本 | ⚠️ 不注册模块直接抛异常 | `JavaTimeModule`（**项目已配**） |
| **循环引用** | 双方都抛异常 | ⚠️ 默认失败 | `@JsonManagedReference` / `@JsonIdentityInfo` |
| **null 字段** | 输出 `"field": null` | ✅ 不丢 | — |
| **大数（BigDecimal/long）** | 数字 | ⚠️ 跨语言消费时丢精度（JS number 是 double） | 转字符串输出（`ToStringSerializer`） |

#### 4.5.3 重点展开三个

**① 多态字段：JSON 里没有"类型"这个概念**

```java
Animal a = new Dog("旺财", "会接飞盘");     // Dog 有特有字段 skill
objectMapper.writeValueAsString(a);
// → {"name":"旺财"}                        ← skill 字段"丢"了？不是丢，是 Dog 的类型信息没写进 JSON
// 读回来：按 Animal 反序列化 → 变成一个"没有 skill 的 Animal"
```

解法三选一：

| 方案 | 原理 | 适用 |
|---|---|---|
| **指定目标类型**（项目做法） | 读的时候告诉 Jackson"按 Dog.class 填" | ★ 存/读在同一个应用——缓存场景天然满足 |
| `@JsonTypeInfo` + `@JsonSubTypes` | JSON 里内嵌类型标识（`"@type":"dog"`） | 多态对象集合、跨版本 |
| `GenericJackson2JsonRedisSerializer` | 自动内嵌 `@class` 字段 | 懒人版（value 里带类型，可读性略差） |

**② 时间类型：不配模块直接抛异常**

`LocalDateTime`/`OffsetDateTime` 不是 JSON 原生类型——不注册 `JavaTimeModule`，Jackson 序列化时直接抛 `InvalidDefinitionException`（每个团队第一次接 Redis 缓存都会撞一次，项目 `CacheConfiguration` 已配好）。

**③ record：Jackson 原生支持，但更依赖"目标类型"**

record 没有 getter（是 `taskId()` 这样的访问器）——Jackson 通过**构造器绑定**反序列化：必须知道目标类型是哪个 record，才能按构造器参数名匹配填值。这就是 `CacheConfiguration` 里 `Jackson2JsonRedisSerializer<>(mapper, ResearchReportResponse.class)` **第二个参数不可省**的原因。

#### 4.5.4 缓存场景的独特优势：存和读是同一个应用

```text
写入：ResearchReportResponse 对象 → JSON → Redis
读取：Redis → JSON → 按 ResearchReportResponse.class 反序列化
```

- **同一个类、同一份图纸**——往返天然对称，字段不会认错；
- 比"跨语言 API"安全得多（前端拿 JSON 是鸭子类型，字段名对不上就 undefined）。

**唯一真实的残余风险：类结构变更。** 缓存写入后你给 record 加了字段/改了字段名/改了字段类型：

```text
旧缓存 JSON（没有新字段）→ 读回新类 → 新字段 = null/默认值（通常不炸，但数据缺）
字段改了类型（String → Long）→ 反序列化可能直接抛异常
```

兜底（项目已具备）：**TTL 30 分钟**——最坏情况旧数据最多存活 30 分钟自然消失。这是"缓存场景敢于随意序列化"的底气；如果是长期存储，就必须认真做版本管理（key 带版本号 / 兼容旧字段）。

#### 4.5.5 JS / Java 序列化丢失对照表（总复习）

| 内容 | JS `JSON.stringify` | Java Jackson |
|---|---|---|
| 方法/函数 | ❌ **丢**（函数是属性） | ✅ 不丢（方法在类里，不参与序列化） |
| 私有数据 | `#field` 会丢 | ✅ private 字段正常序列化 |
| `undefined` 字段 | ❌ 直接省略 | `null` 字段输出为 `null` |
| 日期对象 | 变 ISO 字符串 | 需 `JavaTimeModule`（项目已配） |
| 循环引用 | 直接抛错 | 默认抛错，需注解治理 |
| Symbol | 丢 | 无此概念（枚举 → 字符串） |
| 大数精度 | ❌ number 精度丢失 | BigDecimal 保留（给前端消费时才要注意） |
| 类型信息 | 本来就没有（鸭子类型） | 默认丢失，**按目标类型补齐**（项目已指定） |

#### 4.5.6 一句话总结

```text
方法不会丢——Java 方法长在类（图纸）上，不在对象（数据）里；
序列化只搬数据，反序列化时按图纸重建，方法自动就位。
真正要警惕的是"JSON 表达不了的东西"：类型信息、时间类型、循环引用、transient——
而项目的 CacheConfiguration（指定目标类型 + JavaTimeModule + JSON）+ 30 分钟 TTL 已把缓存场景的坑填平。
```

#### 4.5.7 延伸：除了 JSON 和二进制，还有别的序列化吗？二进制是怎么"编"出来的？

**家族全景（按产物形态分三类）**：

| 类别 | 成员 | 主要用途 |
|------|------|---------|
| **文本类** | JSON、XML、YAML、CSV、Properties | 人要看的配置/交换/文档 |
| **二进制类** | JDK 序列化、Kryo、Hessian、**Protobuf**、Thrift、Avro、MessagePack、CBOR、FlatBuffers、SBE | 机器对机器、高性能、小体积 |
| **表映射类** | JPA/ORM（对象 ↔ 数据库行）、各类 HashMapper | 对象 ↔ 结构化存储 |

> 前端其实天天在用序列化：`JSON.stringify`、`FormData`（multipart 编码）、URL 查询串（URL 编码）、Cookie 的 `key=value; `；浏览器内部还有**结构化克隆算法**（`postMessage` / `structuredClone`）——能序列化 Map/Set/Date/循环引用，比 JSON 强，但只活在浏览器里。

**二进制序列化的核心原理：三件套**——① 一份约定（schema/注册表/类描述）；② 一套编码规则（类型标签、varint、长度前缀）；③ 两端说同一种方言。

三个代表的三种思路：

| 格式 | 约定放在哪 | 编码思路 |
|------|-----------|---------|
| JDK 序列化 | **写进数据流**（类名、字段签名内嵌） | 自描述，但臃肿，且只能 Java 读 |
| Kryo | **注册表**：类 → 整数 ID | 流里只写"类 ID + 字段值按序"，元数据全省 |
| Protobuf | **独立 schema 文件**（`.proto`） | 流里只写"字段编号 + 值" |

**亲手拆一个字节：JSON vs Protobuf**（同一个对象：`name = "lcc"`，`level = 99`）：

JSON（26 字节，字段名原封不动重复写——体积大的根源）：

```text
7B 22 6E 61 6D 65 22 3A 22 6C 63 63 22 2C 22 6C 65 76 65 6C 22 3A 39 39 7D
{  "  n  a  m  e  "  :  "  l  c  c  "  ,  "  l  e  v  e  l  "  :  9  9  }
```

Protobuf（7 字节）：

```text
0A 03 6C 63 63 10 63
```

逐字节翻译：

```text
0A       → 字段标签：00001 010（字段编号 1；类型 2「长度分隔」）
03       → 长度：3
6C 63 63 → "lcc"（ASCII）
10       → 字段标签：00010 000（字段编号 2；类型 0「变长整数」）
63       → 99（varint 编码：小数字只占 1 字节）
```

两个关键设计：

1. **字段名不写进数据**——`name`、`level` 完全消失，两端靠同一份 `.proto` 知道"编号 1 是 name"；JSON 每条数据都重复写一遍字段名 → 26 字节 vs 7 字节；
2. **兼容性靠编号**：新增字段用新编号，老代码遇到不认识的编号跳过不读 → 平滑演进。

**接回 Redis**：Spring 的序列化统一走 `RedisSerializer<T>` 接口——JSON / Kryo / Protobuf 都有现成实现，插上即生效。效果差异（缓存 100 万个 1KB 对象）：

```text
JSON   → Redis 内存 ≈ 1.2 GB（可读，排障方便）
Kryo/Protobuf → ≈ 0.6 GB（省一半真金白银；代价：不可读 + 切换时旧缓存全部失效）
```

> **序列化的完整专题**（机制详解 / JDK 序列化 / JSON 注解 / 全家族对比 / 场景选型 / 坑速查）见 [J10·Java序列化全解](../Java基础实战/J10·Java序列化全解.md)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/docs/Java基础实战/J10·Java序列化全解.md)）。

---

### 4.6 运行时发生了什么（亲手验证）

```text
第 1 次 GET /api/tasks/{id}/report
  → @Cacheable：Redis 里没有 report::<taskId> → 执行方法体（查库+组装）→ 结果写入 Redis
第 2 次起
  → @Cacheable：命中 → 直接返回（★ Controller/Service 方法体根本不执行，日志里没有新查询）
报告重建（POST 触发装配）
  → @CacheEvict：删掉 report::<taskId> → 下次读重新回源
```

用 redis-cli 亲眼验证：

```bash
KEYS report::*
TTL report::<你的taskId>       # 剩余秒数（≤1800）
GET report::<你的taskId>       # 能看到 JSON 原文（因为用了 JSON 序列化）
```

> 更多查看命令（`DBSIZE` / `SCAN` / `TYPE` / `INFO keyspace` / `CLIENT LIST` 等）见 §1.3 命令速查。

### 4.7 ★ 高频疑问①：Spring Cache 能用五大类型吗？缓存的内容到底是什么？

**答：Spring Cache（RedisCacheManager）只会用 Redis 的 String 类型**——每条缓存就是**一个独立的 String key**，不存在 Hash/List/Set/ZSet。因为它的定位是"**键值缓存**"抽象：给一个 key，还一个 value，仅此而已。

**缓存的内容 = 方法的返回值**。完整链条：

```text
方法体执行完
  → 拿到返回值：ResearchReportResponse（record 对象）
    → CacheConfiguration 配的 Jackson 序列化器转成 JSON 字符串    ← ★ 这就是"存的内容"
      → SET report::<taskId> "<完整JSON>"  EX 1800               ← 隐藏的 set
```

redis-cli 里亲眼看：

```bash
127.0.0.1:6379> GET report::3aa23f89-xxxx
{"taskId":"3aa23f89-xxxx","title":"AI 研究报告","sections":[...],"generatedAt":"..."}
127.0.0.1:6379> TTL report::3aa23f89-xxxx
1745        ← 30 分钟 TTL 在倒数
```

读取时反向走：`GET` → JSON 反序列化回 `ResearchReportResponse` → 直接作为方法返回值（方法体没执行）。

几个容易懵的点一次说清：

| 疑问 | 答案 |
|------|------|
| 缓存的是过程还是结果？ | **只有结果**（返回值的 JSON）。执行时的 SQL、计算过程都不保存 |
| 缓存的粒度？ | **一次调用 = 一条**。不同 `taskId` 是不同的 key、各存各的 |
| `key = "#taskId"` 怎么取到参数？ | SpEL 表达式——拦截时读出实际入参拼进 key，每个任务独立缓存条目 |
| 返回 `null` 会被缓存吗？ | 默认**不会**（`allowNullValues=false`）——查不到每次都打到库（= 缓存穿透成因之一，解药见 04 课：可开启空值缓存） |
| `@CacheEvict` 删的是什么？ | 就是删 `report::<taskId>` 这个 String key——删了下次读自动重建 |

**五大类型在"缓存 vs 手动"两条路线里的分工**：

```text
┌─ 声明式（注解流）：只管"缓存"这一件事 ──────────────┐
│  @Cacheable / @CacheEvict                          │
│  → Spring 自动 GET/SET/DEL（只碰 String 类型）       │
│  → 业务方法零改动，缓存逻辑零代码                     │
└────────────────────────────────────────────────────┘
┌─ 命令式（手动流）：五大类型随便用 ────────────────────┐
│  StringRedisTemplate.opsForXxx()                    │
│  → 锁（场景⑨）、验证码、限流、排行榜、队列……            │
│  → get/set 自己写，但自由度拉满                       │
└────────────────────────────────────────────────────┘
```

### 4.8 ★ 高频疑问②：真实项目会显式调 get/set 吗，还是只用注解？

**答：两种都会用，长期共存——分工是"缓存归注解，其余归手动"。**

| 需求 | 用什么 | 说明 |
|------|--------|------|
| 接口/方法结果缓存（读多写少） | `@Cacheable` / `@CacheEvict` | 声明式最省事，标准场景首选 |
| 写后失效缓存 | `@CacheEvict` | 跟着写操作走 |
| **分布式锁** | 手动 `StringRedisTemplate`（或 Redisson） | 注解做不了 |
| **验证码 / 短信码** | 手动 `set` + TTL | 注解做不了 |
| **接口限流 / 计数器** | 手动 `increment` | 注解做不了 |
| **排行榜 / 热词** | 手动 `opsForZSet` | 注解做不了 |
| **去重 / 黑名单** | 手动 `opsForSet` | 注解做不了 |
| **任务队列** | 手动 `opsForList`（大项目上 Kafka） | 注解做不了 |

规律：**`@Cacheable` 家族只能表达"方法进出加缓存"这一种模式**；锁、验证码、限流这些非缓存能力，它天生管不了——必然要手动调 API。

即使是"缓存"，也有一批情况注解搞不定、必须手动：

| 情况 | 为什么注解不行 |
|------|---------------|
| **多级缓存**（本地 Caffeine + Redis） | 注解只管一层——项目里的 `MultiLevelCache` 就是为此手动抽象的 |
| **动态 TTL**（不同数据不同过期） | 注解的 TTL 在 `CacheConfiguration` 里写死（30 分钟） |
| **读-改-写**（取出缓存对象改字段再存回） | 注解只有"整体缓存/整体删除" |
| **批量操作**（一次取 50 个 key） | 注解按方法粒度缓存，没有 mget/pipeline |
| **复杂 key 逻辑** | SpEL 写复杂了难读，不如手写 |
| **先更库再删缓存的精细编排** | 注解时机固定，极端一致性场景要手动控制 |

**生产补充**：真实公司里分布式锁很少手写 SET NX + Lua，普遍用 **Redisson**（`redissonClient.getLock(...).tryLock()`）——自带看门狗自动续期、可重入、红锁等。本项目手写是为了教学（理解原理）。

**判断口诀**：

```text
"缓存一个方法的返回值" → 注解（@Cacheable / @CacheEvict）
"Redis 当工具用（锁/计数/队列/去重/排行）" → 手动 API（StringRedisTemplate）
```

---

## 5. 第 4 课：实战②——分布式锁（场景⑨ / ⑩）

### 5.1 为什么需要

一个任务不该被同时执行两次（重复扣费/重复调用 LLM）。单机用 `synchronized` 就够，但**部署多实例**时，两台机器各有各的 `synchronized`，拦不住对方 → 需要一把**所有实例共享的锁** → 放 Redis。

**为什么不用数据库行锁**：任务执行包含多次 LLM 调用（数十秒），DB 锁会把整个执行包成长事务，连接池被打满。Redis 锁不占数据库资源，TTL 自动兜底。

**前端类比**：像一个全局的"执行名额"——`SET NX EX` = 抢名额（只有没被占时才能抢到），TTL = 名额 5 分钟后自动回收，Lua 释放 = **只有本人能退自己的名额**。

### 5.2 逐行解读 [`DistributedTaskLock`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)）

```java
// ① 抢锁：SET NX EX——"key 不存在才设置 + 带过期时间"，一条命令原子完成
Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
//   token = UUID：每人一个凭证，释放时验明正身
//   NX    = Not eXists（不存在才设）→ 天然互斥：谁先到谁拿到
//   EX    = 带 TTL（5 分钟）→ 进程崩溃锁也会自动消失，不会死锁

// ② 释放：Lua 脚本——"先比 token，一致才删"，两条命令合成一条原子操作
private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
        Long.class);
Long deleted = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
```

**为什么释放要用 Lua**：如果拆成"GET 比对 → DEL"两步，中间可能恰逢锁过期、被别人抢走——你就会把**别人的锁**删掉。Lua 脚本在 Redis 服务端一次性执行，中间不会插入其他命令。

### 5.3 ★ 高频疑问：抢锁只有 `setIfAbsent`，没有 `get`——它怎么知道"有没有锁"？

**答案：`setIfAbsent` 的返回值本身就是答案。** 它对应 Redis 命令 `SET key value NX EX`（NX = Not eXists）：

> **"这个 key 不存在时，才写入"——检查和写入是同一条命令，原子完成。**

| 返回值 | 含义 | 结果 |
|--------|------|------|
| `true` | key 原来**不存在**（没人持锁）→ 写入成功 | 🎉 抢到锁 |
| `false` | key **已存在**（有人持锁）→ 什么都没写 | 没抢到，跳过本次 |

所以代码里不需要 `get`——`acquired == false` 就等于"Redis 里已经有这把锁了"。

**为什么不能"先 GET 再 SET"？**（check-then-act 竞态条件）

```text
时刻 1：实例 A 执行 GET → null（没有锁）
时刻 2：实例 B 执行 GET → null（没有锁）★ 间隙里插进了别人的判断！
时刻 3：实例 A 执行 SET → 成功，A 拿到锁
时刻 4：实例 B 执行 SET → 也成功！★ 两把锁同时成立，互斥彻底失效
```

`SET NX` 是 Redis **单线程执行的一条命令**，"检查不存在 + 写入"之间不可能插入任何其他命令——**永远只有一个人能返回 true**。

> **前端类比**：秒杀按钮。不能"先问还有没有票、再下单"——两个人同时问都会听到"有"，然后都下单成功（超卖）。秒杀的正确实现就是服务端一条原子操作："**有票才扣，扣了才告诉你成功**"。数据库类比：唯一索引表的 `INSERT ... ON CONFLICT DO NOTHING`——不需要先 SELECT。

**那"读"去哪了？** 抢锁阶段不需要读——抢不到就直接走，不需要知道持有者是谁。`GET` 只在**释放时**出现（Lua 里 `get + 比对 token + del`，见 §5.2）。

**一句话**：不是没有判断，而是**判断和上锁被合成了一条不可分割的命令**——这正是它能当分布式锁、而 GET+SET 不能的根本原因。

### 5.4 调用方怎么用（固定套路）

```java
Optional<String> token = distributedTaskLock.tryLock(taskId);
if (token.isEmpty()) {
    return;                       // 已有人在执行，直接退出（或返回"执行中"）
}
try {
    doExecuteTask(taskId);        // 真正的业务
} finally {
    distributedTaskLock.unlock(taskId, token.get());   // ★ finally 里释放，异常也要还锁
}
```

项目里的两个使用方：
- 任务执行互斥（场景⑨）：`lock:task:exec:<taskId>`；
- 定时清理互斥（场景⑩）：`lock:cleanup`——多实例部署时保证定时任务**全集群只跑一份**（[`RetentionCleanupJob`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java)））。

### 5.5 ★ 锁的精确语义：锁的是"执行权"，粒度由 key 决定（2026-11-20 补）

**核心理解**：分布式锁锁的不是"任务本身"，而是**"这个任务当前的执行权"**——**key 的粒度 = 互斥的范围**：

```text
实例A 执行任务 111 → 持有 lock:task:exec:111
实例B 执行任务 222 → 持有 lock:task:exec:222     ← 不同 key = 不同锁 = 互不影响，正常并行 ✅
实例B 想执行任务 111 → 抢 lock:task:exec:111 → 抢不到（A 持有）→ 跳过 ✅
```

**设计含义**：给每个异步任务定义唯一的 key，"谁抢到这个 key 谁执行"；key 按什么粒度设计（按任务/按用户/全局一把），就决定了互斥的范围——全局一把锁会让所有任务串行，按任务分锁才能"该并行的并行、该互斥的互斥"。

### 5.5.1 双实例抢同一任务的完整时序

```text
Kafka 把任务 111 的消息投给实例A（也可能投给 B——消费组不保证给谁）
实例A：SET lock:task:exec:111 token-A NX EX 300  → 成功（key 不存在）★ 互斥 ✓
        → 状态守卫 CAS（QUEUED→RUNNING）★ 防重复 ✓
        → 执行业务
实例B 同时触发任务 111（重投/重复消费）
        → SET NX 失败（key 已存在）→ 跳过
实例A 执行完 → Lua 校验 token-A → DEL → 释放
极端情况：实例A 执行中崩溃 → 没有 DEL → TTL 5 分钟后锁自动过期 → 其他实例可重试
                ★ 防崩溃死锁 ✓
```

**三重防线各司其职**：

| 防线 | 防什么 | 机制 |
|------|--------|------|
| `SET NX EX` | **防同时执行** | 原子抢占，只有一个成功 |
| 状态守卫（DB CAS） | **防重复执行**（锁释放后再次触发） | `UPDATE ... WHERE status='QUEUED'`——已 RUNNING/COMPLETED 的任务不会被重新标记 |
| TTL | **防崩溃死锁**（执行者挂了锁不放） | 到期自动过期 |

### 5.5.2 两个注意点

1. **TTL 与业务时长的矛盾**：锁过期 ≠ 业务完成——手写版靠调大 TTL（不优雅），生产用 Redisson 看门狗（T7，自动续期）；
2. **锁只防"同时执行"，不防"先后重复执行"**：A 释放锁后，B 再抢到锁还会把任务再执行一遍 → **锁必须配合幂等**（状态守卫：已 COMPLETED 的任务不会被重新标记 RUNNING）——这就是"锁是防线之一，不是全部"（防线家族见[分布式并发全景](../服务架构/分布式并发全景——有序性原子性互斥.md)）。

**一句话**：**Redis 通过 `SET NX EX` + 唯一 key，让"同一个异步任务在多实例集群里只有一个实例执行，其他实例自动跳过，崩溃后锁自动释放"——配合状态守卫做幂等，才是完整的方案。**

### 5.6 概念展开

锁的进化四步（SETNX → SET NX EX → token+Lua → Redlock）、看门狗续期、三大进阶问题 → 见 [05·分布式锁深入](05·分布式锁深入.md)。

---

## 6. 第 5 课：Key 规范——别让 Redis 变成"全局变量垃圾场"

项目有统一的 [`RedisConstant`](../../backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java)）：

| 常量 | 值 | 用途 |
|------|-----|------|
| `LOCK_TASK_EXEC_PREFIX` | `lock:task:exec:` | 任务执行锁前缀（拼 taskId） |
| `LOCK_CLEANUP` | `lock:cleanup` | 定时清理全局锁 |
| `CACHE_REPORT` | `report` | Spring Cache 的 cacheName |

**命名规范**：全小写、冒号分层 `类型:业务域:实体:标识`（如 `lock:task:exec:<uuid>`）；动态部分由静态方法拼接，禁止调用方手拼字符串。

**前端类比**：等于给"全局变量"加命名空间——没有规范，Redis 里就是 300 个看不出用途的散 key，谁也不敢删。

**注意**：`@Cacheable(value = "report")` 里的 `report` 是 Spring Cache 的**逻辑名**，Redis 里真实 key 是 `report::<taskId>`（cacheName + `::` + 业务 key）。

---

## 7. Redis 的四大角色：存储 / 协调 / 广播 / 计算（2026-11-14 补）

> 学到这里可以回答一个总纲性问题：**"Redis 到底是干什么的？"** 它不止是缓存——按解决什么问题，可分为四大角色。本篇已覆盖前两个，后两个等你遇到对应需求时，就是"加一行命令"的事。

### 7.1 总览：一张表看懂四大角色

| 角色 | 干什么 | 核心能力 | 本项目 |
|------|--------|---------|:---:|
| **存储型** | 存数据、加速读取 | String 存 JSON + TTL | ✅ 场景⑦ 报告缓存 |
| **协调型** | 多进程"抢资源/排队" | SET NX 原子操作 | ✅ 场景⑨⑩ 分布式锁 |
| **广播型** | 进程间"喊话/传信" | Pub/Sub、Streams | ❌ 还没用到 |
| **计算型** | 边存边算统计 | INCR、ZSet、HyperLogLog、Bitmap | 🔜 演示控制器的 ZSet 雏形 |

> 分布式锁很多人归入"存储型"，但它**不存业务数据，只存一个"占用标记"**——本质是拿存储能力做**协调**，单列出来更好理解。

### 7.2 广播型（消息通信）：进程之间"喊话"

前端世界里早见过这个模式——Node.js 的 `EventEmitter`、浏览器的 `postMessage`、WebSocket 广播。Redis 提供三种"传话"方式：

| 方式 | 原理 | 消息会存下来吗 | 适用 |
|------|------|:---:|------|
| **Pub/Sub 发布订阅** | 发布者喊一嗓子，所有在线订阅者同时收到 | ❌ **不落地**（没人在线就没了） | 实时通知：聊天室、弹幕、配置变更广播 |
| **Streams（Redis 5.0+）** | 消息写入日志流，消费者组按序消费、可回溯 | ✅ 落地 | 轻量级消息队列（迷你 Kafka） |
| **List 队列** | 左进右出 | ✅ 落地 | 最简单的任务队列 |

**★ Pub/Sub 最大的坑（初学者必记）**：

```text
发布者发消息时，订阅者必须在线；
不在线 → 消息直接蒸发，事后补不回来（fire and forget）。
所以：通知用 Pub/Sub，业务数据用 Streams/队列。
```

**项目扩展方向**：多实例部署里，某个实例清了缓存想通知其他实例也清各自的 Caffeine 本地缓存——Pub/Sub 广播一条即可（这正是多级缓存 `MultiLevelCache` 未来要用的机制）。

### 7.3 计算型（边存边算）：数据进去，统计结果出来

Redis 最"惊艳"的部分——**不是存完再算，而是写入时就把答案算好了**：

| 能力 | 命令 | 场景 | 惊奇点 |
|------|------|------|--------|
| **计数器** | `INCR` | 阅读量、点赞数、限流 | 单线程原子递增，百万并发也不重复计数 |
| **排行榜** | `ZSet` | 热搜榜、积分榜 | 写入即排好序，Top10 一条命令 |
| **HyperLogLog** | `PFADD` / `PFCOUNT` | 网站 UV（独立访客）统计 | **亿级数据只占 12KB**，误差 0.81% |
| **Bitmap 位图** | `SETBIT` / `BITCOUNT` | 每日签到、连续登录 | 一年的签到只占 46 字节 |
| **Geo 地理位置** | `GEOADD` / `GEOSEARCH` | 附近的人/门店 | 底层就是 ZSet 存经纬度编码 |

**前端类比**：

```js
// 计数器：不能"读出来 +1 再写回"（并发下丢计数），INCR 是原子递增
await redis.incr("views:article:1");

// HyperLogLog：JS 做"亿级去重计数"要一个巨型 Set；Redis 只给 12KB
await redis.pfadd("uv:2026-11-14", userId);
const uv = await redis.pfcount("uv:2026-11-14");   // 独立访客数
```

**为什么这么省**：HyperLogLog/Bitmap 都是**概率型/有损结构**——统计够准即可（UV 差 0.8% 无所谓），用"精确性"换"空间"。

#### ★ 顺带解答：代码里的 `1L` 是什么意思？

**`L` 是 Java 的数字字面量后缀**——`1L` 表示"long 类型（64 位整数）的 1"，与默认的 `1`（int，32 位）相对。

**为什么 Redis 相关代码里到处是 `L`**：

1. **Redis 的计数是 64 位的**：`INCR` 理论上能从 0 加到 2⁶³（约 922 亿亿），远超 int 上限（±21 亿）——用 `1L` 表达"按 64 位算，别把它当普通 int"；
2. **Spring Data Redis 的 API 参数就是 long**：

```java
// 每次访问 +1：第二个参数就是 long 类型
redisTemplate.opsForValue().increment("views:article:1", 1L);

// 不传增量默认 +1，返回值也是 Long（64 位）
Long count = redisTemplate.opsForValue().increment("views:article:1");
```

**顺带记一个 TTL 的特殊返回值**（`getExpire()` 的约定哨兵值，初学者常困惑）：

```text
返回正数    → 剩余过期秒数
返回 -1L   → key 存在，但没有设置过期时间（永不过期）
返回 -2L   → key 不存在
```

**字面量后缀全家福**：`1`（int，默认）/ `1L`（long）/ `1.0f`（float）/ `1.0`（double，默认）——注意**没有 int 后缀**（默认即是），也没有 byte/short 后缀。

> **前端类比**：JS 所有数字都是 64 位浮点（Number），没有 int/long 之分——代价是超过 2⁵³ 就丢精度（雪花 ID 直接传给 JS 会变错的数），而 Java 的 `long` 精确到 2⁶³。所以后端给前端传雪花 ID 前要**转成字符串**（坑速查 §10 #7）。

### 7.4 需求 → 角色 → 实现（决策总表）

| 产品需求 | 角色 | Redis 能力 |
|---------|------|-----------|
| 接口结果缓存、Session 共享 | 存储型 | String + TTL |
| 多实例互斥执行、防重复下单 | 协调型 | SET NX EX + Lua |
| 配置变更通知所有实例 | 广播型 | Pub/Sub |
| 轻量任务队列 | 广播型 | Streams / List |
| 阅读量、点赞数、限流 | 计算型 | INCR |
| 热搜榜、积分榜 | 计算型 | ZSet |
| 千万级 UV 统计 | 计算型 | HyperLogLog |
| 签到、活跃天数 | 计算型 | Bitmap |
| 附近的人 | 计算型 | Geo |

### 7.5 项目角色地图（现状 + 扩展方向）

```text
已用：
  存储型  → 场景⑦ 报告缓存（@Cacheable）
  协调型  → 场景⑨⑩ 分布式锁（SET NX + Lua）

未来可加（都是一行命令的事）：
  广播型  → 多实例本地缓存失效广播（Pub/Sub：某实例清了缓存，喊一声让别的实例也清）
  计算型  → 任务执行统计（INCR 今日执行数）/ 搜索热词榜（ZSet incrementScore，§2.4 已埋雏形）
```

**一句话**：**存储型管"数据放哪"，协调型管"谁先动手"，广播型管"怎么传话"，计算型管"边存边算"。** 四个角色用同一个 Redis、同一套数据结构——区别只是你拿它们解决什么问题。

---

## 8. 概念课导航（01~07 什么时候读）

| 课 | 一句话 | 什么时候读 |
|----|--------|-----------|
| [01·五大类型与底层结构](01·五大类型与底层结构.md) | String/Hash/List/Set/ZSet 的底层是 SDS/压缩表/跳表 | 用熟 §2 的 opsForXxx 之后回看，理解"为什么这么设计" |
| [02·单线程为什么快](02·单线程为什么快.md) | 单线程 + IO 多路复用（≈ Node.js 事件循环的思路） | 想明白"为什么 Redis 读这么快、单条命令天生原子" |
| [03·持久化RDB与AOF](03·持久化RDB与AOF.md) | RDB=定期快照，AOF=追加日志；生产两者都开 | 关心"Redis 重启数据会不会丢"时 |
| [04·缓存三件套](04·缓存三件套.md) | 穿透/击穿/雪崩 + 解药 | ★ **上缓存前必读**（场景⑦ 的安全须知） |
| [05·分布式锁深入](05·分布式锁深入.md) | SETNX→SET NX EX→token+Lua→Redlock | ★ **第 5 课（§5）的原理展开**，项目场景⑨ 全对号 |
| [06·过期与内存淘汰](06·过期与内存淘汰.md) | 惰性+定期删除、8 种淘汰策略记 4 个 | 用熟 TTL 之后（§3 的底层） |
| [07·主从哨兵集群](07·主从哨兵集群.md) | 主从复制、哨兵自动故障转移、集群分槽 | 上生产/面试前 |

---

## 9. 动手实验清单（按顺序做）

1. **起服务**：Docker 起 Redis → `redis-cli` 玩 SET/GET/TTL/DEL（§1.1）；
2. **玩类型**：把 §2.3 的五段代码抄进一个 `@Profile("dev")` 的演示 Controller，逐个 curl 验证，并用 redis-cli 观察数据形态；
3. **看缓存**：启动后端 → 连续两次 `GET /api/tasks/{id}/report` → 观察第二次明显变快 + `redis-cli` 里出现 `report::<taskId>`（§4.6）；
4. **画锁**：读 [`DistributedTaskLock`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)），画出"抢锁→执行→释放/过期"生命周期图；
5. **概念自测**：读 04（三件套）和 05（进阶三问），能用自己的话答出来即过关。

---

## 10. 常见坑速查

| 坑 | 现象 | 解法 |
|----|------|------|
| record 不实现 Serializable | `Cannot serialize ... requires a Serializable payload` | 用 JSON 序列化器（[`CacheConfiguration`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)）已解决） |
| 空密码串 | `ERR Client sent AUTH, but no password is set` | 本地无密码就**别配** `password:` |
| yml 空 `redis:` 节点 | 属性绑定诡异失败 | 空壳节点删掉 |
| `@Cacheable` 自调用失效 | 同类里 `this.query()` 不走缓存 | 同 AOP 自调用（J2 §20.7③）——拆 Bean 或走代理 |
| `KEYS *` 在生产 | 阻塞单线程，全站卡顿 | 用 `SCAN` 游标遍历 |
| 锁不设 TTL | 进程崩 → 死锁 | 永远 `setIfAbsent(key, val, ttl)` 三参版 |
| 释放锁不用 Lua | 可能误删别人的锁 | token + Lua compare-and-del（§5.2） |

---

## 11. 一句话总结

```text
Redis = 全进程共享的内存大字典；
StringRedisTemplate = 操作它的 API 门面；
TTL = 到点自动消失（锁和缓存的兜底）；
@Cacheable/@CacheEvict = 读走缓存、写后失效（场景⑦）；
SET NX EX + token + Lua = 分布式锁三件套（场景⑨/⑩）。
概念（01~07）随时回查——先会用，再懂原理，最后面试自如。
```
