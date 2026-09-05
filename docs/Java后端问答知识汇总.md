# Java 后端问答详细讲义（2026-08~09 会话）

> 本讲义把基于 AI Agent Explorer 项目（Spring Boot 3.5 + JPA/MyBatis + H2/PostgreSQL）的历次问答
> **展开成可独立阅读的详细讲解**，每个主题包含：是什么 → 为什么 → 代码示例 → 流程图 → 易错点 → 类比。
>
> 相关配套文档：
> - `docs/SQL学习手册-CRUD与多表查询.md`
> - `docs/Redis使用策略与实战手册.md`
>
> 项目内文件路径一律使用相对项目根目录的写法（如 `backend/src/main/java/...`），不带盘符。
>
> ⚠️ 注意：本讲义"第三部分 Redis"提到的 `RedisCacheConfig.java` 与 `@Cacheable`
> 在后续提交 `d163142 chore: 优化本地开发配置，完善文档，移除Redis缓存` 中已被整体移除
> （配置文件、pom.xml redis 依赖、ResearchTaskQueryService 的 @Cacheable 均已删除）。
> 该部分内容当前作为**历史讲解记录**保留，不代表项目现况。

---

# 第一部分：数据库认知

## 1.1 企业里用得最多的数据库是什么

### 全球 vs 中国的差异

这个问题必须分两个维度看，因为"全球存量"和"中国企业实际选择"差别很大：

**全球范围（DB-Engines 2026 年 8 月排名）**

| 排名 | 数据库 | 说明 |
|---|---|---|
| 1 | Oracle | 综合评分第一，但主要是**存量市场**（金融、大型国企的历史系统） |
| 2 | MySQL | **新项目实际选用最多**，互联网行业绝对主流 |
| 3 | Microsoft SQL Server | .NET 生态为主 |
| 4 | PostgreSQL | 全球增长最快，AI 时代常被当作数据底座 |

注意：DB-Engines 排的是"综合热度/存量"。**如果问"今天新建系统选什么"，MySQL 才是实际第一名。**

**中国企业的情况**
- 互联网/创业公司：MySQL 占比最高，几乎是默认选择，MyBatis/JPA 生态成熟、资料最多
- 传统大企业/金融：Oracle 存量很大，但正被替换
- 国产化（信创）：党政领域替代率已超 90%，主流有达梦、OceanBase、GaussDB、PolarDB，多数兼容 MySQL 或 Oracle 语法
- 整体关系型数据库约占 76% 市场份额

**一句话结论**：想学"最通用、最好找工作" → MySQL；想要"功能强、免费、符合现代趋势" → PostgreSQL；进国企/金融做存量维护 → Oracle/国产库。

### 本项目处于什么位置

- dev 模式：H2 内存库（**PostgreSQL 兼容模式**），免安装
- 生产（默认 `application.yml`）：PostgreSQL + Flyway
- Redis：依赖和配置已接入，缓存已落地（见第三部分）
- 这个组合（H2/PG 模式开发 + PG 生产）是企业常见做法：开发 SQL 与生产一致，避免"开发能跑生产挂了"

---

## 1.2 Oracle / MySQL / PostgreSQL / SQL Server 的区别

### 一句话定位

- Oracle：老牌商业王者，功能最全，贵，运维要求高
- MySQL：开源最流行，简单易用，互联网标配
- PostgreSQL：开源功能天花板，现代开发者首选，增长最快
- SQL Server：微软系，Windows/.NET 生态深度绑定

### 核心区别（4 个维度）

| 维度 | Oracle | MySQL | PostgreSQL | SQL Server |
|---|---|---|---|---|
| 厂商/授权 | Oracle 公司，商业收费（按 CPU 授权） | Oracle 旗下，开源免费 | 社区驱动，开源免费 | 微软，商业收费 |
| 语法特色 | PL/SQL、NVL、SYSDATE、手动建序列 | AUTO_INCREMENT、LIMIT、IFNULL、ON DUPLICATE KEY | 最贴合 SQL 标准、SERIAL/IDENTITY、RETURNING | T-SQL、TOP、GETDATE() |
| 并发模型 | 基于回滚段 MVCC | InnoDB undo log MVCC | 基于 xmin MVCC，读写不阻塞 | 行锁 + 快照隔离 |
| JSON | 12c+，偏弱 | 5.7+，弱 | **JSONB + GIN 索引，最强** | 2016+，一般 |

### 关键差异点详细说

**1. 许可证是最现实的区别**
- Oracle、SQL Server 收费，尤其 Oracle 按 CPU 核心授权，企业版可能几十万到上百万/年
- MySQL、PostgreSQL 免费，省下钱可以投到服务器和运维

**2. 语法方言不通用（最坑的点）**
`IFNULL` vs `NVL` vs `COALESCE`、`LIMIT` vs `ROWNUM`、自增 vs 序列——换库意味着改 SQL。
这正是 MyBatis 比 JPA 对换库更敏感的原因：**JPA 靠 Hibernate 方言自动适配；MyBatis 手写 SQL 的方言要人工改**。

**3. PostgreSQL 为什么增长最快**
- 开源却拥有接近 Oracle 的能力：分区、物化视图、递归查询、窗口函数、扩展机制、JSONB
- AI 时代是 pgvector 向量检索的首选底座（LangChain4j 生态也更亲近 PG）
- 没有 Oracle 授权费，也没有 MySQL 在某些复杂查询上的能力短板

---

## 1.3 查看一个项目用了哪些数据库

### 通用查看方法

1. **看依赖**：`pom.xml`（Java）/`package.json`（Node）里的数据库驱动
2. **看配置**：`application*.yml`/`.env` 里的 `datasource.url`、`driver-class-name`
3. **看连接串前缀**（一眼识别）：
   - `jdbc:mysql://` → MySQL
   - `jdbc:postgresql://` → PostgreSQL
   - `jdbc:h2:mem:` → H2 内存库
   - `jdbc:oracle:` → Oracle
4. **看建表脚本方言**：`AUTO_INCREMENT` → MySQL；`SERIAL` → PG；`ROWNUM` → Oracle
5. **看迁移目录**：Flyway/Liquibase 的 `db/migration`

### 本项目实际情况

| 数据库 | 使用位置 | 证据 |
|---|---|---|
| H2 | dev | `application-dev.yml` 的 `jdbc:h2:mem:research-agent;MODE=PostgreSQL...`，用 `schema-dev.sql` 初始化，Flyway 关闭 |
| PostgreSQL | 生产/默认 | `application.yml` 的 `jdbc:postgresql://localhost:5432/research_agent`，Flyway 开启 |
| Redis | 缓存 | `RedisCacheConfig` + `@Cacheable`（本次会话已接入） |

### Navicat 的澄清

- **PostgreSQL 是数据库服务器**，需要装 PostgreSQL 本体才能跑
- Navicat 分版本：**Navicat MySQL 不能连 PostgreSQL**
- 客户端工具建议：pgAdmin（官方自带免费）、DBeaver（免费通用，一个工具管所有库）
- 你的项目 dev 用 H2，**连 PostgreSQL 都不用装**；想看图可用 `http://localhost:8080/h2-console`

---

## 1.4 PostgreSQL vs MySQL 专项对比

### 语法差异（换库最疼的地方）

| 场景 | MySQL | PostgreSQL |
|---|---|---|
| 自增主键 | `AUTO_INCREMENT` | `SERIAL` / `IDENTITY` |
| 字符串拼接 | `CONCAT(a,b)` | 只能 `a \|\| b` |
| 空值处理 | `IFNULL(x,0)` | `COALESCE(x,0)` |
| 布尔值 | `TINYINT(1)` | 原生 `BOOLEAN` |
| 更新返回 | 无原生 | `UPDATE ... RETURNING *` |
| 大小写 | 库表名 Linux 下区分大小写 | 列名/表名自动折叠成小写 |
| UPSERT | `INSERT ... ON DUPLICATE KEY UPDATE` | `INSERT ... ON CONFLICT ... DO UPDATE` |
| 分页 | `LIMIT ... OFFSET` | `LIMIT ... OFFSET`（**一样**） |

### 能力特性差异（PG 的主要优势）

- **JSONB**：PG 二进制存储 + GIN 索引，可按 JSON 字段查询建索引；MySQL 的 JSON 只是文本校验
- **全文搜索**：PG 内置 tsvector，支持中文扩展；MySQL 较弱
- **物化视图**：PG 原生支持；MySQL 没有
- **索引类型**：PG 支持部分索引/表达式索引/BRIN/GIN/GiST 等十几种；MySQL 主要是 B+Tree + 全文
- **扩展能力**：PG 有 `CREATE EXTENSION`（pgvector、PostGIS、pgcrypto）；MySQL 几乎没有
- **窗口函数/递归查询**：PG 完整支持；MySQL 8.0 才补齐

### MySQL 的优势

- 简单：安装、配置、上手门槛低，资料极多
- 运维生态成熟：主从复制、MGR 简单实用
- 读多写少、简单查询、高吞吐小事务下性能优秀
- 国产兼容：OceanBase/PolarDB/TiDB 都兼容 MySQL 协议

### 性能怎么选（实用判断）

