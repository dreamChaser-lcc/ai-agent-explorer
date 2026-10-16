# S1 · 读数据 SELECT 精读

> **本课目标**：把一条 SELECT 拆到骨头里——每个子句是干什么的、和 JS 数组操作怎么一一对应、为什么书写顺序和执行顺序不一样。
> **教材**：`backend/src/main/resources/mapper/ResearchTaskMapper.xml`（项目在跑的真实 SQL）
> **前置**：无（场景③ 听过一遍也没关系，本课是系统化精读）

---

## 一、前端类比开场：SQL 查询 ≈ JS 数组链式操作

你天天在写这种代码：

```js
// 前端从接口拿到全量数据后，本地筛选/排序/分页
const pageData = allTasks
  .filter(t => t.status === 'RUNNING')   // WHERE
  .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))  // ORDER BY
  .slice(20, 30);                         // LIMIT + OFFSET
```

SQL 就是把这套操作**搬到数据库里执行**：

```sql
SELECT id, title, status
FROM research_task
WHERE status = 'RUNNING'
ORDER BY created_at DESC
LIMIT 10 OFFSET 20;
```

**关键区别（也是 SQL 的核心哲学）——声明式 vs 命令式**：

| | JS 链式 | SQL |
|---|---|---|
| 风格 | **命令式**：你一步步告诉它"先 filter 再 sort 再 slice" | **声明式**：你只说"我要什么"，**怎么做由数据库自己决定** |
| 数据位置 | 全量数据已经拉到前端内存里 | 数据在数据库里，**不过网**，数据库内部筛选完只返回结果 |
| 性能 | 数据量大 = 白给（网络传输 + 浏览器内存爆炸） | 走索引的话，百万行也是毫秒级 |

一句话：**SQL 是"给数据库下的一张采购单"，不是"操作手册"**。

---

## 二、逐行翻译：项目的 `findPage`（场景③ 主角）

`ResearchTaskMapper.xml` 第 17–29 行，这是项目列表接口真正在跑的 SQL：

```sql
SELECT id, task_no, title, goal, execution_mode, status, current_stage,
       current_step_id, requires_confirmation, priority, error_message,
       created_by, started_at, completed_at, idempotency_key, version, created_at, updated_at
FROM research_task
WHERE status = 'RUNNING'
ORDER BY created_at DESC
LIMIT 10 OFFSET 20
```

| SQL 片段 | 人话 | 前端对应 |
|---|---|---|
| `SELECT id, task_no, title, ...` | **列清单**：只要这些列，顺序即返回顺序 | 接口 DTO 的字段清单（`select` 字段） |
| `FROM research_task` | 从哪张表取数 | `fetch('/api/tasks')` |
| `WHERE status = 'RUNNING'` | 过滤：只要满足条件的行 | `.filter(t => t.status === 'RUNNING')` |
| `ORDER BY created_at DESC` | 按创建时间**倒序**（最新在前） | `.sort((a,b) => new Date(b.createdAt) - new Date(a.createdAt))` |
| `LIMIT 10` | 最多返回 10 行 | `.slice(0, 10)` 的"取多少" |
| `OFFSET 20` | 跳过前 20 行 | `.slice(20, 30)` 的"从哪开始" |
| `#{status}` | **参数占位符**：SQL 骨架与参数值分开两次发送，防 SQL 注入（当时漏讲，已增补进 S4 §二） | `textContent` vs `innerHTML`（永远当文本，不被当代码执行） |

三个注意点：

1. **列全写、不写 `SELECT *`**（mapper 注释原话）：结果稳定（加列不炸接口）、可读、可走"覆盖索引"。前端类比：接口按 DTO 精确返回字段，而不是把数据库整行结构透出去。
2. **`DESC` 是倒序**（Descending），默认（不写）是升序 `ASC`。项目要"最新任务在前"，所以 `DESC`。
3. **`LIMIT 10 OFFSET 20` = 第 3 页**（每页 10 条）：跳过前 20 条、取接下来 10 条。

---

## 三、WHERE 家族全谱（对照 JS `filter`）

单条件不够用，真实查询都是组合拳。全集：

