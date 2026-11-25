# SB2 · 自定义 Starter（五要素 + 两个真实的坑）

> **一课目标**：从零写一个 Spring Boot Starter，理解"自动配置 + 属性绑定 + 条件装配"怎么封装成可复用组件。
> **前置**：[SB1·自动配置原理深入](SB1·自动配置原理深入.md)
> **本课产出**：项目内的 `demo-greeting` starter（4 个文件 + imports 注册），实测通过 + **踩到并修复两个真实坑**。

---

## 目录

- [一、Starter 是什么（和普通依赖 jar 的区别）](#sb2-1)
- [二、两层结构（官方推荐，为什么分两层）](#sb2-2)
- [三、五要素（写一个 Starter 就这五步）](#sb2-3)
- [四、本次实战：demo-greeting starter（完整代码）](#sb2-4)
- [五、实测结果（2026-11-23 lite 模式）](#sb2-5)
- [六、★ 两个真实的坑（本轮踩到，讲义的价值所在）](#sb2-6)
- [七、生产化：拆成真正的多模块 Starter](#sb2-7)
- [八、自测题（答案）](#sb2-8)
- [九、一句话](#sb2-9)

---

<a id="sb2-1"></a>
## 一、Starter 是什么（和普通依赖 jar 的区别）

```text
普通依赖 jar：给你类，你自己 new / 自己 @Bean / 自己配
Starter：    给你类 + 【自动装配好】——引入依赖就能用，配置可选

Spring Boot 官方 Starter 的承诺：
  ① 你不用写 @Bean（自动配置给你默认实现）
  ② 你不用记全配置项（有默认值 + IDE 提示）
  ③ 你能随时覆盖（@ConditionalOnMissingBean 让你接管）
```

<a id="sb2-2"></a>
## 二、两层结构（官方推荐，为什么分两层）

```text
xxx-spring-boot-autoconfigure    ← 自动配置逻辑（依赖实现库）
xxx-spring-boot-starter          ← 空壳 pom（只做依赖聚合 + 指向 autoconfigure）

为什么分两层：
  ① 别人可能只想用 autoconfigure（自己控制实现库版本）
  ② starter 的 pom 只聚合依赖，不含代码 → 心智简单
  ③ 官方所有 starter（spring-boot-starter-web 等）都是这个结构

★ 本项目实验是【单模块内模拟】（教学用）：把 autoconfigure 的类放进 backend，
  再注册到 imports——机制完全一样，只是没拆 maven 模块（第七节讲真实拆分）。
```

<a id="sb2-3"></a>
## 三、五要素（写一个 Starter 就这五步）

```text
① 属性类      @ConfigurationProperties(prefix="xxx")
② 自动配置类  @AutoConfiguration + @ConditionalOn* + @EnableConfigurationProperties
③ 注册        META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
④ 默认 Bean   @Bean + @ConditionalOnMissingBean（用户覆盖即让位）
⑤ 元数据      （可选）spring-configuration-metadata.json —— IDE 配置提示
```

<a id="sb2-4"></a>
## 四、本次实战：demo-greeting starter（完整代码）

### 4.1 属性类（`DemoGreetingProperties`）

> 📄 **查看源码**：[`DemoGreetingProperties.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingProperties.java) · [网页版 L11-22](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingProperties.java#L11-L22)

```java
package com.aiexplorer.researchagent.infrastructure.experiment.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo-greeting")
public record DemoGreetingProperties(String prefix, String suffix) {

    public DemoGreetingProperties {
        if (prefix == null) {
            prefix = "Hello, ";
        }
        if (suffix == null) {
            suffix = "!";
        }
    }
}
```

```text
要点：
① record + @ConfigurationProperties：Boot 3 支持构造器绑定（属性从 yml 注入）
② 默认值在紧凑构造器兜底（yml 不配也能用）→ 这就是"开箱即用"的一半
```

### 4.2 服务类（`DemoGreetingService`）

> 📄 **查看源码**：[`DemoGreetingService.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingService.java) · [网页版 L6-17](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingService.java#L6-L17)

```java
public class DemoGreetingService {
    private final DemoGreetingProperties properties;

    public DemoGreetingService(DemoGreetingProperties properties) {
        this.properties = properties;
    }

    public String greet(String name) {
        return properties.prefix() + name + properties.suffix();
    }
}
```

```text
注意：没有 @Component / @Service——它是 Starter 提供的类，
由自动配置类装配（否则引入 starter 的人随便扫描就生效，失去"条件控制"）
```

### 4.3 自动配置类（`DemoGreetingAutoConfiguration`）

> 📄 **查看源码**：[`DemoGreetingAutoConfiguration.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingAutoConfiguration.java) · [网页版 L23-33](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingAutoConfiguration.java#L23-L33)

```java
@AutoConfiguration
@ConditionalOnProperty(prefix = "app.demo-greeting", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(DemoGreetingProperties.class)
public class DemoGreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DemoGreetingService demoGreetingService(DemoGreetingProperties properties) {
        return new DemoGreetingService(properties);
    }
}
```

```text
五个注解各自的职责：
  @AutoConfiguration            走 imports 清单（不是组件扫描）+ 可声明 before/after
  @ConditionalOnProperty        开关：app.demo-greeting.enabled（matchIfMissing=true 默认开）
  @EnableConfigurationProperties 绑定属性类（关键！否则 @ConfigurationProperties 不生效）
  @Bean + @ConditionalOnMissingBean 默认实现 + 用户覆盖即让位
```

### 4.4 注册清单（`resources/META-INF/spring/...AutoConfiguration.imports`）

> 📄 **查看源码**：[`AutoConfiguration.imports`](../../backend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports) · [网页版 L1](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports#L1)

```text
com.aiexplorer.researchagent.infrastructure.experiment.starter.DemoGreetingAutoConfiguration
```

```text
★ 路径必须一字不差：
  META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
放错目录 → 完全不生效、不报错（最隐蔽的坑）
```

### 4.5 使用方（Controller，也是本次的坑所在）

> 📄 **查看源码**：[`DemoGreetingController.java`](../../backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingController.java) · [网页版 L19-35](https://github.com/dreamChaser-lcc/ai-agent-explorer/blob/main/backend/src/main/java/com/aiexplorer/researchagent/infrastructure/experiment/starter/DemoGreetingController.java#L19-L35)

```java
@RestController
@RequestMapping("/api/demo")
public class DemoGreetingController {

    private final ObjectProvider<DemoGreetingService> greetingService;

    public DemoGreetingController(ObjectProvider<DemoGreetingService> greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/greeting")
    public String greeting(@RequestParam(defaultValue = "world") String name) {
        return greetingService.getIfAvailable() != null
                ? greetingService.getObject().greet(name)
                : "[starter 未启用] app.demo-greeting.enabled=false";
    }
}
```

<a id="sb2-5"></a>
## 五、实测结果（2026-11-23 lite 模式）

```text
① --debug 的 report：
   DemoGreetingAutoConfiguration matched:
      - @ConditionalOnProperty (app.demo-greeting.enabled=true) matched (OnPropertyCondition)
   DemoGreetingAutoConfiguration#demoGreetingService matched:
      - @ConditionalOnMissingBean (types: ...DemoGreetingService) ...

② 接口：
   GET /api/demo/greeting?name=Kafka  →  Hello, Kafka!
   GET /api/demo/greeting             →  Hello, world!

③ 降级（预期行为，未在本轮实跑）：app.demo-greeting.enabled=false
   → 自动配置跳过 → Service 不存在 → ObjectProvider 兜底 → 接口返回 "[starter 未启用] ..."
```

<a id="sb2-6"></a>
## 六、★ 两个真实的坑（本轮踩到，讲义的价值所在）

### 坑 1：`@ConditionalOnBean` 不能用在组件扫描的类上

```text
现象：
  DemoGreetingController 上写了 @ConditionalOnBean(DemoGreetingService.class)
  → 接口 404（Controller 没注册）

原因：
  条件注解的评估发生在【配置类解析阶段】，
  而 @RestController/@Component 的注册发生在【组件扫描阶段】（更晚）。
  → 判断条件时"Service 还没注册"→ 条件不成立 → Controller 不装配
  （Spring 官方文档明确：@ConditionalOnBean/@ConditionalOnMissingBean 只用于自动配置类）

正确写法：
  条件放自动配置类上；组件扫描的类用 ObjectProvider 做"可选依赖"兜底
```

### 坑 2：`@Bean` 注册的 Controller 不被 RequestMappingHandlerMapping 识别

```text
现象：
  把 Controller 移进 @AutoConfiguration 用 @Bean 注册（类上只 @RequestMapping + @ResponseBody）
  → Bean 创建了（report matched），但接口仍 404
  → 即使加 @AutoConfiguration(before = WebMvcAutoConfiguration.class) 仍 404

原因：
  Spring 6 的 RequestMappingHandlerMapping 识别 handler 时依赖 @Controller（或类级 @RequestMapping），
  @Bean 注册的普通类在这个链路里不可靠（Boot 3 / Spring 6 实务结论）
  → 结论：Starter 的 Web 层用【标准 @RestController + 组件扫描】最稳

正确写法（本课最终版）：
  Controller 加 @RestController（走组件扫描）
  对可选的服务用 ObjectProvider<XxxService>（starter 关闭时不炸，接口给提示）
```

```text
★ 两个坑的共同点：都在"组件扫描 vs 自动配置"的边界上
  → 记住一句话：条件注解只信自动配置类；Web 组件就用标准注解走扫描。
```

<a id="sb2-7"></a>
## 七、生产化：拆成真正的多模块 Starter

```text
mvn 多模块（官方结构）：
  demo-spring-boot-autoconfigure/
    src/main/java/.../DemoGreetingAutoConfiguration.java
    src/main/java/.../DemoGreetingProperties.java
    src/main/resources/META-INF/spring/...AutoConfiguration.imports
    pom.xml  （依赖：spring-boot-autoconfigure + 实现库）
  demo-spring-boot-starter/
    pom.xml  （依赖：demo-spring-boot-autoconfigure）
    src/main/resources/META-INF/spring/...（无，空壳）

额外的工程化加分项：
  ① spring-boot-configuration-processor（编译期生成元数据 → application.yml 里有 IDE 提示）
     <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-configuration-processor</artifactId>
       <optional>true</optional>
     </dependency>
  ② 自动配置类加 @AutoConfigureBefore/After 声明顺序（如依赖 DataSource 就 after DataSourceAutoConfiguration）
  ③ 不要在 autoconfigure 模块里依赖 spring-boot-starter（避免循环 + 减少传递依赖）
  ④ 单测：ApplicationContextRunner（不启动整个应用就能测自动配置）——SB6 会讲
```

<a id="sb2-8"></a>
## 八、自测题（答案）

```text
Q1 Starter 为什么要分 autoconfigure 和 starter 两层？
Q2 imports 文件里写什么？放错位置会怎样？
Q3 属性类为什么必须配 @EnableConfigurationProperties（或 @ConfigurationPropertiesScan）？
Q4 @ConditionalOnMissingBean 在 Starter 里的作用是什么？
Q5 为什么 Starter 提供的类不写 @Component？
Q6 @ConditionalOnBean 为什么不能用在 @RestController 上？
```

### 答案

### Q1：Starter 为什么要分 autoconfigure 和 starter 两层？

**答案（完整）**：

```text
解耦两类职责：
  autoconfigure 模块  = 自动配置【逻辑与代码】（AutoConfiguration + Properties + imports）
                       依赖"实现库"（如 spring-data-redis），不依赖 starter
  starter 模块        = 空壳 pom，只做【依赖聚合】（依赖 autoconfigure 模块 + 传递实现库）

两层的目的：
  ① 使用者可以【只引 autoconfigure】——自己控制实现库的版本（避免被 starter 的版本绑死）
  ② starter 的 pom 保持极简（一眼看清"引了什么"），代码不混在 starter 里
  ③ 官方所有 starter（spring-boot-starter-web / data-redis / kafka）都是这个结构：
     spring-boot-autoconfigure（一个总 jar）+ 各 starter（空壳）
```

**机制展开（依赖方向）**：

```text
正确方向：
  xxx-spring-boot-autoconfigure  ──依赖──→  实现库（redis/kafka/jdbc...）
  xxx-spring-boot-starter        ──依赖──→  xxx-autoconfigure
  （starter 不写代码、不依赖 spring-boot-starter，保持最小）

反面（常见错误）：
  autoconfigure 模块里依赖 spring-boot-starter-web
  → 使用方引入你的 autoconfigure 就被迫带上整个 web 栈（依赖膨胀）
```

**本项目验证**：

```text
本次实验是【单模块内模拟】（教学走捷径）：把 autoconfigure 内容放进 backend 的
infrastructure/experiment/starter 包，imports 指向它——机制与真实 starter 完全一致。
真实拆分见第七节（mvn 多模块）。
```

**易错点**：

```text
① autoconfigure 模块不要依赖 spring-boot-starter（避免传递依赖膨胀/循环）
② starter 空壳 pom 里不要写代码（否则又回到"单模块"的问题）
③ 版本管理：autoconfigure 与 starter 必须同版本发布（starter 的 pom 里引用 autoconfigure 的同一版本）
```

### Q2：imports 文件里写什么？放错位置会怎样？

**答案（完整）**：

```text
文件：META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
内容：每行一个【自动配置类的全限定名】（FQCN）
     com.example.MyAutoConfiguration
     com.example.other.OtherAutoConfiguration

放错位置的后果：静默失效 —— 启动完全正常，但自动配置不装配（最难查的一类问题）
```

**机制展开（三个精度层级，后果不同）**：

```text
① 路径错（如 META-INF/spring/xxx.imports 少了类名层级、或写进 spring.factories）
   → Boot 3 的 ImportCandidates.load 读不到 → 不装配 → 【静默】（启动正常、功能缺失）

② 路径对但类名拼错（类不存在）
   → ImportCandidates.load 尝试加载类 → ClassNotFoundException
   → 启动【报错】（反而是"好事"：能立刻发现）

③ 路径对、类存在，但类上没有 @AutoConfiguration/@Configuration
   → 会被当配置类解析（可能报"类声明了 @Bean 但不是配置类"）
```

**为什么 Boot 3 只认 imports（而不是 spring.factories）**：

```text
spring.factories 是"多用途大杂烩"（EnableAutoConfiguration/Listener/EnvironmentPostProcessor），
imports 是专用清单：① 可读性（一行一类）② 可分组（Boot 3 支持按条件分组）
③ 便于构建期优化（AOT/Native 要解析清单）
```

**本项目验证**：

```text
本次 imports 写 1 行 → --debug report 出现：
  DemoGreetingAutoConfiguration matched:
     - @ConditionalOnProperty (app.demo-greeting.enabled=true) matched
（若改成错误路径 → report 里不会再出现该类，且启动无报错）
```

**易错点**：

```text
① 路径逐字拼：META-INF / spring / org.springframework.boot.autoconfigure.AutoConfiguration.imports
   （中间没有额外的目录，不能写成 META-INF/spring/autoconfigure.imports）
② 一行只能一个类名（逗号分隔不行——那是 spring.factories 的格式）
③ 用 IDE 模板最容易漏：新建文件时把路径当成"包名"来创建（导致多出目录）
```

### Q2 补充：多个 starter 时 imports 怎么组织（2026-11-24 学员问）

**两种情况的区别**：

```text
情况 1：一个 starter 内有多个自动配置类 → 同一个 imports 文件【多行】
  META-INF/spring/...AutoConfiguration.imports
    com.example.MyCoreAutoConfiguration
    com.example.MyWebAutoConfiguration
    com.example.MyMetricsAutoConfiguration
  （参照：spring-boot-autoconfigure 这一个 jar 的 imports 有 ~150 行）

情况 2：多个 starter → 各自 jar 里各有一份 imports，不写在一起
  spring-boot-autoconfigure-3.5.13.jar   → ~150 行（框架自带）
  my-demo-starter-1.0.jar                → 1 行
  backend 自己的 classes                  → 1 行（本项目 demo-greeting）
```

**合并机制**：

```text
① 启动时 ImportCandidates.load 用 ClassLoader.getResources() 找 classpath 上【所有】同名文件
   → 合并成一个清单（是合并，不是覆盖）
② 重复声明 → removeDuplicates 去重
③ 合并后按 @AutoConfigureOrder / Before / After 排序；
   无声明 → 按 classpath 顺序（不同机器可能不同）
④ 你在 backend 里写的 imports 与依赖 jar 里的会一起生效
```

**实践建议**：

```text
✅ 一个 autoconfigure 模块多个自动配置类 → 同一文件多行
✅ 多个 starter → 各自管各自（不要在一个文件里替别人写）
✅ 跨 starter 的依赖顺序 → @AutoConfiguration(after = XxxAutoConfiguration.class)
❌ 不要用"imports 文件行序"控制顺序（会被 @AutoConfigureOrder 覆盖）
```
### Q3：属性类为什么必须配 @EnableConfigurationProperties（或 @ConfigurationPropertiesScan）？

**答案（完整）**：

```text
@ConfigurationProperties 只是"声明"（元数据：哪些前缀绑定到哪些字段），
它【不会】把类注册成 Bean。

三种启用方式：
  ① @EnableConfigurationProperties(XxxProperties.class)   ← 推荐（自动配置类上，精确声明）
  ② @ConfigurationPropertiesScan                           ← 扫描包（属性类多时用）
  ③ @Component + @ConfigurationProperties                  ← 不推荐（绕过条件装配）

不启用的后果：属性类不是 Bean → 注入 Properties 报 NoSuchBeanDefinitionException
```

**机制展开**：

```text
@EnableConfigurationProperties 做了两件事：
  ① 注册 ConfigurationPropertiesBindingPostProcessor（绑定器：把 Environment 里的值绑到属性类）
  ② 把 XxxProperties 注册为单例 Bean（@ConfigurationProperties 类需要被 Spring 管理才能注入）

spring-boot-configuration-processor（编译期依赖）另做一件事：
  生成 META-INF/spring-configuration-metadata.json → IDE 在 application.yml 里提示配置项
  （与运行时绑定无关，纯开发体验）
```

**本项目验证**：

```text
DemoGreetingAutoConfiguration 上：
  @EnableConfigurationProperties(DemoGreetingProperties.class)
→ /api/demo/greeting 能读到默认值 "Hello, " / "!"
→ 若删掉这行：注入 DemoGreetingProperties 会失败（启动报 NoSuchBeanDefinitionException）
```

**易错点**：

```text
① 只写 @ConfigurationProperties 不启用 → 注入失败（报错清晰，好查）
② @EnableConfigurationProperties 与 @ConfigurationPropertiesScan 混用 → 重复注册（一般无害）
③ 属性类加 @Component → 引入方扫描到就生效，绕过 Starter 的开关（同 Q5 的问题）
```

### Q4：@ConditionalOnMissingBean 在 Starter 里的作用是什么？

**答案（完整）**：

```text
Starter 的核心扩展点（记住这句）：
  "默认给你一个能用的；你定义了自己的，我就让位。"

两个作用：
  ① 开箱即用：引入 starter 不写任何配置就能跑（默认 Bean 装配）
  ② 可覆盖：使用方定义同类型 Bean → 默认实现不装配（无需改 Starter 代码）
```

**机制展开（顺序与类型匹配）**：

```text
① 顺序：使用方配置类（@ComponentScan）先解析 → Starter 自动配置后解析
   → 条件评估时"已经能看到你的 Bean"→ 条件不成立 → 默认 Bean 不装（SB1 实验 1 已实测）
② 类型匹配：按 @Bean 方法的返回类型（含泛型推断）
   → 你定义的是子类/实现类，也能被识别（但泛型擦除的边角要注意）
③ 判断对象：BeanDefinition（不是实例）——所以"实例化顺序"不影响它
```

**本项目验证**：

```text
--debug report：
  DemoGreetingAutoConfiguration#demoGreetingService matched:
     - @ConditionalOnMissingBean (types: ...DemoGreetingService; SearchStrategy: all) ...
→ 条件成立 → 默认 Service 装配

如果我们在项目里再定义一个 @Bean DemoGreetingService：
→ 该行会变成 Negative（默认不装配），使用方覆盖成功
```

**易错点**：

```text
① 多个默认 Bean 同类型且都加 @ConditionalOnMissingBean(CacheManager) → 使用方与默认冲突（SB1 实验 2）
② 条件写在类上（过宽）vs 方法上（精确）——Starter 一般写在 @Bean 方法上
③ 依赖"后置注册"的 Bean 覆盖（BeanDefinitionRegistryPostProcessor）→ 误判（SB1 八点五 讲）
```

### Q5：为什么 Starter 提供的类不写 @Component？

**答案（完整）**：

```text
因为 @Component 会【绕过】Starter 的条件装配：
  引入方只要扫描到该类 → 就装配（不管 enabled 开关、不管依赖条件）
→ Starter 失去"开关/降级/依赖判断"的控制权 —— 而这是 Starter 的全部价值
```

**机制展开（两种装配路径的对照）**：

```text
路径 A（错误）：Starter 的 Service 写 @Component
  引入方 @ComponentScan → 扫到 → 装配
  → app.demo-greeting.enabled=false 也不生效（条件在自动配置类上，管不到这个 @Component）

路径 B（正确，本课做法）：Service 是"裸类"（无 Spring 注解）
  只由 @AutoConfiguration 的 @Bean 装配
  → 条件全部生效（enabled=false → 不装配 → ObjectProvider 兜底）

★ 结论：Starter 里的"能力类"必须保持裸类；只有 Web 组件（Controller）例外（见 Q6）
```

**本项目验证**：

```text
DemoGreetingService：无 @Component/@Service
→ 只在 DemoGreetingAutoConfiguration 装配时存在
→ enabled=false 时不存在（接口用 ObjectProvider 优雅降级，不启动失败）
```

**易错点**：

```text
① 属性类也不要 @Component（用 @EnableConfigurationProperties）
② Controller 是例外：Web 组件需要 @RestController 走组件扫描（Q6 的坑）
③ 切面/拦截器类：同理不写 @Component，由自动配置 @Bean 注册（但注意 AOP 需要代理，@Bean 也能生效）
```

### Q6：@ConditionalOnBean 为什么不能用在 @RestController 上？

**答案（完整）**：

```text
因为时机跨阶段：
  组件扫描（@RestController 注册）发生在【扫描阶段】
  Starter 的 Service 由【自动配置类】注册，发生在【扫描之后】
  → 扫描 Controller 时评估条件 → "Service 还没有" → 条件不成立 → Controller 被跳过 → 404

正确做法：
  ① 条件放在自动配置类上（@ConditionalOnBean(DemoGreetingService.class)）
  ② 组件类用 ObjectProvider<XxxService> 做可选依赖（本课最终版）
```

**机制展开（启动顺序，这是坑的根）**：

```text
Spring 启动的关键顺序：
  ① ConfigurationClassParser 解析主配置类
       └── @ComponentScan → 立即执行扫描 → 注册 Controller/Service 的 BeanDefinition
  ② 处理 @Import / imports 清单 → 解析【自动配置类】
       └── 此时才注册 Starter 的 Bean（DemoGreetingService）

→ 第 ① 步扫描 Controller 时，条件评估看 Service → 不存在 → 跳过
→ 等到第 ② 步 Service 才被注册（已经晚了）

★ 一句话：条件注解判断的是"此刻"，不是"最终"——跨阶段就会误判。
```

**本项目验证（实测证据）**：

```text
第一版：DemoGreetingController 加 @ConditionalOnBean(DemoGreetingService.class)
  → 启动正常，但 GET /api/demo/greeting → 404（Controller 没注册）
改版：条件去掉，Controller 注入 ObjectProvider<DemoGreetingService>
  → GET /api/demo/greeting?name=Kafka → "Hello, Kafka!" ✅
  → enabled=false 时返回 "[starter 未启用]..."（优雅降级）
```

**易错点**：

```text
① 以为"条件注解写在任何类上都可靠"——它对配置类可靠，对扫描类要看时机
② 用 @DependsOn / @Order 试图调整顺序 → 无效（这是解析阶段顺序，不是实例化顺序）
③ 正解只有两条：条件挪到自动配置类 / 改用 ObjectProvider（不要硬调顺序）
```

<a id="sb2-9"></a>
## 九、一句话

```text
Starter = 属性类 + 自动配置类 + imports 注册 + @Bean/@ConditionalOnMissingBean。
机制不复杂，难点在两个边界：
  ① 条件注解只信自动配置类（组件扫描的类别用）
  ② Web 组件用标准 @RestController 走扫描（@Bean 注册 Controller 在 Boot 3 不可靠）
```




