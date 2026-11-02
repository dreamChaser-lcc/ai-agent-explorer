# 专题 · 集合框架全景（List · Set · Map · Queue）

> **定位**：[J3](J3·泛型与集合.md) 讲了泛型、Stream 和 HashMap 细节（§18）；本专题补全**整个家族的全景地图**——每个容器的底层结构、选型依据、避坑要点。
> **前置**：[J3·泛型与集合](J3·泛型与集合.md) / [04·哈希表](../数据结构实战/04·哈希表：冲突与扩容.md)（HashSet 的底层）/ [06·平衡树](../数据结构实战/06·平衡树：AVL与红黑树.md)（TreeMap 的底层）/ [07·堆](../数据结构实战/07·堆与优先队列.md)（PriorityQueue 的底层）
> **图说话风格**：与 04 课一致——一张结构图 + 一条流水线 + 一组对比数字

---

## 一、全景地图（一张图看全家族）

```text
Iterable
  └── Collection（单值容器）
        ├── List（有序、可重复、有索引）
        │     ├── ArrayList        ← Object[] 动态数组（默认首选）
        │     ├── LinkedList       ← 双向链表（+实现 Deque）
        │     └── CopyOnWriteArrayList  ← 写时复制（并发）
        ├── Set（不可重复）
        │     ├── HashSet          ← = HashMap 换皮（哈希）
        │     ├── LinkedHashSet    ← = LinkedHashMap 换皮（保插入序）
        │     └── TreeSet          ← = TreeMap 换皮（红黑树排序）
        └── Queue / Deque（队列/双端）
              ├── ArrayDeque       ← 循环数组（栈/队列首选）
              ├── PriorityQueue    ← 二叉堆（07 课）
              └── LinkedList       ← 也实现 Deque（但少用）

Map（键值对，不属于 Collection——单独一棵）
  ├── HashMap                    ← 数组+链表/红黑树（04 课/J3 §18）
  ├── LinkedHashMap              ← HashMap + 双向链表（保序/LRU）
  ├── TreeMap                    ← 红黑树（排序/范围）
  └── ConcurrentHashMap          ← 线程安全（并发首选）
```

**先记住一句话**：`Set 三兄弟就是 Map 换皮，`List 两个主力是"数组 vs 链表"，`Queue 的 ArrayDeque 吊打 LinkedList。

---

## 二、ArrayList：动态数组（90% 场景的选择）

### 结构全景

```java
transient Object[] elementData;   // 底层就是一个 Object 数组
private int size;                 // 实际元素数（≠ 数组容量）
```

### 扩容流水线（add 时容量不够）

```text
new ArrayList()          → elementData = {}（懒初始化，容量 0）
首次 add("a")            → 扩到 10（DEFAULT_CAPACITY）
   [a][ ][ ][ ][ ][ ][ ][ ][ ][ ]   size=1 capacity=10
add 满 10 个后再 add     → 扩容 1.5 倍：10 → 15
   新建 Object[15]，System.arraycopy 搬运旧数据 ← 一次 O(n) 拷贝
再满 → 15 → 22 → 33 → 49 ...（每次 ×1.5）

10 万次 add 的总拷贝代价：均摊下来每次 O(1)（均摊分析）
```

### 读 vs 写的复杂度

```text
get(i)          O(1)   ← 数组基址 + i×元素大小 直接寻址（CPU 缓存友好）
add(末尾)       O(1)均摊
add(中间/头部)  O(n)   ← 后面全部元素要右移一格
   [a][b][c][d] → 在 index=1 插入 x → [a][x][b][c][d]
                          b、c、d 全部右移（System.arraycopy）
remove(中间)    O(n)   ← 同理左移
```

### 三个坑

```text
① 遍历时删除 → ConcurrentModificationException（modCount 检测）
   正确姿势：iterator.remove() 或 removeIf()
② Arrays.asList(...) 返回固定长度列表（add 会抛异常）
③ 初始知道大小时 new ArrayList<>(10000) —— 省掉多次扩容拷贝
```

---

## 三、LinkedList：双向链表（实际开发很少用的真相）

### 结构全景

```java
private static class Node<E> {
    E item;
    Node<E> next;      // 后指针
    Node<E> prev;      // 前指针
}
// 首尾各一个 first/last 指针
```

```text
first                                    last
  ↓                                        ↓
[3] ⇄ [7] ⇄ [12] ⇄ [1] ⇄ [9]
  每个节点：item + next + prev（三个引用，内存开销大）