| SQL 写法 | 含义 | JS 等价 |
|---|---|---|
| `status = 'RUNNING'` | 等值 | `t.status === 'RUNNING'` |
| `status <> 'FAILED'` / `!=` | 不等于 | `t.status !== 'FAILED'` |
| `priority >= 3` | 大于等于 | `t.priority >= 3` |
| `priority BETWEEN 1 AND 5` | 闭区间 [1,5] | `t.priority >= 1 && t.priority <= 5` |
| `status IN ('CREATED', 'RUNNING')` | 枚举多个值 | `['CREATED','RUNNING'].includes(t.status)` |
| `title LIKE '%AI%'` | 模糊：`%` 任意多字符，`_` 单个字符 | `t.title.includes('AI')` |
| `error_message IS NULL` | 是空值（**不能用 `= NULL`**） | `t.errorMessage == null` |
| `error_message IS NOT NULL` | 不是空值 | `t.errorMessage != null` |
| `AND` / `OR` / `NOT` | 组合条件 | `&&` / `\|\|` / `!` |
| `( ... )` | 括号改变优先级 | 同 JS |

组合示例：

```sql
SELECT id, title, priority
FROM research_task
WHERE status IN ('CREATED', 'RUNNING')     -- 状态是这两个之一
  AND priority >= 3                        -- 且优先级够高
  AND title LIKE '%AI%'                    -- 且标题含 AI
  AND error_message IS NULL                -- 且没有报错（NULL 判断专用 IS）
ORDER BY priority DESC, created_at DESC
LIMIT 10;
```

**两个必踩的坑**：

1. **`= NULL` 永远查不到东西**。NULL 是"没有值"，不是某个可比较的值，`NULL = NULL` 的结果是"未知"。判断空必须 `IS NULL` / `IS NOT NULL`。
2. **`AND` 优先级高于 `OR`**，和 JS 一样。混用时**加括号**保平安：`WHERE a = 1 AND (b = 2 OR c = 3)`。

---

## 四、执行顺序：书写顺序 ≠ 执行顺序

SELECT 的**书写顺序**固定是 `SELECT → FROM → WHERE → ORDER BY → LIMIT`，但数据库**执行顺序**是：

```text
FROM      ① 先定位从哪张表取数
WHERE     ② 再过滤行（过滤完才进入下一步）
SELECT    ③ 再挑列（计算/取别名发生在这里）
ORDER BY  ④ 再排序（此时才能按别名/计算结果排）
LIMIT     ⑤ 最后截取
```

**这个顺序解释了一个经典报错**：

```sql
-- ✗ 报错：WHERE 里不能用 SELECT 的别名
SELECT title AS t FROM research_task WHERE t LIKE '%AI%';
```

因为 `WHERE`（②）执行时 `SELECT`（③）还没跑，别名 `t` 还不存在。**别名要到 ORDER BY（④）才能用**。

---

## 五、SELECT 的细节三件套

**① 列别名 `AS`**——给返回列起个前端友好的名字：

```sql
SELECT title AS task_title, created_at AS created_time FROM research_task;
```

前端类比：接口字段映射（`snake_case → camelCase`）。

**② `DISTINCT` 去重**——只要"不重复"的值：

```sql
SELECT DISTINCT status FROM research_task;   -- 看系统里到底出现过哪些状态
```

前端类比：`[...new Set(arr)]`。★ 注意 `DISTINCT` 作用于**整行**：`SELECT DISTINCT status, priority` 是"这两个列的组合不重复"。

**③ `SELECT *` 为什么不推荐**——三条罪：结果不稳定（加列会悄悄改接口结构）、可读性差、用不上覆盖索引。项目 mapper 里全是列全写，就是这个原因。

---

## 六、项目关联地图

本课的每一块，都在仓库这些地方：

| SQL 知识 | 项目位置 | 用法 |
|---|---|---|
| 列清单 + `ORDER BY` + `LIMIT/OFFSET` | `mapper/ResearchTaskMapper.xml` `findPage` | 列表接口（场景③） |
| 列全写不 `SELECT *` | 同上第 18–20 行 | 注释里写了三条理由 |
| `WHERE` 动态拼接 | 同上 `<where>` + `<if>` | status 传了才拼，没传查全部 |
| `COUNT(*)` | 同上 `countPage` | 分页总数（S3 展开） |
| 等值 + 唯一索引 | 同上 `findByIdempotencyKey` | 幂等快路径（场景④） |
| `ORDER BY ... DESC` 单表版 | `mapper/TaskEventLogMapper.xml` `findByTaskIdOrderByCreatedAtDesc` | 事件日志时间线倒序 |