- 简单 CRUD、高并发小事务（电商订单）：两者差距不大
- 复杂分析、多表 JOIN、报表：PG 明显更强
- JSON 半结构化数据：PG 碾压
- 本项目任务报告用 JSON 列存 → PG `JSONB` 是天然选择，当前配置（H2 PG 兼容模式开发 + PG 生产）完全正确

---

## 1.5 视图（CREATE VIEW）深入讲解

### 视图是什么

**视图 = 一张"虚拟表"**。它不存任何数据，本质是一条**被保存下来的 SELECT 查询**。查视图 = 执行那条 SELECT。

### 用项目例子演示（每个任务 + 最新报告）

```sql
CREATE VIEW v_task_with_latest_report AS
SELECT t.id, t.title, t.status, r.summary, r.generated_at
FROM research_task t
LEFT JOIN research_report r ON r.task_id = t.id
WHERE r.version = (SELECT MAX(version) FROM research_report WHERE task_id = t.id);
```

**为什么这样写**：一个任务可能有多版报告（version 递增）。如果不筛选，LEFT JOIN 会让任务重复出现多行。"取每任务最大 version 的报告"保证每个任务一行最新报告。`WHERE r.version = (相关子查询)` 对每个任务单独求最大版本号。

### 视图的 4 个价值

1. **简化复杂查询**：把 JOIN+子查询封装成"表"，之后 `SELECT * FROM v_xxx` 一行搞定
2. **复用**：列表页、报表、定时任务多处共用一个查询逻辑
3. **隐藏敏感字段**：只暴露必要列（如隐藏 `planner_prompt_snapshot`）
4. **隔离表结构变化**：底层表加列，只要视图对外输出不变，调用方代码不用改

### 视图 vs 物化视图（PG 特有）

| | 普通视图 | 物化视图 |
|---|---|---|
| 存数据 | 不存，实时算 | **真存结果** |
| 查询速度 | 每次重算 | 快（读存好的） |
| 刷新 | 自动最新 | 需 `REFRESH MATERIALIZED VIEW` |
| 适用 | 简单/频繁变数据 | 计算重、不用实时的统计报表 |

### 视图放哪

视图属于"结构管理"，**适合放进 Flyway 迁移脚本**（V2__xxx.sql），不是业务数据。

---

## 1.6 外键深入讲解

### 外键不是主键

**外键 = 本表里的一个普通列，它存的值是另一张表主键的值**，用来表示"我属于谁"。

例子（步骤表引用任务表）：

```
research_task（任务表）                    research_step（步骤表）
id = 1001（主键）        ←被引用—          task_id = 1001（外键，可重复）
id = 1002                                task_id = 1001
                                         task_id = 1002
```

### 主键 vs 外键对比

| | 主键 | 外键 |
|---|---|---|
| 位置 | 本表唯一标识 | 本表一列，指向别表 |
| 重复 | ❌ 必须唯一 | ✅ 可重复（一个任务很多步骤） |
| 为空 | ❌ 不能 | ✅ 可以 |
| 作用 | "我是谁" | "我属于谁" |

### 两种"强度"

**逻辑外键（只写列，不加约束）**——大多数企业项目：
```sql
CREATE TABLE research_step (
    task_id UUID,   -- 只是普通列，数据库不管
    ...
);
```
靠应用代码保证 task_id 一定存在。灵活、性能好、删除自由。

**物理外键（加 REFERENCES 约束）**——你项目 V1 采用：
```sql
CREATE TABLE research_step (
    task_id UUID REFERENCES research_task (id),  -- 数据库强制存在性
    ...
);
```
好处：数据库兜底一致性，防止脏数据。坏处：每次插删要检查别表，影响性能；删除/更新被引用记录被阻塞；分库分表时外键无法跨库。所以很多企业宁可用逻辑外键 + 应用层保证。

---

# 第二部分：SQL 实战

## 2.1 SQL 在企业里写在哪些地方

| 场景 | SQL 放哪 | 谁来执行 |
|---|---|---|
| 业务查询（增删改查） | MyBatis Mapper XML（本项目 `src/main/resources/mapper/TaskEventLogMapper.xml`）或 JPA 注解/方法名 | 应用运行时自动 |
| 建表/改表结构 | Flyway `db/migration/V1__xxx.sql` | 应用启动自动 |
| 上线数据订正 | 带日期的独立脚本文件 | DBA 手动执行 |
| 临时查询 | 不存文件，客户端（pgAdmin/DBeaver）直接敲 | 人手动 |

**结论**：不是每个 SQL 都要"建文件手动执行"。业务 SQL 和建表 SQL 都有框架自动管理；只有"一次性 + 需留痕"的数据订正才单独放文件手动跑。

## 2.2 Flyway 详解

### 是什么

**数据库结构版本管理工具**，可以类比"数据库版的 Git"。解决的问题：
- 团队多人改表结构不同步 → 部署后报"列不存在"
- 测试/生产环境表结构不一致
- 上线靠人手动建表 → 漏执行、重复执行出错

### 工作机制

```
src/main/resources/db/migration/
├── V1__init_research_agent_schema.sql
├── V2__add_xxx.sql
└── V3__xxx.sql
```

应用启动时 Flyway 自动：
1. 检查数据库有没有 `flyway_schema_history` 表（没有就自动建）
2. 对比已执行版本 vs 脚本目录版本
3. 从低到高执行新版本脚本
4. 成功后在 history 表记录版本号
5. 下次启动发现已执行 → 跳过

### 命名与规则（重要）

- `V<版本号>__<描述>.sql`，双下划线是分隔符，版本只增不减
- **已执行过的文件不能改内容**（checksum 校验会报错）——防止有人偷改历史
- 以后改表 = **新建 V+1 文件**，绝不改旧文件

### 迁移脚本能做什么

建表、`ALTER TABLE` 加/删/改列、加索引、建视图、插入**种子/字典数据**（状态枚举、配置项）。

### 不能做什么

**不能放大量业务数据**（用户、订单、日志）——那是应用运行时通过 API/Service 写入的。理由：
- 迁移脚本只执行一次，塞进去的数据永不更新
- 绕过了业务逻辑（校验、状态流转、审计）
- 所有环境被塞同一条数据，测试/生产不一致
- 职责分离：结构归结构管，数据归应用管

### 本项目配置

```yaml
# application.yml（生产）
flyway:
  enabled: true
  locations: classpath:db/migration

# application-dev.yml（开发）
flyway:
  enabled: false    # H2 每次全新，用 schema-dev.sql 初始化即可
```

## 2.3 ALTER TABLE 详解

### 是什么

DDL（数据定义语言），**修改已存在表的结构**，不动表里已有数据。

```
CREATE TABLE = 盖房子
ALTER TABLE = 装修
DROP TABLE = 拆房子（数据全没，不可恢复）
```

### 常用操作

```sql
-- 加列（已有数据的表要带默认值）
ALTER TABLE research_task ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;

-- 删列
ALTER TABLE research_task DROP COLUMN retry_count;

-- 改类型（注意 PG 与 MySQL 语法不同）
ALTER TABLE research_task ALTER COLUMN title TYPE VARCHAR(500);  -- PG
ALTER TABLE research_task MODIFY title VARCHAR(500);             -- MySQL

-- 加唯一约束
ALTER TABLE research_task ADD CONSTRAINT uk_task_title UNIQUE (title);
```

### 两个坑

1. **大表加列会锁表**：千万级数据线上表直接 ALTER 会锁整表，业务停摆。企业做法：业务低峰期执行、用 PG 11+/MySQL 8.0 的快路径（带 DEFAULT 的 ADD COLUMN）
2. **语法不通用**：改类型 PG 用 `ALTER COLUMN ... TYPE`、MySQL 用 `MODIFY`；改列名 PG 用 `RENAME COLUMN a TO b`、MySQL 用 `CHANGE a b ...`

### 企业用法

不手动执行，写进 Flyway：`V2__add_retry_count_to_task.sql` → 部署自动执行 → 所有环境一致。

## 2.4 JOIN 多表查询详解

### JOIN 的本质

**把两张表按"某列相等"的关系横向拼成一张大表。**

### 三种 JOIN 的区别

| JOIN | 含义 | 结果 |
|---|---|---|
| `INNER JOIN` | 只留两边都匹配的行 | 交集 |
| `LEFT JOIN` | 左表全保留，右表没有就补 NULL | 左表为主 |
| `RIGHT JOIN` | 右表全保留 | 右表为主（一般用 LEFT 调换替代） |

口诀：**INNER = 交集，LEFT = 左表为主补 NULL**。日常 90% 用这两种。

### ON 到底是什么意思

`ON s.task_id = t.id` = **连接条件**，告诉数据库"凭哪个关系把两表拼一起"。

**最重要的考点：ON 和 WHERE 的区别**

| | ON | WHERE |
|---|---|---|
| 时机 | JOIN 拼表时，决定哪些行能拼上 | 拼完之后，对整行过滤 |
| 影响 | 决定"拼不拼得上" | 决定"拼上的要不要" |