```

### 复杂度对比（与 ArrayList 反着来）

```text
头/尾插删       O(1)   ← 改指针即可
   [3]⇄[7] 插头 x：[x]⇄[3]⇄[7]   只动两个指针
随机访问 get(i) O(n)   ← ★ 必须从头（或尾）数过去
   get(9999) 要走 9999 步
```

### 选型真相（面试加分点）

```text
教科书说："频繁插删用 LinkedList" —— 实际大多不成立：

① 随机位置的插删：先 O(n) 定位 + O(1) 改指针 = 还是 O(n)，不比 ArrayList 快
② CPU 缓存局部性：ArrayList 内存连续 → 缓存命中率极高；
   LinkedList 节点散落堆中 → 每跳一步都可能 cache miss
   → 实测遍历 ArrayList 比 LinkedList 快数倍
③ 作者 Josh Bloch 自己说："I never use LinkedList"

LinkedList 的真实用武之地：频繁头尾操作的队列/栈
—— 但这个场景 ArrayDeque 更快（连续数组 + 循环指针）→ 所以 LinkedList 几乎总是被替代
```
---

## 四、Set 三兄弟：本质都是 Map 换皮

### 去重原理（HashSet 的源码就两行）

```java
public class HashSet<E> {
    private transient HashMap<E, Object> map;          // ← 底层就是一个 HashMap
    private static final Object PRESENT = new Object(); // ← 所有 value 都是它

    public boolean add(E e) {
        return map.put(e, PRESENT) == null;            // ★ put 返回 null = 之前没有 = 添加成功
    }
    // contains / remove 也全部转发给 map
}
```

```text
Set 的"不重复" = HashMap 的"key 不重复"（hash + equals 判重，04 课/J3 §18）
→ 用自定义对象做 Set 元素/Map key 时：必须正确重写 hashCode 和 equals
  （只重写 equals 不重写 hashCode → 两个"相等"的对象 hash 不同 → 判重失效）
```

### 三兄弟对照（一句话记住）

```text
HashSet       = HashMap 换皮          → 无序（遍历顺序不可预测）
LinkedHashSet = LinkedHashMap 换皮    → 保插入顺序（多一条双向链表记顺序）
TreeSet       = TreeMap 换皮          → 自动排序（红黑树，06 课）/ 支持范围
```

```text
示例：依次 add("c" "a" "b")
HashSet       遍历：可能 [a, c, b]（hash 决定）   无序
LinkedHashSet 遍历：[c, a, b]（插入序）            保序
TreeSet       遍历：[a, b, c]（自然排序）          排序
```

---

## 五、Map 家族四将

### ① HashMap —— 04 课/J3 §18 已深入（数组+链表+红黑树、扰动、扩容），此处略

### ② LinkedHashMap：HashMap + 双向链表（保序 + LRU 的实现载体）

```text
在 HashMap 的桶结构之外，每个节点多两个指针（before/after）串成双向链表：

插入序模式（accessOrder=false，默认）：
  put 顺序 c → a → b → 遍历顺序就是 c, a, b（和插入一致）

访问序模式（accessOrder=true）★ LRU 缓存的核心：
  get(b) 后，b 自动挪到链表尾（最近使用）
  淘汰时从链表头删（最久未使用）

★ 重写 removeEldestEntry → 一个 10 行的 LRU 缓存：
    new LinkedHashMap<K,V>(16, 0.75f, true) {   // true = 访问序
        protected boolean removeEldestEntry(Map.Entry e) {
            return size() > 1000;               // 超容量删最久未用
        }
    }
—— 这就是 LeetCode 146（LRU 缓存）的标准答案
—— 也是 T6 Caffeine 的"手工前身"（Caffeine 用更先进的 W-TinyLFU）
```

### ③ TreeMap：红黑树（06 课的知识落地）

```text
底层 = 红黑树 → key 自动排序
独有能力：firstKey/lastKey/floorKey(≤)/ceilingKey(≥)/headMap/tailMap/subMap
适用：需要按 key 范围查询/遍历的场景（如"查某时间段的日志"——按时间戳做 key）
代价：put/get O(log n)（比 HashMap 的 O(1) 慢）
```

### ④ ConcurrentHashMap：并发首选（Hashtable 的替代者）

```text
JDK 7：分段锁（16 段，锁粒度 = 段）
JDK 8+：CAS + synchronized 锁单个桶头节点——粒度更细
√ 读不加锁；✗ key/value 不允许 null（HashMap 允许 null——别搞混）
```

---

## 六、Queue / Deque：ArrayDeque 与 PriorityQueue

### ArrayDeque：栈和队列的首选实现

```text
底层 = 循环数组（head/tail 两个指针在数组里转圈）：

     tail
      ↓
 [ ][7][8][9][ ][ ]
  ↑ head 在尾部时绕回数组开头（所以叫"循环"）

