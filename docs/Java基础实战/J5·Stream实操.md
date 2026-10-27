# J5 · Stream 实操（精读讲义）

> **教材**：`ResearchTaskOrchestrator`（3 处）+ `ResearchReportAssemblyService` + `ApiExceptionHandler`
> **一句话主题**：把"流水线"拆到零件级——**惰性**、**中间/终结**、**短路**、**collect 家族**。
> **建立**：2026-10-05（含 StreamLab 动手实证）

---

## 0. 教材五处（都在项目里）

| # | 出处 | 流水线 |
|---|------|--------|
| 1 | `markFetchedSourcesAsCitationReady` | `filter → toList → forEach`（"先筛、再收、后办"）|
| 2 | `promoteNextStep` | `filter → findFirst → ifPresent`（Optional 链）|
| 3 | `markTaskCompleted` | `allMatch`（"全部完成？"）|
| 4 | `buildMarkdown` | `limit(5) → forEach`（+ StringBuilder）|
| 5 | `ApiExceptionHandler` 校验明细 | `map → toList` |

---

## 1. Stream 是什么？——"**你画地图，系统跑路**"

```java
// for 版（你带路）：造篮子 → 逐个看 → 合格就处理
List<String> result = new ArrayList<>();
for (String s : list) { if (s.startsWith("a")) result.add(s.toUpperCase()); }

// Stream 版（你画地图）：描述"要什么"
List<String> result = list.stream()
        .filter(s -> s.startsWith("a"))
        .map(String::toUpperCase)
        .toList();
```

**核心区别**：**声明式**（要什么）vs **命令式**（怎么走）。注意：`filter`/`map` 写下去时**不是在"干活"，是在"装机器"**。

---

## 2. ★ 惰性流水线（本课核心）

```text
数据 ─→ [filter 机] ─→ [map 机] ─→ [toList 出口]
        ↑ 中间操作："装机"——装的时候【机器不转】
                                        ↑ 终结操作："按总开关"
```

- **只装不按** = 一行数据都不跑（零成本，可随时扔掉反悔）；
- **按开关**（`toList()`）= 传送带启动，数据**逐元素流过**。

**实证**（StreamLab 第一幕）：`peek` 打印插在链上——**"拼装中/拼装完成"两行之间零输出**；按下 `toList()` 后 `[peek]` 才逐条刷出 ✓。

---

## 3. ★ 中间 vs 终结——一条口诀

> **"返回 `Stream` 的 = 中间（还能接）；返回别的 = 终结（交卷）"**

| 分类 | 方法 | 返回 |
|------|------|------|
| 中间 | `filter` / `map` / `sorted` / `limit` / `distinct` / `peek` | `Stream` |
| 终结 | `toList` | `List` |
| 终结 | `findFirst` | `Optional` |
| 终结 | `forEach` | `void` |
| 终结 | `count` | `long` |
| 终结 | `allMatch` / `anyMatch` | `boolean` |

**"流的尽头必须是一个答案"**——交卷后流**关闭**（再操作会报 `stream has already been operated upon or closed`）。

---

## 4. 逐元素流过（过程流 ≠ 阶段流）

按下开关后：**一个元素走完全程，再轮到下一个**（apple 先过 filter 再过 map；banana 被 filter 拦下直接"出线"，不碰 map）——**不是"先全员 filter 再全员 map"**。

---

## 5. 实战三场景（项目原文）

**① 筛来源（filter → toList → forEach）**

```java
List<SourceDocumentEntity> fetched =
        repo.findByTaskIdOrderByCreatedAtDesc(taskId).stream()
                .filter(s -> s.getSourceType() == SourceType.FETCHED_PAGE).toList();
fetched.forEach(s -> s.setCitationReady(true));
```

**② 找下一步（filter → findFirst → ifPresent）**——Optional 链吃掉判空：

```java
...orderByStepNoAsc(taskId).stream()
        .filter(step -> step.getStepNo() == currentStepNo + 1)
        .findFirst()
        .ifPresent(step -> { if (step.getStatus() == PENDING) { ...激活... } });
```

**③ 全完成？（allMatch）**：

