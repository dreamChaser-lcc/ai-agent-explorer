# 03 · 持久化——RDB 与 AOF

> **状态**：⬜ 未学习 ｜ **下一课**：04·缓存三件套

---

## 一、两本账的对比

| | RDB（快照） | AOF（日志） |
|---|---|---|
| 存什么 | 某时刻的全量二进制 | 每条写命令（文本协议） |
| 恢复速度 | **快**（直接加载） | 慢（重放全部命令） |
| 丢失窗口 | 大（上次快照到崩溃） | 小（每秒/每命令刷盘） |
| 文件大小 | 小 | 大（要 rewrite 压缩） |

## 二、工作机制

```text
RDB：fork 子进程 → 子进程把内存整体 dump 成 rdb 文件
     （fork 的 COW 写时复制：父进程继续服务，子进程看"快照"）
     → bgsave 后台执行 / save 阻塞执行（禁用！）

AOF：写命令追加到 aof_buf → 按 fsync 策略刷盘：
     always（每命令，最安全最慢）/ everysec（默认，最多丢 1s）/ no（交给 OS）
AOF rewrite：命令太长 → fork 子进程把"当前状态"压缩成等价的最小命令集
```

## 三、★ 一条命令的完整旅程（含 Lua 脚本）

```text
   客户端命令        │        Redis 进程
  ──EVALSHA──────►  │  内存数据库 ← 唯一的服务真相（权威数据源）
                    │     │ 执行中随时改
                    │     ▼
                    │  待传播队列（also_propagate）
                    │     │ 脚本整体执行完才统一传播
                    │     ▼
                    │  AOF 缓冲区（aof_buf）← 只记【写命令】，读命令不进流水
                    └─────┬───────────────────────┘
                          │ fsync（everysec：最多滞后 1s）
                          ▼
                    ┌─────────────┐
                    │   AOF 文件   │ ← 磁盘上的流水账
                    └─────┬───────┘
                          │ 崩溃重启
                          ▼
                    重放流水 → 重建内存
                    ─── 脚本没执行完 = 流水里没有它 = 效果缺席 ───
```

### 这张图要说明的三件事

**① 内存是权威，AOF 是「事后记账」**

命令先改内存，再把**写命令**追加进 AOF 缓冲区。读命令（`GET` / `EXISTS`）不进流水——所以 AOF 文件重放出来的一定是「数据的演化史」，不含查询。

**② 脚本的传播发生在「整体执行完之后」**

Lua 脚本执行期间，它产生的写命令先挂到 `also_propagate` 队列，**脚本跑完才统一传播**到 AOF 缓冲区和从库。这不是实现细节，而是原子性在持久化层面的体现：

```text
脚本执行到一半 Redis 崩溃
→ 内存中的改动随进程消失（内存是唯一真相）
→ AOF 里也没有这个脚本的任何痕迹
→ 重启重放 = 这个脚本「从未发生过」
→ 不会出现「改了一半」的脏数据
```

**③ 但 AOF 仍有秒级窗口——那是 AOF 的特性，不是脚本的错**

```text
脚本执行完 → 已进 aof_buf → 还没 fsync 到磁盘 → 崩溃
→ 重启后这段效果丢失（everysec 策略下最多丢 1 秒）
```

想「绝不丢」只能 `appendfsync always`（每命令刷盘，性能代价大）。

> **原子性保证的是「要么全做要么全不做」，不保证「做了就不会丢」**——这是两个维度：前者靠单线程执行，后者靠 fsync 策略。

### 与前面章节的联动

```text
09 篇（原子操作）：脚本为什么能原子 —— 单线程执行、中间不被插入
本篇（持久化）：  脚本的效果如何落盘 —— 整体传播 + fsync 窗口
两条合起来：      “要么全做要么全不做”，落盘时机另算

本项目的 deduct_stock.lua：
即使扣减到一半进程崩溃，重放的 AOF 也只会得到「扣减前」或「扣减后」，
不会出现「扣了一半」的中间状态。
```

> 📌 **Redis 7.0 起的变化**：AOF 改为 multi-part 结构（`appendonlydir/` 下 base + incr 文件 + manifest 管理），不再是单个 `appendonly.aof`；且默认 `aof-use-rdb-preamble yes`。上图是**逻辑视角**，物理文件形态在 7.0 之后变了。

## 四、经典组合（生产标配）

```text
RDB + AOF 双开：
  平时 AOF 兜底（丢 1 秒）；重启用 AOF 重放
  RDB 备份用于"快速冷启动"和归档
混合持久化（4.0+）：rewrite 时把 RDB 打头 + 后续增量 AOF——两头的好处
```

## 五、配置写在哪：文件、启动参数还是运行时命令

三层来源，优先级从高到低（与 06 篇 `maxmemory` 同一套规则）：

```text
① 启动命令行参数   redis-server --appendonly yes --appendfsync everysec
② 配置文件         redis.conf 里写 appendonly yes
③ 内置默认值       appendonly no / appendfsync everysec / save 900 1 300 10 60 10000
```

### 各环境的实际位置

| 环境 | 配置文件 | 说明 |
|---|---|---|
| **Windows 服务版**（本项目 dev） | `D:\softwares\Redis\redis.windows-service.conf` | 服务注册时 `--service-run` 指定；同目录的 `redis.windows.conf` 是命令行手动启动用的，**服务不读它** |
| **Docker（compose）** | 镜像默认不带 conf | 要么 `command` 追加参数，要么挂 `redis.conf`（见下） |
| **K8s** | ConfigMap 挂载 `redis.conf` | `command: ["redis-server", "/etc/redis/redis.conf"]` |
| **云托管** | 控制台「参数组」 | 阿里云 Tair / AWS ElastiCache 不提供文件 |

