# J12 · 对象相等性契约——equals · hashCode · String 不可变

> **定位**：初学者第一高频坑——`==` 和 `equals` 的区别、为什么重写 equals 必须重写 hashCode、String 为什么不可变。这是 J3（Map/Set）、J6（枚举）、T1（Lombok `@Data`）、场景⑦（record 缓存）的**地基课**。
> **前置**：J1（record）、J3（Map/Set）、T1（Lombok）｜**强关联**：[数据结构实战 04·哈希表](../数据结构实战/04·哈希表：冲突与扩容.md)（hashCode 的底层职责）
> **建立**：2026-11-20

---

## 目录

- [1. 一个真实困惑：两个"相等"的对象，`==` 却是 false](#1-一个真实困惑两个相等的对象-却是-false)
- [2. `==` 的三种行为](#2--的三种行为)
- [3. equals：定义"内容相等"](#3-equals定义内容相等)
- [4. hashCode：equals 的"索引搭档"](#4-hashcodeequals-的索引搭档)
- [5. 经典翻车：只重写 equals 不重写 hashCode](#5-经典翻车只重写-equals-不重写-hashcode)
- [6. String 不可变与字符串池](#6-string-不可变与字符串池)
- [7. 什么时候用 ==，什么时候用 equals（决策表）](#7-什么时候用--什么时候用-equals决策表)
- [8. 常见坑速查](#8-常见坑速查)
- [9. 项目关联地图](#9-项目关联地图)
- [10. 动手实验](#10-动手实验)
- [11. 面试速答](#11-面试速答)
- [12. 一句话总结](#12-一句话总结)

---

## 1. 一个真实困惑：两个"相等"的对象，`==` 却是 false

```java
UserDto a = new UserDto("1001", "lcc");
UserDto b = new UserDto("1001", "lcc");

System.out.println(a == b);       // false ？？两个对象的字段明明一样！
System.out.println(a.equals(b));  // 看情况——取决于 equals 怎么定义
```

**前端类比**：JS 里 `{id:"1001"} === {id:"1001"}` 同样是 false——**对象比较的是引用，不是内容**，这一点 JS 和 Java 完全一致。你已经懂了，只是 Java 把"内容比较"做成了正式契约：`equals`。

---

## 2. `==` 的三种行为

| 比较对象 | `==` 比什么 | 例子 |
|---------|-----------|------|
| **基本类型**（int/long/boolean…） | **值** | `1 == 1` → true |
| **对象引用** | **地址**（是不是同一个对象） | `a == b` → 同一个对象才 true |
| **特例：String 字面量** | 地址相同（池共享）→ true | 见 §6 |

**`==` 对对象永远比"是不是同一个"**——想比"内容一样"必须用 `equals`。

---

## 3. equals：定义"内容相等"

### 3.1 默认行为

`Object.equals` 的默认实现就是 `==`：

```java
// java.lang.Object 源码
public boolean equals(Object obj) {
    return (this == obj);
}
```

所以**不重写 equals 的类，equals 和 == 一样**（比引用）。

### 3.2 重写标准写法

```java
public class UserDto {
    private final String id;
    private final String name;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;                          // 同一个对象，快速返回
        if (!(o instanceof UserDto)) return false;           // 类型不同 = 不相等
        UserDto that = (UserDto) o;
        return Objects.equals(id, that.id)                   // ★ 用 Objects.equals 防 null
                && Objects.equals(name, that.name);
    }
}
```

### 3.3 现代写法：让工具自动生成（三种）

| 方式 | 原理 | 项目实例 |
|------|------|---------|
| **record** | 编译器自动生成 equals/hashCode（按所有字段） | [`ResearchReportResponse`](../../backend/src/main/java/com/aiexplorer/researchagent/api/response/ResearchReportResponse.java)、`CreateOrUpdateVo` |
| **Lombok `@Data`** | 编译期生成 equals/hashCode/toString/getter | 全部 Entity（[`ResearchTaskEntity`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/entity/ResearchTaskEntity.java) 等 16 个文件） |
| **IDE 生成** | 手写样板代码 | 老项目 |

> 前端类比：record/`@Data` ≈ **自动帮你写好 `JSON.stringify` 后逐字段比对**——你定义了字段，工具保证"所有字段相等 = 对象相等"。Lombok 详见 [T1·Lombok](../技术栈补齐实战/T1·Lombok.md)。

### 3.4 equals 必须满足的契约（面试）

```text
自反性：x.equals(x) == true
对称性：x.equals(y) == y.equals(x)
传递性：x.equals(y) && y.equals(z) → x.equals(z)
一致性：多次调用结果相同（不依赖可变状态）
非空性：x.equals(null) == false
```

**常见违反对称性的坑**：`"abc".equals(new MyString("abc"))` true，但 `new MyString("abc").equals("abc")` false（MyString 的 equals 只接受 MyString 类型）——所以判断要用 `instanceof` 双向兼容。

---

## 4. hashCode：equals 的"索引搭档"

### 4.1 为什么需要它

回忆 J3 的 `HashSet`/`HashMap`：存取元素时要**先定位到"桶"**（数据结构 04 讲的哈希表）——**定位靠 hashCode，确认靠 equals**：

```text
set.contains(x)：
  ① x.hashCode() → 算出桶编号 → 直接跳到那个桶   （不用遍历全部 → O(1)）
  ② 桶内逐个 equals(x) → 找到完全相等的 → 返回
```

### 4.2 契约（必须背下来）

```text
① equals 相等的两个对象 → hashCode 必须相等          （★ 否则 HashSet/HashMap 直接坏掉）
② hashCode 相等的两个对象 → equals 不一定相等        （哈希冲突，正常现象）
③ equals 不相等 → hashCode 尽量不相等               （减少冲突，性能好）
```

### 4.3 为什么"只重写 equals 不重写 hashCode"是经典 bug

```java
class UserDto {
    private final String id;
    // 只重写了 equals（内容相等），没重写 hashCode（还是 Object 的地址哈希）
}

Set<UserDto> set = new HashSet<>();
set.add(new UserDto("1001", "lcc"));          // 放进去（hashCode = 地址算出的桶）
set.contains(new UserDto("1001", "lcc"));     // ★ false！
// 过程：新对象 hashCode 不同 → 定位到另一个桶 → 桶里根本没有 equals 相等的对象
// 表现：去重失效、缓存查不到、Map.get 返回 null——而且完全静默
```

**自动生成的工具都会同时生成两个**（record / Lombok `@Data` / IDE）——所以用现代写法不会踩这个坑；**手写时必须成对重写**。

---

## 5. 经典翻车：只重写 equals 不重写 hashCode

（§4.3 的完整复现代码——动手实验 3 会跑一遍）

```java
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

class UserDto {
    private final String id;
    private final String name;

    UserDto(String id, String name) { this.id = id; this.name = name; }

    @Override
    public boolean equals(Object o) {                       // ✅ 重写了 equals
        if (!(o instanceof UserDto)) return false;
        UserDto that = (UserDto) o;
        return Objects.equals(id, that.id) && Objects.equals(name, that.name);
    }
    // ❌ 没重写 hashCode → 继承 Object 的地址哈希
}

public class Bug {
    public static void main(String[] args) {
        Set<UserDto> set = new HashSet<>();
        set.add(new UserDto("1001", "lcc"));
        set.add(new UserDto("1001", "lcc"));      // equals 判断"重复"了吗？
        System.out.println(set.size());           // ★ 2 ！去重失效——两个 hashCode 不同，进了两个桶
        System.out.println(set.contains(new UserDto("1001", "lcc")));  // ★ false
    }
}
```

**修法**：加两行

```java
@Override
public int hashCode() {
    return Objects.hash(id, name);          // ★ 与 equals 用同一批字段
}
// Lombok @Data / record 自动包含这行逻辑
```

---

## 6. String 不可变与字符串池

### 6.1 String 为什么不可变（三个设计原因）

```java
String s = "hello";
s.toUpperCase();          // ★ 返回新字符串，s 还是 "hello"
System.out.println(s);    // hello
```

| 原因 | 说明 |
|------|------|
| **字符串池共享** | `"hello"` 只存一份，多个变量指向同一对象——不可变才敢共享 |
| **hashCode 缓存** | String 的 hashCode 被缓存（第一次算后存起来）——不可变才能缓存，才能放心当 HashMap 的 key |
| **安全** | 路径/类名/网络地址等如果可变，多线程/安全检查会出大问题 |

### 6.2 字面量 vs new（`==` 的经典陷阱）

```java
String a = "hello";
String b = "hello";
System.out.println(a == b);                  // true！两个字面量指向池里同一个对象

String c = new String("hello");
System.out.println(a == c);                  // false！new 强制在堆里建新对象
System.out.println(a.equals(c));             // true——比内容用 equals
```

> **前端类比**：JS 的字符串**同样不可变**（`str.toUpperCase()` 返回新串）——你对 JS 字符串的心智直接可用；差别只在 Java 有"池 + `==` 地址比较"这两个额外的坑。

### 6.3 拼接性能：循环里用 `+` 是反模式

```java
// ❌ 循环拼接：每次 + 都新建对象并复制（O(n²) 总代价）
String sql = "";
for (String col : columns) {
    sql = sql + col + ", ";
}

// ✅ 循环拼接用 StringBuilder（可变缓冲区，O(n)）
StringBuilder sql = new StringBuilder();
for (String col : columns) {
    sql.append(col).append(", ");
}
String result = sql.toString();
```

> **细节**：编译器会把**单行**的 `a + b + c` 自动优化成 StringBuilder——所以简单拼接没问题；**只有循环里的 + 是真坑**。前端类比：JS 同样语义（字符串不可变 + 每次拼接产生新串），V8 也有类似优化，但循环拼接一样是反模式。

### 6.4 String 常用 API 对照表（前端 → Java）

| JS | Java | 备注 |
|----|------|------|
| `str.includes("x")` | `str.contains("x")` | |
| `str.startsWith / endsWith` | 同名 | |
| `str.indexOf("x")` | `str.indexOf("x")` | 找不到都返回 -1 |
| `str.slice(a, b)` | `str.substring(a, b)` | |
| `str.replace(a, b)` | `str.replace(a, b)` | Java 的 replace 是**全部**替换（第一个参数可为正则或字面量）；JS 需 `replaceAll` |
| `str.trim()` | `str.strip()`（Unicode 全角空白也处理） | |
| `str.toUpperCase()` | 同名 | |
| `str.split(",")` | `str.split(",")` | ★ Java 参数是**正则**——按 `.` 分割要写 `split("\\.")` |
| `str.charAt(i)` | `str.charAt(i)` | 返回 char（基本类型） |
| `str.length` | `str.length()` | Java 是**方法**带括号 |

---

## 7. 什么时候用 ==，什么时候用 equals（决策表）

| 类型 | 用什么 | 原因 |
|------|--------|------|
| **枚举** | `==`（推荐） | 枚举实例全 JVM 唯一（J6），且天然防 null |
| **String** | `equals` | 字面量池是特例，`==` 不可靠 |
| **Integer 等包装类** | `equals` | ★ 缓存陷阱：`Integer a=127,b=127; a==b` true（缓存池），`a=128,b=128` false！ |
| **record / @Data 类** | `equals`（自动生成的内容比较） | |
| **自定义类** | 重写 equals 后用 `equals` | |
| **判空** | `x == null` / `x != null` | 引用比较就是判空的本意 |

**两个防 null 技巧**：

```java
// 常量在前，变量在后——变量是 null 也不炸
"FIXED".equals(variable);

// 或用工具类（null 安全的双向比较）
Objects.equals(a, b);
```

---

## 8. 常见坑速查

| # | 坑 | 说明 | 解法 |
|---|-----|------|------|
| 1 | `==` 比 String / 包装类 | 地址比较，结果不可预测 | 用 equals |
| 2 | Integer 缓存陷阱 | `-128~127` 之外 `==` 恒 false | 用 equals |
| 3 | 只重写 equals 不重写 hashCode | HashSet/HashMap 去重失效、get 返回 null | 成对重写（Objects.hash） |
| 4 | hashCode 用了可变字段 | 对象入 Set 后改字段 → 永远取不出来 | hashCode 用不可变字段（如 id） |
| 5 | `str.split(".")` 空结果 | split 参数是正则，`.` 匹配任意字符 | `split("\\.")` |
| 6 | 循环里用 `+` 拼接 | O(n²) 性能炸 | StringBuilder |
| 7 | BigDecimal 用 equals | `1.0` 与 `1.00` equals 为 false（精度也参与比较） | 数值比较用 `compareTo` |
| 8 | Lombok `@Data` + JPA Entity | equals/hashCode 含全部字段（含可变关联）→ 放 Set 后改字段取不出 | Entity 用 `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` 只含 id |
| 9 | equals 参数类型写错 | `equals(UserDto o)` 是重载不是重写（没 @Override 时） | 参数必须是 `Object`，加 `@Override` 让编译器把关 |
| 10 | null 调 equals | NPE | `Objects.equals(a, b)` 或常量在前 |

---

## 9. 项目关联地图

| 知识点 | 项目落点（Ctrl+Click 直达） |
|--------|----------------------------|
| record 自动 equals/hashCode（缓存值比较） | [`ResearchReportResponse`](../../backend/src/main/java/com/aiexplorer/researchagent/api/response/ResearchReportResponse.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/response/ResearchReportResponse.java)） |
| Lombok `@Data` 的 Entity | [`ResearchTaskEntity`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/entity/ResearchTaskEntity.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/entity/ResearchTaskEntity.java)）等 16 个文件 |
| 枚举用 `==` | [`ErrorCode`](../../backend/src/main/java/com/aiexplorer/researchagent/shared/enums/ErrorCode.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/enums/ErrorCode.java)）、`AuditResult` |
| key 字符串常量（池的意义） | [`RedisConstant`](../../backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java)（[网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/constant/RedisConstant.java)） |
| Map/Set 用法 | J3（[J3·泛型与集合.md](J3·泛型与集合.md)）、[专题·集合框架全景](专题·集合框架全景（List·Set·Map·Queue）.md) |
| hashCode 底层（桶定位） | [数据结构实战 04·哈希表](../数据结构实战/04·哈希表：冲突与扩容.md) |
| Lombok 详解 | [T1·Lombok](../技术栈补齐实战/T1·Lombok.md) |
| record 存 Redis（equals 往返） | Redis 08 §4.5（[08·Spring实战整合.md](../Redis实战/08·Spring实战整合.md)） |

**反向索引**：写 `==` 比对象 / Set 去重失效 / Map.get 拿不到 → 回查本篇。

---

## 10. 动手实验

1. **字符串池**：`String a="hello", b="hello", c=new String("hello")` → 打印 `a==b`、`a==c`、`a.equals(c)`；
2. **Integer 缓存陷阱**：`Integer a=127, b=127; a==b`（true）→ 换 128（false）→ 解释原因；
3. **hashCode 缺失翻车**：复现 §5 的 `set.size()==2` → 加 hashCode → 变 1；
4. **record 对照**：定义一个同样的 record → 自动去重成功 → 说明"编译器生成"的价值；
5. **拼接性能**：循环 10 万次 `+` vs `StringBuilder.append` → 计时对比（差距几十倍）；
6. **BigDecimal**：`new BigDecimal("1.0").equals(new BigDecimal("1.00"))`（false）→ `compareTo`（0）→ 业务上用 compareTo。

---

## 11. 面试速答

```text
Q：== 和 equals 的区别？
A：== 比引用（对象是不是同一个），equals 比内容（类定义的语义）。
   基本类型 == 比值。String/包装类必须用 equals。

Q：为什么重写 equals 必须重写 hashCode？
A：HashSet/HashMap 先按 hashCode 定位桶，再按 equals 确认——
   只重写 equals 时，内容相等的对象 hashCode 不同 → 进了不同的桶 → 永远找不到。
   契约：equals 相等 ⇒ hashCode 必须相等。

Q：String 为什么设计成不可变？
A：① 字符串池共享（不可变才敢共享）；② hashCode 缓存（放心当 Map key）；
   ③ 安全（路径/类名不可被篡改）。

Q：Integer 127 == 127 是 true，128 为什么是 false？
A：Integer 有 -128~127 的缓存池，自动装箱时小值直接取缓存对象（地址相同）；
   超出范围 new 新对象 → 地址不同。所以包装类比较一律用 equals。
```

---

## 12. 一句话总结

```text
== 比引用，equals 比内容——对象相等永远用 equals；
equals 和 hashCode 是一对契约：内容相等 ⇒ 哈希必须相等（Map/Set 的地基）；
record / Lombok @Data 自动生成两者——手写时必须成对重写；
String 不可变（池共享 / hashCode 缓存 / 安全），循环拼接用 StringBuilder；
枚举用 ==，包装类用 equals，判空用 == null——一张决策表记住所有场景。
```