```java
boolean allCompleted = ...stream().allMatch(step -> step.getStatus() == COMPLETED);
```

---

## 6. ★ 短路："答案需要多少数据，决定能不能短路"

| 会短路（找到答案就停） | 不短路（答案需要全部数据） |
|----------------------|--------------------------|
| `allMatch`（第一个 false 即停）| `count` |
| `anyMatch`（第一个 true 即停）| `forEach` |
| `findFirst` / `findAny` | `toList` / `sorted` |

**对照实验**：`allMatch(s -> 完成)`（100 步、第一个就 false → **只检查 1 个**）vs `filter(s -> 未完成).count() == 0`（**跑完全部**）。

---

## 7. collect 家族（StreamLab 实测）

```java
// 分组：组名 → 那一组的数据（Map）
Map<String, List<Task>> byStatus = tasks.stream().collect(Collectors.groupingBy(Task::status));
// 实测：WAITING_FOR_CONFIRMATION → 4 条；COMPLETED → 2 条

// 拼串：分隔符、前缀、后缀
String s = titles.stream().collect(Collectors.joining("、", "【", "】"));
// 实测：【测试任务-A、测试任务-B、…、旧任务-F】

// 两分组 + 计数（partitioningBy 只分 true/false 两组）
Map<Boolean, Long> counted = tasks.stream()
        .collect(Collectors.partitioningBy(t -> ..., Collectors.counting()));
// 实测：测试组=3，其他=3
```

**记忆**：`toList` 是 `collect` 的"专用简化版"；`collect` 是万能收口（分组/拼串/计数/转 Map）。

---

## 8. peek：调试窗（位置即视野）

**StreamLab 实测彩蛋**：`peek` 放在 `filter` **之前** → **6 条全被看到**（包括会被淘汰的"正式任务-D / 旧任务-E/F"）；若放在 filter 之后 → 只看到通过的 3 条——**"链上的位置 = 观察窗的位置"**（**顺序即语义**，J3 知识的延伸）。

---

## 9. for vs Stream（思考题收束）

**语义相同**（"找到即停"两者都会）、**性能等价**（别拿性能当理由——Stream 开销纳秒级）。

差别在**表达**：

| | for | Stream |
|---|-----|--------|
| 读法 | "怎么一步步走"（脑子里模拟执行）| "我要什么"（像一句话）|
| 需求变更 | 动骨架 | 换词（`findFirst`→`toList`）|
| 反超场合 | 复杂流程控制 / 要索引 / **调试**（一步步走）| 筛/改/找/聚合（数据加工）|

> **代码是写一次读一百次的**——"让读的人少花脑力"是主要优化方向。

---

## 10. 动手实录（StreamLab，2026-10-05）

| 幕 | 实测结果 | 判定 |
|----|---------|------|
| 惰性 | 拼装阶段零输出；`toList()` 后 `[peek]` 逐条刷出 | ✅ 铁证 |
| peek 位置 | peek 在 filter 前 → **6 条全路过**（含被淘汰的 3 条）| ✅ "位置即视野" |
| groupingBy | `WAITING=4 / COMPLETED=2` | ✅ |
| joining | `【测试任务-A、…、旧任务-F】` | ✅ |
| partitioningBy+counting | 测试组=3 / 其他=3 | ✅ |

---

## 11. 项目关联地图

| 零件 | 现身处 |
|------|--------|
| `filter→toList→forEach` | `ResearchTaskOrchestrator.markFetchedSourcesAsCitationReady` |
| `findFirst→ifPresent` | `ResearchTaskOrchestrator.promoteNextStep` |
| `allMatch` | `ResearchTaskOrchestrator.markTaskCompleted` |
| `limit→forEach` | `ResearchReportAssemblyService.buildMarkdown` |
| `map→toList` | `ApiExceptionHandler` / `TaskResponseMapper` / 各 QueryService |
| 全项目 `.stream()` | 18 处（`collect` 家族目前 0 处——认识即为储备）|

---

## 补充：思考题与答案（课堂问答收录）

**题 1**——`promoteNextStep` 为什么用 `filter + findFirst`，而不是 `for` 循环 + `break`？