LEFT JOIN 时同一条件放不同位置结果完全不同：
```sql
-- ON 里过滤：左表行保留，不匹配详情为 NULL（查所有任务，步骤只显示完成的）
LEFT JOIN research_step s ON s.task_id = t.id AND s.status = 'DONE';

-- WHERE 里过滤：不匹配行整行被删（只查有完成步骤的任务）
LEFT JOIN research_step s ON s.task_id = t.id
WHERE s.status = 'DONE';
```

### 多表 JOIN（3 张及以上）

没有新语法，**重复 JOIN 一层层拼**，每一层 ON 指向上一步的结果表：

```sql
FROM research_task t
INNER JOIN research_plan p ON p.task_id = t.id      -- ① 任务+计划
INNER JOIN research_step s ON s.plan_id = p.id      -- ② 结果+步骤（用 p）
LEFT JOIN step_execution e ON e.step_id = s.id      -- ③ 结果+执行记录（用 s）
```

注意 ON 是链式的：第②步连接上次的 `p`，第③步连接上次的 `s`——**外键挂在谁身上就接谁**。

### 三个坑

1. **一对多 JOIN 行数膨胀**：一个任务多计划 × 多步骤 × 多执行 = 行爆炸。列表查询别乱 JOIN，要统计配 `GROUP BY`
2. **ON 漏写 = 笛卡尔积**：两表全组合，数据全乱
3. **列名歧义**：多表列名重复（title/status/created_at 各表都有），必须带别名 `t.xxx`/`s.xxx`

### 压平复杂度技巧

- 子查询当表用：`LEFT JOIN (SELECT task_id, COUNT(*) c FROM research_step GROUP BY task_id) cnt ON cnt.task_id = t.id`
- 拆多次查询在代码组装（MyBatis `<collection>`）
- 视图封装常用 JOIN

## 2.5 聚合、COUNT、AS、GROUP BY / HAVING

### 一段完整 SQL 的执行顺序

```sql
SELECT task_id, COUNT(*) AS step_count, MAX(step_no) AS max_step
FROM research_step
GROUP BY task_id
HAVING COUNT(*) >= 3
ORDER BY step_count DESC;
```

执行过程：
1. `GROUP BY task_id`：相同 task_id 归组
2. 聚合：每组数出行数 COUNT、最大 step_no
3. `HAVING`：丢弃步骤数 < 3 的组
4. `ORDER BY`：按 step_count 降序

### COUNT(*)

聚合函数，数行数。三种写法的差别：
```sql
COUNT(*)              -- 所有行（含 NULL）
COUNT(列名)           -- 该列非空的行
COUNT(DISTINCT 列名)  -- 去重后的非空个数
```

### AS

给结果列**起别名**，只影响本次查询结果集的列名，不改表。
```sql
SELECT COUNT(*) AS step_count FROM research_step;  -- 结果列叫 step_count
```
用途：表达式列名难看（COUNT(*)）、下游代码好引用（MyBatis resultMap）、`ORDER BY` 可用别名。

### WHERE vs HAVING（常考）

| | WHERE | HAVING |
|---|---|---|
| 时机 | 分组之前过滤行 | 分组之后过滤组 |
| 可用 | 普通列 | 聚合结果 COUNT/MAX/SUM |
| 示例 | `WHERE status != 'FAILED'` | `HAVING COUNT(*) >= 3` |

标准组合：**先 WHERE 删行，再 GROUP BY，再 HAVING 删组。**

## 2.6 IN / NOT IN / NOT EXISTS（含 NULL 大坑）

### IN

`IN (...)` = 等于括号里任何一个值，是长串 `OR` 的简写，用于一次匹配多个值。
```sql
WHERE task_no IN ('T01','T02','T05')     -- = 三个 OR
```
进阶用法——匹配子查询结果：
```sql
DELETE FROM research_task
WHERE id IN (SELECT task_id FROM step_execution WHERE status = 'FAILED');
```

### NOT IN 遇到 NULL 会"翻车"（重要）

**现象**：`NOT IN` 括号里出现 NULL 时，结果**空表**——明明应该有行却查不出来。

**原因（SQL 三值逻辑）**：SQL 有 TRUE / FALSE / **UNKNOWN**。任何 NULL 参与比较结果都是 UNKNOWN。
```sql
task_no NOT IN ('T01', 'T02', NULL)
-- 等价于 task_no <> 'T01' AND task_no <> 'T02' AND task_no <> NULL
--                                      ↑ 永远 UNKNOWN
-- WHERE 只保留 TRUE，UNKNOWN 全部被丢弃 → 空结果
```

**解决**：用 `NOT EXISTS`——它不比较值，只判断"子查询有没有行"，天然免疫 NULL：
```sql
SELECT * FROM research_task t
WHERE NOT EXISTS (
    SELECT 1 FROM step_execution e
    WHERE e.task_id = t.id AND e.status = 'FAILED'
);
```

**三条规矩**：`= NULL` 永远查不到（用 `IS NULL`）；`NOT IN` 集合别含 NULL；集合来自子查询时优先 `NOT EXISTS`。

## 2.7 DML 安全铁律（"删库跑路"警示）

### 为什么危险

```sql
DELETE FROM research_task;                    -- ❌ 忘 WHERE = 全表清空，无确认、可能不可恢复
UPDATE research_task SET status = 'DONE';     -- ❌ 忘 WHERE = 全表被改
```

生产数据被删/被改 = 经济损失 + 法律责任，所以有"删库跑路"的梗。

### 企业防事故手段

1. **UPDATE/DELETE 必须有 WHERE，尽量用主键精确锁定**：
```sql
DELETE FROM research_task WHERE id = '550e8400-...';
```
2. **先 SELECT 确认影响行数再执行**（八字真言：先 SELECT 再 UPDATE/DELETE）
3. **事务包裹**：`BEGIN; DELETE ...; ROLLBACK/COMMIT;`
4. **权限分离**：生产库删除权限只给 DBA
5. **技术拦截**：SQL 拦截器检测无 WHERE 的 DELETE/UPDATE 直接报错
6. **备份兜底**：生产库每天自动备份

---

# 第三部分：Redis

## 3.1 Redis 是什么、为什么需要启动一个服务

### 概念

Redis 是**基于内存的键值存储数据库**，数据放内存（所以极快，微秒级）。它的核心价值一句话：**把"查得多、变得少"的热点数据从数据库搬到内存，挡数据库压力、提升响应速度。**

### 为什么需要"启动服务"

Redis 是一个**独立运行的数据库服务器进程**，应用只是"连它的客户端"：
```
你的 Spring Boot 应用（客户端）
   ↓ TCP 连接 localhost:6379
Redis 服务进程（服务器）
   ↓
内存中的数据
```

**对比**：本地缓存（Caffeine/ConcurrentHashMap）存在应用自己 JVM 内存里，引入依赖就能用，**不需要独立服务**；而 Redis 缓存数据存在独立的 Redis 进程里，**必须先启动 Redis**，应用才能连上。没启动 Redis，应用启动会报连接失败。

### Windows 安装（本项目实测步骤）

1. 下载 tporadowski/redis 的 `Redis-x64-5.0.14.msi`（会注册成 Windows 服务，开机自启）
2. 安装，如果没勾 PATH，手动把安装目录（本项目 `D:\softwares\Redis`）加到用户 PATH：
```powershell
# 用户级 PATH（不需管理员）
[Environment]::SetEnvironmentVariable('Path',
  ([Environment]::GetEnvironmentVariable('Path','User')).TrimEnd(';') + ';D:\softwares\Redis', 'User')
```
3. 验证服务在跑 + 命令可用：
```powershell
Get-Service Redis          # Status 应为 Running
redis-cli ping             # 返回 PONG
```
4. 项目配置已是默认（localhost:6379 无密码），无需改配置。

## 3.2 缓存核心策略

### 缓存热点数据（最常用）

```
请求 → 先查 Redis（命中直接返回）
        ↓ 未命中
      查数据库 → 写入 Redis（设 TTL 过期）→ 返回
```

### 三大经典问题及对策

| 问题 | 现象 | 对策 |
|---|---|---|
| 穿透 | 查根本不存在的 key，每次都打数据库 | 空结果也缓存（短过期）/ 布隆过滤器 |
| 击穿 | 某热点 key 过期瞬间，海量请求同时打库 | 热点 key 不过期 / 加互斥锁重建 |
| 雪崩 | 大量 key 同时过期，数据库被打爆 | TTL 加随机值，避免集中失效 |

### 其他用途

- 分布式锁：`SET key value NX EX`（防任务并发重复执行）
- 计数器/限流：`INCR` 原子自增
- 会话/临时状态：验证码、token、临时进度，配 TTL

## 3.3 本项目落地（代码讲解）

### 配置文件 `RedisCacheConfig.java`

真实路径：
`backend/src/main/java/com/aiexplorer/researchagent/infrastructure/config/RedisCacheConfig.java`

```java
@Configuration
@EnableCaching                       // 开启 @Cacheable/@CacheEvict 注解
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(30))      // 默认 30 秒过期
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(StringRedisSerializer.UTF_8))   // key 字符串可读
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer())) // value 用 JSON
                .disableCachingNullValues();           // 不缓存 null，防穿透
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
```

