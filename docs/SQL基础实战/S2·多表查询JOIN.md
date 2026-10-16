# S2 · 多表查询 JOIN 家族

> **本课目标**：把 11 张表关联起来查——四大家族怎么选、`ON` 条件怎么写、别名为什么必须、忘写 `ON` 会发生什么、一对多为什么返回"重复行"。
> **教材**：`backend/src/main/resources/schema-dev.sql`（11 张表的真实关联关系）
> **前置**：S1（SELECT / WHERE / ORDER BY / LIMIT）

---

## 一、前端类比：JOIN ≈ 多次请求后的数据组装

你写过这种"组装页面数据"的代码：

```js
// 任务详情页：三个接口来回，JS 里拼装
const [task, plans, steps] = await Promise.all([
  fetch('/api/tasks/' + id).then(r => r.json()),
  fetch('/api/tasks/' + id + '/plans').then(r => r.json()),
  fetch('/api/tasks/' + id + '/steps').then(r => r.json()),
]);
const pageData = { ...task, plans, steps };   // ← 这一步"拼装"就是 JOIN 干的事
```

SQL 的 JOIN 就是把"**多次请求 + JS 拼装**"合并成**一次查询**，拼装动作在数据库内部完成：

```sql
SELECT t.title, p.plan_summary, s.title
FROM research_task t
JOIN research_plan p ON p.task_id = t.id     -- 拼上"计划"
JOIN research_step s ON s.task_id = t.id     -- 拼上"步骤"
WHERE t.id = '...';
```

三种对应关系（决定用哪种 JOIN 的关键）：

| 关系 | 项目实例 | 前端类比 |
|---|---|---|
| **一对一** | 任务 ↔ 报告（`research_report.task_id` 有唯一索引） | `{ ...task, report }` |
| **一对多** | 任务 → 步骤（一个任务多个步骤） | `{ ...task, steps: [...] }` |
| 多对多 | 本项目暂无（中间表模式，S8+ 按需） | — |

---

## 二、四大家族（记住 LEFT 就够了，但四个都要认识）

以 `research_task`（左，15 行）JOIN `research_plan`（右，有计划的任务才有行）为例：

| 写法 | 保留哪些行 | 无匹配时 | 使用率 |
|---|---|---|---|
| `JOIN` / `INNER JOIN` | **两边都匹配**的交集 | 直接丢掉 | 中 |
| `LEFT JOIN` | **左表全部** + 右表匹配部分 | 右表列填 `NULL` | ★ 最高 |
| `RIGHT JOIN` | 右表全部 + 左表匹配部分 | 左表列填 `NULL` | 低（可改写 LEFT） |
| `FULL JOIN` | 两边全部的并集 | 缺失方填 `NULL` | 很低 |

文氏图文字版：

```text
INNER JOIN   只有重叠部分          ██░░
LEFT JOIN    整个左圆 + 重叠部分    ███░   （右圆多出来的部分不要）
```

**项目里 90% 的场景用 LEFT JOIN**：列表/详情要的是"**所有任务**"，计划、报告、错误信息这些附属数据**可能有、可能没有**（任务刚创建时什么都没有）——没匹配也不能让任务消失，右表列填 NULL 即可。这跟 S1 学的"NULL = 还没发生"完美对上。

**INNER JOIN 的典型用途**：只关心"两边都有"的数据，比如"统计所有**已经生成过计划**的任务"。

---

## 三、ON 条件：JOIN 的"关联规则"

`ON` 回答一个问题：**左表的哪一行 配 右表的哪一行**。

本项目的表结构有个重要特点——**逻辑外键**：`schema-dev.sql` 里只有关联列（`task_id`、`step_id`、`plan_id`），**没有物理 `FOREIGN KEY` 约束**。关联规则不靠数据库强制执行，靠 **JOIN 时手写 ON 条件**：

```sql
-- 关联规则 = "右表的 task_id 等于左表的 id"
FROM research_task t
JOIN research_plan p ON p.task_id = t.id
```

`schema-dev.sql` 的关联全景（箭头 = `ON` 条件，`←` 指向被关联方）：

```text
research_task (id) ──┬──← research_plan.task_id        （一个任务多个计划版本）
                     ├──← research_step.task_id        （一个任务多个步骤）
                     ├──← research_report.task_id      （一对一：唯一索引）
                     ├──← task_event_log.task_id       （一个任务多条事件）
                     ├──← human_confirmation.task_id
                     ├──← source_document.task_id
                     └──← step_execution.task_id
research_plan (id) ──← research_step.plan_id
research_step (id) ──┬──← step_execution.step_id
                     ├──← source_document.step_id
                     └──← research_step.depends_on_step_id   （自引用：步骤依赖步骤）
```