做栈：push/pop 操作 head 端      O(1)
做队列：offer 尾部进 + poll 头部出 O(1)
两端都能操作（Deque = Double Ended Queue）
```

```text
选型铁律：
  要栈 → ArrayDeque（不要用 Stack 类——继承 Vector，全方法加锁，过时）
  要队列 → ArrayDeque（不要用 LinkedList——前述缓存劣势）
  要优先级 → PriorityQueue（下面）
```

### PriorityQueue：二叉堆（07 课直接落地）

```text
底层 = 数组组织的完全二叉堆（小顶堆默认）
父子公式：parent=(i-1)/2, left=2i+1, right=2i+2（07 课速览的内容）
用途：TopK / 任务调度（按优先级出队）/ Dijkstra 最短路

PriorityQueue<Integer> pq = new PriorityQueue<>();       // 小顶堆
pq.offer(5); pq.offer(1); pq.offer(3);
pq.poll();  // → 1（每次取最小的）

前 K 大：维护 K 大小的小顶堆（07 课 TopK 模式）
```
---

## 七、线程安全版对照（一张表）

| 普通容器 | 线程安全替代 | 实现方式 | 适用 |
|---|---|---|---|
| ArrayList | **CopyOnWriteArrayList** | 写时复制（改数据 = 复制整个新数组） | 读极多写极少（监听器列表） |
| ArrayList/LinkedList | ConcurrentLinkedQueue / Deque | CAS 无锁 | 高并发队列 |
| HashMap | **ConcurrentHashMap** | CAS + synchronized 锁单桶 | 并发 Map 唯一正解 |
| TreeMap | ConcurrentSkipListMap | 跳表（无锁链表+多层索引） | 并发 + 排序 |
| 全部 | Collections.synchronizedXxx | 每个方法全表锁 | ✗ 淘汰（性能差） |

```text
CopyOnWriteArrayList 的取舍：
  读：无锁（读的是当前数组快照）→ 极快
  写：复制整个数组 → 写一次 O(n)，写多则灾难
  → 只适合"读远多于写"（如事件监听器列表：注册一次，触发无数次）
```

---

## 八、选型速查表（实战 + 面试一张表）

| 需求 | 选择 | 理由 |
|---|---|---|
| 列表默认选择 | `ArrayList` | 数组 + 缓存友好 |
| 知道大小的列表 | `new ArrayList<>(n)` | 免扩容拷贝 |
| 只读列表 | `List.of(...)` / `.toList()` | 不可变（J3 §4） |
| 栈 / 队列 | `ArrayDeque` | 循环数组，双端 O(1) |
| 优先级出队 | `PriorityQueue` | 堆（07 课） |
| 去重（无序） | `HashSet` | HashMap 换皮 O(1) |
| 去重（保插入序） | `LinkedHashSet` | + 双向链表 |
| 去重（自动排序） | `TreeSet` | 红黑树 |
| 键值映射（默认） | `HashMap` | O(1) |
| 键值 + 访问序（LRU） | `LinkedHashMap(accessOrder=true)` | 淘汰链表头 |
| 范围查询 / 排序键值 | `TreeMap` | 红黑树 |
| 并发 Map | `ConcurrentHashMap` | CAS + 桶锁 |
| 并发读多写少列表 | `CopyOnWriteArrayList` | 写时复制 |

---

## 九、面试速答（高频四连）

```text
Q：ArrayList 和 LinkedList 怎么选？
A：默认 ArrayList——内存连续缓存友好，随机访问 O(1)；
   LinkedList 随机访问 O(n) 且节点散落缓存不友好；
   它宣称的"插删快"只在头尾成立，而这个场景 ArrayDeque 更好。
   （引用作者 Josh Bloch：他自己都建议不用 LinkedList）

Q：HashSet 怎么实现去重？
A：底层 HashMap，add 即 put(key, PRESENT)——
   判重 = 先比 hashCode（定位桶）再比 equals（桶内确认）。
   自定义对象必须同时重写两者，只重写 equals 会判重失效。