**两个关键设计**：
1. **为什么 value 用 JSON 而不是默认 JDK 序列化？** 默认 JDK 要求对象实现 `Serializable` 且 key 带 `\xAC\xED` 乱码；JSON 可读、无需改造实体、方便用 redis-cli 查看
2. **为什么类名叫 `RedisCacheConfig` 而不是 `RedisCacheConfiguration`？** 后者与 Spring 自带的 `org.springframework.data.redis.cache.RedisCacheConfiguration` 同名冲突（import 时类名撞车），所以改名避开

### 使用处 `ResearchTaskQueryService.java`

真实路径：
`backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchTaskQueryService.java`

```java
@Transactional(readOnly = true)
@Cacheable(value = "task:detail", key = "#taskId")   // key = task:detail::<UUID>
public TaskDetailResponse getTaskDetail(UUID taskId) { ... }
```

执行效果：首次调用查库并把结果写进 Redis；30 秒内再次调用直接读缓存；过期后重新查库。任务详情是前端轮询进度的读多写少热点，非常适合。

### 缓存更新策略

**Cache Aside（旁路缓存）**，顺序必须是：**先更新数据库，再删除缓存**（`@CacheEvict`），不能反过来，否则读到脏数据。TTL 是兜底：即使漏删，到点也会失效重载。

# 第四部分：Java / Spring 基础

## 4.1 interface 是什么、implements 和 @Override

### interface

**接口 = 定义"规则/契约"**：只说"能做什么"（方法签名），不写"怎么做"。

类比：USB 标准规定"能传数据"，U 盘/鼠标/手机各自实现；餐厅菜单写菜名，各家做法不同。

```java
public interface ResearchTool {                    // 接口：能力清单
    StepType getSupportedStepType();               // 只有签名，无 {}（无方法体）
    ResearchToolResult execute(ResearchToolContext context);
}
```

### implements 与 @Override 的位置

```java
@Component
public class CitationExtractResearchTool implements ResearchTool {  // implements 写在类声明处（只一次）
    @Override                                              // @Override 写在每个重写方法上
    public StepType getSupportedStepType() { return StepType.CITATION_EXTRACT; }

    @Override
    public ResearchToolResult execute(ResearchToolContext context) { ... }
}
```

- `implements`：**身份声明**，"我这个类实现了某接口"，类名后写一次
- `@Override`：**方法标注**，"这方法是重写接口/父类的"，每个重写方法都标

### interface 解决的三大问题

| 价值 | 说明 |
|---|---|
| 解耦 | 调用方只依赖接口，不关心实现细节 |
| 多态 | 同接口 N 个实现，运行时换实现不改调用方 |
| 扩展点 | 框架预留接口让开发者插入逻辑（WebMvcConfigurer） |

**本项目最佳例子**：`ResearchTool` 接口有 4 个 `@Component` 实现（WebSearch/PageFetch/CitationExtract/Summarize），`ResearchToolRegistry` 构造器注入 `List<ResearchTool>`，Spring 把所有实现自动收进列表——这就是 Bean + 接口 + 多态的典型玩法。也是 JPA/MyBatis 双 Store 切换的原理。

## 4.2 方法结构（public 后面的类型）

```java
public ResearchToolResult execute(ResearchToolContext context) { ... }
-- 访问修饰符 + 返回类型 + 方法名 + (参数) + { 方法体 }
```

| 部分 | 本例 | 含义 |
|---|---|---|
| public | public | 访问修饰符：公开可调 |
| 返回类型 | ResearchToolResult | **方法返回什么类型**，和 `return` 一致；`void` = 不返回 |
| 方法名 | execute | 名字 |
| 参数 | ResearchToolContext context | 调用时传入 |
| 方法体 | {...} | 逻辑 |

**构造器 vs 普通方法**（曾混淆点）：构造器**没有返回类型**（连 void 都没有）、名字必须和类名一模一样、`new` 时自动调用一次，用于初始化。

## 4.3 Spring Bean 与注解体系

### Bean 是什么

**Bean = 由 Spring 容器创建和管理生命周期的 Java 对象**。你不 `new`，Spring 帮你建、帮你存、你声明需要它就自动送过来（依赖注入）。

对照：`new` = 自己造；Bean = 交给容器。

### 注册 Bean 的两类注解（重点区分）

**方式一：类上加 `@Component` 家族（自己写的类）**

| 注解 | 语义 | 本项目例子 |
|---|---|---|
| `@Service` | 业务层 | `ResearchTaskQueryService` |
| `@RestController` | Web 接口层 | `ResearchTaskController` |
| `@Repository` | 数据访问 | 各 Repository |
| `@Component` | 通用工具 | `CitationExtractResearchTool`、`TaskResponseMapper` |

功能全部等价，区别只是**语义分层**（看代码知道属于哪一层）。

**方式二：`@Configuration` + `@Bean`（第三方类/需手动 new 的类）**

```java
@Configuration
public class AsyncExecutionConfiguration {
    @Bean(name = "researchTaskExecutor")
    public Executor researchTaskExecutor(...) {   // @Bean 标注"制造对象的方法"
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();  // 自己 new
        ...
        return executor;                          // return 出去交给 Spring 容器
    }
}
```

为什么需要方式二：`ThreadPoolTaskExecutor`、`RedisCacheManager` 是第三方/框架类，**不可能给它们的源码加 @Component**，而且需要配置一堆参数才能 new，所以自己在方法里造好再上交。关键点：**@Bean 标注的是方法，Spring 调这个方法，把返回值收进容器**。

### 注入 Bean 的注解

| 注解 | 用法 | 说明 |
|---|---|---|
| 构造器注入（最推荐） | 构造器参数 | 单构造器可不写 @Autowired |
| `@Autowired` | 字段/setter/构造器 | 按类型注入 |
| `@Qualifier("名字")` | 配合构造器参数 | 同类型多 Bean 时按名指定（本项目线程池） |

```java
// 本项目实际：Controller 构造器注入（注释明说"不需要 @Autowired"）
public ResearchTaskController(
        ResearchTaskQueryService researchTaskQueryService, ...) { ... }

// 本项目实际：@Qualifier 按名字取线程池
@Qualifier("researchTaskExecutor") Executor researchTaskExecutor
```

### 注册修饰注解

| 注解 | 作用 |
|---|---|
| `@Scope("prototype")` | 改作用域：singleton（默认，全应用一个）/ prototype（每次 new） |
| `@Lazy` | 延迟到首次使用才创建 |
| `@Primary` | 同类型多 Bean 时默认优先注入这个 |

### Bean 默认单例的意义

整个应用一个实例，谁注入都是它 → 无状态 Bean 共享安全。90% 的 Bean 用默认 singleton。有状态且不能共享的类才考虑 prototype，实际用得少。

## 4.4 注册与获取是两个独立环节

**常混淆点**：`@Configuration` 和 `@Autowired` 不是"一对搭配"。

| 环节 | 干什么 | 用什么 |
|---|---|---|
| 注册（往容器放） | 生产对象交给 Spring | `@Component` 家族 / `@Configuration+@Bean` |
| 获取（从容器拿） | 声明"我需要" | 构造器注入 / `@Autowired` |

**@Autowired 能拿到 Executor，不是因为"注册方是 @Configuration"**，而是因为**容器里确实有 Executor 这个 Bean**——不管它是 @Bean 注册还是 @Component 注册，只要在容器里就能拿到。

## 4.5 @Value 与配置读取

### @Value 读的是"哪里的配置"

`@Value("${app.llm.provider:openai}")` 读的是 **Spring Environment（配置环境）**——一个把多种来源合并的大字典，`${key}` 按 key 查最终值。**不只是 application.yml**，来源按优先级从高到低：

1. 命令行参数：`java -jar app.jar --app.llm.provider=gemini`
2. Java 系统属性：`-Dapp.llm.provider=gemini`
3. 操作系统环境变量：`set APP_LLM_PROVIDER=gemini`（宽松绑定：`APP_LLM_PROVIDER` ↔ `app.llm.provider`）
4. profile 配置：`application-dev.yml` 等
5. `application.yml` 主配置
6. `${key:默认值}` 冒号后的兜底

**项目例子**：`application.yml` 里 `provider: ${APP_LLM_PROVIDER:openai}` 就是"嵌套占位符"——先看环境变量，没有就退回 openai。

**规范**：@Value 一定要写默认值，否则配置漏了应用启动直接挂。

### profile 配置在哪

- 文件位置：和 `application.yml` 同目录，命名 `application-{名字}.yml`（如 `application-dev.yml`）
- 激活方式：`mvn spring-boot:run "-Dspring-boot.run.profiles=dev"`（本项目 CODEBUDDY.md 用这个）/ `--spring.profiles.active=dev`
- 合并规则：`application.yml` **永远加载**，激活的 profile 文件**逐 key 覆盖**；profile 没写的 key 沿用主配置
- @Value 读的是合并后的最终值

**本项目覆盖实例**：
```yaml
# application.yml：datasource.url = postgresql...、flyway.enabled = true
# application-dev.yml：datasource.url = jdbc:h2:mem...、flyway.enabled = false
```

### @ConfigurationProperties：@Value 的"批量升级版"

