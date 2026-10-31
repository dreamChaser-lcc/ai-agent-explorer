# T2 · MapStruct：编译期映射

> **状态**：✅ 学习中（2026-10-31，依赖 + 处理器 + 演示 Mapper + 生成源码实测）
> **前置**：[T1·Lombok](T1·Lombok.md)（同一类机制——编译期注解处理器）/ [J1·语法形态](../Java基础实战/J1·语法形态精读.md)（record 作为映射目标）
> **上一课**：[T1·Lombok](T1·Lombok.md)
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单：MapStruct 缺失、手写 Mapper，纠偏半天）

---

## 一、现状：3 个手写 Mapper（实测 186 行）

```text
TaskResponseMapper.java  117 行（多个 toXxx 方法，每个方法手工列字段）
ResearchTaskMapper.java   46 行
TaskEventLogMapper.java   23 行

手写样板（toTaskSummary——9 个字段逐个摆）：
  return new TaskSummaryResponse(
          task.getId(), task.getTaskNo(), task.getTitle(),
          task.getExecutionMode(), task.getStatus(), task.getCurrentStage(),
          task.isRequiresConfirmation(), task.getCreatedAt(), task.getUpdatedAt());
```

**手写的三个痛点**：
```text
① 漏字段无编译检查 —— 少写一个参数？编译照样过（构造器允许 null），运行时响应少字段
② 字段改名时无提示 —— Entity 改 getTitle→getTaskTitle，Mapper 静默变 null
③ 与 T1 的结论相关：纯样板，噪音
```

## 二、MapStruct 机制（和 T1 Lombok 是同一类）★

```text
T1 学过的：Lombok = 编译期注解处理器（javac Annotation Processor）改 AST

MapStruct = 同样是编译期注解处理器，但做的是【生成整份实现类】：
  @Mapper 接口 → 编译时生成 XxxMapperImpl.java（在 target/generated-sources）
  → 编译进 jar，运行时就是普通方法调用（零反射！）

三方对比：
┌─────────────┬──────────────┬──────────────┬────────────────────┐
│             │ 手写 Mapper   │ BeanUtils     │ MapStruct          │
├─────────────┼──────────────┼──────────────┼────────────────────┤
│ 运行时开销   │ 0            │ 反射（慢）    │ 0（编译期生成）     │
│ 漏字段检查   │ ❌ 静默 null  │ ❌ 静默 null  │ ✅ 编译期警告/报错 │
│ 代码量       │ 多（样板）    │ 少            │ 中（接口声明）     │
│ 可控性       │ 最高          │ 低（全拷贝）  │ 高（@Mapping 定制）│
└─────────────┴──────────────┴──────────────┴────────────────────┘
```

## 三、pom 配置（三件套，缺一不生效）

```xml
<!-- ① 编译期 API（注解 + 接口定义） -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.6.3</version>
</dependency>

<!-- ② 处理器（provided——只在编译期在） -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.6.3</version>
    <scope>provided</scope>
</dependency>

<!-- ③ Lombok 与 MapStruct 的协调器（两者同为注解处理器，必须排队） -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok-mapstruct-binding</artifactId>
    <version>0.2.0</version>
    <scope>provided</scope>
</dependency>
```

**关键：`maven-compiler-plugin` 的 `annotationProcessorPaths` 必须登记处理器**（本项目原来只登记了 Lombok——T1 的机制）：

```xml
<annotationProcessorPaths>
    <path>... lombok ...</path>
    <path>... mapstruct-processor ...</path>
    <path>... lombok-mapstruct-binding ...</path>
</annotationProcessorPaths>
```

## 四、实测：一个接口 = 一整份实现（生成的代码长什么样）

**你写的（30 行）**：

```java
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TaskSummaryMapper {
    TaskSummaryResponse toSummary(ResearchTaskEntity entity);
}
```

**MapStruct 生成的（`target/generated-sources/annotations/.../TaskSummaryMapperImpl.java`）**：

```java
@Generated(value = "org.mapstruct.ap.MappingProcessor", date = "...", comments = "version: 1.6.3")
@Component   // ← componentModel = SPRING 的直接效果
public class TaskSummaryMapperImpl implements TaskSummaryMapper {
    @Override
    public TaskSummaryResponse toSummary(ResearchTaskEntity entity) {
        if (entity == null) return null;

        UUID id = entity.getId();                    // ← 9 个字段全部逐行展开
        String taskNo = entity.getTaskNo();          //   同名字段自动映射
        boolean requiresConfirmation = entity.isRequiresConfirmation();  // ← is 前缀自动识别
        ...
        return new TaskSummaryResponse(id, taskNo, title, ...);  // ← record 走全参构造器
    }
}
```

**实测结论**：
- 字段名相同 → 零配置自动映射
- `boolean isXxx()` → 目标组件 `xxx` 自动对上
- 目标 record（J1 的不可变载体）→ MapStruct 1.6 直接支持（走全参构造器）
- 生成类带 `@Component` → 可直接注入使用

## 五、本次踩的两个坑（pom 相关）

```text
坑①：`</dependencies>` 用 Replace 时命中了两处——
     第一处是 <dependencyManagement> 的结尾 → 依赖被误插进"版本管理区"
     教训：pom 里 </dependencies> 不唯一；替换要用更长的上下文锚

坑②：annotationProcessorPaths 的 Replace 缩进不匹配 → 处理器没配上
     表现：编译通过但 target/generated-sources 里【没有 Impl】
     教训：改完 pom 必须验证生成的源码存在（否则"以为配好了"）
```

## 六、渐进式迁移约定（与 T1/T4 一致）

```text
现有 3 个手写 Mapper 不动（功能正常）；
新写映射优先 MapStruct 接口；
迭代旧 Mapper 时顺手替换（保持一次改动一次验证）。
```

## 七、面试速答

```text
Q：MapStruct 和 BeanUtils 的区别？
A：MapStruct 编译期生成实现（零反射、字段对不齐编译期就报）；
   BeanUtils 运行时反射（慢、静默失败）。性能与安全都是 MapStruct 赢。

Q：MapStruct 和 Lombok 能一起用吗？要注意什么？
A：能，但两者都是注解处理器——需要 lombok-mapstruct-binding 协调，
   且 annotationProcessorPaths 里要按顺序登记两个处理器。

Q：@Mapping 什么时候必须写？
A：字段名不一致（source/target 不同名）、需要转换（日期/枚举/嵌套）时；
   同名同类型可省。
```

## 一句话

```text
MapStruct = 编译期把"接口声明"翻译成"整份实现类"（与 Lombok 同类机制）
零反射、漏字段编译期就报；三件套（api + processor + lombok 协调器）缺一不生效
配置完必须验证 target/generated-sources 里真的生成了 Impl
```