**答**：**先钉死"相同"**：论机器行为，两者**完全等价**（都"找到即停"）；性能也基本等价（Stream 框架开销纳秒级）——**别拿性能当理由**。

**差别在"给读代码的人"**：

| | for 版 | Stream 版 |
|---|--------|-----------|
| 读法 | "**怎么一步步走**"——遍历→判断→动作→break，脑子里得模拟执行 | "**我要什么**"——"筛出下一步 → 取第一个 → 有就激活"，像一句话 |
| 需求变更 | 动骨架（删 break、改结构） | 换词（`findFirst`→`toList`；`ifPresent`→`orElseThrow`） |

**根本理由**：**代码是"写一次、读一百次"的东西**——让读的人少花脑力，是优化的主要方向。

**但 for 也有反超场合**（诚实说）：复杂流程控制（多分支/嵌套/提前 return）、需要索引 `i` 运算、**需要逐步调试**（Stream 断点进 lambda 层层难跟——`peek` 就是为调试而生）。

**口诀**：**筛/改/找/聚合（数据加工）→ Stream；流程控制/要索引/要调试 → for。工具没有高下，看场合。**

**题 2**——`allMatch`：100 个步骤、第 1 个就不 `COMPLETED`——实际检查几个？

**答**：**只检查 1 个**——`allMatch` **短路**：遇见第一个 `false` 立刻返回 `false`。

**✦ 深化——"答案需要多少数据，决定能否短路"**：

| 会短路（答案一到手就停） | 不短路（答案天然需要全部数据） |
|------------------------|------------------------------|
| `allMatch`（第一个 false 即停）/ `anyMatch` / `findFirst` | `count` / `forEach` / `toList` / `sorted` |

**对照实验**：`filter(s -> 未完成).count() == 0` 即使第一个就"未完成"，也要**把 100 个全数完**——因为它要的是"准确数字"。

---

## 12. 下一课预告（J6：枚举）

- **教材**：`TaskStatus` / `ExecutionMode` / `EventType` / `OperatorType`……（项目 20+ 个枚举）；
- **主题**：为什么"状态"都用枚举（J4 的 `StepStatus.PENDING` 已经露脸）——枚举的形态、方法、与 switch/数据库的协作。

---

## 附：本课一句话总结

1. **Stream = 画地图**（声明"要什么"），for = 带路（说"怎么走"）；
2. **惰性**：中间操作只"装机"，终结操作才"按开关"——半途可反悔（零成本）；
3. **口诀**："返回 Stream = 中间；返回别的 = 终结"——流的尽头必须是个答案；
4. **短路**："答案需要多少数据，决定能不能短路"（allMatch 只查 1 个 vs count 全跑）；
5. **peek 位置即视野**；**collect 家族**=分组/拼串/计数（`toList` 是它的简化版）。

---

## 进阶一：实操记录——项目真实用例（2026-10-27 补，全 60 处用法中的代表作）

### 用例 1：SQL IN 占位符生成（RetentionCleanupDao）

```java
// 需求：拼出与状态数量一致的 "?,?,?..."——防 SQL 注入的标准姿势（不能直接拼值）
String placeholders = statuses.stream()
        .map(s -> "?")
        .collect(Collectors.joining(","));
// statuses = [COMPLETED, FAILED]  →  "?,?"
```

```text
解读：map（每个元素换成 "?"）→ joining（逗号连接）
配套：DELETE ... WHERE status IN (?,?)
```

### 用例 2：批量日志拼接（RetentionCleanupDao / TokenAuthFilter）

```java
String in = batch.stream()
        .map(id -> "?")
        .collect(Collectors.joining(",", "(", ")"));
// → "(?,?,?)"   带前后缀的 joining

 TokenAuthFilter 里：.collect(Collectors.joining("\n  "))   // 多行缩进日志
```

### 用例 3：实体 → DTO 映射（TaskResponseMapper 等全部 Mapper）

```java
List<TaskSummaryResponse> list = tasks.stream()
        .map(this::toResponse)          // 方法引用：Entity → VO
        .toList();
```

```text
这是项目里最普遍的形态：查询结果 Entity 列表 → 响应 VO 列表
（"对象变身"那一课的第五步，Stream 是它的执行工具）
```

