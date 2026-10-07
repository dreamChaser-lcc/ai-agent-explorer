// ══════════════════════════════════════════════════════════════
//  Java 基础实战 · 裸跑实验场（javac 直接编译，无需 Spring）
//
//  实验清单（编译一次后，按类名运行）：
//    java ExampleJavaGrammar   ← J5：Stream 四幕（惰性 / 分组 / 拼串）
//    java EnumLab              ← J6：枚举（穷尽检查）
//    java AnnotationLab        ← J7：自定义注解三件套（声明 / 使用 / 反射读取）
//    java ThreadPoolLab        ← J8：线程池（排队 / 复用 / 两种提交方式）
//    java BasicsLab            ← J1–J4 精华（record / 多态 / 泛型擦除 / 异常）
//    java JvmLab               ← J9：JVM 体检报告 / -Xmx 对比 / OOM 现场
// ══════════════════════════════════════════════════════════════
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ExampleJavaGrammar {
    // 造点"假任务"（模拟你库里数据的形状）
    record Task(String title, String status) { }

    public static void main(String[] args) {
        List<Task> tasks = List.of(
                new Task("测试任务-A", "WAITING_FOR_CONFIRMATION"),
                new Task("测试任务-B", "WAITING_FOR_CONFIRMATION"),
                new Task("测试任务-C", "WAITING_FOR_CONFIRMATION"),
                new Task("正式任务-D", "WAITING_FOR_CONFIRMATION"),
                new Task("旧任务-E", "COMPLETED"),
                new Task("旧任务-F", "COMPLETED"));

        // ── 第一幕：peek 见证"惰性" ──
        System.out.println("---- 拼装中（应无任何 [peek] 输出）----");
        var pipeline = tasks.stream()
                .peek(t -> System.out.println("  [peek] 路过: " + t.title()))
                .filter(t -> t.title().startsWith("测试"))
                .map(Task::title);
        System.out.println("---- 拼装完成（还是一行没打！）---- 现在按开关：");
        List<String> names = pipeline.toList();
        System.out.println("结果: " + names);

        // ── 第二幕：groupingBy 分组 ──
        Map<String, List<Task>> byStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::status));
        System.out.println("\n按状态分组（组名 → 条数）：");
        byStatus.forEach((k, v) -> System.out.println("  " + k + " → " + v.size() + " 条"));

        // ── 第三幕：joining 拼串 ──
        String titles = tasks.stream().map(Task::title)
                .collect(Collectors.joining("、", "【", "】"));
        System.out.println("\njoining 拼串: " + titles);

        // ── 第四幕：partitioningBy + counting（按"是否测试"分成两组并计数）──
        Map<Boolean, Long> counted = tasks.stream()
                .collect(Collectors.partitioningBy(t -> t.title().startsWith("测试"),
                        Collectors.counting()));
        System.out.println("\n两组计数: 测试组=" + counted.get(true)
                + " 条，其他=" + counted.get(false) + " 条");
    }
}

class EnumLab {

    enum Priority {
        LOW(1, "低"), MEDIUM(2, "中"), HIGH(3, "高");
        private final int level;
        private final String label;
        Priority(int level, String label) { this.level = level; this.label = label; }
        boolean isUrgent() { return level >= 3; }
    }

    static String describe(Priority p) {
        return switch (p) {                      // 箭头 switch（Java 14+）
            case LOW -> "不急";
            case MEDIUM -> "还行";
            case HIGH -> "马上办";                // ★ 第二幕：先把这一行注释掉！见下
        };
    }

    public static void main(String[] args) {
        // 第一幕：带字段/方法的枚举
        for (Priority p : Priority.values()) {
            System.out.println(p + " | level=" + p.level + " | 中文=" + p.label + " | 紧急=" + p.isUrgent());
        }
        Priority p = Priority.valueOf("HIGH");       // 字符串 → 枚举
        System.out.println("解析: " + p + " | 序号: " + p.ordinal());
        System.out.println(describe(Priority.HIGH));
    }
}