**`task_id` 是万能关联键**——所有业务表都挂它，JOIN 时第一个 ON 基本永远是 `ON x.task_id = t.id`。

---

## 四、逐行翻译 + 别名必修课

真实三表查询（任务详情页要的东西）：

```sql
SELECT t.title AS task_title,          -- t = research_task 的别名
       s.title AS step_title,          -- s = research_step 的别名
       s.status AS step_status
FROM research_task t
JOIN research_step s ON s.task_id = t.id
WHERE t.status = 'WAITING_FOR_CONFIRMATION'
ORDER BY t.created_at DESC, s.step_no;
```

| 片段 | 人话 |
|---|---|
| `research_task t` | 给表起别名 `t`（`AS` 可省）——之后用 `t.` 指代它的列 |
| `ON s.task_id = t.id` | 关联规则：步骤属于这个任务 |
| `t.title AS task_title` | **必须写 `t.` 前缀**——两表都有 `title` 列，不写数据库不知道你要哪个 |

**两个必踩坑**：

1. **列名歧义**：`research_task` 和 `research_step` **都有 `title`、`id`、`created_at`**。多表查询时 `SELECT title` 直接报错——必须 `t.title` / `s.title`。所以**别名不是可选项，是生存必需品**。
2. **JOIN 链可以无限接**：`JOIN a ON ... JOIN b ON ... JOIN c ON ...`，每个 JOIN 解决一张表，条件是独立的。

---

## 五、INNER vs LEFT 对比实验（本课核心体感）

同一条查询，只改 JOIN 类型：

```sql
-- A：只要"有计划"的任务
SELECT t.title, p.plan_summary
FROM research_task t
JOIN research_plan p ON p.task_id = t.id;

-- B：所有任务，没计划的 plan_summary 显示 null
SELECT t.title, p.plan_summary
FROM research_task t
LEFT JOIN research_plan p ON p.task_id = t.id;
```

**预期现象**：B 的行数 ≥ A 的行数。A 里"消失的任务"= 还没生成计划的任务；B 里它们以 `plan_summary = null` 的形式存在。

**选择口诀**：要"全部主体，附属可缺"→ LEFT；要"只有两边都齐的"→ INNER。

---

## 六、危险实验：忘写 ON = 笛卡尔爆炸

```sql
-- ✗ 危险：没有 ON，每一行配每一行
SELECT COUNT(*) FROM research_task t JOIN research_plan p;
-- 15 个任务 × N 条计划 = 天文数字（库里计划少，换成 1000×1000 就是 100 万行）
```

**笛卡尔积**：没有关联规则时，左表每一行和右表每一行都配一次对。`1000 行 × 1000 行 = 100 万行`——生产环境一条忘写 ON 的查询能把数据库打挂。**写 JOIN 的习惯：先写 ON 再想别的**。

---

## 七、一对多的"扁平行"问题（为 S3/S7 埋伏笔）

一个任务有 3 个步骤，JOIN 之后返回什么？

```sql
SELECT t.title, s.title AS step_title FROM research_task t
JOIN research_step s ON s.task_id = t.id;
```

```text
task_title          | step_title
--------------------+--------------------
测试任务-A           | 步骤1：搜索
测试任务-A           | 步骤2：抓取      ← 任务的列重复出现了 3 次
测试任务-A           | 步骤3：总结
```

**JOIN 返回的是"表格展开"，不是嵌套 JSON**。前端类比：这不是 `{ task, steps: [...] }`，是**三个扁平行**。组装成嵌套结构是应用层（Java）的活——先 JOIN 一把捞回来（省 3 次请求），再在内存里按 `task_id` 分组。S3 学聚合、S7 学窗口函数后会有更优雅的 SQL 侧解法。

---

## 八、自连接：一张表演两个角色（补课 2026-10-16）

**场景**：一行数据指向**同一张表**的另一行。项目实例就在 `schema-dev.sql` 第 65 行：

```sql
depends_on_step_id UUID NULL   -- 步骤依赖哪个步骤（指向 research_step 自己的 id）
```

前端类比：员工表里每行有 `manager_id`，指向**同表**的另一行（经理也是员工）——查"员工 + 他的经理"就得把员工表假装成两张表用一次。

```sql
SELECT s.title  AS step_title,        -- 角色 1：当前步骤
       d.title  AS depends_on_title   -- 角色 2：它依赖的那个步骤
FROM research_step s
LEFT JOIN research_step d ON d.id = s.depends_on_step_id;
```