### 配置项清单

**RDB 组**

| 配置 | 默认 | 作用 |
|---|---|---|
| `save 900 1` / `300 10` / `60 10000` | 三条规则 | 满足任一条件即触发快照 |
| `save ""` | — | 空串 = 关闭 RDB |
| `dbfilename` | `dump.rdb` | 快照文件名 |
| `dir` | `./` | 落地目录（**容器里必须挂卷**） |
| `stop-writes-on-bgsave-error` | `yes` | 快照失败则拒写，防止「以为在持久化」 |
| `rdbcompression` / `rdbchecksum` | `yes` / `yes` | 压缩与校验 |

**AOF 组**

| 配置 | 默认 | 作用 |
|---|---|---|
| `appendonly` | **`no`** | 总开关 |
| `appendfsync` | `everysec` | `always` 最安全最慢 / `everysec` 最多丢 1s / `no` 交给 OS |
| `no-appendfsync-on-rewrite` | `no` | rewrite 期间暂停 fsync，减少磁盘抖动 |
| `auto-aof-rewrite-percentage` | `100` | 文件涨到上次的 2 倍触发 rewrite |
| `auto-aof-rewrite-min-size` | `64mb` | 且文件至少这么大 |
| `aof-use-rdb-preamble` | **`yes`**（4.0+） | 混合持久化：rewrite 时 RDB 打头 |

### 本机实测值（Windows 服务版）

```text
save                        = 900 1 300 10 60 10000
dbfilename                  = dump.rdb
dir                         = D:\softwares\Redis
stop-writes-on-bgsave-error = yes
appendonly                  = no          ← 没开
appendfsync                 = everysec
auto-aof-rewrite-min-size   = 67108864    (64MB)
aof-use-rdb-preamble        = yes         ← 一旦开 AOF 就是混合格式
```

### 能热改 vs 必须重启

| 参数 | `CONFIG SET` 热改 | 备注 |
|---|---|---|
| `appendonly` | ✅ | `no → yes` 会**立即触发一次 rewrite**，把当前数据落成 AOF |
| `appendfsync` / `save` / `auto-aof-rewrite-*` | ✅ | 立即生效 |
| `dir` / `dbfilename` | ✅ 但危险 | 旧文件不会自动搬家 |
| `aof-use-rdb-preamble` | ⚠️ | 下次 rewrite 才生效 |

> **`CONFIG SET` 只改内存，重启即回到文件里的值**。要持久，用 `CONFIG REWRITE`（把当前值重写回配置文件，要求文件可写）。

### Docker 里怎么配

**方式 A：compose 追加启动参数**（少量项时简单）

```yaml
  redis:
    image: docker.m.daocloud.io/library/redis:7-alpine
    command: ["redis-server", "--appendonly", "yes", "--appendfsync", "everysec"]
```

**方式 B：挂配置文件**（生产推荐，可版本化）

```yaml
  redis:
    volumes:
      - ./redis.conf:/usr/local/etc/redis/redis.conf
      - redis-data:/data          # ★★ 不挂卷，快照/AOF 会随容器消失
    command: ["redis-server", "/usr/local/etc/redis/redis.conf"]
```

> ⚠️ 本项目 compose 里 redis **目前没有 `volumes`** —— `docker compose down` 之后 RDB/AOF 随容器一起丢。要真持久化，`redis-data:/data` 这条必须有。

### 一个常见误解

`application.yml` 里的 `spring.data.redis.*` 配不了这些——那是**客户端**连接配置（host / port / 超时 / 连接池）。持久化是 Redis **服务端**行为，只能改服务端配置文件或启动参数。

### 生产推荐

```conf
appendonly yes
appendfsync everysec
aof-use-rdb-preamble yes
save 900 1                        # 保留 RDB 用于冷启动与归档
stop-writes-on-bgsave-error yes
dir /data                         # 挂在持久卷上
```

## 六、与已学知识的呼应

```text
COW 写时复制 ← J9 JVM 的"写时复制容器"同一母题
AOF 追加日志 ← MySQL redo log / Kafka 顺序日志同源（WAL 思想）
RDB 快照 ← 与"AOF 全量重放"的关系 = 增量 vs 全量备份之争
```

## 面试速答

```text
Q：RDB 和 AOF 怎么选？ A：生产双开（everysec）——恢复快靠 RDB、丢失少靠 AOF。
Q：bgsave 为什么不阻塞？ A：fork+COW，子进程拷贝的是"冻结视图"。
Q：Lua 脚本执行到一半崩溃会怎样？ A：内存改动和 AOF 记录都没有——重启后脚本
   “从未发生”，不会留下半成品；但 everysec 下仍可能丢最后 1 秒（fsync 窗口）。
Q：原子性和持久性是一回事吗？ A：不是。原子性＝要么全做要么全不做（单线程保证）；
   持久性＝做了会不会丢（fsync 策略保证）。
Q：持久化在哪配？ A：redis.conf 为主；命令行参数可覆盖、CONFIG SET 可热改但不持久
   （重启回到文件值，要固化用 CONFIG REWRITE）；容器里用 command 或挂 conf；
   application.yml 管不了（那是客户端配置）。
Q：CONFIG SET appendonly yes 会怎样？ A：立即触发一次 AOF rewrite 把当前数据落成 AOF，
   但不会写回配置文件——重启又回到 no，除非执行 CONFIG REWRITE。
Q：容器里的 Redis 为什么重启数据就没了？ A：多半是没挂 /data 卷——
   RDB/AOF 文件写在容器可写层，容器删了文件跟着消失。
```

## 一句话

```text
RDB 全量快照（恢复快）、AOF 增量命令（丢得少）；生产双开。
底层全是熟悉的老朋友：COW、WAL。
```
