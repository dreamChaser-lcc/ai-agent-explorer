package com.aiexplorer.researchagent.api.controller.redisDemo;

import java.time.Duration;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
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
        String smsCode = String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
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
}
