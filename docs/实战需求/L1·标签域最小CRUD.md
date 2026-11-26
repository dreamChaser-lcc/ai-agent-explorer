# L1 · 标签域最小 CRUD（0.5 天）

> **本级目标**：从零建表 → 5 个 RESTful 接口 → 参数校验 → 统一错误码 → 测试 + 实测。
> **前置**：[八级路线图](00·八级路线图.md)｜**下一级**：L2 分层重构
> **约束**：在现有 backend 内建包；MyBatis；**不实现**分页/关联任务/缓存/锁（留给后面级别）

---

## 目录

- [一、业务背景](#l1-1)
- [二、任务清单（6 项，必须全部完成）](#l1-2)
- [二点五、PO 注解规范：为什么 TagEntity 用 @Getter/@Setter/@NoArgsConstructor 而不用 @Data（2026-11-26 学员问）](#l1-3)
- [三、验收标准（A1–A9，逐条打勾）](#l1-4)
- [四、技术约束](#l1-5)
- [五、关联课程（卡住回查）](#l1-6)
- [六、提示（不给答案）](#l1-7)
- [七、交付物](#l1-8)
- [附：AI 补全记录（2026-11-26）](#l1-9)
- [附录 A：L1 用到的注解总览（含类似注解对比）](#l1-10)
- [附录 B：涉及文件索引（可点击直达源码）](#l1-11)

---

<a id="l1-1"></a>
## 一、业务背景

```text
任务系统需要"标签"能力：给任务打标签（如 重要/紧急/LLM/数据库），便于分类与检索。
本级只做标签的"独立管理"（增删改查），不涉及与任务的关联（L3 才做）。
```

<a id="l1-2"></a>
## 二、任务清单（6 项，必须全部完成）

### 2.1 建表（DDL 写进 schema-dev.sql，幂等）

**表：`tag`**（字段由你设计，但必须满足）

```text
必需字段（语义）：
  · 主键（UUID 或 自增，二选一，说明理由）
  · 标签名 name（业务唯一；长度/字符集你定）
  · 描述 description（可为空）
  · 状态 status（启用/停用——枚举存字符串还是数字，你定）
  · 创建时间 create_time / 更新时间 update_time

必需约束：
  · name 唯一索引（★ L4 幂等会用到）
  · status 普通索引（★ L3 查询会用到）
  · 幂等 DDL：CREATE TABLE IF NOT EXISTS / 索引也用 IF NOT EXISTS（参考 schema-dev.sql 里 request_pipline 的写法）
```

**交付**：`schema-dev.sql` 追加建表段（放在文件末尾，带注释说明"L1 标签域"）。

### 2.2 实体 + Mapper（MyBatis）

```text
· TagEntity（PO，纯数据类，不挂 JPA 注解）
· TagMapper 接口：insert / selectById / selectByName / selectAll / update / deleteById
· TagMapper.xml：放 mapper/tag/TagMapper.xml（mapper-locations 已配置递归 **/*.xml）
· namespace 与接口全限定名一字不差
```

### 2.3 DTO / VO

```text
· CreateTagRequest：name、description
· UpdateTagRequest：description、status（name 是否允许改，你决定并说明）
· TagResponse：id、name、description、status、createTime、updateTime
（命名/字段风格参考 SP11 的 CreateOrUpdateDto 与 RequestPiplineVo）
```

### 2.4 参数校验（Jakarta Validation）

```text
· name：@NotBlank + @Size(min=2, max=32) + @Pattern（允许 字母/数字/中文/-/_）
· description：@Size(max=200)
· 校验失败 → 全局异常处理返回项目统一格式（code/message/path/fields/retryable）
  ★ 复用项目已有的 ApiErrorResponse 与 GlobalExceptionHandler（不要新造格式）
```

### 2.5 统一错误码（复用项目格式）

| 场景 | HTTP | code |
|---|---|---|
| 参数非法 | 400 | `MALFORMED_REQUEST`（项目已有） |
| 标签不存在 | 404 | `TAG_NOT_FOUND`（新增） |
| 标签名重复 | 409 | `TAG_NAME_DUPLICATE`（新增） |

```text
要求：新增错误码在项目的错误码枚举/常量里定义（找现有的放法，保持一致）
```

### 2.6 Controller（RESTful 五接口）

| 方法 | 路径 | 说明 | 成功状态 |
|---|---|---|---|
| POST | `/api/tags` | 创建 | 201 + TagResponse |
| GET | `/api/tags/{id}` | 按 id 查 | 200 |
| GET | `/api/tags` | 列表（**全量，不分页**——L3 加分页） | 200 + 数组 |
| PUT | `/api/tags/{id}` | 更新（description/status） | 200 |
| DELETE | `/api/tags/{id}` | 删除 | 204 |

```text
★ 不要套用 request-pipline 的 POST /create 风格（那是练习用），这里是标准 RESTful
```

<a id="l1-3"></a>
## 二点五、PO 注解规范：为什么 TagEntity 用 @Getter/@Setter/@NoArgsConstructor 而不用 @Data（2026-11-26 学员问）

```text
@Data = @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
（★ @Data 不包含 @NoArgsConstructor）

持久化实体（PO）只保留三个注解：
  @Getter / @Setter        实体需要（读写字段）
  @NoArgsConstructor       MyBatis/JPA 反射实例化需要（无参构造）
不用的三个（都是实体类的坑）：
  ❌ @ToString            打印整实体（大字段/关联对象/敏感字段）→ 日志爆炸；双向关联会栈溢出
  ❌ @EqualsAndHashCode   字段会变（update 后 hashCode 变）→ 放进 HashSet/Map 找不回来；
                          新建对象 id=null → 两个不同对象被判"相等"
  ❌ @RequiredArgsConstructor  生成 final 字段构造器，不是无参构造 → 反而要手动补 @NoArgsConstructor
```

**项目对照**：

| 类型 | 注解 | 例子 |
|---|---|---|
| 持久化实体（PO） | `@Getter @Setter @NoArgsConstructor` | `ResearchTaskEntity` / `RequestPiplineEntity` / `TagEntity` |
| DTO（请求参数） | `@Data` 可用（无持久化语义） | `CreateTagRequestDto` / `UpdateTagRequestDto` |
| VO（响应） | `record`（不可变 + 自动 equals） | `TagResponse` |

**一句话**：

```text
实体 = 数据库行的镜像 → 只要 get/set + 无参构造；
      不要 toString（怕爆炸/循环）、不要 equals/hashCode（字段会变、id 会空）。
DTO/VO = 一次性传输对象 → @Data 随便用。
```
<a id="l1-4"></a>
## 三、验收标准（A1–A9，逐条打勾）

```text
□ A1 启动后 tag 表存在（lite：H2 console 查；docker：PG 查）——附查询截图/输出
□ A2 POST 创建成功 → 201 + 返回带 id 的完整对象
□ A3 同名再创建 → 409 + code=TAG_NAME_DUPLICATE
□ A4 name 空/超长/特殊字符 → 400 + code=MALFORMED_REQUEST + fields 指出具体字段
□ A5 GET 不存在的 id → 404 + code=TAG_NOT_FOUND
□ A6 GET 列表 → 200 + 数组（至少 2 条时顺序稳定，说明排序依据）
□ A7 PUT 更新 description → 200（update_time 变）；DELETE → 204 后再 GET → 404
□ A8 mvn test 全绿 + 至少 3 个测试（创建成功 / 重名 409 / 非法参数 400）
□ A9 交验证记录（模板见路线图第四节），含启动日志 + curl 原文 + 测试输出
```

<a id="l1-5"></a>
## 四、技术约束

```text
① 不新建 maven 模块/项目（在 backend 内加包，如 api/tag、application/tag、infrastructure/tag）
② MyBatis（禁止 JPA）
③ DDL 幂等（CREATE TABLE IF NOT EXISTS）
④ 不实现分页/任务关联/缓存/锁/事件（后面级别）
⑤ 不破坏现有测试（mvn test 全绿）
⑥ 错误格式沿用项目 ApiErrorResponse（不要自造 JSON）
```

<a id="l1-6"></a>
## 五、关联课程（卡住回查）

| 卡点 | 回查 |
|---|---|
| 建表/DDL 写法 | `schema-dev.sql` 里 request_pipline 段；[S5·表结构与约束](../SQL基础实战/S5·表结构与约束.md)（DDL/约束/主键/索引） |
| 分层怎么分包 | [SP11](../Spring基础实战/SP11·请求管线手写实战（request-pipeline-lab）.md) 的包结构 |
| 校验注解 + 全局异常 | SP11 第 4/8 节；场景② 错误码设计 |
| MyBatis XML/namespace | SP11 第 9 节；场景③ |
| RESTful 状态码 | [HTTP 03·状态码全景](../HTTP网络实战/03·状态码全景.md)（201/204/400/404/409 语义） |
| 索引怎么建 | [S6·索引与执行计划](../SQL基础实战/S6·索引与执行计划.md) + [MySQL 01·EXPLAIN实操](../MySQL进阶实战/01·EXPLAIN实操.md) / [02·索引失效场景](../MySQL进阶实战/02·索引失效场景.md) |

<a id="l1-7"></a>
## 六、提示（不给答案）

```text
① 表字段设计先写"业务字典"（name 2-32 字符、status 两态）再定类型（VARCHAR(32)/CHAR(8)）
② 主键选 UUID 还是自增：想想 L4 幂等键和 L3 分页排序哪个更好用
③ 重名检测两条路：先 selectByName 判断（快但有并发窗口）or 靠唯一索引兜底（L4 会讲）
   → L1 可以先用"查询判断"，但要注释说明"L4 会用唯一约束兜底"
④ Mapper XML 的 resultMap 别忘了映射 create_time → createTime（map-underscore-to-camel-case 已开，可不写 resultMap，但要确认生效）
⑤ 测试用 @WebMvcTest（切片，参考 SB6）+ Mock Service；或 @SpringBootTest（慢但真实）——你自己选并说明理由
```

<a id="l1-8"></a>
## 七、交付物

```text
① 代码：表 DDL + Entity + Mapper(接口+XML) + DTO/VO + Service + Controller + 错误码
② 测试：至少 3 个（创建/重名/非法参数）
③ 验证记录（模板见路线图）：启动日志 + 5 个接口 curl 原文 + mvn test 输出
④ 一句话说明：主键为什么选 UUID/自增？name 是否允许更新？
```

---

> **完成后**：把验证记录发我 → 我抽查代码（随机 2 行问"为什么"）→ 通过后开 **L2 分层重构**。

---

<a id="l1-9"></a>
## 附：AI 补全记录（2026-11-26）

> 你在 Controller/Service 层卡住后，AI 补全了以下内容（代码 + 注释），并实测跑通。

### 补全的 10 项

| # | 文件 | 补了什么 |
|---|---|---|
| 1 | `TagStatus` | 语义修正：`CREATE/UPDATE/DELETE`（动作）→ `ACTIVE/DISABLED`（状态） |
| 2 | `CreateTagRequestDto` | 清理：删 status/createTime/updateTime；补 @Size(2-32) + @Pattern |
| 3 | `UpdateTagRequestDto` | 清理 + 三字段可选；status 用 @Pattern(ACTIVE/DISABLED) |
| 4 | `TagController` | 重写：修 `@RequestBody` 导包（swagger→spring）、补 `@PathVariable`、删 void return、返回 VO、加注释 |
| 5 | `TagService` | 重写：接 DTO、去重复 setName、状态走 TagStatus、补重名/不存在检测、PO→VO 转换 |
| 6 | `TagMapper` | 补 `@Mapper`（否则 `@Service` 注入失败 → 启动崩） |
| 7 | `TagMapper.xml` | 修 insert 逗号（`description status` → `description, status`）、selectByName 精确匹配、列表加 `ORDER BY create_time DESC` |
| 8 | `TagNotFoundException` / `TagNameDuplicateException` | 新建（业务异常） |
| 9 | `TagExceptionHandler` | **新建（Tag 专用 Advice）**：`@RestControllerAdvice(assignableTypes=TagController)` + **`@Order(HIGHEST_PRECEDENCE)`**（不加会被全局 Exception 兜底抢走 → 500） |
| 10 | `ErrorCode` | 新增 `TAG_NOT_FOUND`(404) / `TAG_NAME_DUPLICATE`(409) |

### 你原来踩的坑（对照）

```text
① Controller：@RequestBody 从 swagger 导（应为 spring）；缺 @PathVariable；void 里 return
② Service：接 Entity 而非 DTO → 与 Controller 类型打架；setStatus 字符串/枚举混用
③ TagMapper 缺 @Mapper → 启动报 "required a bean of type TagMapper"
④ XML：insert 少一个逗号 → H2 语法错误；selectByName 用 Like → 重名检测失效
⑤ 异常处理：没有 Tag 专用 Advice + 全局 Exception 兜底 → 404/409 全变 500
⑥ 用 Set-Content 写 Java 文件 → BOM 头 → javac 报"非法字符 \ufeff"
```

### 实测结果（lite 模式，2026-11-26）

| 验收 | 结果 |
|---|---|
| A2 创建 | ✅ 201 + `{id,name,description,status:ACTIVE,createTime,updateTime}` |
| A3 重名 | ✅ 409 + `TAG_NAME_DUPLICATE` |
| A4 非法参数 | ✅ 400 + `VALIDATION_ERROR` + fields（name 3 条：非空/长度/正则） |
| A5 不存在 | ✅ 404 + `TAG_NOT_FOUND` |
| A6 列表 | ✅ 200 + 数组（create_time 倒序） |
| A7 更新/删除 | ✅ PUT 200（description/status/updateTime 变）；DELETE 204 |
| A8 测试 | ✅ `TagControllerTest` 3/3 通过（5.074s） |
| A1 表存在 | ✅（lite 启动由 schema-dev.sql 建表，CREATE TABLE IF NOT EXISTS） |
| A9 验证记录 | 本节即验证记录（含 curl 输出摘要） |

### 仍留给你的（L2 及以后）

```text
□ L2：加 @Transactional（当前单表无需，L2 讲事务边界）
□ L3：列表分页（当前全量）；任务-标签关联表
□ L4：重名检测换"唯一索引 + 捕获冲突"（当前先查后插有并发窗口）
```

---

<a id="l1-10"></a>
## 附录 A：L1 用到的注解总览（含类似注解对比）

> 每条：**注解 → 用在哪 → 作用 → 类似注解（对比）→ 本项目为什么选它**。

### A1. Web 层（接口定义）

| 注解 | 用在哪 | 作用 | 类似 / 对比 | 选择理由 |
|---|---|---|---|---|
| `@RestController` | TagController 类 | 声明 REST 控制器，方法返回体直接写 JSON | `@Controller`（返回视图名，需配 `@ResponseBody`） | 纯 JSON API 用 `@RestController` 最省事 |
| `@RequestMapping` | 类（`/api/tags`） | 类级路径前缀 + 可限定 method/consumes | `@GetMapping` 等派生注解（更语义化） | 类级统一前缀用它，方法级用派生注解 |
| `@PostMapping` / `@GetMapping` / `@PutMapping` / `@DeleteMapping` | 5 个方法 | 语义化 HTTP 方法 + 路径 | `@RequestMapping(method=RequestMethod.X)` | 派生注解更短、可读性更好 |
| `@PathVariable` | getById/update/delete 的 `UUID id` | 绑定 URL 路径里的 `{id}` | `@RequestParam`（取 `?id=`）、`@RequestBody`（取 JSON 体） | `{id}` 在路径里 → 必须 `@PathVariable` |
| `@RequestBody` | create/update 的 DTO 参数 | 把请求体 JSON 反序列化成对象 | `@RequestParam`（表单/查询参数）、`@ModelAttribute`（表单绑定） | REST 传 JSON → 用 `@RequestBody` |
| `@ResponseStatus` | create（201）/ delete（204） | 指定成功响应的 HTTP 状态码 | `ResponseEntity<T>`（更灵活：可带 header/多状态） | 成功码固定时 `@ResponseStatus` 更简洁；需要动态状态才用 `ResponseEntity` |

### A2. 参数校验（Jakarta Validation）

| 注解 | 用在哪 | 作用 | 类似 / 对比 | 选择理由 |
|---|---|---|---|---|
| `@Valid` | Controller 方法参数 | 触发 Bean Validation 校验 DTO | `@Validated`（Spring 的，支持分组校验）、手动 `if` 校验 | 简单场景 `@Valid` 够用；需要分组/方法级校验用 `@Validated` |
| `@NotBlank` | name | 非 null 且去空格后非空 | `@NotNull`（只判 null，空串能过）、`@NotEmpty`（判 null+空集合/空串，空格能过） | 字符串必填 → `@NotBlank` 最严格 |
| `@Size(min,max)` | name / description | 长度范围 | `@Length`（Hibernate 特有）、`@Min/@Max`（数值） | 标准 Bean Validation，跨实现通用 |
| `@Pattern(regexp=...)` | name / status | 正则匹配 | 自定义 `@Constraint`（复杂规则） | 单条正则用 `@Pattern` 即可 |

### A3. 分层与持久化（MyBatis）

| 注解 | 用在哪 | 作用 | 类似 / 对比 | 选择理由 |
|---|---|---|---|---|
| `@Service` | TagService 类 | 标记服务层组件（`@Component` 的语义化特化） | `@Component`（通用）、`@Repository`（DAO 层，带异常翻译） | 业务编排层用 `@Service`，语义清晰 |
| `@Mapper` | TagMapper 接口 | 告诉 MyBatis 这是 Mapper 接口（启动时生成代理） | `@MapperScan`（包级扫描，启动类上）、`@Repository`（不生成 MyBatis 代理） | 单个接口 `@Mapper` 最直接；Mapper 多了才用 `@MapperScan` |
| `@Param` | TagMapper 方法参数 | 给 SQL 里的 `#{名字}` 起名 | 单参数（MyBatis 会自动用参数名，可省略）；`@MapKey`（返回 Map 时用） | 多参数/PO 参数必须 `@Param` 起名，否则 XML 取不到 |

### A4. Lombok（实体 / DTO）

| 注解 | 用在哪 | 作用 | 类似 / 对比 | 选择理由 |
|---|---|---|---|---|
| `@Getter` / `@Setter` | TagEntity | 生成 getter/setter | `@Data`（含 toString/equals/hashCode/构造器） | **实体不要 toString/equals/hashCode**（日志爆炸/hashCode 不稳定）——见「二点五 PO 注解规范」 |
| `@NoArgsConstructor` | TagEntity | 生成无参构造 | `@AllArgsConstructor`（全参）、`@RequiredArgsConstructor`（final 字段） | MyBatis/JPA 反射实例化需要**无参构造** |
| `@Data` | CreateTagRequestDto / UpdateTagRequestDto | Getter+Setter+ToString+EqualsAndHashCode+RequiredArgsConstructor | `@Value`（不可变 + 全 final）、`@Builder`（链式构建） | **DTO 无持久化语义**，@Data 风险小、写起来快 |
| `@Builder`（未用，备选） | — | 链式构造（适合参数多的对象） | 构造器 / setter | L1 字段少，暂不需要 |
| `@Slf4j` | TagExceptionHandler | 生成 `log` 变量（SLF4J Logger） | `@Log4j2`（Log4j2）、手动 `LoggerFactory.getLogger(...)` | Spring Boot 默认 SLF4J → `@Slf4j` 最合适 |

### A5. 异常处理

| 注解 | 用在哪 | 作用 | 类似 / 对比 | 选择理由 |
|---|---|---|---|---|
| `@ExceptionHandler` | TagExceptionHandler 方法 | 声明"处理哪类异常" | `HandlerExceptionResolver`（更底层，可实现接口）、`ResponseStatusException`（抛异常自带状态码） | 声明式、集中管理，最常用 |
| `@RestControllerAdvice` | TagExceptionHandler 类 | 全局（或指定 Controller）异常处理 + 返回 JSON | `@ControllerAdvice`（返回视图时用）、`@RestControllerAdvice(assignableTypes=...)`（限定范围） | 用 `assignableTypes` 精确限定到 TagController |
| `@Order(Ordered.HIGHEST_PRECEDENCE)` | TagExceptionHandler 类 | 指定 Advice 优先级（越小越先） | `Ordered` 接口（实现 getOrder）、`@Priority`（Java 标准注解） | **必须最高优先级**，否则被全局 `Exception.class` 兜底抢走（→500）——本项目踩过 |

### A6. 接口文档（SpringDoc / OpenAPI 3）

| 注解（新） | 旧版（Swagger 2） | 用在哪 | 作用 |
|---|---|---|---|
| `@Tag(name, description)` | `@Api` | TagController 类 | 接口分组（Swagger UI 的折叠面板名） |
| `@Operation(summary, description)` | `@ApiOperation` | 5 个方法 | 单个接口的说明 |
| `@Parameter(description, required)` | `@ApiParam` | 3 个路径参数 | 单个参数的说明 |
| `@Schema(name, description, example)` | `@ApiModel` / `@ApiModelProperty` | TagVo / DTO | 模型（类）与字段说明 + 示例值 |

```text
★ SpringDoc（OpenAPI 3）用 io.swagger.v3.oas.annotations.*
  旧版 Swagger 2 用 io.swagger.annotations.*（项目已升级，别混用）
```

### A7. 一个"没加但故意不加"的注解

| 注解 | 为什么不用 |
|---|---|
| `@Table(name="l1_tag")`（JPA） | 本项目持久化层用 **MyBatis**，加 JPA 注解会误导读者、也可能被 JPA 自动配置扫描；表名映射由 **XML 里的 SQL** 决定（`INSERT INTO l1_tag`） |

---

<a id="l1-11"></a>
## 附录 B：涉及文件索引（可点击直达源码）

> 本地链接（IDE 打开）+ 网页版（GitHub 高亮）；路径相对本文件（`docs/实战需求/` → `backend/`）。

| # | 文件 | 作用 | 源码 |
|---|---|---|---|
| 1 | `TagController.java` | REST 接口（5 个 + Swagger 注解） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/api/controller/l1Tag/TagController.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/controller/l1Tag/TagController.java) |
| 2 | `TagService.java` | 应用服务（业务规则 + PO→VO） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/l1Tag/TagService.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/l1Tag/TagService.java) |
| 3 | `TagStatus.java` | 状态枚举（ACTIVE/DISABLED） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/application/service/l1Tag/TagStatus.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/application/service/l1Tag/TagStatus.java) |
| 4 | `TagEntity.java` | 持久化实体（PO） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/entity/l1Tag/TagEntity.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/entity/l1Tag/TagEntity.java) |
| 5 | `TagMapper.java` | MyBatis Mapper 接口 | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/store/mybatis/l1Tag/TagMapper.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/persistence/store/mybatis/l1Tag/TagMapper.java) |
| 6 | `TagMapper.xml` | MyBatis SQL 映射 | [本地](../../backend/src/main/resources/mapper/l1Tag/TagMapper.xml) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/mapper/l1Tag/TagMapper.xml) |
| 7 | `CreateTagRequestDto.java` | 创建请求 DTO | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/api/request/l1Tag/CreateTagRequestDto.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/request/l1Tag/CreateTagRequestDto.java) |
| 8 | `UpdateTagRequestDto.java` | 更新请求 DTO | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/api/request/l1Tag/UpdateTagRequestDto.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/request/l1Tag/UpdateTagRequestDto.java) |
| 9 | `TagVo.java` | 响应 VO（对外契约） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/api/vo/l1Tag/TagVo.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/vo/l1Tag/TagVo.java) |
| 10 | `TagNotFoundException.java` | 业务异常（404） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/shared/exception/TagNotFoundException.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/exception/TagNotFoundException.java) |
| 11 | `TagNameDuplicateException.java` | 业务异常（409） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/shared/exception/TagNameDuplicateException.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/exception/TagNameDuplicateException.java) |
| 12 | `TagExceptionHandler.java` | Tag 专用异常处理（Advice） | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/api/controller/l1Tag/TagExceptionHandler.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/api/controller/l1Tag/TagExceptionHandler.java) |
| 13 | `ErrorCode.java`（新增 2 个枚举） | 统一错误码表 | [本地](../../backend/src/main/java/com/aiexplorer/researchagent/shared/enums/ErrorCode.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/shared/enums/ErrorCode.java) |
| 14 | `schema-dev.sql`（L1 段） | 建表 DDL（幂等） | [本地](../../backend/src/main/resources/schema-dev.sql) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/schema-dev.sql) |
| 15 | `TagControllerTest.java` | 切片测试（3 用例） | [本地](../../backend/src/test/java/com/aiexplorer/researchagent/api/controller/l1Tag/TagControllerTest.java) · [网页版](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/test/java/com/aiexplorer/researchagent/api/controller/l1Tag/TagControllerTest.java) |

### 调用链（按文件顺序读代码）

```text
HTTP 请求
  → TagController.java            （@RestController，参数绑定 + @Valid）
  → TagService.java               （业务规则：重名 409 / 不存在 404 / PO→VO）
  → TagMapper.java                （MyBatis 接口）
  → TagMapper.xml                 （SQL：l1_tag 表）
  → schema-dev.sql                （表结构：l1_tag）
异常路径
  → TagNotFoundException / TagNameDuplicateException
  → TagExceptionHandler.java      （@Order 最高优先级的 Advice）
  → ErrorCode.java                （TAG_NOT_FOUND 404 / TAG_NAME_DUPLICATE 409）
```

