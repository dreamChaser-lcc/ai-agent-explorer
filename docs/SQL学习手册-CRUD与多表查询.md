# SQL 学习手册：CRUD 与多表查询

> 基于 AI Agent Explorer 项目的真实表结构编写，用于日常回顾。
> 本项目表：`research_task`（任务）、`research_plan`（计划）、`research_step`（步骤）、`step_execution`（步骤执行）、`source_document`（引用文档）、`research_report`（报告）、`task_event_log`（事件日志）、`human_confirmation`（人工确认）。

---

## 一、总览：四类语句 + 两类结构语句

| 语句 | 作用 | 难度 |
|---|---|---|
| `SELECT` | 查 | ★★★★★（变体最多） |
| `INSERT` | 增 | ★★☆ |
| `UPDATE` | 改 | ★★★☆（危险：忘 WHERE） |
| `DELETE` | 删 | ★★★☆（危险：忘 WHERE） |
| `CREATE TABLE` | 建表 | ★★☆ |
| `ALTER TABLE` | 改表结构 | ★★☆ |

核心规律：
- **查询（SELECT）最难**：JOIN + 聚合 + 子查询 + 窗口函数组合拳，占开发 SQL 时间 80%
- **增删改最危险**：忘写 WHERE = 全表遭殃

---

## 二、SELECT（查）—— 最难，6 大场景

### ① 基础 + 过滤（WHERE 条件家族）

```sql
SELECT id, title, status
FROM research_task
WHERE status = 'RUNNING'                              -- 等值
  AND priority >= 3                                    -- 比较
  AND title LIKE '%AI%'                                -- 模糊（%任意，_单个）
  AND created_at BETWEEN '2026-08-01' AND '2026-08-31' -- 范围
  AND status IN ('CREATED', 'RUNNING')                 -- 多个值
  AND created_by IS NULL;                              -- 空值判断（不能用 = NULL）
```

### ② 排序 + 分页（企业查询标配）

```sql
SELECT id, title, status
FROM research_task
ORDER BY created_at DESC, priority ASC   -- 先按创建时间倒序，相同再按优先级升序
LIMIT 10 OFFSET 20;                       -- 每页10条，取第3页
```

- MySQL 和 PostgreSQL 的分页写法完全一致（`LIMIT ... OFFSET`）

### ③ 去重

```sql
SELECT DISTINCT status FROM research_task;   -- 看有哪些状态
```

### ④ 聚合统计（GROUP BY + HAVING）

```sql
-- 统计每个任务有几个步骤，只显示步骤数>=3的
SELECT task_id, COUNT(*) AS step_count, MAX(step_no) AS max_step
FROM research_step
GROUP BY task_id
HAVING COUNT(*) >= 3       -- WHERE 过滤行，HAVING 过滤分组
ORDER BY step_count DESC;
```

### ⑤ 多表 JOIN（详见第四章）

```sql
SELECT t.title, s.title AS step_title, e.status AS exec_status
FROM research_task t
LEFT JOIN research_step s ON s.task_id = t.id
LEFT JOIN step_execution e ON e.step_id = s.id
WHERE t.status = 'RUNNING';
```

### ⑥ 子查询（WHERE 里 / FROM 里）

```sql
-- 查"步骤数最多的那个任务"
SELECT * FROM research_task
WHERE id = (
    SELECT task_id FROM research_step
    GROUP BY task_id
    ORDER BY COUNT(*) DESC
    LIMIT 1
);
```

---

## 三、INSERT / UPDATE / DELETE

### INSERT（增）

```sql
-- 单条
INSERT INTO research_task (id, task_no, title, goal, execution_mode, status, current_stage, created_at, updated_at)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'T20260824001', '研究AI智能体', '调研当前AI助手架构', 'ASYNC', 'CREATED', 'PLANNING', NOW(), NOW());

-- 多条（VALUES 逗号分隔）
INSERT INTO research_step (id, task_id, plan_id, step_no, step_type, title, status, created_at, updated_at)
VALUES
  ('...', '...', '...', 1, 'SEARCH', '搜索资料', 'PENDING', NOW(), NOW()),
  ('...', '...', '...', 2, 'FETCH', '抓取网页', 'PENDING', NOW(), NOW());

-- 从别的表拷贝（INSERT ... SELECT）
INSERT INTO task_archive (id, title, status, archived_at)
SELECT id, title, status, NOW()
FROM research_task
WHERE status = 'DONE';
```

要点：列名与值一一对应；外键列必须填已存在的 ID；没写的列用默认值。

### UPDATE（改）⚠️ 必须带 WHERE

