# J9 · JVM 常识——你的代码跑在哪

> **教材**：`backend/Dockerfile`（`JAVA_OPTS`）+ K8s 部署清单（覆盖）+ Nacos（`JVM_XMS/JVM_XMX`）+ `JvmLab` 动手实验
> **完成**：2026-10-07（**J1–J9 系列收官课**）
> **前置**：J8（字节码 `.class` 的去向）、J1（"门牌号/房子"模型）

---

## 1. JVM 是什么——字节码的"翻译官 + 管家"

```text
你写的 .java ──javac──→ .class 字节码 ──→ ★ JVM 读它、翻译它、执行它
                                            （同时是"管家"：管内存、管线程、管 GC）
```

| 认知 | 说明 |
|------|------|
| `java` 命令 = **启动一个 JVM 进程** | `java -jar app.jar` = "开一台 JVM，把 jar 喂给它" |
| **一次编译，到处运行** | Windows 编译的 jar，Linux 容器照样跑——**K8s 部署天天吃这个红利** |
| 字节码**挑 JVM 版本**，不挑机器 | 所以"Java 21"是关键约束 |

---

## 2. 内存版图（人话版）——门牌号与房子的物理坐标

| 区域 | 人话 | 住什么 | 归属 |
|------|------|--------|------|
| **栈（Stack）** | 每个线程的"**工作台**"（方法调用压便签、返回就撕） | 局部变量、**对象引用**（"门牌号"） | 线程私有 |
| **堆（Heap）** | **对象宿舍楼**（所有 `new` 出来的都住这） | 对象本体（"房子"：ArrayList、String、record…） | 全线程共享 |
| 元空间（Metaspace） | 类"档案室"（`Class` 说明书的宿舍） | 类结构信息 | 一句带过 |

```java
int count = 3;                          // 值直接写在【栈】上（基本类型）
List<String> names = new ArrayList<>(); // "门牌号"在栈；"房子"在堆
```

**★ 口诀：变量看栈，对象看堆。**（8 种基本类型是唯一"值直接写栈上"的东西）

**细节陷阱**：Java 里 **`String` 是对象**（不是基本类型——与 JS 的真实差异点！）——所以 `"hello"` 本体住**堆**（有"常量池"特殊待遇，但那区也归堆管）。

### ★ 与 JS 的运行时同构（前端直觉直接迁移——课堂追问收录）

| 概念 | JS / Node | Java |
|------|-----------|------|
| 函数调用 + 局部变量 | 调用栈（Call Stack） | 栈（Stack） |
| **栈爆了** | `Maximum call stack size exceeded`（写无限递归时见过） | `StackOverflowError` |
| 对象、数组住哪 | 堆（V8 heap） | 堆（Heap） |
| 打扫的 | V8 GC（分代） | GC（分代 / G1） |
| **堆大小上限** | `--max-old-space-size=4096`（打包 OOM 时搜到过） | **`-Xmx256m`** |
| **堆爆了** | `heap out of memory` | `OutOfMemoryError: Java heap space` |

**结论**：凡"**带自动垃圾回收的运行时**"（V8 / JVM / CLR / Python）都是同一份答卷——**栈 + 堆 + GC**。不是"要新学一套"，而是"**给早已熟悉的规律换方言**"。

---

## 3. GC——堆的"自动清洁工"

| 问题 | 答案 |
|------|------|
| 谁在打扫？ | GC——后台自动运行（对比 C 语言手动 `free`——Java 把这活自动化了） |
| 怎么判断垃圾？ | **可达性**：从"根"（活跃引用）顺藤摸瓜——**摸不到的房子 = 没人要了** |
| 打扫会打断程序吗？ | 会"**Stop-The-World**"停一下（毫秒~几十毫秒级）——大堆调优的意义所在 |
| `-XX:+UseG1GC`？ | ★ 选清洁工的"**工种**"（G1 = 分区域打扫的现代工种） |