```java
// application.yml: app.llm.provider / app.llm.chat-model / app.llm.temperature / app.llm.max-steps

@ConfigurationProperties(prefix = "app.llm")   // 锁死只绑 app.llm 前缀
public record LlmProperties(
        String provider,      // 自动绑 app.llm.provider
        String chatModel,     // 自动绑 app.llm.chat-model（kebab↔camel 宽松绑定）
        double temperature,   // 自动绑 app.llm.temperature
        int maxSteps) {       // 自动绑 app.llm.max-steps
}
```

- 读取来源与 @Value **完全相同**（同一套 Environment），只是"一次绑一组"
- **必须显式注册才生效**：本项目在 `PropertiesConfiguration` 用 `@EnableConfigurationProperties({ExecutionProperties.class, LlmProperties.class})`
- 注册后是 Bean，可构造器注入使用（如 `AsyncExecutionConfiguration` 注入 `ExecutionProperties` 读线程池大小）

### 对比

| | @Value | @ConfigurationProperties |
|---|---|---|
| 读单个值 | ✅ | 一组绑定成对象 |
| 类型转换 | 弱 | 强（自动 int/boolean） |
| 校验 | 无 | 支持 @Validated |
| 规范 | 零散取值 | 一组相关配置用这个 |

## 4.6 项目注解全景（按层）

### Web 层
```java
@RestController                 // = @Controller + @ResponseBody，方法返回 JSON
@RequestMapping("/api/tasks")   // 类级路径前缀
@GetMapping / @PostMapping      // 绑定 HTTP 方法
@PathVariable UUID taskId       // 从 URL 路径 {taskId} 取值
@RequestBody CreateTaskRequest req  // 请求体 JSON → 对象
@Valid                          // 触发字段校验
@ResponseStatus(HttpStatus.CREATED)  // 自定义状态码（201）
```

### 全局异常
```java
@RestControllerAdvice           // 拦截所有 Controller 抛出的异常
@ExceptionHandler(TaskNotFoundException.class)  // 匹配异常类型
@ResponseStatus(HttpStatus.NOT_FOUND)
```

### 校验注解
`@NotNull`（不能为 null）、`@NotBlank`（不能空/纯空格）、`@Size`、`@Min/@Max`——配合 `@Valid` 在方法参数上触发。

### JPA 实体注解
```java
@Entity  @Table(name="research_task")   // 实体 ↔ 表
@Id  @GeneratedValue(strategy = GenerationType.UUID)   // 主键自生成
@Column(name="task_id", nullable=false) // 列映射/约束
@Enumerated(EnumType.STRING)            // 枚举存字符串而非数字
@MappedSuperclass                       // 父类字段被子实体继承（BaseTimeEntity）
@PrePersist / @PreUpdate                // INSERT/UPDATE 前自动回调（自动填时间）
```

### Lombok
`@Getter` / `@Setter` / `@NoArgsConstructor`——编译期自动生成样板代码。

### 事务
`@Transactional`（见下节）。

## 4.7 @Transactional 为什么只对 Bean 生效

### 靠代理实现

`@Transactional` 不是 Java 原生功能，而是 **Spring AOP 代理**在起作用。容器里存放的不是你的原始对象，而是套了"保安层"的**代理对象**：

```
外部调用 → 代理（保安：BEGIN/COMMIT/ROLLBACK）→ 你的真实方法
```

事务语句（BEGIN/COMMIT/ROLLBACK）全在代理层，你的代码里根本没有。

### 两种失效场景

**① new 出来的普通对象**：Spring 没参与创建、没套代理 → 注解完全被无视。

**② 同类内部 `this.xxx()` 调用**（最隐蔽的坑）：
```java
@Service
public class TaskService {
    @Transactional
    public void methodA() {
        this.methodB();    // ❌ 真实对象内部自调用，绕过代理，methodB 事务失效
    }
    @Transactional
    public void methodB() { ... }
}
```
口诀：**代理只拦"从外面打进来的电话"，不拦"屋子里面自己喊"**。

### 正确做法

- **跨 Bean 调用**（推荐）：需要独立事务的方法放独立 Bean。本项目 `createTask`（事务）调 `researchPlanningService.generateInitialPlan`——跨 Bean，双方事务都正常
- 或注入自身代理（`@Autowired private TaskService self; self.methodB();`）
- 或 `((TaskService) AopContext.currentProxy()).methodB()`

### readOnly

读操作配 `@Transactional(readOnly = true)`：性能提示，Hibernate 可跳过脏检查。

# 第五部分：Spring MVC 与 Web 架构

## 5.1 Spring MVC 是什么

Spring 提供的 Web 框架，唯一职责：**处理 HTTP 请求**——接住请求 → 找到对应方法执行 → 打包响应返回。

### MVC 三字母与本项目的关系

| 字母 | 含义 | 本项目对应 |
|---|---|---|
| M Model | 数据模型 | `TaskDetailResponse` 等 DTO、Entity |
| V View | 展示层 | **无**（前后端分离，View 前移到 Next.js） |
| C Controller | 控制器 | `ResearchTaskController` |

传统 MVC 返回 HTML 页面（JSP/Thymeleaf）；本项目是 REST API，Controller 直接返回 JSON，前端自己渲染。

## 5.2 一次完整请求的旅程（POST /api/tasks 创建任务）

前端发：
```
POST /api/tasks
Content-Type: application/json
{ "title": "研究AI", "goal": "调研架构", "executionMode": "ASYNC" }
```

**① 到达 DispatcherServlet**：Spring Boot 内嵌 Tomcat 接收 → 交给 DispatcherServlet（前端控制器/门卫）。

**② 路由匹配**：HandlerMapping 根据注解匹配到 `ResearchTaskController.createTask`：
```java
@RestController
@RequestMapping("/api/tasks")    // 类前缀
public class ResearchTaskController {
    @PostMapping                 // POST + 路径前缀 = /api/tasks
    public TaskSummaryResponse createTask(@Valid @RequestBody CreateResearchTaskRequest request) { ... }
}
```

**③ 参数解析与校验**：
- `@RequestBody`：Jackson 把 JSON 反序列化成 `CreateResearchTaskRequest` 对象
- `@Valid`：检查 DTO 上的 `@NotBlank`/`@NotNull`，失败立即抛 `MethodArgumentNotValidException`，方法体不执行

**④ 执行 Controller（薄壳）**：方法体只有一行，转交 Service：
```java
return researchTaskCommandService.createTask(request, "demo-user");
```

**⑤ 层层向下**：Controller → Service（业务+@Transactional）→ Repository（JPA 生成 SQL INSERT）→ 数据库 → 返回。

**⑥ 返回值序列化**：因为类是 `@RestController`，`TaskSummaryResponse` 对象被 Jackson 序列化成 JSON 返回。

**⑦ 状态码**：`@ResponseStatus(HttpStatus.CREATED)` → HTTP 201。

### 完整链路图

```
前端(Next.js :3000) → Tomcat :8080 → DispatcherServlet
  → HandlerMapping 路由匹配(@GetMapping)
  → 参数绑定(@RequestBody/@PathVariable) → 校验(@Valid)
  → Controller(薄壳) → Service(业务+事务) → Repository/JPA
  → 返回 DTO → Jackson 序列化 JSON + 状态码 → 前端
```

## 5.3 注解驱动的参数绑定全家

| 注解 | 场景 | 例子 |
|---|---|---|
| `@PathVariable` | URL 路径变量 | `GET /api/tasks/{taskId}` → UUID（自动类型转换） |
| `@RequestBody` | 请求体 JSON | 反序列化成 DTO |
| `@RequestParam` | 查询参数 `?page=2` | 本项目暂未用 |
| `@Valid` + 校验注解 | 参数校验 | 见上 |
| `@ResponseStatus` | 自定义状态码 | 201/204 |

## 5.4 为什么 Controller 要"薄"（业务下沉 Service）

### Controller 该长什么样

只做"翻译 + 转交"，方法体通常一行：
```java
return researchTaskCommandService.createTask(request, "demo-user");
```

### 多端场景是理解的关键（H5 / 小程序 / App）

**核心认知**：多端差异（H5 cookie、小程序 openId、App token）几乎全部发生在**接入层**，业务规则与端无关——"创建一个研究任务要生成编号、校验标题、置状态、生成计划"这条规则**对任何端都一样**。

**错误做法（业务写进 Controller）**：
```
H5 Controller 里复制 20 行业务
小程序 Controller 里再复制 20 行业务   ← 改一处业务要改 N 处
```

**正确做法（业务下沉 Service）**：
```
H5 Controller ──┐（只做适配：解析身份→createdBy）
小程序 Controller ─┼→ 同一个 Service.createTask(request, createdBy)  ← 业务只有一份
定时任务 ────────┘
```
每个 Controller 只写"端相关的接入适配"，核心业务一份。以后改业务只改 Service，所有入口同步。

### 附加价值

- **可单元测试**：业务在 Service，不用启动 HTTP/Tomcat 就能测
- **可复用**：定时任务、消息消费者、命令行都能调同一个 Service（非 HTTP 场景没有 Controller 可走）
- **职责清晰**：接入逻辑易变（参数格式/协议），业务逻辑稳定，让易变的壳依赖稳定的核

## 5.5 DTO（Data Transfer Object）