三个要点：

1. **同一张表出现两次，各发一个别名**（`s` 和 `d`）——数据库把 `research_step` 当成两份拷贝，`ON` 描述两份之间的关联规则；
2. **必须用 LEFT**：第一步（或无依赖的步骤）`depends_on_step_id` 是 NULL，INNER 会把它们丢掉——"步骤存在但没依赖"的事实要保留（S2 主课的老规矩）；
3. `ON d.id = s.depends_on_step_id`：读作"依赖的那行 = 表里 id 等于我指向的那个 id 的行"。

## 九、子查询：查询里套查询（补课 2026-10-16）

**本质**：一条 SQL 内部先跑一个"内层查询"，把结果交给外层用——类比 JS 里**先算出一个中间数组，再拿它做下一步过滤**：

```js
const failedTaskIds = executions.filter(e => e.status === 'FAILED').map(e => e.taskId);  // 内层
const tasks = allTasks.filter(t => failedTaskIds.includes(t.id));                          // 外层
```

SQL 版（IN 子查询，最常用）：

```sql
SELECT id, title
FROM research_task
WHERE id IN (SELECT task_id FROM step_execution WHERE status = 'SUCCESS');
--              ↑ 内层先跑：产出"有成功执行的 task_id 集合"
--              ↑ 外层再跑：任务 id 在集合里的都留下
```

**三种常见位置**：

| 位置 | 写法 | 类比 |
|---|---|---|
| `WHERE ... IN (...)` | 外层拿内层的**值集合**做过滤 | `includes(id)` |
| `FROM (...) 别名` | 内层产出一张**临时表**，外层当表用 | 先 `.map()` 出中间结果再加工 |
| `SELECT (SELECT ...)` | 标量子查询（每行算一个值）——了解即可 | 行内计算属性 |

FROM 子查询实例（把 S3 的分桶结果当临时表再过滤）：

```sql
SELECT status, cnt
FROM (SELECT status, COUNT(*) AS cnt
      FROM research_task
      GROUP BY status) AS bucket   -- 内层结果 = 一张临时表，必须起别名
WHERE cnt >= 2;
```

**与 JOIN 的关系**：很多 IN 子查询可以改写成 JOIN（去重后关联）。两种写法现在知道"等价、可互换"即可——哪种更快，S6 用 EXPLAIN 见分晓。**选择建议**：语义是"在/不在某个集合"→ IN 子查询更直观；语义是"把两边的列拼一起看"→ JOIN 更自然。

---

## 十、项目关联地图

| JOIN 知识 | 项目位置 |
|---|---|
| 关联键全景（task_id 万能） | `schema-dev.sql` 11 张表的 `task_id` / `step_id` / `plan_id` 列 |
| 逻辑外键（无物理 FK 约束） | `schema-dev.sql` 全文——只有关联列，没有 `FOREIGN KEY` 声明 |
| 一对一实例 | `research_report.task_id ... UNIQUE`（第 108 行） |
| 自引用实例 | `research_step.depends_on_step_id`（第 65 行，步骤依赖步骤） |
| 应用层组装 | `TaskResponseMapper` / QueryService（JOIN 结果 → 嵌套 DTO） |

**反向索引**：读 mapper / 实体时看到关联字段 → 回查本篇。

---

## 十一、动手练习（H2 控制台）