**★ 一个重要的直觉**：GC 清理是"**复制式搬家**"——把存活对象搬到新区域——**需要"周转空间"**。存活对象极多时，周转空间不足 → JVM 可能提前摊牌（这就是下文"31MB 之谜"的钥匙之一）。

---

## 4. ★ 参数破译表（你的三处真身全部解码）

| 参数/变量 | 破译 | 项目现身 |
|-----------|------|---------|
| `-Xms128m` | **初始堆**大小 | K8s env / micro-lab 清单 |
| `-Xmx256m` | **最大堆**大小（超了就 OOM） | 同上 |
| `-XX:MaxRAMPercentage=75.0` | **按容器内存限额的 75% 算最大堆** | `backend/Dockerfile` |
| `-XX:+UseG1GC` | 指定 G1 清洁工 | `backend/Dockerfile` |
| **`JAVA_OPTS`** | ★ **社区惯例名——不是 JVM 标准** | Dockerfile 自定义 ENV |
| **`JVM_XMS` / `JVM_XMX`** | ★ **Nacos 镜像自己的变量名约定**（非通用） | `docker run -e JVM_XMS=...` |

### 4.1 `JAVA_OPTS` 的魔法在哪一行

```dockerfile
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
#                             ↑ sh 把变量【展开】后才交给 java——所以名字随便起
```

**为什么 K8s 里改 env 就生效**：镜像 ENV 给默认 → 部署清单 env **覆盖它**。
（★ **"配置覆盖链"第 5 次出现**：Nacos 配置源 → K8s env 覆盖 yml → 环境变量三层级 → JAVA_OPTS 被部署覆盖——母题：**默认在里层，覆盖在外层**。）

### 4.2 容器里为什么必须设（`MaxRAMPercentage` 的用心）

**JVM 默认最大堆 ≈【宿主机物理内存】的 1/4**——★ 注意是**宿主机**，不是容器限额！

```text
容器限额 512MB，宿主机 16GB → JVM 认为"我有 4GB 可用" → 一路 new 超配额
     → 被 K8s 的 OOMKiller【掐死】（OOMKilled / 退出码 137）
     → ★ 不是 Java 抛 OOM，是"被外人杀死"——日志里甚至没有遗言（经典悬案）
```

**两种解法**（项目两种都用了）：固定值 `-Xms/-Xmx`（可预测）或 `MaxRAMPercentage`（按配额百分比，自动适配）。

---

## 5. 为什么项目要求 Java 21

| 理由 | 说明 |
|------|------|
| `record` | **J1 第一课就用了**——需要 16+ |
| LTS | 长期支持版（生产稳定性选择） |
| 虚拟线程 | 21 旗舰特性——J8 线程池思维的"未来形态"（概念地基已铺好） |

---

## 6. 动手实操记录（`JvmLab`，2026-10-07）

**第一步：默认体检**（实测）

```text
Java 版本：  21.0.12
JVM 名称：   OpenJDK 64-Bit Server VM
CPU 核心数： 12
最大堆（-Xmx 上限）： 8116 MB      ← ★ JVM 眼里它有 8GB（宿主机 32GB 的 1/4）
当前堆（已向系统申请）： 508 MB
空闲堆：     504 MB
```

**第二步：`java -Xmx64m JvmLab`**——同一份代码：

```text
最大堆（-Xmx 上限）： 64 MB        ← ★ 世界从 8GB 变成 64MB
当前堆（已向系统申请）： 64 MB
```

**第三步：`java -Xmx64m JvmLab oom`**（OOM 现场）：

```text
===== OOM 现场：开始往堆里塞对象（每袋 1MB） =====
  已塞入 16 MB ...
  ★ 塞到 31 MB 时，JVM 摊牌了：
    java.lang.OutOfMemoryError: Java heap space
```

### ★ "31MB 之谜"——64MB 的堆，为什么塞 31MB 就炸了？

**三层解释**（从浅到深）：

