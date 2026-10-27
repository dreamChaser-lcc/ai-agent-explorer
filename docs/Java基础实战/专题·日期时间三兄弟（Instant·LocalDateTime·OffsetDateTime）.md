# 专题 · 日期时间三兄弟（Instant · LocalDateTime · OffsetDateTime）

> **课程**：Java 基础实战 · 补充专题（2026-10-27 课堂问答）
> **目标**：搞懂四种日期时间类型的区别，解释本项目为什么 73 处全是 `OffsetDateTime`
> **状态**：✅ 完成（项目实测：OffsetDateTime=73，LocalDateTime/Date/ZonedDateTime=0）

---

## 一、四个类型，一句话认全

| 类型 | 它知道什么 | 它不知道什么 | 前端类比 | 适合 |
|---|---|---|---|---|
| **`Instant`** | UTC 时间线上的绝对点（纳秒精度） | 时区、人类日历 | Unix 时间戳 | 机器存储、计算 |
| **`LocalDateTime`** | "墙上钟"的日期时间 | **时区**——不知道这是哪的 10 点 | 无时区字符串 | 明确无时区语义的场景 |
| **`OffsetDateTime`** | 时刻 + UTC 偏移量（如 `+08:00`） | 时区规则（夏令时） | 带偏移的字符串 | **传输 / 存储（首选）** |
| **`ZonedDateTime`** | 时刻 + 完整时区（含夏令时规则） | — | 带时区名的日历时间 | 跨夏令时地区的日历计算 |

**一个比喻**：

```text
Instant        = "距 1970-01-01T00:00Z 过了 xx 纳秒"     （绝对，但人不友好）
LocalDateTime  = "现在是 10 点"                          （10 点？哪的 10 点？）
OffsetDateTime = "北京时间 2026-10-27T10:00+08:00"       （人和机器都满意）
ZonedDateTime  = "Asia/Shanghai 日历上的 10:00"          （涉及夏令时才需要）
```

---

## 二、核心区别：`LocalDateTime` 的"无歧义灾难"

```text
"2026-10-27T10:00" —— 这是什么时刻？
  北京的开发者：早上 10 点
  伦敦的开发者：凌晨 2 点
  数据库：？？？我不知道
```

而 `OffsetDateTime`：

```text
"2026-10-27T10:00+08:00" —— 无歧义
  任何系统、任何时区的任何程序，都能换算成同一个绝对时刻
```

**这就是本项目全用 `OffsetDateTime` 的根本原因。**

---

## 三、项目实测（2026-10-27）

```text
OffsetDateTime   = 73 处   ← 绝对主导（所有 Entity 时间字段 + 所有 Response 时间字段）
Instant          = 2 处    （HealthController 的 timestamp 等）
LocalDateTime    = 0
ZonedDateTime    = 0
java.util.Date   = 0
```

**结论**：团队有意识地统一了时间策略——**API 与存储全链路 `OffsetDateTime`**。

---

## 四、经典架构（存储 / 传输 / 展示 三段式）

```text
存储层：UTC（Instant，或 OffsetDateTime 统一转 UTC）
   ↓
传输层：OffsetDateTime（带偏移，无歧义）
   ↓        "2026-10-27T10:00+08:00"
展示层：前端转成本地时间渲染
           "2026-10-27 10:00"（用户所在时区）
```

**本项目实际**：数据库（H2/PG，PostgreSQL 模式）存 timestamptz → Entity 用 `OffsetDateTime` → JSON 输出 ISO 字符串（含偏移）。

---

## 五、三个经典坑

### 坑 1：`LocalDateTime` 存库后换时区读 = 时间错乱

```text
北京时间 10:00 存成 LocalDateTime → 存的时候没有时区信息
东京的服务器读出来 → 当成东京的 10:00 → 差 1 小时
（换欧美时区能差 6~8 小时）
```

### 坑 2：前端 `new Date("2026-10-27T10:00")` 的解析歧义

```javascript
new Date("2026-10-27T10:00")       // ⚠ 按浏览器【本地时区】解析
new Date("2026-10-27T10:00Z")      // ✅ 按 UTC 解析
new Date("2026-10-27T10:00+08:00") // ✅ 按偏移解析（后端给的这种就没歧义）
```

**后端永远给带偏移的字符串**（我们已经是了），前端就不会错。

### 坑 3：比较/计算要落到 Instant，别比字符串

```java
// ❌ 字符串比较：同一时刻在 "+08:00" 和 "+00:00" 下字符串长得不一样
// ✅ 换算成同一基准再比较
a.toInstant().isBefore(b.toInstant());
```

---

## 六、`LocalDateTime` 不是一无是处

```text
它适合【不带时区语义】的时间：
  · 门店营业时间 09:00-21:00（每个城市的店都是本地 9 点）
  · 生日 1998-05-01（生日没有时区概念）
  · 报表统计的"自然日"

判断标准：这个时间换一个时区还有意义吗？
  有意义 → OffsetDateTime / Instant
  没意义（它本来就是"本地"概念） → LocalDateTime
```

---

## 七、一句话总结

```text
Instant        = 机器的绝对时刻（存储/计算）
LocalDateTime  = 无时区的墙上钟（只在"明确无时区语义"时用）
OffsetDateTime = 时刻 + 偏移 → 无歧义 → 【API 传输与存储的黄金选择】
ZonedDateTime  = 完整时区规则（夏令时场景才需要）

本项目：73 处 OffsetDateTime 全链路统一 —— 这个纪律值得保持
```

---

## 八、课堂问答记录（2026-10-27）

（待补——学员提问后按双台账纪律记录）
