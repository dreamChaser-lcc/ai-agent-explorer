# T2 · MapStruct：编译期映射

> **状态**：✅ 学习中（2026-10-31，依赖 + 处理器 + 演示 Mapper + 生成源码实测）
> **前置**：[T1·Lombok](T1·Lombok.md)（同一类机制——编译期注解处理器）/ [J1·语法形态](../Java基础实战/J1·语法形态精读.md)（record 作为映射目标）
> **上一课**：[T1·Lombok](T1·Lombok.md)
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单：MapStruct 缺失、手写 Mapper，纠偏半天）

---

## 涉及代码文件（点击阅读）

| 文件 | 本课中的作用 |
|---|---|
| [`pom.xml`](../../backend/pom.xml) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/pom.xml) | 依赖三件套 + annotationProcessorPaths 登记处 |
| [`TaskSummaryMapper.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/mapper/TaskSummaryMapper.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/mapper/TaskSummaryMapper.java) | 演示接口（编译生成 Impl 的源头） |
| [`TaskResponseMapper.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskResponseMapper.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/TaskResponseMapper.java) | 手写版对照（本课要替代的对象） |

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

## 附录：更多例子——不只是「一个入参」（2026-10-31 学员追问）

### 先回答：`toSummary(ResearchTaskEntity entity)` 是不是"全部参数"？

```text
是——但只是【最简单形态】：1 个源对象 → 1 个目标对象
MapStruct 的真实能力（入参任意、出参三种）：
  入参：1 个 / N 个源对象（多源合并）
  出参：① 返回新对象 ② void + @MappingTarget（更新已有对象） ③ List/Set（集合批量）

而且 MapStruct 的匹配粒度是【字段级】不是【参数级】：
  默认规则 = 源 getter 的同名字段 ↔ 目标的构造参数/属性（自动对上）
  名字不同 / 类型不同 / 多源歧义 → 才需要 @Mapping 手工干预
```

### 五个例子（`MapStructShowcaseMapper`，全部实测编译通过）

**例 1：单源自动映射**（基础态）

```java
TaskSummaryResponse toSummary(ResearchTaskEntity entity);
// 9 个同名字段全自动；boolean isRequiresConfirmation() 自动识别
```

**例 2：多源合并（两种写法，推荐 default 组装）**

**写法 A：MapStruct 原生多源参数**（mvn 可用，IDE 会报错——见下方坑）

```java
@Mapping(target = "id", source = "task.id")          // 多源同名歧义：必须逐个指定
@Mapping(target = "status", source = "task.status")
@Mapping(target = "latestPlanSummary", source = "plan.planSummary")   // 名字不同：指定
@Mapping(target = "plannedSteps", ignore = true)
TaskDetailResponse toDetail(ResearchTaskEntity task, ResearchPlanEntity plan);
```

**写法 B：default 方法组装（本项目采用，IDE 友好）**

```java
default TaskDetailResponse toDetail(ResearchTaskEntity task, ResearchPlanEntity plan) {
    TaskDetailResponse base = toDetailFromTask(task);      // 单源映射（编译期生成）
    if (base == null) return null;
    return new TaskDetailResponse(base.id(), base.taskNo(), ...,
            plan != null ? plan.getPlanSummary() : null,   // 手工桥接
            null, base.createdAt(), base.updatedAt());
}

@Mapping(target = "latestPlanSummary", ignore = true)      // 无来源字段显式 ignore
@Mapping(target = "plannedSteps", ignore = true)
TaskDetailResponse toDetailFromTask(ResearchTaskEntity task);   // ← MapStruct 只生成这个
```

**★ 本次实测踩的三个坑（真实错误，讲义留档）**：

```text
坑①（mvn 编译期）：多源同名字段歧义
  id/status/createdAt/updatedAt 在 task 和 plan 里都存在
  → javac: "Several possible source properties for target property id"
  → 必须逐字段 @Mapping(source="参数名.字段")

坑②（IDE 报错，mvn 反而通过）：多源参数依赖 -parameters
  IDE（Eclipse JDT 系 / VSCode Java 扩展）不传 -parameters
  → MapStruct 解析不到 "task"/"plan" 参数名
  → IDE 报 "No implementation was created ... erroneous element null"（第 24 行接口声明）
  → 命令行 mvn 有 <parameters>true</parameters> 所以通过——【两套编译路径行为不一致】

坑③（解法权衡）：
  方案 1：给 IDE 补参数名（vscode settings/重启语言服务器）——不稳定，团队环境不一致
  方案 2（采用）：多源合并改用 default 方法组装 —— 编译期零参数名依赖，
         IDE 与命令行行为一致，且逻辑（谁覆盖谁）更显式
```
**例 3：集合映射（List 自动逐元素）**