1. **公摊面积**：堆里住的不只你的 `bag`——JVM 自己的一堆运行时对象也在堆上（启动时就用了 3MB，运行中还在增长）；
2. **GC 的"搬家周转空间"**（专业层）：G1 清理是复制式搬家——存活对象占满大部分堆时，**没有足够的"周转房"来搬运**——JVM 判断"整理不动了"→ 提前摊牌（否则要等到 100% 才炸）；
3. **实用直觉**：**`-Xmx` 是"整个堆的总量"，不是"你能用的全额"**——所以生产调优里 OOM 可能发生在"看起来还有空间"的时刻，**Xmx 要留余量**。

（验证彩蛋：`java -Xmx128m JvmLab oom`——能塞的距离同样会明显小于 128——规律普遍成立。）

---

## 7. 补充：思考题与答案（课堂问答收录）

**题 1**——K8s 清单里不写 `JAVA_OPTS=-Xms128m -Xmx256m` 会怎样？

**答**：JVM 默认最大堆 ≈ **宿主机物理内存的 1/4**（★★ 它看的是**宿主机**，不是容器限额）——容器限额 512MB 而 JVM 以为有几 GB → 用超配额 → **被 K8s OOMKiller 掐死**（OOMKilled，退出码 137）——**这不是 Java 的 OOM，是"被外人处决"**——Java 日志里什么都找不到（Pod 反复重启才是线索）。
**类比**：租客（JVM）以为整个小区（宿主机）都是他家，囤货囤到被物业清出门。
**两解法**：固定值 / `MaxRAMPercentage`。

**题 2**——`count` / `names` 变量 / `new ArrayList<>()` / `"hello"` 分别住哪？

**答**：

| 东西 | 住哪 | 为什么 |
|------|------|--------|
| `count = 3` | **栈** | 基本类型的值直接写在栈上 |
| `names`（变量本身） | **栈** | 它是"门牌号"（引用） |
| `new ArrayList<>()` | **堆** | "房子"——所有对象住堆 |
| `"hello"` 本体 | **堆** ★ | **String 是对象**（与 JS 的真实差异点——JS 里 string 是基本类型） |

**口诀：变量看栈（门牌号），对象看堆（房子）。**

---

### 附加问答（课堂追问收录）

**追问 1：OOM 到底是什么？**

**答**：`OutOfMemoryError`（内存耗尽）——**堆满了的"摊牌时刻"**：

```text
不断 new → 堆涨到 -Xmx 上限 → 装不下
     → JVM 抛出：java.lang.OutOfMemoryError: Java heap space →（通常）进程崩
```

三个要点：① 它是 **Error 不是 Exception**（J4/J6 知识：灾难级——不是"接住继续"，而是"仓库塌了"）；② 它是**"堆有上限"这件事唯一的现身时刻**；③ 生产常见成因：**内存泄漏**（对象"没人用"但引用链挂着——GC 收不走）/ 突发流量 / 堆设太小。
★ **前端锚点**：Node 的 `heap out of memory` 与 `--max-old-space-size`——和 `-Xmx` 是同一种参数（"这个运行时最多能用多少堆"）。

**追问 2：`java JvmLab` 里的 `JvmLab` 是 Java 内置的吗？**

**答**：**不是——是自己写的类**（住在 `exampleJavaGrammar.java` 里）。`java` 才是 JDK 内置的【启动器】；`JvmLab` 是它的"参数"：

```text
java  JvmLab
└┬─┘  └──┬──┘
 │        └─ 自写类——javac 编译产物之一（JvmLab.class）
 └─ 内置启动器：开 JVM → 在 classpath 找 JvmLab.class → 跑 main()
```

**验证**：`dir *.class` → 一长串 class 文件（**一个 .java 里有几个类，就生成几个 .class**；嵌套类带 `$` 分隔：`BasicsLab$PointRecord.class`——★ **与 J2 的 `$$SpringCGLIB$$0` 同款命名体系**——`$` = "嵌套/生成出来的类"）。
**三层分清**：**内置**（`java`/`javac` 命令、`Runtime`/`System`/`List`/`Thread`…）提供零件；**自写**（`JvmLab` 等）组装用途——JvmLab 内部只是**调用**了内置工具。
**小知识**：普通类不要求"文件名 = 类名"（只有 public 类才要求）——所以这个文件才能装下 8 个类还叫 `exampleJavaGrammar.java`。