// ============================================================
// J7 下半场：自定义注解三件套（声明 → 使用 → 反射读取）
// 跑法：javac exampleJavaGrammar.java  然后  java AnnotationLab
// ============================================================
class AnnotationLab {

    // ── ① 声明注解（元注解：寿命 + 靶点）──
    @Retention(RetentionPolicy.RUNTIME)     // RUNTIME：活到运行时（反射能读——框架注解的标配）
    @Target(ElementType.METHOD)             // 只许贴方法（贴错地方，编译报错）
    @interface LogExecution {               // @interface = 注解的声明方式
        String value() default "";          // 注解也能"带参数"（像 @RequestMapping("/x")）
    }

    // ── ② 使用注解 ──
    static class TaskService {
        @LogExecution("创建任务")            // 贴标签（带参数）
        public void createTask() {
            System.out.println("   [业务] 任务已创建");
        }

        public void plainMethod() {          // 对照组：没有标签的方法
            System.out.println("   [业务] 普通方法");
        }
    }

    // ── ③ 反射读取（"框架视角"——Spring 就是这样发现 @Service 的）──
    public static void main(String[] args) throws Exception {
        TaskService service = new TaskService();

        for (String name : new String[]{"createTask", "plainMethod"}) {
            java.lang.reflect.Method m = TaskService.class.getMethod(name);

            if (m.isAnnotationPresent(LogExecution.class)) {          // ★ 核心一问
                LogExecution ann = m.getAnnotation(LogExecution.class);
                System.out.println("[框架] " + name + " 带头顶标签 → value=" + ann.value() + " —— 开始记录执行");
            } else {
                System.out.println("[框架] " + name + " 无标签 → 直接放过");
            }
            m.invoke(service);                                        // 反射调用方法
        }
    }
}

// ============================================================
// J8 下半场：线程池动手实验（固定班组 / 排队 / 复用 / 两种提交方式）
// 跑法：javac exampleJavaGrammar.java  然后  java ThreadPoolLab
// ============================================================
class ThreadPoolLab {

    // ── 业务方法（模拟"研究任务"）──

    static void researchJob(String taskName) {
        long start = System.currentTimeMillis();
        System.out.println("   [" + time() + "] " + taskName + " 开工 @ " + Thread.currentThread().getName());
        try {
            Thread.sleep(2000);                       // 模拟"要跑 2 秒的活"
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long cost = System.currentTimeMillis() - start;
        System.out.println("   [" + time() + "] " + taskName + " 完工 @ " + Thread.currentThread().getName() + "（耗时 " + cost + "ms）");
    }

    static void zeroArgJob() {                        // 零参方法——给"方法引用"用
        System.out.println("   [" + time() + "] 零参任务在 " + Thread.currentThread().getName() + " 上执行");
    }

    static String time() {
        return java.time.LocalTime.now().withNano(0).toString();
    }

    public static void main(String[] args) throws Exception {
        // ========== 第一幕：2 个工人 vs 5 个任务（看排队与复用） ==========
        System.out.println("========== 第一幕：2 个工人 vs 5 个任务 ==========");

        AtomicInteger workerNo = new AtomicInteger(1);
        // 自定义"工人工厂"：给每个工人起名 lab-worker-N
        // （对照项目里的 "research-task-"；可试试去掉它，看默认名 pool-1-thread-1 多难认）
        ExecutorService pool = Executors.newFixedThreadPool(2,
                runnable -> new Thread(runnable, "lab-worker-" + workerNo.getAndIncrement()));

        long startAll = System.currentTimeMillis();
        for (int i = 1; i <= 5; i++) {
            String taskName = "任务-" + i;
            pool.execute(() -> researchJob(taskName));       // ★ 箭头绑参：把 taskName 绑进包裹
        }
        pool.shutdown();                                     // 不再接新单
        pool.awaitTermination(1, TimeUnit.MINUTES);          // 等所有活干完（否则 main 一退 JVM 就关）
        long total = System.currentTimeMillis() - startAll;
        System.out.println(">> 5 个任务总耗时：" + total + "ms（约 3 批 × 2 秒——2 个工人流水线的账）");

        // ========== 第二幕：两种提交方式对比 ==========
        System.out.println();
        System.out.println("========== 第二幕：this:: 直传 vs () -> 绑参 ==========");
        ExecutorService pool2 = Executors.newSingleThreadExecutor(
                runnable -> new Thread(runnable, "lab-worker-solo"));

        pool2.execute(ThreadPoolLab::zeroArgJob);             // ① 零参方法 → 方法引用直传
        pool2.execute(ThreadPoolLab::zeroArgJob);             // ② 再来一次（同一工人复用的证据）
        pool2.execute(() -> researchJob("绑参任务"));          // ③ 有参方法 → 箭头绑参
        pool2.shutdown();
        pool2.awaitTermination(1, TimeUnit.MINUTES);
        System.out.println(">>> 实验结束");
    }
}

// ============================================================
// J1–J4 精华：BasicsLab（record / 多态 / 泛型擦除 / 异常）
// 跑法：javac exampleJavaGrammar.java  然后  java BasicsLab
// ============================================================
class BasicsLab {