**DTO = 专门用来在不同层之间传数据的对象**，分两类：
- 入站 DTO（`api/request`）：收前端请求，如 `CreateResearchTaskRequest`
- 出站 DTO（`api/response`）：返回前端，如 `TaskSummaryResponse`、`TaskDetailResponse`

### 为什么不能直接把 Entity 返回

- Entity 跟表结构强绑定：表加列，前端 JSON 就多字段 → 接口不稳定
- Entity 可能有敏感/内部字段
- 前端只需要 6 个字段，实体有 15 个，白传浪费

### 数据流（转换靠 TaskResponseMapper）

```
数据库行 → Entity（内部专用，对应表）→ Mapper 转换 → DTO（对外契约）→ JSON
```

| | Entity | DTO |
|---|---|---|
| 对应谁 | 数据库表 | 前端/接口契约 |
| 放哪层 | persistence | api |
| 能否给前端 | ❌ | ✅ |

## 5.6 REST 风格详细讲解

### 是什么

REST = 一套设计 HTTP 接口的规范，核心 8 字：**URL 指资源，动词表操作**。

### 四个核心

**① 资源用名词复数放 URL**：`/api/tasks`、`/api/reports`

**② HTTP 方法 = 操作**：
```
GET /api/tasks            查列表（Read）
GET /api/tasks/{id}       查单个
POST /api/tasks           新建（Create）
PUT /api/tasks/{id}       整体更新
DELETE /api/tasks/{id}    删除
```

**③ 子资源用路径层级**：`/api/tasks/{taskId}/executions` = "某任务下的执行记录"

**④ 状态码有语义**：200 成功、201 创建成功、204 成功无体、404 不存在、400 参数错

### REST vs RPC

| | RPC 风格 | REST 风格 |
|---|---|---|
| 视角 | 调函数 | 操作资源 |
| URL | `/模块/方法名`，**动词结尾**（isGray） | `/资源/资源id`，**名词结尾**（tasks） |
| HTTP 方法 | 常只 GET/POST，动作靠 URL | GET/POST/PUT/DELETE 表达语义 |
| 参数 | 查询串 = 函数入参 | URL 定位资源 |
| 适合 | 内部服务互调、计算型 | 对外 API、CRUD 型 |
| 判断口诀 | **URL 结尾是动词 = RPC** | **URL 结尾是名词 = REST** |

例：`/integral/common/isGray?zbId=xxx&names=shortVideoTask` → isGray 是动词、参数像函数入参 → **RPC**。它本质在"调一个判断函数"，不适合硬套 REST。

### 本项目是纯正 REST

`tasks` 是资源名词、GET/POST 语义清晰、201/204/404 状态码规范。

## 5.7 状态码体系：REST 理想 vs 国内实践

### 国内主流观察（真实）

很多国内接口：**HTTP 一律 200 + body 里 {code, msg, data}**（code 细分业务：0 成功，如 `{isok:true, code:0, dataObj:[...], requestId:...}`）。

### 为什么国内这么做（5 个真实原因）

1. **HTTP 状态码太粗**：401 既代表"未登录"又"密码错"，表达不了"账号锁定/余额不足/券过期"，需要无限可细分的业务 code
2. **基础设施对 4xx/5xx 的特殊对待**：网关/监控对 5xx 会重试、摘节点、告警；业务失败返回 500 会被误判"服务挂了"
3. **客户端统一**：前端拦截器统一 `if(code===0) 取数据 else 弹 msg`，不用逐接口处理 HTTP 状态
4. **兼容老系统/异构调用方**：统一协议最好接
5. **历史惯性**：老 Java Web 时代就这么封装，传成团队规范

### 但"并非全链路 200"（你观察到的 404/500）

**关键分层认知**：请求是分层经过的（网关层 → 容器层 → 业务层）。"200+code"只约束**业务层**；404/500 多发生在请求**没走到业务层**时：

| 你看到的 | 谁返回 | 有 code 吗 |
|---|---|---|
| 接口未发布/路径错 404 | 网关/Nginx 或 Tomcat（路由没匹配） | ❌ 无，请求没进业务代码 |
| Controller 主动 throw 资源不存在 | 你的 @RestControllerAdvice | ✅ 有 code |
| 未知 bug 没被 advice 接住 500 | Tomcat 兜底 | ❌ |
| 服务崩/超时 502/504 | 网关 | ❌ |

**Tomcat 兜底逻辑**：DispatcherServlet 里找不到 handler → 404；DispatcherServlet 抛异常没被 advice 接住 → 冒到 Tomcat → 500。这两类发生在业务代码之外，你无法给它们包 code。

### 推荐折中（B 混合模式）

**HTTP 粗分 + body code 细分**——分工原则：
- HTTP 管"通用/传输层"问题：400 参数错、401 未登录、404 资源不存在、500 系统异常
- body code 管"只有本业务才懂"的问题：余额不足、券过期、重复提交

你项目 `ApiExceptionHandler`（404/400 + `{code, message}`）已是雏形。完整落地需要：
1. 统一成功包装 `ApiResponse<T>(code, msg, data, requestId)`
2. 成功也包一层：`{code:0, msg:"操作成功", data:{...}}`
3. 兜底 `@ExceptionHandler(Exception.class)` 防止裸 500/堆栈输出

## 5.8 Tomcat 与 Servlet

### 三个概念的澄清（常混淆）

| 概念 | 是什么 | 管什么 |
|---|---|---|
| **Servlet** | Java 处理 HTTP 的**标准接口**（init/service/destroy） | 定义"Java 怎么处理请求" |
| **Servlet 容器** | 运行 Servlet 的环境软件 | 收 HTTP、解析成 request/response 对象、调 Servlet、写回 |
| **Tomcat** | 最流行的 Servlet 容器 + Web 服务器 | 监听端口 + 管理 Servlet 生命周期 |

**Spring IoC 容器 ≠ Servlet 容器**：IoC 容器管 Bean，Servlet 容器管 HTTP，二者无关。

### Spring Boot 内嵌 Tomcat

- 老式开发：打成 `.war` 丢进单独安装的 Tomcat
- Spring Boot：把 Tomcat **打包进应用**（内嵌），`spring-boot-starter-web` 内含。跑 `main` 就自动启动 Tomcat + 注册 DispatcherServlet + 监听 8080

### DispatcherServlet 本身就是 Servlet

Spring 的 `DispatcherServlet` 实现了 Servlet 规范，是 Spring MVC 接入 Java Web 世界的"插头"。Tomcat 把 HTTP 翻译成 request/response 对象交给它，最后再把对象翻译回 HTTP。

### 一次请求在 Tomcat 内

```
① Tomcat 监听 8080 收到 HTTP 报文
② 从线程池取一个线程
③ 把文本 HTTP 解析成 HttpServletRequest 对象
④ 按 URL 找到 DispatcherServlet
⑤ 调 dispatcherServlet.service(request, response)
⑥ 进 Spring MVC：路由 → Controller → Service → 返回
⑦ DispatcherServlet 把结果写进 HttpServletResponse
⑧ Tomcat 把 response 序列化成 HTTP 报文发回
```

**核心认知**：Spring MVC 并不直接接触 HTTP 报文，是 Tomcat 翻译成 Java 对象给它。

## 5.9 Servlet 生命周期

```
应用启动 → init() 1 次（实例化后立即，做初始化）
  每个请求 → service() 每请求 1 次（同一实例并发调用）
应用关闭 → destroy() 1 次（释放资源）
```

| 方法 | 次数 | 时机 | 用途 |
|---|---|---|---|
| init() | 1 | 实例化后 | 初始化连接/配置 |
| service() | 每请求 1 | 每次请求 | 业务处理 |
| destroy() | 1 | 容器关闭前 | 释放资源 |

**单实例多线程**：一个 Servlet 只 new 一次，N 个请求并发调它的 service → Servlet 内不能随便存请求相关状态到成员变量（和单例 Bean 的坑一样）。

**DispatcherServlet 的 init**：Spring Boot 启动时（eager）执行——在这里收集所有 @RestController、解析 @GetMapping 建好路由表。所以路由表启动时就绪。

**对照 Spring Bean**：`@PostConstruct` / `@PreDestroy` 就是 Servlet 生命周期思想在 Bean 上的复刻。

## 5.10 Thread-per-request 模型与线程池

### 什么是 Thread-per-request

**Tomcat 默认处理方式 = 一个请求占一个线程，处理完才释放**。线程池默认最多 200 个。

### IO 等待是线程杀手

请求线程在"干等外部返回"时白白占着线程：
- 查数据库（等 DB 返回）
- 调外部 LLM API（OpenAI 返回可能要 10 秒）
- 抓网页（网络 IO）

**算一笔账**：200 线程中 190 个在等 LLM 返回 → 第 191 个请求只能排队 → 接口变慢/超时。这就是 IO 密集型应用容易打满 Tomcat 线程池的原因。

### 吞吐量公式

```
QPS（每秒能处理的请求）≈ 线程数 ÷ 平均响应时间
200 线程、响应 50ms  → 4000 QPS
200 线程、响应 500ms → 400 QPS
200 线程、响应 2s    → 100 QPS
```

两条路提高吞吐：**缩短响应时间**（异步化）或 **增加线程总数**（加机器）。