---

## 8. 项目关联地图

| JVM 设施 | 项目现身 |
|---------|---------|
| `JAVA_OPTS`（百分比重） | `backend/Dockerfile` 49~52 行 |
| 部署侧覆盖（固定值） | K8s deployment env（`-Xms128m -Xmx256m`） |
| `JVM_XMS/JVM_XMX` | Nacos 容器启动命令（微服务课） |
| Java 21 | `pom.xml`（Boot 3.5.13 要求）+ 本地 `java -version` 21.0.12 |
| 堆/GC 现象 | 日志偶发停顿、OOMKilled（K8s） |

---

## 9. ★ 收官：J1–J9 全景（九课一张图）

| 课 | 一句话 | 核心收获 |
|----|--------|---------|
| **J1** | 语法形态 | record / static / final / lambda——**数据长什么样** |
| **J2** | 接口与多态 | 合同 / 装配 / IoC 钩子 / 反射入门——**行为怎么接** |
| **J3** | 泛型与集合 | List/Map/Optional/Stream 预告——**数据怎么装** |
| **J4** | 异常体系 | 受检/非受检/值班室——**出错怎么办** |
| **J5** | Stream 实操 | 惰性/流水线/短路——**数据怎么加工** |
| **J6** | 枚举 | 状态表达/穷尽检查——**有限选项怎么管** |
| **J7** | 注解体系 | 标签/反射五件套/操作全谱——**怎么给框架传话** |
| **J8** | 并发基础 | 线程池/异步/lambda 传函数——**活怎么后台干** |
| **J9** | JVM 常识 | 栈堆/GC/启动参数——**代码跑在哪** |
| 专题 | 静态 vs 动态 | "编译期还是运行期？"万能发问——**跨课母题** |

> **J 系列九课全部完成**——从"Java 基础不好"到"能读框架级代码、能追执行链、能亲手验证"。
> 下一步：进入《学习路线规划》的业务场景实战清单。

---

*（本课为 J 系列最后一课。下一阶段：业务场景实战——"遇卡即停，随时增补 J10+"。）*

---

## 10. 进阶：面试题——线上 OOM 怎么排查？（2026-10-27 补）

> 学员要求补充面试进阶：OOM 排查与 JVM 调优。J9 正文讲过内存版图（§2）、GC（§3）、
> 参数破译（§4），这节补"出了事怎么查"的实战方法论。

### 10.1 一句话总纲（面试开口先说这句）

```text
先保现场（HeapDump 自动落盘）→ 再找大户（MAT 支配树）→ 后定位代码（GC Roots 引用链）
```

### 10.2 第一步分诊：OOM 的五种类型（报错不同，病不同）

| 报错信息 | 哪满了 | 常见根因 |
|---|---|---|
| `Java heap space` | 堆 | 大集合/一次性大查询/缓存无淘汰 |
| `GC overhead limit exceeded` | 堆（GC 失效） | 内存泄漏——GC 花 98% 时间却收不回 2% 空间 |
| `Metaspace` | 元空间 | 动态类爆炸（CGLIB 代理、反射、脚本引擎、热部署） |
| `unable to create new native thread` | 线程数 | `new Thread` 无上限 / 线程池泄漏 |
| `Direct buffer memory` | 堆外 | NIO DirectBuffer 分配后未释放 |

> 本课 §2 的版图直接对应：堆满、档案室满、宿舍走廊（线程）满——先看报错定位"哪栋楼着火"。

### 10.3 排查七步（标准流程）