    // ══════════ 第一幕（J1）：record vs class——"那 60 行"去哪了 ══════════

    record PointRecord(int x, int y) { }        // 一行——构造器/访问器/toString/equals 全自动

    static class PointClass {                   // 对照：故意【不写】toString 和 equals
        final int x, y;
        PointClass(int x, int y) { this.x = x; this.y = y; }
    }

    // ══════════ 第二幕（J2）：迷你多态——"Spring 选人"的一分钟重演 ══════════

    interface Greeter { String greet(String name); }        // 合同（interface）

    static class ChineseGreeter implements Greeter {        // 面孔 A
        public String greet(String name) { return "你好，" + name; }
    }

    static class EnglishGreeter implements Greeter {        // 面孔 B
        public String greet(String name) { return "Hello, " + name; }
    }

    // ══════════ 第三幕（J3）：泛型擦除——标签在运行时消失 ══════════

    static void genericsErasure() {
        List<String> strings = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        System.out.println("List<String>  的运行时类：" + strings.getClass().getName());
        System.out.println("List<Integer> 的运行时类：" + numbers.getClass().getName());
        System.out.println("两者是同一个类吗？ " + (strings.getClass() == numbers.getClass()));
        System.out.println("（尖括号里的标签在运行时完全不见踪影——泛型只活到编译期）");

        try {
            List.of("a", "b").add("c");        // List.of 给的是不可变清单
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of 的清单拒绝修改，抛了：" + e.getClass().getSimpleName());
        }
    }

    // ══════════ 第四幕（J4）：异常——"受检"与"非受检"的分界线 ══════════

    static class TaskNotFound extends RuntimeException {    // 非受检（同项目里的 TaskNotFoundException）
        TaskNotFound(String message) { super(message); }
    }

    static void findTask(String id) {
        if (!"T-001".equals(id)) {
            throw new TaskNotFound("任务不存在：" + id);
        }
        System.out.println("   找到任务 " + id);
    }

    static void checkedFind() throws java.io.IOException {  // 受检：方法声明 throws 本身不报错
        throw new java.io.IOException("模拟受检异常：文件读取失败");
    }