### 异步化（本项目做法）

```java
public void startExecution(UUID taskId) {
    if (task.getExecutionMode() == ExecutionMode.ASYNC) {
        // 不占 Tomcat 线程跑重活：丢进自己的线程池
        researchTaskExecutor.execute(() -> executeTask(taskId));
        return;   // Tomcat 线程立刻释放
    }
    executeTask(taskId);   // 同步：重活占 Tomcat 线程
}
```

- `researchTaskExecutor` 是 `AsyncExecutionConfiguration` 配置的**独立线程池**
- 慢活（AI 流程/调 LLM/抓网页）在独立池里跑，Tomcat 200 线程只"接单转交"
- 代价：前端不能同步拿结果 → 配合 SSE 轮询进度（`/stream` 接口）

## 5.11 SSE 长连接占不占线程

**结论：空闲等待时不占 Tomcat 工作线程。**

Spring `SseEmitter` 基于 Servlet 异步机制（AsyncContext）：
```
前端连 /api/tasks/{id}/stream
  → 请求进来 → 取一个线程 → Controller 返回 SseEmitter → ★线程立刻释放★
  → 连接挂在 NIO selector（不占工作线程）
  → 后台任务完成一步 → sseEmitter.send(...) → 临时取线程写入 → 写完释放
  → 任务完成 → sseEmitter.complete() → 连接关闭
```

- 空闲等事件：不占工作线程（NIO 管连接）
- 推送瞬间：占一个线程，写完释放
- 连接本身占的是"连接数"（受 `max-connections` 限制），不是工作线程

**反面教材**：自己写死循环轮询写 SSE、或每事件处理逻辑很重且同步 → 才真正占线程。

## 5.12 Tomcat 线程池参数怎么调

```yaml
server:
  tomcat:
    threads:
      max: 200            # 最大工作线程（默认 200）
      min-spare: 10       # 常驻空闲线程（避免频繁创建）
    accept-count: 100     # 等待队列长度
    max-connections: 8192 # 最大 TCP 连接（连接≠线程，空闲连接不占工作线程）
    connection-timeout: 20000
```

### 水流模型

```
请求 → max-connections 连接层（8192）
      → 有工作线程？→ 处理
      → 没有 → accept-count 队列（100）→ 满则拒绝
```

### 该设多少

- 需求线程数 ≈ QPS × 平均响应时间（Little's Law）
  - 100 QPS × 0.5s = 50 线程
  - 100 QPS × 2s = 200 线程
- CPU 密集：CPU 核数 + 1；IO 密集：可调高但别超 500~1000（线程过多调度开销反而大）
- 你的项目已把慢活丢独立线程池，Tomcat 保持默认 200 即可

### 怎么观察线程占用

| 方式 | 命令/依赖 | 看什么 |
|---|---|---|
| Actuator（推荐） | 加 `spring-boot-starter-actuator`，开 `metrics,threaddump` | `curl .../actuator/metrics/tomcat.threads.busy`（忙碌线程）；`config.max`（上限）；busy 贴近 max 说明快打满 |
| Actuator threaddump | 同上 | `curl .../actuator/threaddump`，搜 `http-nio-8080-exec` 前缀线程停在哪 |
| jstack | JDK 自带 | `jps -l` 拿 PID → `jstack PID`，数 RUNNABLE/WAITING |
| Arthas | 阿里开源 | `dashboard` 实时看线程；`thread -n 3` 看最忙线程 |

## 5.13 高并发专题：200 线程到底够不够

### 5.13.1 先统一"高并发"的度量衡（四个指标）

谈高并发前，先把指标说清，否则都是空谈：

| 指标 | 含义 | 说明 |
|---|---|---|
| **QPS** | Queries Per Second，**每秒查询数** | 最常用的吞吐指标，"每秒能处理多少请求" |
| **TPS** | Transactions Per Second，每秒事务数 | 偏写操作/业务事务，一个事务含多次请求 |
| **RT** | Response Time，**平均响应时间** | 单请求从发出到返回的耗时（如 50ms / 500ms） |
| **并发线程数** | 同时处理中的请求数 | 不等于在线人数，不等于 QPS |
| **TP99 / TP999** | 99%/99.9% 请求的耗时上限 | 只看平均会掩盖"尾巴延迟"，线上更关注 TP99 |

**它们之间的关系（Little's Law）**：

```
并发线程数 = QPS × RT

例：目标 2000 QPS、RT = 100ms(0.1s)
    → 需要的并发线程 = 2000 × 0.1 = 200 个
例：同样 2000 QPS、但 RT 涨到 1s
    → 需要 2000 × 1 = 2000 个线程（翻了 10 倍！）
```

**结论一**：在并发线程数固定的前提下，**RT 越长、能支撑的 QPS 越低**。这就是"慢接口吃线程、快接口省线程"的数学本质。前面 5.10 讲的"异步化缩短占用 = 省线程"，本质就是在降 RT、提 QPS。

### 5.13.2 用公式演算：默认 200 线程能扛多少

假设单机 Tomcat `max-threads=200`，按 RT 分三档估算极限 QPS：

| RT（平均响应） | 极限 QPS（200÷RT） | 典型场景 |
|---|---|---|
| 20ms（纯内存/缓存命中） | 10,000 | 查 Redis/本地缓存 |
| 100ms（查一次库） | 2,000 | 常规 CRUD 接口 |
| 500ms（多次查库/外部依赖） | 400 | 组装型接口 |
| 2s（调 LLM/同步重活） | 100 | AI 推理（同步） |

**注意这只是"线程数"层面的理论上限**，真实能到多少还取决于下游（数据库连接池、网络、第三方）能不能跟上。**Tomcat 线程不是第一个瓶颈，数据库连接池才是。**

### 5.13.3 高并发时，真正的瓶颈排队顺序

请求每过一层都要"领号排队"，容量最小的一层决定整体上限（木桶效应）：

```
① 入口层：Nginx/网关（连接数可达几万~几十万，几乎不是瓶颈）
② 应用层：Tomcat 线程池（200）
③ 数据库连接池：HikariCP 默认 10！  ← 通常最早打满
④ 数据库本身：单库单表 SQL 吞吐（几百~几千 TPS）
⑤ 磁盘/网络 IO：最底层物理资源
```

**关键认知**：即使你把 Tomcat 线程调到 2000，如果数据库连接池只有 10，**同一时刻最多也只有 10 个请求在真正查库**，其余 1990 个线程都在"等连接"。**连接池耗尽的表现**：日志出现 `Connection is not available, request timed out`，Tomcat 线程继续堆积 → 最终 Tomcat 也打满 → 新请求排队 → RT 飙升 → 雪崩。

**所以优化顺序的经验法则**：先看下游（DB 连接池/慢 SQL/外部调用）有没有打满，再考虑调应用线程。**"加 Tomcat 线程"是最廉价的表象优化，往往解决不了真瓶颈。**

### 5.13.4 高并发四大板斧（详细展开）

#### 板斧一：缓存挡热点（最有效、最先做）

**原理**：高并发流量里有大量"读同一个热点数据"的请求（同一商品详情、同一任务状态）。让它们不落到数据库，吞吐立刻提升几个数量级。

**缓存四级分层**（从近到远）：

```
浏览器缓存（静态资源 Cache-Control）
  → CDN（全国/全球边缘节点，静态资源就近返回）
    → 本地缓存 Caffeine（进程内，微秒级，如字典）
      → Redis 分布式缓存（跨实例共享，毫秒级）
        → 数据库（最终兜底）
```

**什么数据适合缓存**：读多写少、变化不频繁、允许短暂不一致。典型：商品详情、配置、字典、用户资料。
**不适合**：强实时数据（库存扣减）、写频繁数据。

**Cache Aside 更新模式（标准做法）**：
```
读：先查缓存 → 未命中查库 → 写缓存(带TTL)
写：先更新数据库 → 删除缓存（让下次读重建）
顺序不能反：先删缓存再写库，会读到旧值
```

**本项目对应**：任务详情 `getTaskDetail` 是读多写少热点——正是当时 `@Cacheable` 缓存的原因（现已被移除提交删除，属历史实现）。TTL 30s 就是"允许短暂不一致"的权衡。

#### 板斧二：异步省线程 + MQ 削峰填谷

**目标**：把"重而慢"的操作从请求线程里挪走，让 Tomcat 线程只干"快事"。

**异步化有两种档位**：

**档位 1：线程池异步（响应快但请求仍然同步等结果时）**

```java
// 提交到独立线程池，立即返回
researchTaskExecutor.execute(() -> executeTask(taskId));  // 本项目已用
```
适用：后台任务、可接受"稍后完成 + 轮询/推送进度"的场景。

**档位 2：消息队列削峰填谷（应对瞬时洪峰，如秒杀）**

```
1 万请求/秒（瞬时）                   系统真实处理能力 500/秒
  → MQ 先收下（吞吐可以很高）           → 后台消费者按 500/秒慢慢消费
  → 立刻返回"已受理"
```

| | 直接打到后端 | 经过 MQ |
|---|---|---|
| 瞬时 1 万请求 | 后端直接被压垮 | 全部收进队列，系统稳如泰山 |
| 返回体验 | 大量超时/失败 | 秒回"排队中" |
| 削峰 | ❌ | ✅ 把峰填到谷，平滑处理 |

