# example-java —— Java 裸跑实验场

**无 Spring、无 Maven**：`javac` 编译 + `java` 直接跑，专门用来“亲眼看见”语法/数据结构的真实行为。

## 文件清单

| 文件 | 内容 | 运行入口 |
|---|---|---|
| `exampleJavaGrammar.java` | J1–J9 六类小实验（多类共文件） | `java ExampleJavaGrammar` / `java EnumLab` / `java AnnotationLab` / `java ThreadPoolLab` / `java BasicsLab` / `java JvmLab` |
| `RedBlackLab.java` | 红黑树（06 课配套） | `java RedBlackLab` |
| `BTreeLab.java` | B 树 2-3 教学版（08 课） | 被 `TreeLab` 调用 |
| `BPlusTreeLab.java` | B+ 树 2-3 教学版（08 课） | 被 `TreeLab` 调用 |
| `NaiveBstLab.java` | 朴素 BST（退化对照组） | 被 `TreeLab` 调用 |
| `TreeLab.java` | **三树 + 查询性能基准** | `java TreeLab` |

## 编译运行

```powershell
cd d:\project\lcc-github\ai-agent-explorer\example-java

# 一次编译全部
javac -encoding utf-8 *.java

# 跑哪个选哪个
java TreeLab                       # B 树 / B+ 树演示 + 4 结构性能对比
java RedBlackLab                   # 红黑树插 1~7 + 五规则自检
java ExampleJavaGrammar            # Stream 四幕
java JvmLab                        # JVM 体检（OOM 现场演示慎跑）
```

> 控制台中文乱码时：先 `chcp 65001`，或加 `-Dfile.encoding=UTF-8`。

## 与讲义的关系

| 实验 | 对应讲义 |
|---|---|
| 红黑树 / B 树 / B+ 树 / 基准 | `docs/数据结构实战/06·平衡树`、`08·B树与B+树` |
| Stream / 枚举 / 注解 / 线程池 / 语法 / JVM | `docs/Java基础实战/J1–J9` |

## 约定

- **每个类带 main 就能独立跑**；多类共文件时只编译一次（`javac *.java`）
- `*.class` 已被 `.gitignore` 忽略，不会入库