```sql
-- 练习 1：INNER JOIN 任务 + 计划（只返回有计划的任务）
SELECT t.title AS task_title, p.version, p.plan_summary
FROM research_task t
JOIN research_plan p ON p.task_id = t.id
ORDER BY t.created_at DESC;

-- 练习 2：同查询改 LEFT JOIN——数一数行数变多了吗？多出来的是谁？
SELECT t.title AS task_title, p.version, p.plan_summary
FROM research_task t
LEFT JOIN research_plan p ON p.task_id = t.id
ORDER BY t.created_at DESC;

-- 练习 3：三表链式 任务 → 步骤 → 执行记录（步骤没执行过时 attempt_no 为 null）
SELECT t.title AS task_title, s.step_no, s.title AS step_title,
       e.attempt_no, e.status AS execution_status
FROM research_task t
JOIN research_step s ON s.task_id = t.id
LEFT JOIN step_execution e ON e.step_id = s.id
ORDER BY t.created_at DESC, s.step_no;

-- 练习 4：COUNT 对比 INNER vs LEFT 的行数差
SELECT COUNT(*) AS inner_count FROM research_task t JOIN research_plan p ON p.task_id = t.id;
SELECT COUNT(*) AS left_count  FROM research_task t LEFT JOIN research_plan p ON p.task_id = t.id;

-- 练习 5（只跑 COUNT，别 SELECT *）：故意漏写 ON，看笛卡尔爆炸
SELECT COUNT(*) AS cartesian_count FROM research_task t JOIN research_plan p;

-- 练习 6（可选）：列名歧义现场——把下面这条的 title 前缀去掉，看报什么错
SELECT t.title, s.title FROM research_task t
JOIN research_step s ON s.task_id = t.id;

-- 练习 7（补课）：自连接——步骤 + 它依赖的步骤（同一张表演两个角色）
SELECT s.title  AS step_title,
       s.depends_on_step_id,
       d.title  AS depends_on_title
FROM research_step s
LEFT JOIN research_step d ON d.id = s.depends_on_step_id
ORDER BY s.created_at DESC
LIMIT 15;
-- 预期留心：depends_on_step_id 为 null 的行 = 没设依赖的步骤（LEFT 保住它们）

-- 练习 8（补课）：IN 子查询——"有成功执行记录"的任务
SELECT id, title
FROM research_task
WHERE id IN (SELECT task_id FROM step_execution WHERE status = 'SUCCESS');
-- 预期：只返回有 SUCCESS 执行记录的任务（对照 S2 主课练习③：任务 B 无执行记录应缺席）

-- 练习 9（补课）：FROM 子查询——分桶结果当临时表再过滤
SELECT status, cnt
FROM (SELECT status, COUNT(*) AS cnt
      FROM research_task
      GROUP BY status) AS bucket
WHERE cnt >= 2;
-- 预期：与 S3 练习 1 的分桶结果一致，但小桶（cnt < 2）被滤掉
```

**验收标准**：
1. 练习 2 行数 ≥ 练习 1，且能指出"多出来的行 = 哪些任务"；
2. 练习 4 两个数字能解释差额来源；
3. 练习 5 的数字 = 15 × 计划数（乘法关系，亲眼见证）；
4. 练习 6 去掉前缀后看到"列名歧义"报错，理解为什么要写 `t.` / `s.`；
5. **补课**：练习 7 能指出"哪列来自角色 s、哪列来自角色 d"；练习 8 缺席的任务与 S2 主课练习③的 null 行对得上；练习 9 结果与 S3 分桶对账。

---

## 十二、验证记录（2026-10-15 首跑，6/6 全绿；补课练习见末行）

| 练习 | 结果 | 判定 |
|---|---|---|
| ① INNER JOIN 任务+计划 | 15 行，`version` 全 1 | ✅ |
| ② LEFT JOIN 同查询 | 也是 15 行，与①**完全一致** | ✅（解读一） |
| ③ 三表链式 | 任务 A 的 5 个步骤 `execution_status` 全 `SUCCESS`；任务 B 的 5 个步骤 `attempt_no` 全 `null` | ✅ **LEFT JOIN 的活教材**（解读二） |
| ④ COUNT 对比 | `inner_count = 15`，`left_count = 15`，相等 | ✅（解读一） |
| ⑤ 漏 ON 笛卡尔爆炸 | **225 = 15 × 15** | ✅ 乘法实证（解读三） |
| ⑥ 列名歧义 | 带前缀跑通（多行正常返回） | ✅（去前缀看报错为可选，未做不拦关） |

### ★ 解读一：inner = left = 15 ≠ "两种 JOIN 没区别"

②④相等只说明**当前数据现状**：15 个任务**全都有计划**，没有"光秃秃的任务"。但业务上任务**刚创建的那一刻就是没计划的**——那一刻 INNER 会让新任务从查询里"消失"，LEFT 才会保留它（`plan_summary = null`）。**数据现状会变，JOIN 选型要看业务语义，不是看当前数据**。

### ★ 解读二：练习③ 是 LEFT JOIN 的真正用武之地

结果里的两幅面孔：

- 任务 A：5 个步骤全部 `SUCCESS`（执行过，`step_execution` 有记录）；
- 任务 B：5 个步骤 `attempt_no` / `execution_status` 全 `null`（**步骤建了但还没执行**，`step_execution` 里无记录）。

如果练习③第二步改成 `JOIN step_execution`（INNER），**任务 B 的 5 个步骤会整行消失**——"步骤存在但没执行过"的事实被查询吞掉。LEFT JOIN 让步骤以 `null` 执行状态出现，事实完整。这就是"**要全部主体、附属可缺 → LEFT**"的实证。

### ★ 解读三：225 的账

漏写 ON 时，左表 15 行 × 右表 15 行 = 225 种配对全部出现。1000 × 1000 就是 100 万——**先写 ON 再想别的**。
