package com.aiexplorer.researchagent.api.controller.redisDemo;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 五大类型 + 验证码实战演示（教学用，可删）。
 *
 * <p>仅在 {@code dev} 与 {@code dev-docker} 环境启用——与 NPlusOneDemoController /
 * AdminCleanupController 同一模式：教学脚手架不进生产。</p>
 *
 * <p>注意：Spring 的 {@code @Profile} 是精确匹配、不认继承关系，
 * 因此 {@code dev-docker}（本地 mvn 连 Docker 中间件模式）必须显式列出，
 * 否则该 Controller 不会被注册，Swagger 里也就看不到这些端点。</p>
 *
 * <p>配套讲义：{@code docs/Redis实战/08·Spring实战整合——初学者实战篇.md} §2 / §2.4。
 * 使用方法：启动后端后逐个访问端点，每访问一个就到 redis-cli 里
 * {@code KEYS demo:*} 观察数据形态（String/Hash/List/Set/ZSet 五种结构肉眼可辨）。</p>
 */
@RestController
@Profile({"dev", "dev-docker"})
@RequestMapping("/api/redis-playground")
public class RedisPlaygroundController {

    private final StringRedisTemplate redisTemplate;

    /**
     * 「INCR + 首次创建时设过期」的 Lua 脚本——整个脚本在 Redis 端作为一个命令原子执行。
     *
     * <pre>
     * local count = redis.call('INCR', KEYS[1])
     * if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
     * return count
     * </pre>
     *
     * <p>为什么不用 Java 分两步（{@code increment} 后 {@code expire}）：
     * ① 两步之间存在中间态（INCR 成功、EXPIRE 前崩溃 → 计数永不过期，
     * 2026-11-16 实测就撞上了，表现是 TTL 返回 -1）；
     * ② Spring Data Redis 3.5.10 的 {@code DefaultedRedisConnection#pExpire(byte[], Duration)}
     * 默认实现是自递归，{@code expire(key, Duration)} 与 {@code expire(key, long, TimeUnit)}
     * 都会直接 StackOverflowError；
     * 用 Lua 一次往返解决这两个问题（讲义 09 §7）。</p>
     */
    private static final RedisScript<Long> INCREMENT_WITH_DAILY_TTL_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    public RedisPlaygroundController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /* ==================== 五大类型演示 ==================== */

    /**
     * String 类型：最基础的 key-value——验证码/锁/缓存 JSON 全是它。
     * 演示：写入 + 读回 + 查看 TTL。
     */
    @GetMapping("/string")
    public Map<String, Object> stringTypeDemo(@RequestParam(defaultValue = "greeting") String key) {
        String fullKey = "demo:string:" + key;
        redisTemplate.opsForValue().set(fullKey, "hello redis", Duration.ofMinutes(10));
        String value = redisTemplate.opsForValue().get(fullKey);
        Long ttlSeconds = redisTemplate.getExpire(fullKey);
        return Map.of("key", fullKey,
                "value", String.valueOf(value),
                "ttlSeconds", String.valueOf(ttlSeconds));
    }

    /**
     * Hash 类型：像 JS 对象一样按字段读写（改一个字段不影响其他字段）。
     * 典型场景：购物车、用户资料。
     */
    @GetMapping("/hash")
    public Map<Object, Object> hashTypeDemo() {
        redisTemplate.opsForHash().put("demo:user:1001", "name", "lcc");
        redisTemplate.opsForHash().put("demo:user:1001", "level", "gold");
        return redisTemplate.opsForHash().entries("demo:user:1001");
    }

    /**
     * List 类型：队列（左进右出 = FIFO）。
     * 典型场景：简易任务队列、最新 N 条动态。
     */
    @GetMapping("/list")
    public Map<String, Object> listTypeDemo() {
        redisTemplate.opsForList().leftPush("demo:queue", "task-1");
        redisTemplate.opsForList().leftPush("demo:queue", "task-2");
        String popped = redisTemplate.opsForList().rightPop("demo:queue");
        return Map.of("popped", String.valueOf(popped),
                "remaining", String.valueOf(redisTemplate.opsForList().range("demo:queue", 0, -1)));
    }