```sql
-- 基础更新
UPDATE research_task
SET status = 'DONE', completed_at = NOW(), updated_at = NOW()
WHERE id = '550e8400-...';

-- 关联更新（用另一个表的值改本表）
UPDATE research_task t
SET error_message = e.error_message, status = 'FAILED'
FROM step_execution e                 -- PG 写法（MySQL 用 JOIN 语法）
WHERE e.task_id = t.id AND e.status = 'FAILED';
```

### DELETE（删）⚠️ 必须带 WHERE

```sql
-- 基础删除
DELETE FROM research_task WHERE id = '550e8400-...';

-- 按子查询结果删
DELETE FROM research_task
WHERE id IN (
    SELECT task_id FROM step_execution WHERE status = 'FAILED'
);

-- 清空表（更快但不可回滚）
TRUNCATE TABLE task_event_log;
```

### ⚠️ 安全铁律（防"删库跑路"）

1. **先写 WHERE 再写表名**（倒着写，逼自己先想条件）
2. **先 SELECT 确认影响行数，再 UPDATE/DELETE**
3. **UPDATE/DELETE 的 WHERE 尽量用主键/唯一键精确锁定**
4. 危险写法示例（禁止）：

```sql
DELETE FROM research_task;                    -- ❌ 全表清空
UPDATE research_task SET status = 'DONE';     -- ❌ 全表被改
```

---

## 四、多表查询（JOIN）

### JOIN 本质

**把两张表按"某列相等"的关系横向拼成一张大表。** 每次只拼一张，像串糖葫芦一样一层层 JOIN。

```sql
FROM 主表 主别名
[JOIN] 从表1 别名1 ON 别名1.外键 = 主别名.主键
[JOIN] 从表2 别名2 ON 别名2.外键 = 别名1.主键   -- 外键挂在谁身上就接谁
```

### 三种 JOIN 的区别

| JOIN 类型 | 含义 | 结果 |
|---|---|---|
| `INNER JOIN` | 只留两边都匹配的行 | 交集 |
| `LEFT JOIN` | 左表全保留，右边没有填空 NULL | 左表为主 |
| `RIGHT JOIN` | 右表全保留 | 右表为主（一般用 LEFT 替代） |

口诀：**INNER = 交集，LEFT = 左表为主补 NULL。** 日常 90% 用这两种。

### ON 是什么意思

- `ON` = **连接条件**，告诉数据库"两张表按哪个关系拼起来"
- 99% 写法：`ON 从表.外键 = 主表.主键`
- **ON 管"能不能拼上"，WHERE 管"拼上的要不要"**
- LEFT JOIN 时，同一条件放 ON 还是 WHERE 结果不同：
  - 放 ON：左表行保留，不匹配的详情字段为 NULL
  - 放 WHERE：不匹配的行整行消失

### 多表 JOIN 完整示例（本项目 4 张表）

```sql
SELECT
    t.title                              AS 任务标题,
    p.plan_summary                       AS 计划摘要,
    s.title                              AS 步骤标题,
    e.status                             AS 执行状态,
    e.duration_ms                        AS 耗时
FROM research_task t
INNER JOIN research_plan p    ON p.task_id = t.id      -- ① 任务 + 计划
INNER JOIN research_step s    ON s.plan_id = p.id      -- ② 结果 + 步骤（用 p）
LEFT JOIN step_execution e    ON e.step_id = s.id      -- ③ 结果 + 执行记录（用 s）
WHERE t.status = 'RUNNING'
ORDER BY t.created_at DESC;
```

### 避坑指南

1. **一对多 JOIN 行数暴增**：列表查询别乱 JOIN，要统计就配合 `GROUP BY`
2. **ON 写错 = 笛卡尔积**：漏写条件 → 全组合，数据全乱
3. **列名歧义**：多表时必须带别名 `t.xxx` / `s.xxx`
4. **优化**：先 JOIN 过滤条件最多的表，减少中间结果

### 多表查询降复杂度技巧

- **子查询当表用**：`LEFT JOIN (SELECT task_id, COUNT(*) ... GROUP BY task_id) cnt ON ...`
- **拆成多次查询**：先查主表，再查 `IN (...)`，在代码里组装（MyBatis `<collection>` 可自动组装）
- **视图封装**：复杂 JOIN 建成视图，以后一行查询

---

## 五、视图（CREATE VIEW）

**视图 = 一张"虚拟表"**，不存数据，本质是一条被保存下来的 SELECT 查询。

```sql
-- 每个任务 + 最新一版报告
CREATE VIEW v_task_with_latest_report AS
SELECT t.id, t.title, t.status, r.summary, r.generated_at
FROM research_task t
LEFT JOIN research_report r ON r.task_id = t.id
WHERE r.version = (SELECT MAX(version) FROM research_report WHERE task_id = t.id);
```

价值：简化复杂查询、复用查询逻辑、隐藏敏感字段、隔离表结构变化。

**物化视图（PG 特有）**：把结果真正存下来，查询快但需刷新：

