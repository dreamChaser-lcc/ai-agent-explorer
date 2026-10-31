# T1 · Lombok：@Slf4j 与样板消除

> **状态**：✅ 学习中（2026-10-31，已改造 3 处示范）
> **前置**：[J7·注解体系](../Java基础实战/J7·注解体系.md)（注解的两种读取方式——运行时反射 vs 编译期处理）
> **下一课**：[T4·ErrorEnum](00·课程总纲.md)（错误码枚举化）
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单：Lombok 依赖存在、零使用；手写 Logger 31 处、纠偏 1 天）

---

## 一、为什么开这课（规范差距）

```text
项目现状（实测）：
  pom 已有 lombok 依赖 ✓
  手写 LoggerFactory.getLogger = 31 处（api / application / infrastructure 全部）
  @Slf4j 使用 = 29 处（另一批代码已经在用——混用状态）

规范要求（差距清单第 1 项）：
  Lombok @Slf4j 替换 31 处手写 Logger —— 预计 1 天
```

## 二、Lombok 的本质（和 J7 注解体系的关键区别）★

```text
J7 学过的注解（@Repository / @Transactional / @RequestMapping）：
  运行时读取——反射或动态代理（JVM 加载类之后）

Lombok：
  【编译期注解处理器】——javac 的 Annotation Processor API
  编译时读到 @Slf4j → 直接往 AST（抽象语法树）里插一个字段
  编译后的 .class 里【没有 @Slf4j 的痕迹】——标签已被"兑现"

→ 所以：
  · Lombok 没有运行时开销（连反射都没有）
  · 生成的字节码与手写【完全等价】（javap 可对比验证）
  · 但源码"看起来少了几行"——调试时 setter/getter 是"隐形"的（坑之一）
```

## 三、@Slf4j 生成的是什么（一字不差）

```java
// 你写：
@Slf4j
public class AuditLogService { }

// 编译后等价于（javap -p 可见）：
private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
```

**log 字段名固定为 `log`**——所以所有 `log.info(...)` 调用一行都不用改。

## 四、核心注解清单（按本项目用得上排序）

| 注解 | 替代的样板 | 项目场景 |
|---|---|---|
| **`@Slf4j`** | 3 行 Logger 声明 | ★ 31 处待替换 |
| **`@RequiredArgsConstructor`** | 构造器注入样板（final 字段逐个赋值） | SP1 构造器优先 → XML 更省 |
| `@Getter` / `@Setter` | getter/setter | Entity |
| `@Builder` | Builder 模式（工厂课） | DTO 可选 |
| `@NonNull` | 入参空检查 | 工具方法 |
| `@Value` | 不可变类全套 | record 的替代（J1） |
| `@Data` | getter+setter+equals+hashCode+toString | ⚠️ **Entity 上禁止**（见坑） |

## 五、坑与争议（团队规范要写清）

```text
① @Data 用在 JPA Entity 上 = 事故源：
   · equals/hashCode 包含全部字段（含 updatedAt / @Version）→ HashSet/Map 里"同 id 不同对象"判定失败
   · toString 打印懒加载关联 → 触发 N+1（场景⑪ 会讲）
   · 规范取向：Entity 只开 @Getter，setter 由业务方法控制

② IDE 需要 Lombok 插件（新版 IDEA 内置）——否则"红色波浪线"满天飞

③ 调试体验下降：断点里看不到 setter/getter 源码（但它们真实存在于 .class）

④ 团队分歧（面试可聊）：
   显式派：代码即文档，新人 0 门槛
   Lombok 派：样板代码是噪音，31 处 Logger 就是证据
   本项目结论：跟随 live-java 规范——用（差距清单明确要求纠偏）

⑤ 与 record 的边界（J1 课）：
   不可变数据载体 → record（无需 Lombok）
   可变 Entity / 需要继承 → class + Lombok
```

## 六、本次实操记录（3 处示范改造）

**改造方式**（每个文件 5 行 → 2 行，净减 3 行）：

```diff
-import org.slf4j.Logger;
-import org.slf4j.LoggerFactory;
+import lombok.extern.slf4j.Slf4j;

-@Service
+@Slf4j
+@Service
 public class AuditLogService {
-
-    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
```

**已改 3 个**（覆盖三个层）：

| 文件 | 层 | 注解位置 |
|---|---|---|
| `AuditLogService` | application | @Slf4j 在 @Service 前 |
| `ApiExceptionHandler` | api | @Slf4j 在 @RestControllerAdvice 前 |
| `TokenAuthFilter` | infrastructure | @Slf4j 在 @Component 前（类前有 3 个注解时注意别放错） |

**验证**：`mvn clean compile` 通过（COMPILE=0）；log 字段名一致，调用代码零改动。

**踩坑记录**：批量脚本按"@Xxx\npublic class"匹配注解位置——TokenAuthFilter 类前有 `@Component/@Order/@ConditionalOnProperty` 三个注解，正则没匹配到导致 @Slf4j 漏加 → 编译报 `找不到符号 log`。**教训：注解插入要按"类声明前最近位置"处理，不能假设只有一行注解**。

## 七、剩余 28 处：渐进式策略（不特意批量改）

**决策（2026-10-31 学员定）**：剩余 28 处**保持原样，不专项批量改造**——等文件有业务迭代时顺手改。

```text
为什么这个策略是对的：
① 手写 Logger 功能完全正常——专项批量改 = 纯风险无收益
   （改 28 个文件，每个都要回归验证日志输出，收益只是"少 3 行样板"）
② 顺手改 = 一次改动、一次验证、一次 review——
   风险被摊薄到本来就"要动这个文件"的时机里
③ 这是业界的"渐进式重构"原则：不做 big-bang 式全仓改造，
   只在"恰好要碰的文件"里完成迁移

执行约定（后续每次迭代某个文件时）：
  文件里有 `private static final Logger log = LoggerFactory.getLogger(...)`
  → 顺手换成类上 @Slf4j（按本课第六节模板）
  → 编译 + 跑一次该功能，确认日志照常
```

**当前进度**：31 处 → 已改 3 处（AuditLogService / ApiExceptionHandler / TokenAuthFilter），剩 28 处待迭代顺手改。

## 八、面试速答

```text
Q：Lombok 的原理？
A：编译期注解处理器（javac Annotation Processor）——编译时改 AST 生成代码，
   运行时零开销、字节码与手写等价；不是反射、不是代理。
   （对照：Spring 的 @Transactional 是运行时 AOP 代理——两类注解机制）

Q：@Data 有什么坑？
A：Entity 上禁止——equals/hashCode 含可变字段（@Version/updatedAt）破坏集合语义；
   toString 触发懒加载 N+1；setter 破坏封装。Entity 只建议 @Getter。

Q：为什么团队还用它？
A：样板是噪音（Logger/构造器/getter 三类最典型）；配合规范（Entity 限 @Getter）
   风险可控。分歧本质是"显式 vs 简洁"的取舍。
```

## 一句话

```text
Lombok = 编译期改 AST 的样板消除器（非运行时）
@Slf4j 一行 = Logger 三行，字节码等价、调用零改
@Entity 上禁用 @Data；注解插入要认准"类声明前最近位置"
```
