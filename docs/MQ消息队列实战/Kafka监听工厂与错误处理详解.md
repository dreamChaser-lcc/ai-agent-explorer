# 专题 · Kafka 监听工厂与消费错误处理详解（A/B 模式）

> **定位**：场景⑥ 的深化补课——讲清 `@KafkaListener` 背后的**监听容器工厂**、`CommonErrorHandler` 的**自动装配原理**、以及**并发/批量/提交这些"规则"到底定义在哪里**。
> **前置**：场景⑥（[异步升级Kafka](../业务场景实战/⑥异步升级Kafka.md)，含 §7.5 速览版）
> **建立**：2026-11-20

---

## 目录

- [1. 全景：`@KafkaListener` 背后的三层结构](#1-全景kafkalistener-背后的三层结构)
- [2. 模式 A：自动装配——你的规则从 application.yml 来](#2-模式-a自动装配你的规则从-applicationyml-来)
- [3. 模式 B：手写工厂——规则由代码定义](#3-模式-b手写工厂规则由代码定义)
- [4. A/B 对比与混用形态](#4-ab-对比与混用形态)
- [5. concurrency 与分区数的关系（初学者必懂）](#5-concurrency-与分区数的关系初学者必懂)
- [6. 错误处理器的触发范围](#6-错误处理器的触发范围)
- [7. 企业实践分布](#7-企业实践分布)
- [8. 项目关联地图](#8-项目关联地图)
- [9. 常见坑速查](#9-常见坑速查)
- [10. 一句话总结](#10-一句话总结)

---

## 1. 全景：`@KafkaListener` 背后的三层结构

```text
@KafkaListener(topics = "...")        ← 你写的：只声明"我要消费什么、处理逻辑是什么"
        │
        ▼ 由谁创建并驱动？
ConcurrentKafkaListenerContainerFactory   ← ★ 监听容器工厂（本篇主角）
        │  持有：消费者配置、并发数、批量开关、ack 模式、错误处理器……
        ▼
MessageListenerContainer（监听容器，常驻后台）
   ├─ 连接 broker、订阅 topic
   ├─ 按 concurrency 起 N 个消费线程
   ├─ poll 消息 → 调用你的方法
   └─ 出异常 → 转给 CommonErrorHandler
```

工厂 Bean 的默认名字是 **`kafkaListenerContainerFactory`**（`@KafkaListener` 的 `containerFactory` 属性默认值即此）。

---

## 2. 模式 A：自动装配——你的规则从 application.yml 来

**直接回答高频疑问**：模式 A 下没写 `setConcurrency(3)`、`setBatchListener(true)` 这些代码，是因为**这些规则定义在 `application.yml` 的 `spring.kafka.listener.*` 配置里**——Spring Boot 组装默认工厂时，把 yml 属性逐一应用到工厂上。

### 2.1 创建流程（源码级，简化）

```java
// org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration（简化）
@Bean
@ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")   // ★ 有同名自定义 Bean 就整体让位
ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory(
        ConsumerFactory<Object, Object> consumerFactory) {

    var factory = new ConcurrentKafkaListenerContainerFactory<Object, Object>();
    factory.setConsumerFactory(consumerFactory);        // ① 挂消费者工厂（读 spring.kafka.consumer.*）

    var configurer = new ConcurrentKafkaListenerContainerFactoryConfigurer(...);
    configurer.configure(factory, consumerFactory);     // ② ★ 把 yml 的 listener 属性逐一应用
                                                        //    concurrency / type(batch) / ack-mode ...
    // ③ 扫描容器里的 CommonErrorHandler Bean，恰好一个则挂上
    Map<String, CommonErrorHandler> handlers = context.getBeansOfType(CommonErrorHandler.class);
    if (handlers.size() == 1) {
        factory.setCommonErrorHandler(handlers.values().iterator().next());
    }
    return factory;
}
```

### 2.2 yml 属性 ↔ Java setter 对照表（核心）

| `application.yml` 属性 | 等价的 Java 代码 | 默认值 | 说明 |
|---|---|---|---|
| `spring.kafka.listener.concurrency` | `factory.setConcurrency(n)` | **1** | 消费线程数（受分区数上限约束，见 §5） |
| `spring.kafka.listener.type: batch` | `factory.setBatchListener(true)` | `single` | single=单条 / batch=批量（方法签名变 List） |
| `spring.kafka.listener.ack-mode` | `factory.getContainerProperties().setAckMode(...)` | `BATCH` | 提交时机：record/BATCH/MANUAL… |
| `spring.kafka.listener.poll-timeout` | 容器 poll 超时 | 5s | |
| `spring.kafka.consumer.*`（group-id 等） | `ConsumerFactory`（工厂持有的"原料"） | — | 消费者本身的配置，与工厂独立 |

**结论**：模式 A = **"规则写在 yml，Spring 代你 set"**；模式 B = **"规则写在代码，自己 set"**——同一批属性的两种表达。

### 2.3 项目实录：application.yml 的 kafka 段

[`application.yml`](../../backend/src/main/resources/application.yml)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml)）：

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP:localhost:9092}
    producer:
      key-serializer: ...StringSerializer
      value-serializer: ...StringSerializer
    listener:
      # ★ 长任务：每条处理成功就提交 offset
      ack-mode: record                      # ← 模式 A 的规则示例：AckMode.RECORD
    consumer:
      group-id: research-agent-backend
      max-poll-records: 1                   # ★ 一次只拉一条：长任务避免一批卡住全批重投
      auto-offset-reset: earliest           # 新消费组从最早消息读（重放演示的前提）
      properties:
        max.poll.interval.ms: 3600000       # ★ 长任务护航：默认 5 分钟会被误判消费者死亡
```

**这就是模式 A 的完整规则定义**：`ack-mode: record`（每条提交）+ `max-poll-records: 1`（单条拉取）+ 超大 `max.poll.interval.ms`——三件套共同适配"研究任务一次跑几分钟"的长任务特性。没写的属性（`concurrency`、`type`）走默认值（1 / single）。

---

## 3. 模式 B：手写工厂——规则由代码定义

```java
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler errorHandler) {          // ← 直接注入已有错误处理器

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);                       // ← 规则①：并发 3（代码里定义）
        factory.setBatchListener(true);                  // ← 规则②：批量模式
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);   // ← 规则③：手动提交
        factory.setCommonErrorHandler(errorHandler);     // ← ★ 手写工厂必须显式挂错误处理器
        return factory;
    }
}

// 使用：显式指定工厂；批量模式下方法签名变 List
@KafkaListener(topics = "xxx-batch", containerFactory = "batchFactory")
public void onBatch(List<String> messages) { ... }
```

**模式 B 的一个大坑**：自定义工厂**不会自动应用** `spring.kafka.listener.*`（configurer 只服务 Boot 默认工厂）——并发/批量/ack 都要自己 set；而 `spring.kafka.consumer.*` **仍然生效**（因为 `ConsumerFactory` Bean 还是 Boot 自动配置的）。另外**错误处理器必须显式挂**——自动收集那步只发生在默认工厂里。

**多工厂共存**：自定义工厂叫 `batchFactory`（不是默认名）时，Boot 的默认工厂**照常存在**——两者并存，不同 `@KafkaListener` 用 `containerFactory` 属性各选各的。这是真实项目最常见形态：**默认工厂 + N 个专用工厂**。

---

## 4. A/B 对比与混用形态

| 维度 | 模式 A：自动 | 模式 B：手写 |
|---|---|---|
| 规则定义处 | `application.yml` 的 `spring.kafka.listener.*` | Java 代码 `factory.setXxx(...)` |
| 代码量 | 0 行工厂代码 | 一个 `@Bean` 配置类 |
| 错误处理器 | 自动收集挂载 | **必须显式 set** |
| 灵活性 | 一套配置打天下 | 多套工厂并存，各监听器各选各的 |
| 适用 | 标准单条消费（大多数场景） | 批量、高并发、手动 ack、差异化配置 |

**混用形态（真实项目最常见）**：默认工厂跑普通业务监听器 + 1~2 个专用工厂跑批量/特殊主题。

---

## 5. concurrency 与分区数的关系（初学者必懂）

`concurrency: 3` 不是"越多越快"——**并发线程数受 topic 分区数硬约束**：

```text
topic 有 4 个分区，concurrency=3：
  线程1 → 分区0   线程2 → 分区1   线程3 → 分区2
  分区3 没人消费？不对——K3s 下的再均衡会把它分给某个线程（一线程管 2 个分区），
  但总并行度上限 = 分区数。

topic 只有 2 个分区，concurrency=3：
  线程1 → 分区0   线程2 → 分区1   线程3 → ★ 空闲（分不到分区，白占资源）
```

**规则**：`并行度上限 = 分区数`；**最佳实践 = concurrency ≤ 分区数**（常见做法：concurrency = 分区数，一一对应）。想提高并行度，先**加分区**，再提并发。

---

## 6. 错误处理器的触发范围

| 事件 | 走 `CommonErrorHandler` 吗 |
|------|:---:|
| `@KafkaListener` 处理某条消息抛异常（业务报错、校验失败） | ✅ 正是它：重试 → 死信 |
| 应用启动时 Kafka 连不上 | ❌ 启动/重连机制 |
| 分区 Rebalance | ❌ `ConsumerRebalanceListener` |
| 生产者发送失败 | ❌ 生产者异常处理 |
| 反序列化失败 | ⚠️ 特殊：默认刷日志跳过，需单独配 `ErrorHandlingDeserializer` |

项目的处理器链路（详见 [`KafkaErrorHandlingConfig`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/KafkaErrorHandlingConfig.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/KafkaErrorHandlingConfig.java)））：

```text
消费异常 → FixedBackOff 重试 3 次（间隔 1 秒）
   ├─ 成功 → 继续
   └─ 仍失败 → DeadLetterPublishingRecoverer → 投递 原topic.DLT → 提交 offset 继续消费
IllegalArgumentException（不可恢复）→ 不重试，直接 DLT
```

---

## 7. 企业实践分布

| 阶段 | 形态 |
|------|------|
| 大多数业务消息 | **模式 A**（默认工厂 + yml + 注解）——覆盖 70%+ 需求 |
| 批量 / 手动 ack / 差异化 | 按需加**模式 B**（一个项目常见 1~2 个专用工厂） |
| 成熟团队规范 | 显式定义**一个**统一工厂（配置集中显式、便于审计）——"经常见到手写工厂"是真的，但每个 listener 都手写不常见 |
| 平台级 | Spring Cloud Stream / RocketMQ 抽象层（类比 Redisson 之于手写 Redis 锁） |

**口诀**：`标准消费一条条处理 → A；批量/手动 ack/差异化配置 → B（记得显式 set 错误处理器）`。

---

## 8. 项目关联地图

| 知识点 | 落点（Ctrl+Click 直达） |
|--------|------------------------|
| 错误处理器 Bean | [`KafkaErrorHandlingConfig`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/KafkaErrorHandlingConfig.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/KafkaErrorHandlingConfig.java)） |
| yml 的 Kafka 配置（模式 A 规则） | [`application.yml`](../../backend/src/main/resources/application.yml)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/application.yml)） |
| 场景速览版 | 场景⑥ §7.5（[⑥异步升级Kafka.md](../业务场景实战/⑥异步升级Kafka.md)） |
| 消费端业务代码 | `TaskExecutionConsumer`（场景⑥ 第 5 章） |

---

## 9. 常见坑速查

| # | 坑 | 解法 |
|---|-----|------|
| 1 | 手写工厂忘了 `setCommonErrorHandler` | 异常回到"无限重投"老路——手写必显式 set |
| 2 | 以为自定义工厂也吃 `spring.kafka.listener.*` | 不会——listener 段只服务 Boot 默认工厂；consumer 段仍生效 |
| 3 | concurrency > 分区数 | 多余线程空闲白占资源；先加分区再提并发 |
| 4 | 长任务撞 `max.poll.interval.ms` | 调大间隔 + 调小 max-poll-records（项目实录） |
| 5 | 批量模式方法签名没变 List | 启动/消费报类型不匹配——type=batch 时签名必须 `List<T>` |
| 6 | 多工厂时 listener 忘写 `containerFactory` | 会走默认工厂，自定义配置没生效（不报错，难排查） |
| 7 | 手动 ack 忘了提交 | offset 不提交 → 重启后重复消费（配合幂等才安全） |

---

## 10. 一句话总结

```text
工厂 = @KafkaListener 的运行环境生产线（默认名 kafkaListenerContainerFactory）；
模式 A：规则写在 application.yml 的 spring.kafka.listener.*，Spring 代你 set，CommonErrorHandler 自动挂；
模式 B：规则写在代码里自己 set，错误处理器必须显式挂——代价换灵活（批量/并发/手动 ack）；
企业形态：A 为主、按需 B、多工厂共存；并发上限 = 分区数。
```