```text
① 埋雷（启动参数，事前做）：
   -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/logs/heap.hprof
   → OOM 发生瞬间自动保存堆快照（第一现场，最宝贵）

② 拿到 dump（没埋雷时的补救）：
   jmap -dump:live,format=b,file=heap.hprof <pid>
   （注意：dump 会 STW，生产低峰期做）

③ 快速三板斧（不 dump 先看概况）：
   jstat -gcutil <pid> 1000    → 每 1s 看：新生代/老年代占用、GC 次数与耗时
   jmap -histo <pid> | head -20 → 对象直方图：哪个类的对象最多、最占内存

④ MAT（Eclipse Memory Analyzer）打开 hprof
⑤ Leak Suspects 报告（MAT 一键泄漏分析）
⑥ Dominator Tree（支配树）→ 找最大内存持有者
   → 右键 Path to GC Roots（排除弱引用）→ 谁一直引用它 = 泄漏点
⑦ 定位到代码行 → 修复 → 压测复验
```

### 10.4 命令行工具速查（面试加分）

| 命令 | 作用 | 使用时机 |
|---|---|---|
| `jps` | 找 Java 进程号 | 一切开始 |
| `jstat -gcutil <pid> 1000` | GC 概况（占比/次数/耗时） | 判断"GC 是否在正常工作" |
| `jmap -histo <pid>` | 对象直方图（按大小排序） | 快速看"谁的对象最多" |
| `jmap -dump` | 堆快照 | 深度分析前 |
| `jstack <pid>` | 线程栈快照 | 配合"线程 OOM"和 CPU 100% |
| `arthas dashboard/heapdump` | 阿里在线诊断工具 | 生产环境最常用（Attach 不重启） |

### 10.5 四大经典根因（案例库，面试直接讲）

```text
① 缓存只加不删：HashMap 当缓存，无淘汰无上限 → 最终 heap space
   （正确姿势：Caffeine 设 maximumSize / TTL——见支线 T6）
② 大查询不分页：SELECT 百万行一次性进 List → 瞬间 heap space
   （正确姿势：分页 / 流式读取 / 限定 LIMIT）
③ ThreadLocal 未 remove：线程池的线程长命百岁，每次请求塞一个 → 缓慢累积
   （正确姿势：finally 里 remove，或用 try-with-resources 包装）
④ 动态类爆炸：每次请求都 new 一个 CGLIB 代理类 → Metaspace 满
   （正确姿势：代理类复用缓存——Spring 的代理是启动期一次性生成的）
```

### 10.6 项目关联

```text
· 本项目生产 JVM（Dockerfile）：-XX:MaxRAMPercentage=75.0（§4 已破译）
  → 按容器内存限额自动算堆——这是"容器化部署"的正确姿势
· 生产建议（当前未配，可作为改进项）：
  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp
  → 保留 OOM 第一现场，是排查的起点
· 开发环境的 JAVA_TOOL_OPTIONS 里目前只有代理参数（J9 §4 破译过）
```

### 10.7 一句话

```text
OOM 排查 = 先分诊（五种类型定位病区）→ 保现场（dump）→
           找大户（支配树）→ 定代码（GC Roots 引用链）
事前埋雷（HeapDumpOnOutOfMemoryError）比事后补救（jmap）有价值十倍
```

---

## 11. 进阶：面试题——JVM 调优怎么调？（2026-10-27 补）

### 11.1 诚实的开场（面试第一句这么说，加分）

```text
"大多数场景默认参数就够——调优是【有目标、有数据】之后的动作，不是开局就调。"

面试官问"你调过优吗"，最忌讳一上来背参数清单。
先讲方法论（目标→观测→瓶颈→单参数迭代），再带一两个实战参数。
```

### 11.2 调优三角（目标先行，三者不可兼得）

```text
      吞吐量（单位时间处理量）
        /\
       /  \
      /    \
  延迟 ←————→ 内存占用
```

| 业务场景 | 目标 | 适合的 GC 器 |
|---|---|---|
| 批处理 / 离线计算 | 吞吐量 | Parallel（JDK8 默认） |
| **在线服务（本项目）** | 延迟（P99） | **G1（JDK9+ 默认）** |
| 超大堆 + 极低延迟 | 亚毫秒停顿 | ZGC / Shenandoah（JDK15+/17+） |

### 11.3 GC 器演进（JDK8 → 21，面试高频）

