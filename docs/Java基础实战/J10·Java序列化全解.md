# J10 · Java 序列化全解（机制 · 格式 · 场景选型）

> **定位**：把散落在 Redis 08 / 场景⑦⑨ / J2（代理）/ J7（反射）里的"序列化"知识收拢成一篇——讲清**什么是序列化、Java 有哪些序列化方式、二进制是怎么"编"出来的、什么场景选什么格式**。
> **前置**：J2（接口与多态 / CGLIB 代理）、J7（注解 + 反射）｜**强关联**：[Redis 08·Spring实战整合](../Redis实战/08·Spring实战整合.md)（§4 序列化与 §4.5 深挖）
> **建立**：2026-11-13

---

## 目录

- [1. 什么是序列化？为什么需要？](#1-什么是序列化为什么需要)
- [2. 反序列化的本质：图纸与快照](#2-反序列化的本质图纸与快照)
- [3. 方式全景：一张家族表](#3-方式全景一张家族表)
- [4. JDK 原生序列化详解](#4-jdk-原生序列化详解)
- [5. JSON 序列化详解（Jackson）](#5-json-序列化详解jackson)
- [6. 二进制是怎么"编"出来的（原理详解）](#6-二进制是怎么编出来的原理详解)
- [7. 全家对比大表](#7-全家对比大表)
- [8. 场景选型](#8-场景选型)
- [9. 常见坑速查](#9-常见坑速查)
- [10. 项目关联地图](#10-项目关联地图)
- [11. 动手练习](#11-动手练习)
- [12. 一句话总结](#12-一句话总结)

---

## 1. 什么是序列化？为什么需要？

**序列化（Serialization）**：把 JVM 堆内存里的对象，翻译成**字节流或文本**的过程；反过来叫**反序列化（Deserialization）**。

**为什么需要**：对象活在 JVM 堆内存里——**进程一死就没了，而且出不了这台 JVM**。只要有下面任何一种需求，就必须先序列化：

| 需求 | 序列化到哪 | 项目实例 |
|------|-----------|---------|
| **缓存**（跨进程共享数据） | Redis（String 值） | 场景⑦ `report::<taskId>` |
| **远程调用 RPC**（数据发给另一台机器） | HTTP body / RPC 协议 | 前端接口的 JSON 响应 |
| **消息队列**（事件发给另一个服务） | Kafka 消息体 | 场景⑥ 任务消息 |
| **持久化**（数据存盘） | 数据库行 / 文件 | `@Transactional` 落库 |
| **深拷贝 / 落地传输** | 字节数组 | 少见，了解即可 |

**前端类比**：`localStorage` 只能存字符串 → 存对象必须 `JSON.stringify`，读出来 `JSON.parse`。Java 序列化是同一件事，只是"格式"选择更多、坑也更多。

---

## 2. 反序列化的本质：图纸与快照

序列化**只搬数据（字段值），不搬行为（方法）**——因为 Java 的对象模型：

```text
Java 对象 = 字段数据（堆里的快照） + 指向 Class 元数据的引用（图纸）

方法属于"图纸"（Class），不属于"快照"（数据）——
序列化导出快照，反序列化按图纸重建 → 方法自动全在，永远不会"丢方法"。
```

> 这与 JS 完全不同：JS 对象是属性袋，方法也是属性（函数也是值），`JSON.stringify` 只导出数据属性 → **方法会丢**。对照表见 §6.4。

**类型信息默认不进快照**：JSON 里没有"这是 Dog 还是 Animal"——**类型由读取方补齐**（告诉 Jackson 目标类，或用注解内嵌类型）。这是 §4.4 里 `Jackson2JsonRedisSerializer<>(mapper, ResearchReportResponse.class)` 第二个参数存在的原因。

---

## 3. 方式全景：一张家族表

| 类别 | 成员 | 主要用途 |
|------|------|---------|
| **文本类** | **JSON**（Jackson / Gson / Fastjson2）、XML（JAXB / XStream）、YAML、CSV、Properties | 接口交换、配置文件、人要看的场景 |
| **二进制类** | **JDK 序列化**、Kryo、Hessian、**Protobuf**、Thrift、Avro、MessagePack、CBOR、FlatBuffers、SBE | RPC、高性能缓存、大流量消息 |
| **表映射类** | JPA / MyBatis（对象 ↔ 数据库行） | 持久化（本课不展开，见场景系列） |

三种"约定放在哪"的流派：

| 流派 | 约定位置 | 代表 | 特点 |
|------|---------|------|------|
| **自描述** | 类描述写进数据流 | JDK 序列化 | 体积大；只能 Java 读 |
| **注册表** | 两端预先注册（类 → 整数 ID） | Kryo | 紧凑；两端版本必须同步 |
| **外置 schema** | 独立的定义文件（`.proto` / `.avsc`） | Protobuf / Avro | 跨语言；演进机制最成熟 |

---

## 4. JDK 原生序列化详解

### 4.1 用法

```java
import java.io.*;

class User implements Serializable {                    // ① 实现 Serializable 标记接口
    private static final long serialVersionUID = 1L;    // ② 显式声明版本号（★ 必写）
    private String name;
    private transient String password;                  // ③ transient = 不参与序列化

    // getter / setter ...
}

// 写（序列化）
try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("user.bin"))) {
    out.writeObject(new User());
}

// 读（反序列化）
try (ObjectInputStream in = new ObjectInputStream(new FileInputStream("user.bin"))) {
    User user = (User) in.readObject();
}
```

**三个关键字段**：

| 关键字 | 作用 |
|--------|------|
| `Serializable` | 标记接口（无方法）——"我同意被序列化"；不实现则抛 `NotSerializableException` |
| `serialVersionUID` | 版本号——**不写时 JVM 按类结构自动算**，类一改（加字段）自动值就变，旧数据读回直接 `InvalidClassException`；显式写死 = 允许类演进时兼容旧数据 |
| `transient` | 标记"这个字段不序列化"（密码、连接、大临时对象） |

### 4.2 原理：类描述也写进了流

```text
AC ED 00 05                ← 魔数（Java 序列化协议标志）
73 72 00 0A ...            ← 类描述（类名、serialVersionUID、字段签名）★ 内嵌！
...字段值逐个写入（递归：字段里还有对象就继续展开）
```

**自描述的代价**：
1. **体积大**——类名、字段名全写了一遍；
2. **只能 Java 读**——格式绑定 Java 反射机制；
3. **反序列化安全漏洞**——攻击者构造恶意字节流，`readObject` 时执行任意代码（著名的反序列化攻击链），**绝不能用 JDK 序列化处理不可信来源的数据**。

### 4.3 结论

```text
JDK 序列化：新项目不要用。了解原理即可（面试常问 serialVersionUID / transient / 安全问题）。
```

---

## 5. JSON 序列化详解（Jackson）

### 5.1 三大库

| 库 | 定位 | 备注 |
|----|------|------|
| **Jackson** | **Spring Boot 默认内置**（spring-boot-starter-web 自带） | 功能最全，本项目用它 |
| Gson | Google 出品 | 轻量 |
| Fastjson2 | 阿里出品 | 国内多，历史上安全漏洞较多 |

### 5.2 常用注解（面试 + 实战高频）

| 注解 | 作用 |
|------|------|
| `@JsonIgnore` | 字段不序列化（≈ JDK 的 transient，但注解表达） |
| `@JsonProperty("别名")` | 改输出的字段名 |
| `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` | 日期格式化 |
| `@JsonTypeInfo` + `@JsonSubTypes` | 多态字段内嵌类型信息（解决 §"多态丢失"） |
| `@JsonCreator` / `@JsonProperty` | 反序列化时指定构造器/参数（record 已自动支持） |

### 5.3 record 的支持

Jackson 2.12+ 原生支持 record：**按构造器参数名匹配 JSON 字段**——但必须知道目标类型是哪个 record（`RedisPlayground` 里缓存读回靠 `CacheConfiguration` 指定了 `ResearchReportResponse.class`）。

### 5.4 项目的实战配置

见 [`CacheConfiguration`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)）：JSON 序列化 + `JavaTimeModule` + 30 分钟 TTL——每一行防一个坑（详见 Redis 08 §4.4 / §4.5）。

---

## 6. 二进制是怎么"编"出来的（原理详解）

二进制序列化的本质 = **三件套**：

```text
① 一份"约定"：数据的结构定义（schema / 注册表 / 类描述）
② 一套"编码规则"：类型标签、变长整数（varint）、长度前缀
③ 两端"说同一种方言"：读写必须用同一份约定
```

"二进制不可读"的本质不是魔法——**是方言你不会**；会读的人拿同一份约定，每个字节都能解释。

### 6.1 亲手拆一个字节：JSON vs Protobuf

同一个对象 `name = "lcc"`、`level = 99`：

**JSON（26 字节，字段名原样重复——体积大的根源）**：

```text
7B 22 6E 61 6D 65 22 3A 22 6C 63 63 22 2C 22 6C 65 76 65 6C 22 3A 39 39 7D
{  "  n  a  m  e  "  :  "  l  c  c  "  ,  "  l  e  v  e  l  "  :  9  9  }
```

**Protobuf（7 字节）**：

```text
0A 03 6C 63 63 10 63
```

逐字节翻译：

```text
0A       → 字段标签：字段编号 1 + 类型 2（长度分隔）
03       → 长度：3
6C 63 63 → "lcc"（ASCII 原文）
10       → 字段标签：字段编号 2 + 类型 0（变长整数）
63       → 99（varint：小数字只占 1 字节）
```

**两个关键设计**：

1. **字段名不写进数据**——`name`、`level` 完全消失，两端靠同一份 `.proto` 知道"编号 1 是 name"；26 字节 vs 7 字节，体积差 3.7 倍；
2. **兼容性靠编号**：新增字段用新编号，老代码遇到不认识的编号跳过不读 → 平滑演进。

### 6.2 三种"约定流派"对照

| 流派 | 约定位置 | 代表 | 特点 |
|------|---------|------|------|
| 自描述 | 类描述内嵌数据流 | JDK 序列化 | 体积大；仅 Java |
| 注册表 | 两端预注册（类 → ID） | Kryo | 紧凑；版本须同步 |
| 外置 schema | 独立 `.proto` / `.avsc` 文件 | Protobuf / Avro | 跨语言；演进最成熟 |

> MessagePack 是"二进制版 JSON"：结构还是 map/array，每个值带 1 字节类型前缀——想保持 JSON 结构又要省空间时的折中。

---

## 7. 全家对比大表

| 维度 | JSON（Jackson） | JDK 序列化 | Kryo | Protobuf | Hessian |
|------|----------------|-----------|------|----------|---------|
| 可读性 | ✅ 文本可读 | ❌ 二进制 | ❌ | ❌（需 schema） | ❌ |
| 跨语言 | ✅ 天然 | ❌ 仅 Java | ❌ 仅 Java | ✅ 多语言 | ✅ |
| 体积 | 大 | 大 | **小** | **最小** | 中 |
| 速度 | 中 | 慢 | **快** | 快 | 快 |
| 需要 schema/接口 | 否（按目标类） | 实现 Serializable | 注册类 | **.proto 文件** | 实现接口 |
| 类型/版本演进 | 宽松（按目标类补齐） | 差（serialVersionUID） | 差（注册表同步） | **最好**（编号演进） | 中 |
| 安全 | 相对安全 | ⚠️ 反序列化漏洞著名 | 一般 | ✅ | 一般 |
| 典型用途 | 接口/缓存/配置 | 遗留系统 | Java 内部缓存 | RPC（gRPC）/大流量 MQ | Dubbo |

---

## 8. 场景选型

| 场景 | 推荐 | 理由 |
|------|------|------|
| **前后端接口** | JSON | 前端只会 parse JSON；可读 |
| **Redis 缓存**（中小流量） | JSON | redis-cli 可读，排障效率值钱 |
| **Redis 缓存**（大流量/内存敏感） | Kryo / Protobuf | 体积省 30%~50%（切换时旧缓存全失效——先清缓存！） |
| **RPC**（gRPC） | Protobuf | 跨语言 + 高性能 + schema 演进 |
| **RPC**（Dubbo） | Hessian | Dubbo 默认搭档 |
| **消息队列**（大吞吐） | Protobuf / Avro + Schema Registry | 消息体越小带宽越省 |
| **配置文件** | YAML / Properties | 人可读可维护 |
| **前端存储** | JSON 字符串 | `localStorage` 只认字符串 |

**判断口诀**：

```text
人对着屏幕看（调试/联调/低频）    → JSON
数据量小、结构简单                → JSON
量大、机器对机器、内存/带宽敏感     → 二进制（Kryo / Protobuf）
跨语言 + 高性能 RPC               → Protobuf
遗留系统                          → 被迫兼容 JDK 序列化（尽快迁走）
```

> **生产切换警告**：序列化格式一换，**旧缓存/旧消息全部读不出来**（格式不认识）——必须先清空缓存，或设计双读过渡。Redis 08 §4.4 的 `CacheConfiguration` 注释"值保持一致避免缓存全失效"说的就是这件事。

---

## 9. 常见坑速查

| # | 坑 | 说明 / 解法 |
|---|-----|-----------|
| 1 | `serialVersionUID` 不写 | 类结构一改自动值就变 → 旧数据 `InvalidClassException`；显式写死 |
| 2 | `transient` 误用 | 标了就不序列化——关键字段标了等于删了 |
| 3 | 循环引用 | JSON 双方都抛异常；用 `@JsonManagedReference`/`@JsonBackReference` 或 DTO 解耦 |
| 4 | record + JDK 序列化 | 不友好；JSON 场景 Jackson 构造器绑定没问题（需指定目标类型） |
| 5 | JDK 反序列化漏洞 | 不用 JDK 序列化处理不可信数据 |
| 6 | 切换序列化格式 | 旧缓存/旧消息全部失效 → 先清缓存或双读过渡 |
| 7 | 大数给前端 | `BigDecimal`/大 long 经 JSON 到 JS 丢精度 → 转字符串输出 |
| 8 | 枚举改名 | 旧数据字符串对不上新枚举 → 枚举名当不可变 |
| 9 | 多态字段类型丢失 | 指定目标类型 / `@JsonTypeInfo` / `GenericJackson2JsonRedisSerializer` |
| 10 | `@Cacheable` 自调用 | 同类 `this.xxx()` 不走代理 → 缓存失效（J2 §20.7③） |

---

## 10. 项目关联地图

| 本课知识点 | 项目里的落点（Ctrl+Click 直达） |
|-----------|-------------------------------|
| JSON 序列化器 + TTL | [`CacheConfiguration`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/CacheConfiguration.java)） |
| `@Cacheable` / `@CacheEvict` | [`ResearchReportQueryService`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportQueryService.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportQueryService.java)） / [`ResearchReportAssemblyService`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java)） |
| Redis Key 规范 | [`RedisConstant`](../../backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java)） |
| 分布式锁（token = String 值） | [`DistributedTaskLock`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java)） |
| 序列化深挖（会丢什么） | Redis 08 文档 §4.5（[本目录关联](../Redis实战/08·Spring实战整合.md)） |
| 锁原理 / 缓存实战 | [Redis 08·Spring实战整合](../Redis实战/08·Spring实战整合.md) |
| 代理（为什么注解能生效） | J2 §12 / §20（[J2·接口与多态.md](J2·接口与多态.md)） |
| 注解与反射 | J7（[J7·注解体系.md](J7·注解体系.md)） |

**反向索引**：在场景⑦⑨⑩、Redis 08 里遇到的"序列化"疑问，回查本篇；本篇没覆盖的，就是 J11 的候选。

---

## 11. 动手练习

1. **体积对比实验**：同一个对象分别 JSON（Jackson）与 JDK 序列化（`ByteArrayOutputStream`），打印字节数组长度——直观感受"JSON 大在哪"；
2. **transient 实验**：给字段加 `transient` → 序列化再读回 → 字段变 null；
3. **serialVersionUID 实验**：写死 `serialVersionUID = 1L` → 加字段后旧数据仍可读；删掉它 → 加字段后读旧数据抛 `InvalidClassException`；
4. **循环引用实验**：两个对象互相引用 → Jackson 抛错 → 加 `@JsonManagedReference`/`@JsonBackReference` 解决；
5. **大数实验**：`long` 雪花 ID 返回给前端 → JS 里精度丢失 → `@JsonSerialize(using = ToStringSerializer.class)` 转字符串。

---

## 12. 一句话总结

```text
序列化 = 对象（JVM 堆）↔ 字节/文本 的翻译——跨进程必经之路；
只搬数据不搬方法（方法在类图纸里，反序列化自动就位）；
JDK 序列化：了解原理 + 面试（serialVersionUID / transient / 安全漏洞），新项目不用；
JSON：默认选择（可读/跨语言/安全），配好序列化器与 TTL；
二进制（Kryo/Protobuf）：量大之后省内存省带宽的升级项——切换时旧数据全部作废，先清再切。
```
