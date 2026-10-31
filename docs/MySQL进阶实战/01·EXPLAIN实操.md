# 01 · EXPLAIN 实操——给 SQL 做体检

> **状态**：⬜ 未学习 ｜ **下一课**：02·索引失效

---

## 一、用法与四列生死线

```sql
EXPLAIN SELECT * FROM research_task WHERE task_no = 'TASK-123';
```

| 列 | 健康值 | 危险信号 |
|---|---|---|
| **type** | const / eq_ref / ref / range | **index（扫全索引）/ ALL（全表扫）** |
| **key** | 用到的索引名 | NULL（没走索引） |
| **rows** | 越小越好 | 与表总行数同量级 |
| **Extra** | Using index（覆盖索引） | **Using filesort / Using temporary** |

## 二、type 从好到坏（记住梯度）

```text
const  >  eq_ref  >  ref  >  range  >  index  >  ALL
主键/唯一  联合主键    普通     范围     扫全索引   全表扫
一击命中   一击命中   索引命中  BETWEEN  （不要沾）  （必须优化）
```

## 三、两个危险 Extra 的含义

```text
Using filesort：ORDER BY 没吃到索引序 → 内存/磁盘额外排序（数据量大就慢）
Using temporary：GROUP BY/DISTINCT 建临时表 → 同上
对策：让 where + order by 的列都在同一个联合索引里
```

## 四、实战判读口诀

```text
一眼看 type：ALL 就亮红灯
二眼看 key：NULL 说明索引白建
三眼看 rows：是不是和表行数一个量级
四眼看 Extra：filesort/temporary 就是"白扫了还要加班"
```

## 五、项目关联

```sql
-- 项目分页列表（场景③ 写过）：
EXPLAIN SELECT * FROM research_task
WHERE status = 'COMPLETED' ORDER BY created_at DESC LIMIT 20;
-- 理想：联合索引 (status, created_at) → type=ref，Extra 无 filesort
```

## 一句话

```text
EXPLAIN 四列：type 定位方式、key 用没用索引、rows 扫多少、Extra 有没有加班。
ALL + filesort = 两个红灯，先建对联合索引再说。
```