```java
List<TaskSummaryResponse> toSummaries(List<ResearchTaskEntity> entities);
```

生成代码（自动循环 + 复用 toSummary）：

```java
List<TaskSummaryResponse> list = new ArrayList<TaskSummaryResponse>(entities.size());
for (ResearchTaskEntity researchTaskEntity : entities) {
    list.add(toSummary(researchTaskEntity));
}
return list;
```

**例 4：@MappingTarget —— 更新已有对象**（不创建新实例）

```java
void copyInto(ResearchTaskEntity source, @MappingTarget ResearchTaskEntity target);
// 把 source 字段逐个 set 进 target（要求 target 有 setter）
// record 不能当 target（不可变）；Entity 可以
```

### 多源参数的前置条件（一个容易漏的配置）

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <parameters>true</parameters>   <!-- ★ 保留参数名，否则 source="plan.xxx" 解析不了 -->
    <annotationProcessorPaths>...</annotationProcessorPaths>
  </configuration>
</plugin>
```

### 速查：什么时候必须写 @Mapping

| 场景 | 需要 @Mapping 吗 | 写法 |
|---|---|---|
| 字段同名同类型 | 不写 | 自动 |
| 字段名不同 | 必须 | `@Mapping(target="a", source="b")` |
| 多源有同名字段（歧义） | 必须 | `@Mapping(target="id", source="task.id")` |
| 目标有、源没有 | 建议显式 | `@Mapping(target="x", ignore = true)` |
| 需要常量值 | 可选 | `@Mapping(target="x", constant="固定值")` |
| 需要自定义逻辑 | 可选 | `@Mapping(target="x", expression="java(...)")` |

### 一句话

```text
MapStruct 的"自动"是字段级的同名字段匹配；入参可以任意多个、出参三种形态
名字不同 / 类型不同 / 多源同名 → 才需要 @Mapping 手工指定
多源参数必开 <parameters>true</parameters>，否则参数名解析不了
```
## 附录二：完整调用链路——Mapper 在哪一环、替代了什么（2026-11-01 学员追问）

### 一、真实链路图（项目当前代码）

```text
HTTP GET /api/tasks?page=0&size=10
   ↓
ResearchTaskController.listTasks()                  [api 层]
   ↓
ResearchTaskQueryService.listTasks()                [application 层]
   ├─ TaskRepository 查询 → List<ResearchTaskEntity> [infrastructure，查库]
   ├─ ★ .map(taskResponseMapper::toTaskSummary).toList()
   │       ↑↑↑ Mapper：Entity → Response 的"翻译哨卡"
   ↓
PageResponse<TaskSummaryResponse>                   [api 层契约 record]
   ↓
Jackson 序列化 → JSON

详情端点（同模式）：
GET /api/tasks/{id} → Controller → Service：
   findById + PlanRepository + StepRepository → ★ toTaskDetail(task, plan, steps)
```

### 二、Mapper 这一步在干嘛

```text
输入：ResearchTaskEntity   —— 胖对象（18+ 字段、JPA 注解、懒加载、敏感字段）
输出：TaskSummaryResponse —— 瘦契约（record，9 字段，纯数据）

必须要有这层的四个理由：
① Entity 含敏感/内部字段（createdBy / idempotencyKey 不该给前端）
② 契约稳定：Entity 改字段不该影响前端 JSON
③ 序列化 Entity 可能触发懒加载 → N+1（场景⑪）
④ 两表 → 一响应（详情端点）必须有地方合并
```

### 三、"替代了什么"——三个层次

**层次 1：字段搬运样板 ✅ 完全替代**

```java
// 手写（TaskResponseMapper.toTaskSummary 真实代码）：9 个字段逐个抄
return new TaskSummaryResponse(task.getId(), task.getTaskNo(), task.getTitle(), ...);

// MapStruct：接口 1 行，编译生成同样的 new + get
TaskSummaryResponse toSummary(ResearchTaskEntity entity);
```

| 维度 | 手写 | MapStruct |
|---|---|---|
| 代码量 | 9 行 new | 1 行声明 |
| 漏字段 | 静默 null | 编译期警告 |
| 字段改名 | 静默出错 | 编译期报警 |
| 30 个 DTO | 30 个方法手抄 | 30 个 1 行声明 |

**层次 2：也替代 BeanUtils 反射方案 ✅**

```java
BeanUtils.copyProperties(...)  // 运行时反射：慢 + 静默 + 无类型检查
MapStruct                      // 编译期生成：零反射 + 编译检查
```

**层次 3：不替代业务派生逻辑 ❌**

```java
// toTaskDetail 里 MapStruct 做不到的部分：
steps.stream().map(ResearchStepEntity::getTitle).toList()   // 跨集合加工
latestPlan != null ? latestPlan.getPlanSummary() : null      // 条件组装