    public static void main(String[] args) {
        // ── 第一幕：record vs class ──
        System.out.println("===== 第一幕（J1）：record vs class =====");
        PointRecord pr = new PointRecord(3, 4);
        System.out.println("record 自动 toString：             " + pr);
        System.out.println("record 访问器是 x() 不是 getX()：  " + pr.x());
        System.out.println("record 自动 equals（内容相同就相等）：" + pr.equals(new PointRecord(3, 4)));

        PointClass pc = new PointClass(3, 4);
        System.out.println("普通 class 没写 toString：         " + pc);
        System.out.println("普通 class 没写 equals（内容相同也不等）：" + pc.equals(new PointClass(3, 4)));
        System.out.println("（差距 = record 替你自动生成的那 60 行样板）");

        // ── 第二幕：迷你多态 ──
        System.out.println();
        System.out.println("===== 第二幕（J2）：迷你多态——一分钟重演 Spring 选人 =====");
        boolean useEnglish = false;                              // 模拟"条件装配"（Spring 用 @ConditionalOnProperty）
        Greeter greeter = useEnglish ? new EnglishGreeter() : new ChineseGreeter();
        System.out.println("当前实现：" + greeter.getClass().getSimpleName());   // 多态报幕（J2 DemoRunner 同款）
        System.out.println("同一句调用，换个人干活：" + greeter.greet("LCC"));

        greeter = new EnglishGreeter();                          // 手动切换（等于改配置重启）
        System.out.println("切换后实现：" + greeter.getClass().getSimpleName());
        System.out.println("同一句调用（内容一模一样），不同结果：" + greeter.greet("LCC"));

        // ── 第三幕：泛型擦除 ──
        System.out.println();
        System.out.println("===== 第三幕（J3）：泛型擦除 =====");
        genericsErasure();

        // ── 第四幕：异常 ──
        System.out.println();
        System.out.println("===== 第四幕（J4）：受检 / 非受检 =====");
        findTask("T-001");                                       // 正常

        try {
            findTask("T-999");                                   // 非受检：不强迫处理，但可以接
        } catch (TaskNotFound e) {
            System.out.println("   接住非受检异常：" + e.getMessage());
        }

        try {
            checkedFind();                                       // 受检：编译器强迫表态——这里 catch
        } catch (java.io.IOException e) {
            System.out.println("   受检异常必须处理（已接住）：" + e.getMessage());
        }

        // ★ 交互实验：去掉下面这行的注释、再 javac —— 亲眼看"编译器翻脸"（Unhandled exception）
        // checkedFind();
        System.out.println("（交互实验见源码注释：把 checkedFind() 调用取消注释，看编译器如何拒绝）");
    }
}

// ============================================================
// J9 下半场：JvmLab（体检报告 / -Xmx 对比 / OOM 现场）
// 跑法：
//   javac exampleJavaGrammar.java
//   java JvmLab                  ← 体检报告（默认堆上限）
//   java -Xmx64m JvmLab          ← 同一程序：堆被限成 64MB（对比 maxMemory）
//   java -Xmx64m JvmLab oom      ← ★ OOM 现场（务必带 -Xmx64m——小堆安全）
// ============================================================
class JvmLab {

    static final long MB = 1024 * 1024;

    public static void main(String[] args) {
        Runtime rt = Runtime.getRuntime();

        System.out.println("===== 体检报告 =====");
        System.out.println("Java 版本：  " + System.getProperty("java.version"));
        System.out.println("JVM 名称：   " + System.getProperty("java.vm.name"));
        System.out.println("CPU 核心数： " + rt.availableProcessors());
        System.out.println("最大堆（-Xmx 上限）： " + rt.maxMemory() / MB + " MB");
        System.out.println("当前堆（已向系统申请）： " + rt.totalMemory() / MB + " MB");
        System.out.println("空闲堆：     " + rt.freeMemory() / MB + " MB");
        System.out.println("（不设 -Xmx 时，最大堆约等于宿主机内存的 1/4——容器里这就是风险！）");

        if (args.length > 0 && args[0].equals("oom")) {
            System.out.println();
            System.out.println("===== OOM 现场：开始往堆里塞对象（每袋 1MB） =====");
            List<byte[]> bag = new ArrayList<>();   // 局部变量 bag 是"根"——塞进去的东西 GC 收不走
            int i = 0;
            try {
                while (true) {
                    bag.add(new byte[1024 * 1024]); // 每次 1MB
                    i++;
                    if (i % 16 == 0) {
                        System.out.println("  已塞入 " + i + " MB ...");
                    }
                }
            } catch (OutOfMemoryError e) {
                System.out.println("  ★ 塞到 " + i + " MB 时，JVM 摊牌了：");
                System.out.println("    " + e.getClass().getName() + ": " + e.getMessage());
                System.out.println("    （这就是 OOM。生产里的内存泄漏，就是它的慢速版）");
            }
        }
    }
}