**反向索引**：读 mapper XML 时遇到任何 SELECT 相关问题 → 回查本篇。

---

## 七、动手练习（H2 控制台现场验证）

启动 dev（`cd backend; .\start-dev.ps1`），开 `http://localhost:8080/h2-console`，JDBC URL 填：

```text
jdbc:h2:file:./data/h2db/research-agent;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
```

用户名 `sa`、密码空、点连接。先创建几个任务（前端界面或 curl 都行）让表里有数据，然后逐条跑：

```sql
-- 练习 1：全表扫一眼（认识 11 张表的"主角" research_task）
SELECT id, title, status, priority, created_at FROM research_task;

-- 练习 2：等值过滤 + 倒序（对照 findPage 的迷你版）
SELECT title, status FROM research_task WHERE status = 'CREATED' ORDER BY created_at DESC;

-- 练习 3：NULL 判断（只能用 IS，试把 IS NULL 改成 = NULL 看它返回空）
SELECT title, error_message FROM research_task WHERE error_message IS NULL;

-- 练习 4：模糊 + IN + BETWEEN 组合拳
SELECT title, priority FROM research_task
WHERE priority BETWEEN 1 AND 5 AND title LIKE '%AI%' ORDER BY priority DESC;

-- 练习 5：别名 + DISTINCT（看系统里出现过哪些状态）
SELECT DISTINCT status AS task_status FROM research_task;
```

**验收标准**：5 条全部跑出结果、且能口头说出每条"对应 JS 的什么操作"。第 3 条故意做错一次（`= NULL`），亲眼看到"永远查不到"——这个坑要留体感。

**下一课预告**：S2 多表查询 JOIN——11 张表怎么关联起来查（任务 + 它的步骤 + 它的报告一次查出来）。

---

## 八、验证记录（2026-10-15 首跑）

| 练习 | 结果 | 判定 |
|---|---|---|
| ① 全表扫一眼 | 15 行，`priority` 全是 `0`，中文标题在控制台显示 `?????` | ✅（`?????` 是 H2 Web 控制台的中文编码显示缺陷，数据本身没问题——接口返回中文一直正常） |
| ② `WHERE status = 'CREATED'` | **no rows** | ⚠️ 不是 SQL 写错——**数据里根本没有 CREATED 状态**（见下方排障） |
| ③ `error_message IS NULL` | 15 行 `error_message` 全 `null` | ✅ NULL 的实物：没出错的任务 = 这列没值 |
| ④ `BETWEEN 1 AND 5 AND LIKE '%AI%'` | **no rows** | ⚠️ 同上——表里 `priority` 全是 `0`，`BETWEEN 1 AND 5` 一个都匹配不上 |
| ⑤ `DISTINCT status AS task_status` | 3 行：`CANCELLED` / `COMPLETED` / `WAITING_FOR_CONFIRMATION` | ✅ 别名生效，且**反证了②为什么空** |

### ★ 排障心法（本课彩蛋）："查不到" ≠ "写错了"

练习②④都是 **no rows**，但 SQL 语法完全正确——真正原因是**数据分布不满足条件**。排查顺序永远是：

1. **先别怀疑语法**，先看数据里有什么：`SELECT DISTINCT status FROM research_task;`（练习⑤已给出答案：库里只有 3 种状态，没有 `CREATED`）；
2. 再看目标列的实际取值范围：练习①显示 `priority` 全是 `0` → `BETWEEN 1 AND 5` 必然为空；
3. 改条件复测：把④改成 `priority BETWEEN 0 AND 5` 立刻出全表——**用证据说话**。

> 补两个可选体感实验：把③改成 `error_message = NULL` 看它永远返回空（NULL 不能用 `=` 的铁证）；把④的 `BETWEEN 1 AND 5` 改成 `BETWEEN 0 AND 5` 看数据全出来。