Q：什么是 fail-fast？
A：ArrayList/HashMap 等用 modCount 记录结构修改次数，
   iterator 遍历中发现 modCount 变了 → 立即抛 ConcurrentModificationException。
   目的：尽早暴露并发/遍历中修改的 bug（快速失败而非带病运行）。
   遍历中删除的正解：iterator.remove() / removeIf()；
   并发场景用 CopyOnWriteArrayList（fail-safe：遍历旧快照）。

Q：手写一个 LRU 缓存？
A：LinkedHashMap(accessOrder=true) + 重写 removeEldestEntry（10 行）。
   原理：get 时节点移到链表尾（最近使用），超容量删链表头（最久未用）。
   进阶：为什么 Caffeine 不用 LRU？→ 扫描污染（T6 课，W-TinyLFU）。
```

---

## 十、一句话

```text
家族地图：List（数组 vs 链表）/ Set（Map 换皮）/ Queue（ArrayDeque+堆）/ Map（四将）
默认选择：ArrayList + HashMap + ArrayDeque——先满足 90% 场景
特殊需求再换：保序（Linked*）/ 排序（Tree*）/ 并发（Concurrent* / CopyOnWrite*）
原理都学过：HashMap（04/J3）、红黑树（06）、堆（07）、LRU（T6 前身）——本专题是把它们装进家族图谱
```
---

## 附录：跳表（Skip List）——ConcurrentSkipListMap / Redis ZSet 的底层（2026-11-02 学员要求）

### 一、解决什么：有序链表查找 O(n)

```text
有序链表：head → 10 → 15 → 20 → 30 → 40 → 50 → nil
查 40 = 挨个走 5 步（O(n)，10 万节点平均走 5 万步）
链表不能二分（无法随机跳到中间）——跳表的解法：加"快速通道"层
```

### 二、结构全景（三层）

```text
L2（抽样）：head ────────────────→ 30 ─────────────→ nil
L1（半抽样）：head ─────→ 15 ──────→ 30 ─────→ 50 ─→ nil
L0（原始）：head → 10 → 15 → 20 → 30 → 40 → 50 → nil

底层 L0 = 完整有序链表；上层是下层的"抽样"（被抽中的节点多层存在）
```

### 查 40 的走法

```text
L2：head → 30（40>30 往右）→ next nil（太大，下降）
L1：30 → 50（40<50，下降）
L0：30 → 40 ✓ 命中

走的节点：head → 30 → 50 → 40 = 4 步（链表版 5 步）
每下一层剩余范围约减半 → 期望 O(log n)（100 万节点 ≈ 20 步，与红黑树同级）
```

### 三、插入：不旋转不变色，抛硬币定层数

```text
插入 25：抛硬币（每次 50% 概率升层，封顶 32）→ 假设 2 层
L0 找位插入；抽中的层也插入。完成。

L2: head ──────→ 30 ──────────→ nil
L1: head ─→ 15 → 25 → 30 → 50 ─→ nil      ← 25 抽中 L1
L0: head → 10 → 15 → 20 → 25 → 30 → 40 → 50 → nil

概率平衡 vs 严格平衡：
  红黑树：旋转/变色保证平衡（确定性、规则复杂）
  跳表：随机层数统计上平衡（第 i 层期望节点数 n/2^i → 天然金字塔）
```

### 四、经典面试：Redis ZSet / ConcurrentSkipListMap 为什么用跳表不用红黑树

```text
① 实现简单：无旋转无变色，代码量红黑树的 1/3
② 范围查询强：底层有序链表 → 定位起点后顺序扫（B+ 树叶链同款优势）
③ 并发友好：插入只改邻居指针（ConcurrentSkipListMap 无锁读的基础）；
   红黑树插入旋转要动多个节点，锁范围大
④ 性能相当：统计上跳表实际表现与红黑树持平甚至更好

红黑树优势：最坏情况严格保证（跳表靠概率，理论上有极小退化可能）

选型：
  有序 + 单线程 + 最坏保证  → 红黑树（TreeMap）
  有序 + 高并发 / 范围扫描  → 跳表（ConcurrentSkipListMap / Redis ZSet）
```

### 五、本项目/学习地图位置

```text
① Redis ZSet（排行榜）：跳表 + dict 双结构——跳表管排序/范围（ZRANGE），dict 管 O(1) 单点（ZSCORE）
② ConcurrentSkipListMap：TreeMap 的并发替代（T7 讲义"并发 + 排序"的底层）
③ 学习链：链表（集合专题）→ 跳表（本节）→ 对比红黑树（06 课）→ B+ 树叶链（08 课）
   —— 三者都是"有序结构的查找与范围"这条线
```