---

## 进阶二：Collector 家族（对照 SQL 学最快）

| Collector | 作用 | SQL 对照 | 项目场景 |
|---|---|---|---|
| `toList()` | 收集为 List | `SELECT` 结果集 | Mapper 映射 |
| `joining(",")` | 字符串拼接 | —（应用层活） | 占位符、日志 |
| `groupingBy(f)` | 按键分组 `Map<K,List>` | `GROUP BY` | 按状态分组任务 |
| `groupingBy(f, counting())` | 分组计数 `Map<K,Long>` | `GROUP BY + COUNT` | 状态统计 |
| `toMap(k, v)` | 键值 Map | — | id → 实体索引 |
| `partitioningBy(pred)` | 按布尔二分 | `WHERE` 二桶 | 成功/失败分桶 |
| `counting() / summingInt()` | 计数/求和 | `COUNT/SUM` | 汇总 |

```java
// groupingBy 实战：按状态统计任务数（= SELECT status, COUNT(*) GROUP BY status）
Map<TaskStatus, Long> count = tasks.stream()
        .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));

// toMap 实战：建 id → title 索引
Map<UUID, String> index = tasks.stream()
        .collect(Collectors.toMap(Task::getId, Task::getTitle));
```

---

## 进阶三：三个高频易错点（面试与踩坑通用）

### ① `map` vs `flatMap`（一对多展开）

```java
// map：1 → 1（每个任务 → 它的步骤数）
tasks.stream().map(t -> t.getSteps().size())

// flatMap：1 → N（每个任务的步骤列表 → 摊平成一条流）
tasks.stream().flatMap(t -> t.getSteps().stream())   // Stream<Step>，不是 Stream<List<Step>>
```

```text
记忆：flatMap = map + flatten（先映射成流，再摊平）
场景："所有任务的全部步骤"这种嵌套结构拉直
```

### ② `toMap` 的键冲突（直接炸）

```java
// 两个元素同键 → IllegalStateException: Duplicate key
// 解决：给第三个参数（merge 函数）
Collectors.toMap(Task::getTaskNo, t -> t,
                 (a, b) -> a)        // 冲突时保留先出现的
```

### ③ `parallelStream` 的适用边界（能不用就不用）

```text
适合：CPU 密集 + 数据量万级以上 + 无序容忍
不适合（都会更慢或出事）：
  · IO 密集（DB/HTTP）→ 线程切换开销远大于收益，还会占满公共池
  · 小数据量（<1000）→ 并行开销倒挂
  · 共享可变状态 → 竞态（Stream 依然要遵守 J8 的"共享+可变"铁律）
默认公共 ForkJoinPool——一个阻塞，全应用的其他 parallelStream 跟着饿
```

---

## 面试级问答（2026-10-27）

**Q1：Stream 的惰性求值是什么？**

```text
中间操作（map/filter/flatMap/sorted/peek）只是【登记】要做什么，不执行
终端操作（collect/forEach/count/reduce/anyMatch）才触发整条流水线一次性跑完
好处：多步操作可被"融合"成单遍循环（fused）——不是每次 filter 都遍历一遍
```

**Q2：中间操作 vs 终端操作怎么分？**

```text
看返回值：
  返回 Stream 的 = 中间（还能继续 .xxx）
  返回结果/void 的 = 终端（collect/toList/count/forEach/reduce）
另外：一个 Stream 实例只能被终端消费【一次】——再来一次抛 IllegalStateException
     （要用第二次：重新 stream() 从源开始）
```

**Q3：Stream 比 for 循环慢吗？**

```text
小数据量：略慢（lambda + 对象包装开销）
大数据量：JIT 优化后基本持平
业务代码：可读性收益 > 微小的性能差异 → 优先 Stream
热点路径（Profile 证明是瓶颈）：再换 for / 手工优化
```

### 一句话

```text
Stream = 声明式的"数据流水线"：中间操作登记、终端操作触发、只消费一次
项目三大真实用法：占位符 joining / Mapper 映射 toList / 分组 groupingBy
三个坑：flatMap 才能摊平、toMap 冲突要 merge、parallelStream 慎用（IO 场景反伤）
```