    /**
     * Set 类型：去重 + 成员判断（O(1)）。
     * 典型场景：黑名单、点赞去重。
     */
    @GetMapping("/set")
    public Map<String, Object> setTypeDemo(@RequestParam String phone) {
        redisTemplate.opsForSet().add("demo:blacklist", phone);
        return Map.of("members", String.valueOf(redisTemplate.opsForSet().members("demo:blacklist")),
                "isBlocked", Boolean.TRUE.equals(redisTemplate.opsForSet().isMember("demo:blacklist", phone)));
    }

    /**
     * ZSet 类型：按 score 排序——排行榜本命。
     * 演示：每访问一次关键词，热度 +1，返回 Top 榜。
     */
    @GetMapping("/zset")
    public Map<String, Object> zsetTypeDemo(@RequestParam String keyword) {
        redisTemplate.opsForZSet().incrementScore("demo:rank:search", keyword, 1);
        return Map.of("top", String.valueOf(redisTemplate.opsForZSet()
                .reverseRangeWithScores("demo:rank:search", 0, 9)));
    }

    /* ==================== 真实场景：短信验证码（String + TTL 的完整闭环） ==================== */

    /**
     * 发送验证码：生成 6 位随机码，写入 Redis 并设 5 分钟 TTL。
     *
     * <p>开发环境把验证码直接返回（{@code devCode}），方便自测；真实项目这里是调短信服务商。</p>
     *
     * <p>★ 知识点：TTL 到点自动删除——"5 分钟内有效"不用写任何定时清理代码。</p>
     */
    @PostMapping("/sms/send")
    public Map<String, Object> sendSmsCode(@RequestParam String phone) {
        String smsCode = generateDevSmsCode();
        redisTemplate.opsForValue().set("demo:captcha:" + phone, smsCode, Duration.ofMinutes(5));
        return Map.of("message", "验证码已发送（5 分钟内有效）",
                "devCode", smsCode,
                "tip", "用 redis-cli 执行 GET demo:captcha:" + phone + " 可看到验证码与剩余时间");
    }

    /**
     * 校验验证码：读出比对 → 通过后立刻删除（一次性使用）。
     *
     * <p>★ 三种失败路径全覆盖：key 不存在 = 已过期；值不一致 = 输错；通过后删除 = 防重复使用。</p>
     */
    @GetMapping("/sms/verify")
    public Map<String, Object> verifySmsCode(@RequestParam String phone, @RequestParam String code) {
        String savedCode = redisTemplate.opsForValue().get("demo:captcha:" + phone);
        if (savedCode == null) {
            return Map.of("success", false, "message", "验证码已过期，请重新获取");
        }
        if (!savedCode.equals(code)) {
            return Map.of("success", false, "message", "验证码错误");
        }
        redisTemplate.delete("demo:captcha:" + phone);
        return Map.of("success", true, "message", "校验通过（验证码已作废）");
    }

    /* ==================== 限流对比：同样的"发验证码"，三种防护等级 ==================== */

    /**
     * 发送验证码 v2：带<b>窗口限流</b>（默认 60 秒，可传参覆盖）——与 {@code /sms/send} 对比用。
     *
     * <p>★ 原子性要点：{@code setIfAbsent(key, value, ttl)} 底层是
     * {@code SET key value EX n NX} —— <b>判断不存在 + 写入 + 设过期</b>在同一条命令内完成。
     * 若拆成 {@code SETNX} 再 {@code EXPIRE} 两条命令，中间一旦中断（进程崩溃 / 网络断），
     * key 会永不过期 → 该手机号被永久限流（讲义 09 §3 / §5）。</p>
     *
     * <p>对比测试：连点两次 {@code /sms/send} 都成功；连点两次本接口，第二次返回
     * {@code RATE_LIMITED} 并带回 {@code remainingSeconds}。
     * 传 {@code windowSeconds=1} 可把窗口缩到 1 秒，便于快速验证。</p>
     */
    @PostMapping("/sms/send-guarded")
    public Map<String, Object> sendSmsCodeGuarded(@RequestParam String phone,
                                                  @RequestParam(defaultValue = "60") long windowSeconds) {
        String limitKey = "demo:sms:limit:" + phone;

        // 一条命令完成"仅当不存在才设值 + 同时带过期"，原子且无中间态
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(limitKey, "1", Duration.ofSeconds(windowSeconds));

        if (!Boolean.TRUE.equals(acquired)) {
            Long remainingSeconds = redisTemplate.getExpire(limitKey);
            return Map.of("sent", false,
                    "reason", "RATE_LIMITED",
                    "message", windowSeconds + " 秒内只能发送一次，请 " + remainingSeconds + " 秒后再试",
                    "remainingSeconds", String.valueOf(remainingSeconds));
        }

        String smsCode = generateDevSmsCode();
        redisTemplate.opsForValue().set("demo:captcha:" + phone, smsCode, Duration.ofMinutes(5));
        return Map.of("sent", true,
                "devCode", smsCode,
                "message", "验证码已发送（5 分钟内有效）",
                "windowSeconds", String.valueOf(windowSeconds));
    }