处理方式：① default 方法手写（ShowcaseMapper.toDetail 即此法）
        ② @Mapping(expression = "java(...)")
        ③ MapStruct 基础映射 + Service 手工补字段（生产最常见）
```

### 四、项目现状（诚实版）

| Mapper | 性质 | 调用方 |
|---|---|---|
| TaskResponseMapper（手写 117 行） | **生产在用** | 3 个 Service（TaskQuery/Command/ReportQuery） |
| TaskSummaryMapper（MapStruct） | 演示 | **0** |
| MapStructShowcaseMapper（4 例） | 演示 | **0** |

```text
"替代"目前是【示例级证明】：已证明同样需求下 1 行声明 + 编译校验 + 零反射
真正落地步骤（渐进式）：
① 纯字段搬运方法（toTaskSummary/toResearchReport）→ 直接进 MapStruct 接口
② 带派生逻辑的（toTaskDetail）→ default 方法组装
③ Service 注入类型替换，调用方零改
④ 编译 + 逐字段断言测试
```

### 一句话

```text
调用链：Controller → Service →（Repository → Entity）→ ★Mapper → Response → JSON
Mapper 站在"数据库对象 → 接口契约"的边界：
  替代字段搬运样板（顺带替代 BeanUtils 反射）
  不替代业务派生逻辑（default 方法补）
现状：手写 Mapper 仍是生产主力，MapStruct 是已验证可替换的方案
```
## 附录三：编译后长什么样——TaskSummaryMapperImpl 全文解剖（2026-11-01 学员问）

### 一、生成位置

```text
backend/target/generated-sources/annotations/
  com/aiexplorer/researchagent/application/mapper/TaskSummaryMapperImpl.java   （51 行）
```

### 二、全文（真实产物）

```java
@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-11-01T10:12:25+0800",
    comments = "version: 1.6.3, compiler: Eclipse JDT (IDE) 3.46.100, environment: Java 21.0.12"
)
@Component
public class TaskSummaryMapperImpl implements TaskSummaryMapper {

    @Override
    public TaskSummaryResponse toSummary(ResearchTaskEntity entity) {
        if ( entity == null ) {
            return null;                                   // ③ 防御性 null 检查
        }

        UUID id = null;                                    // ④ 先按 record 组件顺序声明局部变量
        String taskNo = null;
        ...
        boolean requiresConfirmation = false;

        id = entity.getId();                               // ④ 再逐个 getter 赋值
        requiresConfirmation = entity.isRequiresConfirmation();   // is 前缀自动识别
        ...

        TaskSummaryResponse taskSummaryResponse = new TaskSummaryResponse(   // ⑤ record 全参构造
                id, taskNo, title, executionMode, status, currentStage,
                requiresConfirmation, createdAt, updatedAt );
        return taskSummaryResponse;
    }
}
```

### 三、五段解读

| 段 | 内容 | 来源 |
|---|---|---|
| ① `@Generated` | 生成水印（**含处理器版本 + 编译器 + JDK**） | MapStruct 自动加；排查 "mvn 过 IDE 报错" 时看 compiler 字段 |
| ② `@Component` | 进 Spring 容器，Service 可直接注入 | 你的 `componentModel = SPRING` |
| ③ `if (entity == null) return null` | 源为 null 不抛 NPE | MapStruct 默认策略（可配置） |
| ④ 局部变量声明 + 逐个赋值 | 固定模板（便于插入转换/表达式/null 兜底） | MapStruct 生成策略 |
| ⑤ `new TaskSummaryResponse(...)` | record 走 canonical constructor | 目标 record → 全参构造（所以 record 不能当 @MappingTarget） |

### 四、与手写版对照

```java
// 手写（TaskResponseMapper.toTaskSummary）：直接 new 里逐字段 get
// 生成（TaskSummaryMapperImpl）：null 检查 + 局部变量中转 + new
// 本质完全等价（零反射、零运行时开销）——差别只在"谁写这 9 个字段"
```

### 五、编译前后全景

```text
编译前：接口 1 行声明
编译后：
  target/classes/.../TaskSummaryMapper.class        ← 接口
  target/classes/.../TaskSummaryMapperImpl.class    ← 真正执行的实现
  target/generated-sources/.../Impl.java            ← 可读源码（可调试、可 diff）

运行时：Spring 扫 @Component → 注入 Service → toSummary 执行字段搬运
```