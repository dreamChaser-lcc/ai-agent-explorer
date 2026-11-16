package com.aiexplorer.researchagent.api.controller.redisDemo;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lua 脚本「独立文件版」的教学入口（教学用，可删）。
 *
 * <p>与 {@link RedisPlaygroundController} 里的内嵌写法形成对照：</p>
 * <ul>
 *   <li>内嵌版：{@code /api/redis-playground/sms/send-daily-limited}
 *       —— 脚本字符串直接写在 Java 里（{@code INCREMENT_WITH_DAILY_TTL_SCRIPT}）；</li>
 *   <li>文件版（本类）：{@code /api/redis-lua/stock/deduct}
 *       —— 脚本放在 {@code src/main/resources/lua/deduct_stock.lua}，
 *       由 {@code RedisScriptConfig} 加载成 Bean 注入。</li>
 * </ul>
 *
 * <p>业务场景：秒杀扣库存防超卖——"读库存 → 判断是否充足 → 扣减"三步必须原子，
 * 否则并发下两个请求可能都读到 stock=1，各自扣减后变成 -1（超卖）。</p>
 *
 * <p>启用条件：{@code dev} / {@code dev-docker} profile。</p>
 */
@RestController
@Profile({"dev", "dev-docker"})
@RequestMapping("/api/redis-lua")
public class RedisLuaScriptController {

    /** 演示库存 key 前缀 */
    private static final String STOCK_KEY_PREFIX = "demo:stock:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 库存扣减脚本，来自 {@code classpath:lua/deduct_stock.lua}。
     *
     * <p>{@code @Qualifier} 指定 Bean 名：容器里可能注册多个 {@code RedisScript<Long>}，
     * 按类型注入会歧义。</p>
     */
    private final RedisScript<Long> deductStockScript;

    public RedisLuaScriptController(StringRedisTemplate redisTemplate,
                                    @Qualifier("deductStockScript") RedisScript<Long> deductStockScript) {
        this.redisTemplate = redisTemplate;
        this.deductStockScript = deductStockScript;
    }

    /**
     * 初始化演示库存——普通 {@code SET}，这种情况不需要 Lua。
     *
     * <p>对照点：只有当多个命令必须"一起成功或一起失败"时才需要脚本；
     * 单纯写一个值是单命令语义，天然原子。</p>
     */
    @PostMapping("/stock/init")
    public Map<String, Object> initStock(@RequestParam(defaultValue = "p1") String productId,
                                         @RequestParam(defaultValue = "10") long stock) {
        String key = STOCK_KEY_PREFIX + productId;
        redisTemplate.opsForValue().set(key, String.valueOf(stock));
        return Map.of("productId", productId,
                "key", key,
                "stock", String.valueOf(stock),
                "tip", "库存已初始化，接着调 POST /api/redis-lua/stock/deduct 扣减");
    }

    /**
     * 原子扣减库存——执行文件版 Lua 脚本。
     *
     * <p>返回值的三种情况由脚本内的 {@code return -1 / -2 / 剩余库存} 决定，
     * 这里只负责翻译成 HTTP 响应。</p>
     */
    @PostMapping("/stock/deduct")
    public Map<String, Object> deductStock(@RequestParam(defaultValue = "p1") String productId,
                                           @RequestParam(defaultValue = "1") long quantity) {
        String key = STOCK_KEY_PREFIX + productId;

        // execute(脚本, KEYS 列表, ARGV...)：KEYS 用来做集群路由，ARGV 是业务参数
        Long result = redisTemplate.execute(deductStockScript, List.of(key), String.valueOf(quantity));

        if (result == null) {
            return Map.of("success", false,
                    "reason", "REDIS_ERROR",
                    "message", "脚本执行失败，请检查 Redis 连接");
        }
        if (result == -1L) {
            return Map.of("success", false,
                    "reason", "NOT_INITIALIZED",
                    "message", "库存未初始化，先调 POST /api/redis-lua/stock/init");
        }
        if (result == -2L) {
            return Map.of("success", false,
                    "reason", "OUT_OF_STOCK",
                    "message", "库存不足，扣减失败（脚本返回 -2）");
        }
        return Map.of("success", true,
                "productId", productId,
                "deducted", String.valueOf(quantity),
                "remainingStock", String.valueOf(result),
                "scriptSource", "classpath:lua/deduct_stock.lua");
    }

    /** 查看当前库存（普通 GET，方便观察脚本执行效果） */
    @GetMapping("/stock")
    public Map<String, Object> getStock(@RequestParam(defaultValue = "p1") String productId) {
        String value = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + productId);
        return Map.of("productId", productId,
                "key", STOCK_KEY_PREFIX + productId,
                "stock", value == null ? "（未初始化）" : value);
    }
}