**核心思想**：**请求量和系统能力不匹配时，不是硬扛，而是"先存下来慢慢做"**——你项目用线程池 + SSE 做的正是 MQ 思想的简化版（队列换成线程池，进度用 SSE 推）。

#### 板斧三：水平扩容 + 负载均衡（"200 线程不够就复制"）

**单机上限是物理的，多机是无限的**：

```
1 台 Tomcat（200 线程）→ 10 台 Tomcat（2000 线程）
Nginx/负载均衡把请求轮流分到各台
```

**扩容的前提：应用必须无状态**：
- ❌ 把用户登录态、临时数据存在本机内存/本机 Session → 请求被分到 B 机器就读不到 → 必须粘性会话或外置
- ✅ 状态放数据库 / Redis（外置）→ 任何机器处理都一样 → 随意加减机器

**本项目适合横向扩容**：任务数据在数据库、进度走 SSE（连的是具体某台但可用网关支持 SSE 长连接的转发），Tomcat 本身不存状态——典型的可水平扩展设计。

**集群进阶**：K8s + HPA（Horizontal Pod Autoscaler）根据 CPU/QPS 指标**自动加减实例**——平时 3 台、大促前扩到 30 台、结束后自动缩回。

#### 板斧四：限流 / 降级 / 熔断（有损保命，防止雪崩）

流量远超系统能力且无法扩容时，目标是**系统别死**，宁可损失部分请求。

**① 限流：挡住超量请求**

| 算法 | 原理 | 特点 |
|---|---|---|
| 固定窗口计数 | 每秒计数，超阈值拒绝 | 简单，临界点可能双倍放行 |
| 滑动窗口 | 细粒度统计最近 N 秒 | 更平滑 |
| **漏桶** | 请求进桶，恒定速率流出 | 平滑流量，能削峰 |
| **令牌桶** | 桶里令牌，拿到才放行，可预存 | 允许突发（常用，Guava RateLimiter） |

单机可用 Guava/Resilience4j；多实例要**分布式限流**（Redis + Lua 统计总量）。限流策略：直接拒绝（返回"系统繁忙"）或排队。

**② 熔断：下游快挂了就快速失败**

```
正常：A → B（B 出错率 < 阈值）
熔断开启：A 不再调 B，直接快速返回兜底（出错率 > 阈值）
半开：过一段时间放少量请求试探，B 恢复了就关闭熔断
```
典型实现：Resilience4j / Sentinel / Hystrix。作用：**别让一个慢下游把整条链路拖垮**（比如依赖的第三方 AI 接口变慢，不熔断的话所有线程都堵在等它）。

**③ 降级：非核心功能让路**

核心功能保住，非核心暂时关掉：推荐位返回默认数据、日志降级不落库、评论功能暂时禁用。**有损但系统活着**。

### 5.13.5 数据库侧：真正的高并发终点

加再多 Tomcat，最终请求都要读写数据库。**数据库容量规划**：

| 手段 | 解决什么 | 说明 |
|---|---|---|
| **连接池调优** | 连接数不够（默认 10） | HikariCP 建议 = `CPU核数×2 + 磁盘数` 附近，别贪大 |
| **索引优化** | 慢 SQL 拖吞吐 | 先 EXPLAIN 分析慢查询 |
| **读写分离** | 读多写少时把读分流到从库 | 主库写、从库读，减轻单库压力 |
| **分库分表** | 单表数据量/写入超单机上限 | 水平拆分（按 ID/用户哈希分片），配套中间件 ShardingSphere/MyCat |
| **NoSQL 分担** | 非核心数据迁移 | 缓存/计数/日志用 Redis/MongoDB/ES，别全压关系库 |

**演进顺序铁律**：缓存 → 索引/连接池 → 读写分离 → 分库分表。**别一上来就分库分表**——那是复杂度最高的终局手段，早期用不上还增加巨大维护成本。

### 5.13.6 怎么验证当前能扛多少（压测与监控）

**不要"觉得能扛多少"，要压出来**：

| 工具 | 用途 |
|---|---|
| wrk / ab | 命令行快速压测 GET 接口 |
| JMeter | 图形化、复杂场景（登录→下单→查询链路） |
| Gatling / k6 | 脚本化压测 |

**压测时看四类指标**：
1. **吞吐**：QPS/TPS 到了多少
2. **RT 分布**：平均、TP99（判断是否有长尾慢请求）
3. **资源水位**：Tomcat `tomcat.threads.busy`（5.12 讲过）、DB 连接池使用率、CPU
4. **失败率**：超时/5xx 比例

**典型结论形态**：
```
100 并发压测 → QPS 稳定 800，RT TP99=180ms，Tomcat busy 稳定在 60
加大到 300 并发 → QPS 卡在 850 不再涨，busy=200（打满），DB 连接池 10/10（已满）
结论：瓶颈是 DB 连接池 → 加连接池 + 缓存热点查询
```

### 5.13.7 从 200 线程到高并发的演进路线（按顺序做）

```
第 1 步（成本最低）：缓存 + 索引 + 连接池调优
   → 单机 200 线程可支撑的 QPS 从几百提到几千（多数系统到此就够）

第 2 步：异步化（线程池/MQ）+ 独立线程池跑慢活
   → Tomcat 线程不再被慢操作占死，抗突发能力提升

第 3 步：水平扩容 + 负载均衡（无状态化）
   → N 台 × 单机容量，吞吐线性增长

第 4 步：数据库读写分离/分库分表、NoSQL 分流
   → 数据层不再成为瓶颈

第 5 步（最后才上）：限流/降级/熔断、全链路监控、容量评估体系
   → 保证极端流量下系统"活着且有底线"
```

### 5.13.8 本项目现状盘点（200 线程够不够，取决于目标）

| 已具备 | 说明 |
|---|---|
| 异步化 ✅ | `researchTaskExecutor` 独立线程池跑 AI 研究流程，Tomcat 线程只接单 |
| SSE 非阻塞 ✅ | 长连接不占工作线程 |
| 无状态 ✅ | 数据在 DB，可横向扩容 |
| Redis 缓存 | 历史实现已被移除（现未启用）——若要支撑更高读并发，这是第一步该恢复的 |
| MQ / 限流 / 熔断 | 未引入（学习阶段不需要） |

**结论**：按"研究任务"这类低频业务（几十个用户并发创建任务），单机 200 线程绰绰有余。真正上线遇到的问题是 **AI 流程耗时几分钟会占大量独立线程池线程** → 那时要先扩 `researchTaskExecutor` 的池大小/队列，再考虑 Redis 缓存读接口、最后上集群。**别过早优化**——先压测，让数据告诉你瓶颈在哪。

### 5.13.9 常见误区澄清

| 误区 | 真相 |
|---|---|
| "并发 100 万" = 一台机器同时扛 100 万 | ❌ 总流量被 CDN/网关/多实例分摊，每台可能只有几千 QPS |
| 高并发 = 调大 max-threads | ❌ 线程调度有开销；瓶颈常在 DB 连接池/下游；正确顺序是先缓存、再扩容 |
| 在线用户数 = 并发线程数 | ❌ 在线 10 万人可能同时只有 2000 人在发请求 |
| 加了缓存一定快 | ❌ 缓存也有穿透/击穿/雪崩问题，且写频繁的数据不适合缓存 |
| QPS 高就成功了 | ❌ 要同时看 RT 和 TP99——高 QPS + 高 RT = 用户其实在排队 |
| 为了高并发一开始就微服务/分库分表 | ❌ 复杂度是渐进加的，单体+缓存+集群能扛住就先别拆 |

### 5.13.10 本章一句话总结

> **200 线程不是天花板，RT 才是核心变量（QPS≈线程÷RT）；四板斧按序上——缓存挡热点 → 异步/MQ 削峰 → 多实例水平扩容 → 限流降级熔断保命；瓶颈常在数据库而不在 Tomcat；先压测找瓶颈，再对症下药，别过早优化。**

---

# 附：核心口诀速记

1. UPDATE/DELETE 先写 WHERE，先 SELECT 验证再执行（防"删库跑路"）
2. ON 管拼不拼得上，WHERE 管拼上的要不要
3. NOT IN 遇 NULL 翻车 → 用 NOT EXISTS
4. 注册 Bean：类上 @Component 家族；第三方类 @Configuration+@Bean
5. @Transactional 只对 Bean 生效，同类 this 自调用会绕过代理失效
6. Controller 薄、Service 厚：接入差异归一化，核心业务只有一份
7. URL 结尾动词=RPC，名词=REST
8. 国内业务层 200+code；404/500 是网关/容器兜底（请求没到业务层）
9. SSE 空闲不占工作线程；慢操作丢独立线程池
10. 高并发 = 缓存 + 异步 + 横向扩容 + 削峰限流，不是改线程数
11. 外键 = 本表存别表主键值的列（可重复可空），不是主键本身
12. 视图 = 存起来的复杂 SELECT（虚拟表，不存数据）
13. @Value 读的是合并后 Environment；@ConfigurationProperties 是批量升级版，需 @EnableConfigurationProperties


