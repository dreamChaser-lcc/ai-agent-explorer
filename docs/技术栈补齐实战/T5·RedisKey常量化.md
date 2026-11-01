# T5 · Redis Key 常量化：RedisConstant

> **状态**：✅ 完成（2026-11-01，3 处散落 key 全部迁移，编译通过）
> **前置**：[Redis 实战 01](../Redis实战/01·五大类型与底层结构.md)（key 是 Redis 的"文件名"）/ [场景⑨](../业务场景实战/⑨并发控制实验.md)（分布式锁）
> **上一课**：[T4·ErrorEnum](T4·ErrorEnum错误码枚举.md)
> **依据**：[技术栈对照-live-java规范](技术栈对照-live-java规范.md)（差距清单：RedisConstant 缺失，纠偏 1 小时）

---

## 涉及代码文件（点击阅读）

| 文件 | 本课中的作用 |
|---|---|
| [`RedisConstant.java`](../../backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java) | ★ 本课产出：key 常量与构造方法 |
| [`DistributedTaskLock.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/lock/DistributedTaskLock.java) | 改造点：类内 KEY_PREFIX → 常量类 |
| [`RetentionCleanupJob.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/cleanup/RetentionCleanupJob.java) | 改造点：`tryLock("cleanup")` 裸串 |
| [`ResearchReportQueryService.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportQueryService.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportQueryService.java) | 改造点：`@Cacheable(value="report")` |
| [`ResearchReportAssemblyService.java`](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java) [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/ResearchReportAssemblyService.java) | 改造点：`@CacheEvict(value="report")` |

---

## 一、改造前：3 处散落（实测）

```java
// ① DistributedTaskLock 类内私有前缀（别的类要用只能复制字符串）
private static final String KEY_PREFIX = "lock:task:exec:";

// ② RetentionCleanupJob：裸字符串（拼错编译期查不出）
Optional<String> token = lock.tryLock("cleanup");

// ③ 缓存逻辑名 ×2（读/写两侧必须一致，改一侧忘另一侧 = 缓存永不失效）
@Cacheable(value = "report", key = "#taskId")
@CacheEvict(value = "report", key = "#taskId")
```

**痛点**：

```text
① 拼写错误编译期查不出（"lock:task:exe:" 少个 c → 锁失效，且测试难发现）
② 风格不统一（有人用 ":" 分层、有人用 "_"、有人加环境前缀）
③ 无法一处看全"项目到底用了哪些 Redis key"（排查大 key / 清库时抓瞎）
④ 读/写两侧的 cacheName 必须人工保持一致（改一处忘一处 = 缓存永不失效的幽灵 bug）
```

## 二、命名规范（本类遵守）

```text
格式：类型:业务域:实体:标识
示例：
  lock:task:exec:<taskId>      锁（lock: 前缀 = 分布式锁）
  lock:cleanup                 全局清理锁
  cache:task:detail:<taskId>   缓存（cache: 前缀，新项目建议）

规则：
① 全小写；冒号分层（Redis 社区惯例，redis-cli 里 tree 视图可折叠）
② 动态部分（id）必须用【静态构造方法】拼，禁止调用方手拼
③ 环境前缀（prod:/dev:）由配置注入，不硬编码
```

## 三、RedisConstant 实现

```java
public final class RedisConstant {

    private RedisConstant() {}                       // 工具类禁止实例化

    /* ===== 锁 ===== */
    public static final String LOCK_TASK_EXEC_PREFIX = "lock:task:exec:";
    public static final String LOCK_CLEANUP = "lock:cleanup";

    public static String lockTaskExec(UUID taskId) { // 动态 key 的构造方法
        return LOCK_TASK_EXEC_PREFIX + taskId;
    }

    /* ===== 缓存（Spring Cache 逻辑名） ===== */
    public static final String CACHE_REPORT = "report";   // ★ 值保持不变 = 已有缓存不失效
}
```

**两个设计取舍**：

```text
① 常量化 ≠ 改值：
   CACHE_REPORT 的值保留 "report"（不改成 "cache:report"）
   → 已有 Redis 缓存全部失效的代价换"命名更好看"，不值
   → 规范（cache: 前缀）留给新项目/新 key

② 注解里能用常量：
   @Cacheable(value = RedisConstant.CACHE_REPORT)  ✓
   （static final String = 编译期常量，注解值必须是常量表达式）
```

## 四、改造清单（3 处，实测完成）

| 位置 | 改造前 | 改造后 |
|---|---|---|
| DistributedTaskLock | 类内 `KEY_PREFIX` | `RedisConstant.lockTaskExec(taskId)` |
| RetentionCleanupJob | `tryLock("cleanup")` | `tryLock(RedisConstant.LOCK_CLEANUP)` |
| ResearchReportQueryService | `@Cacheable(value="report")` | `value = RedisConstant.CACHE_REPORT` |
| ResearchReportAssemblyService | `@CacheEvict(value="report")` | `value = RedisConstant.CACHE_REPORT` |

**验证**：`mvn clean compile` 通过（COMPILE=0）；`KEY_PREFIX` 残留 0；裸串 `"cleanup"` 残留 0；RedisConstant 引用 5 处。

## 五、Redis key 设计原则（面试常问）

```text
① 可读：冒号分层（lock:task:exec:uuid 一眼看出是"任务执行锁"）
② 勿大 key：单 key 的 value 别超 10KB（Hash/List 元素数也别上万）
   —— 大 key 会阻塞主线程（Redis 单线程）→ 删除/迁移都卡
③ 必有过期：缓存必须 TTL（无 TTL 的缓存 = 内存泄漏的定时炸弹）
   锁必须 TTL（进程崩溃后的自动兜底）
④ 避免热 key：单个 key QPS 过高 → 打散（加随机后缀分桶）或用本地缓存挡
⑤ 环境隔离：prod:/dev: 前缀；或直接用不同 db/实例（不要靠人工自觉）
⑥ SCAN 代替 KEYS：KEYS * 会阻塞整个 Redis（生产禁用）
```

## 六、面试速答

```text
Q：Redis key 怎么设计？
A：类型前缀 + 冒号分层 + 动态部分用构造方法；必带 TTL；避大 key/热 key。

Q：为什么不能直接用字符串？
A：拼写/一致性纯靠人工——读写两侧 cacheName 不一致 = 缓存永不失效；
   常量化后编译期可查、一处看全、IDE 可跳转。

Q：常量化时值要不要顺便改规范？
A：不要。值变 → 线上缓存全部失效（雪崩一波）。规范留给新增 key。
```

## 一句话

```text
Redis key = Redis 的"文件名"——必须集中管理：
  常量类（前缀 + 构造方法）+ 冒号分层命名 + 必带 TTL
常量化不改值（避免缓存全失效），规范留给新 key
3 处散落迁移完 = 规范差距清单第 5 项清零
```

## 七、追问：`@Cacheable(value=..., key="#taskId")` 里的 key 是什么意思（2026-11-01）

### 两层 key 结构（关键）

```java
@Cacheable(value = RedisConstant.CACHE_REPORT, key = "#taskId")
//          ↑ 第一层 cacheName="report"    ↑ 第二层 SpEL 表达式

Redis 实际 key = cacheName + "::" + key 的值
              = report::f84bca74-13e1-4dd7-ac11-74c6fe4434a0
                ↑ 缓存区（可统一 TTL/清理）  ↑ 业务维度（任务 ID）

文件柜类比：value = 哪个抽屉；key = 抽屉里哪个文件夹
```

### `#taskId` 是 SpEL 引用方法参数

```java
public ResearchReportResponse getReportByTaskId(UUID taskId)   // 参数名 taskId
@Cacheable(key = "#taskId")   // #taskId → 取该参数值
```

| 写法 | 含义 | 场景 |
|---|---|---|
| `#taskId` | 取名为 taskId 的参数 | 单参数 |
| `#req.taskId` | 参数对象属性 | 参数是对象 |
| `#p0` / `#a0` | 第 1 个参数（不依赖参数名） | 参数名不可用时的保险写法 |
| `#result.id` | 返回值属性 | @CachePut 回填 |

### 为什么用 taskId 不是 reportId

```text
方法入口只有 taskId → 缓存维度必须与查询维度一致
用 reportId 作 key = 永远算不出同一 key = 永不命中
业务前提：一个任务只有一份最终报告（1:1）→ taskId 天然唯一
```

### ⚠ 坑：SpEL 依赖 -parameters（与 MapStruct 同源）

```text
#taskId 的 "taskId" 是参数名 → 字节码需保留参数名（编译 -parameters）
本项目 Maven 已配 parameters=true ✓（T2 时为 MapStruct 加的，这里顺带受益）
未开时 SpEL 报 "Property or field 'taskId' cannot be found"
保险写法：#p0（单参数等价，不依赖参数名）
```

### 不写 key 的默认行为

```text
单参数 → 默认用参数值（与 #taskId 等效）
无参数 → SimpleKey.EMPTY
多参数 → SimpleKey(参1,参2).toString()（可读性差、易冲突）
→ 单参数可省但建议显式；多参数必须写
```

### 验证

```bash
redis-cli
> SCAN 0 MATCH report::* COUNT 100
1) "report::f84bca74-13e1-4dd7-ac11-74c6fe4434a0"
> TTL report::f84bca74-...
```