```text
JDK 8：默认 Parallel Scavenge（吞吐优先，STW 长但稀）
JDK 9：默认 G1（Region 化、可预测停顿）★ 本项目 Java 21 用的就是它
JDK 14：CMS 被移除（老面试题"CMS 三色标记"已成历史）
JDK 15/17/21：ZGC / Shenandoah 生产可用（停顿 <1ms，吞吐略降）
```

**G1 一句话**（面试答法）：

```text
把堆切成约 2048 个等大 Region，优先回收"垃圾最多"的（Garbage First），
用 -XX:MaxGCPauseMillis=200 设停顿目标，G1 自己决定每次收哪些 Region。
```

### 11.4 调优参数表（实用就这几个）

| 参数 | 作用 | 实践要点 |
|---|---|---|
| `-Xms` = `-Xmx` | 初始堆 = 最大堆 | 避免运行期扩容抖动；容器里用 `MaxRAMPercentage` 替代 |
| `-XX:MaxRAMPercentage=75.0` | 按容器限额百分比算堆 | **本项目 Dockerfile 在用**（75% 留 25% 给元空间/栈/堆外） |
| `-XX:MaxGCPauseMillis=200` | G1 停顿目标 | 默认 200ms；要求更低会牺牲吞吐 |
| `-XX:MetaspaceSize` / `-XX:MaxMetaspaceSize` | 元空间初始/上限 | 排查"动态类泄漏"时收紧观察 |
| `-Xlog:gc*:file=gc.log:time,uptime:filecount=5,filesize=10m` | GC 日志（JDK9+） | **调优必须先开日志——没数据调什么** |
| `-XX:+HeapDumpOnOutOfMemoryError` | OOM 自动 dump | 排查的保险丝（§10） |

### 11.5 调优流程四步（面试答题骨架）

```text
① 定目标（量化）："P99 接口延迟 < 200ms，Full GC < 1 次/天"
② 开观测：GC 日志 + Prometheus/Grafana（Micrometer 暴露 JVM 指标）
③ 找瓶颈（对照症状表）：
   · Minor GC 太频繁       → 新生代太小（Eden 装不下短期对象）
   · 对象过早晋升老年代     → Survivor 太小 / 大对象直配
   · Full GC 频繁          → 先查泄漏/大对象（§10），再谈调参
④ 单参数迭代：一次只改一个 → 观察 24h → 无效回滚
   （一次改一堆 = 无法归因，等于没调）
```

### 11.6 高频追问三连（附标准答案）

**Q1：线上 CPU 100% 怎么排查？**

```bash
top -Hp <pid>              # 1. 找最耗 CPU 的【线程】TID
printf '%x\n' <tid>        # 2. TID 转十六进制（jstack 里 nid 是 16 进制）
jstack <pid> | grep -A 20 'nid=0x<hex>'   # 3. 定位线程栈 → 看它正在跑什么代码
```

**Q2：内存泄漏 vs 内存溢出？**

```text
泄漏（leak）＝ 慢性病：该回收的对象被引用着，GC 回收不掉 → 堆缓慢上涨 → 最终溢出
溢出（overflow）＝ 急性症状：一次装太多（大查询/大文件）直接爆
泄漏的终点是溢出；溢出不一定有泄漏
```

**Q3：怎么避免 Full GC？**

```text
先分诊：是什么触发的 Full GC？
· 老年代满了   → 查泄漏（§10）/ 大对象直配
· 元空间满了   → 动态类泄漏
· System.gc() → 代码里有人手贱调用（加 -XX:+DisableExplicitGC 屏蔽）
· 晋升太快     → Survivor 太小 / MaxTenuringThreshold 太低
低频 Full GC（天级）可接受；调优目标是"可控"，不是"归零"
```

### 11.7 一句话

```text
JVM 调优 = 目标先行（三角选二）→ 数据说话（GC 日志）→ 单参数迭代（一次一个）
默认参数覆盖 80% 场景；调优是"有症状、有数据、有目标"之后的精修
开口先讲方法论再讲参数，面试官立刻知道你是真做过
```