    /**
     * 发送验证码 v3：带<b>每日上限</b>（默认 5 条，可传参覆盖）——与 {@code /sms/send} 对比用。
     *
     * <p>★ 两个关键设计：</p>
     * <ol>
     *   <li><b>只在 {@code count == 1} 时设过期</b>：若每次 INCR 都刷新 EXPIRE，
     *       TTL 会被无限续期，"次日 0 点清零"永远到不了，每日限制形同虚设；</li>
     *   <li><b>过期时间对齐到次日 0 点</b>：{@code LocalDate.now().plusDays(1).atStartOfDay()}，
     *       保证所有计数器同时归零。</li>
     * </ol>
     *
     * <p>⚠️ {@code INCR} 与 {@code EXPIRE} 是两条命令，理论上仍有中间态（INCR 成功后崩溃、
     * 未执行 EXPIRE → 计数永不过期）。要彻底原子需用 Lua 脚本（讲义 09 §7）。</p>
     */
    @PostMapping("/sms/send-daily-limited")
    public Map<String, Object> sendSmsCodeDailyLimited(@RequestParam String phone,
                                                       @RequestParam(defaultValue = "5") long dailyLimit) {
        String dailyKey = "demo:sms:daily:" + phone;

        // "到次日 0 点"的剩余秒数：所有计数器在同一时刻归零
        LocalDateTime nextMidnight = LocalDate.now().plusDays(1).atStartOfDay();
        long ttlSeconds = Math.max(1L, Duration.between(LocalDateTime.now(), nextMidnight).toSeconds());

        // Lua 一次往返：INCR 自增 + 仅首次创建时设过期（原子，无中间态）
        Long todayCount = redisTemplate.execute(INCREMENT_WITH_DAILY_TTL_SCRIPT,
                List.of(dailyKey),
                String.valueOf(ttlSeconds));

        if (todayCount == null) {
            // 连接异常时脚本可能返回 null，直接短路，避免后续拆箱 NPE
            return Map.of("sent", false,
                    "reason", "REDIS_ERROR",
                    "message", "计数写入失败，请稍后重试");
        }

        if (todayCount > dailyLimit) {
            Long resetInSeconds = redisTemplate.getExpire(dailyKey);
            return Map.of("sent", false,
                    "reason", "DAILY_LIMIT_EXCEEDED",
                    "message", "今日发送次数已达上限（" + dailyLimit + " 条），" + resetInSeconds + " 秒后重置",
                    "todayCount", String.valueOf(todayCount),
                    "resetInSeconds", String.valueOf(resetInSeconds));
        }

        String smsCode = generateDevSmsCode();
        redisTemplate.opsForValue().set("demo:captcha:" + phone, smsCode, Duration.ofMinutes(5));
        return Map.of("sent", true,
                "devCode", smsCode,
                "message", "验证码已发送（5 分钟内有效）",
                "todayCount", String.valueOf(todayCount),
                "remainingQuota", String.valueOf(dailyLimit - todayCount));
    }

    /**
     * 生成 6 位验证码（首位 1~9，不会出现 0 开头的 5 位数字）。
     *
     * <p>⚠️ 教学演示用 {@code Math.random()}（伪随机、可预测）；
     * 生产环境必须换成 {@code SecureRandom}。</p>
     */
    private String generateDevSmsCode() {
        return String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
    }
}