```sql
CREATE MATERIALIZED VIEW mv_task_stats AS
SELECT task_id, COUNT(*) AS step_count FROM step_execution GROUP BY task_id;

REFRESH MATERIALIZED VIEW mv_task_stats;   -- 数据变化后手动刷新
```

---

## 六、外键（Foreign Key）

**外键不是主键，而是"本表里存放另一张表主键值的列"**，表示"我属于谁"。

```
research_task.id(1001)  ← 被引用
    ↑ task_id=1001  步骤1
    ↑ task_id=1001  步骤2
```

| | 主键 | 外键 |
|---|---|---|
| 位置 | 本表唯一标识 | 本表一列，指向别表 |
| 重复 | ❌ 必须唯一 | ✅ 可重复（一对多） |
| 为空 | ❌ 不能 | ✅ 可以 |

**两种强度：**
- 逻辑外键：普通列，靠应用保证一致性（多数企业做法）
- 物理外键：`REFERENCES` 约束，数据库强制（本项目 V1 用了）

```sql
task_id UUID REFERENCES research_task (id)   -- 物理外键
```

---

## 七、SQL 存放位置（企业实践）

| 场景 | SQL 放哪 | 谁来执行 |
|---|---|---|
| 业务查询（增删改查） | Mapper XML / Java 注解（MyBatis） | 应用运行时自动 |
| 建表、改表结构 | Flyway 的 `db/migration/*.sql` | 应用启动自动 |
| 上线数据订正 | 带日期的独立脚本文件 | DBA 手动执行 |
| 临时查询 | 不存文件，客户端里敲 | 人手动 |

### Flyway 核心知识

- **作用**：数据库结构版本管理工具（"数据库版的 Git"）
- **机制**：应用启动检查 `flyway_schema_history` 表 → 按版本号从小到大执行新脚本 → 记录已执行版本
- **命名**：`V<版本号>__<描述>.sql`，版本号只增不减，**改已执行过的文件会报错**
- **改表姿势**：新建 `V2__xxx.sql` 写 ALTER，重启自动执行，不改旧文件
- 本项目：生产（PostgreSQL）Flyway 开启；dev（H2）关闭，用 `schema-dev.sql` 初始化

### ALTER TABLE（改表结构，不动数据）

```sql
ALTER TABLE research_task ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;  -- 加列
ALTER TABLE research_task DROP COLUMN retry_count;                            -- 删列
ALTER TABLE research_task ALTER COLUMN title TYPE VARCHAR(500);               -- 改类型(PG)
ALTER TABLE research_task ADD CONSTRAINT uk_task_title UNIQUE (title);        -- 加约束
```

⚠️ 大表加列会锁表，线上操作选低峰期；PG 与 MySQL 语法有差异（`TYPE` vs `MODIFY`）。

---

## 八、IN 用法速记

```sql
WHERE task_no IN ('T01', 'T02', 'T05')   -- 等于其中任意一个（多值 OR 简写）
WHERE id IN (SELECT task_id FROM ...)    -- 子查询结果作为匹配集合
WHERE id NOT IN (... )                    -- 反选
```

⚠️ 坑：`NOT IN` 遇到 NULL 返回空结果，含空值场景用 `NOT EXISTS`。

---

## 九、数据库差异速查（PG vs MySQL）

| 场景 | PostgreSQL | MySQL |
|---|---|---|
| 自增主键 | `SERIAL` / `IDENTITY` | `AUTO_INCREMENT` |
| 分页 | `LIMIT ... OFFSET` | `LIMIT ... OFFSET`（相同） |
| 空值处理 | `COALESCE(x, 0)` | `IFNULL(x, 0)` |
| 布尔 | 原生 `BOOLEAN` | `TINYINT(1)` |
| UPSERT | `ON CONFLICT ... DO UPDATE` | `ON DUPLICATE KEY UPDATE` |
| 改列类型 | `ALTER COLUMN ... TYPE` | `MODIFY ...` |
| 改列名 | `RENAME COLUMN a TO b` | `CHANGE a b ...` |
| FULL OUTER JOIN | ✅ | ❌（需 UNION 模拟） |
| 字符串比较 | 区分大小写（默认） | 不区分（utf8mb4） |

**结论：JOIN、子查询、分页等核心语法两者几乎 100% 一致；差异集中在 DDL、空值函数、UPSERT。**

---

## 十、核心口诀

1. **ON 管拼不拼得上，WHERE 管拼上的要不要**
2. **UPDATE/DELETE 先写 WHERE，先 SELECT 验证再执行**
3. **多表查询 = 一层层 JOIN，ON 挂谁就接谁**
4. **Flyway 改表 = 新建 V+1 文件，不改旧文件**
5. **视图 = 存起来的复杂查询**
6. **NOT IN 遇到 NULL 会翻车，用 NOT EXISTS**
