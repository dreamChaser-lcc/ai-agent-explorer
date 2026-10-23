package com.aiexplorer.researchagent.infrastructure.lock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 场景⑨：任务级分布式锁（Redis SET NX EX）。
 *
 * <p>为什么用 Redis 而不是 DB 行锁：
 * 任务执行包含多次 LLM 调用（数十秒），DB 悲观锁会把整个执行包成【长事务】——
 * 连接被占用几十秒、行锁不释放，并发一高就把连接池打满（SP9 讲过的坑）。
 * Redis 锁不占数据库资源，靠 TTL 自动过期兜底。</p>
 *
 * <p>三个关键设计：
 * <ul>
 *   <li>{@code SET NX EX}：原子"不存在才设置 + 过期时间"，避免 setnx 后进程挂掉导致死锁；</li>
 *   <li><b>token 校验释放</b>（Lua 脚本）：只删自己持有的锁，防止"执行超时后锁过期、别人拿到锁、自己把别人的锁删了"；</li>
 *   <li>TTL 兜底：即使进程崩溃，锁也会在 TTL 后自动释放。</li>
 * </ul></p>
 */
@Component
public class DistributedTaskLock {

    private static final Logger log = LoggerFactory.getLogger(DistributedTaskLock.class);

    private static final String KEY_PREFIX = "lock:task:exec:";

    /** 锁 TTL：需覆盖一次完整执行（含多次 LLM 调用）。 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    /** 释放锁的 Lua：只有 value 与自己的 token 一致才删除（原子操作）。 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public DistributedTaskLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 尝试获取任务执行锁。
     *
     * @return 持有凭证（token）；已被别人持有则返回 empty
     */
    public Optional<String> tryLock(UUID taskId) {
        return acquire(KEY_PREFIX + taskId, "taskId=" + taskId);
    }

    /** 释放任务执行锁（token 不匹配则不删——防止误删他人锁）。 */
    public void unlock(UUID taskId, String token) {
        release(KEY_PREFIX + taskId, token, "taskId=" + taskId);
    }

    /**
     * 通用锁（场景⑩：定时清理等非任务场景使用，key 由调用方给定）。
     */
    public Optional<String> tryLock(String lockKey) {
        return acquire(lockKey, "key=" + lockKey);
    }

    /** 释放通用锁。 */
    public void unlock(String lockKey, String token) {
        release(lockKey, token, "key=" + lockKey);
    }

    private Optional<String> acquire(String key, String logTag) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, LOCK_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            log.info("[锁] 获取成功 {} token={} ttl={}s", logTag, token.substring(0, 8), LOCK_TTL.getSeconds());
            return Optional.of(token);
        }
        log.warn("[锁] 获取失败：已有持有者，跳过本次执行 {}", logTag);
        return Optional.empty();
    }

    private void release(String key, String token, String logTag) {
        Long deleted = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        log.info("[锁] 释放 {} 结果={}", logTag, deleted);
    }
}