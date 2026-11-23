package com.aiexplorer.researchagent.api.controller.redisDemo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T7 亲手验证：Redisson 可重入 + 看门狗（配套文档 §四"亲手验证"小节）。
 *
 * <p>为什么要在【一个请求】里串完整流程：可重入的前提是"同一个线程"重复加锁，
 * 而 HTTP 每个请求都由不同线程处理——拆成多个端点就演示不了重入。</p>
 *
 * <p>端点：</p>
 * <ol>
 *   <li>GET /api/redis-demo/reentrant-lifecycle —— 同线程 5 步生命周期
 *       （加锁 → 重入 → 睡 8s 看看门狗续期 → 两次 unlock 配对释放），每步返回 Redis 实时快照</li>
 *   <li>GET /api/redis-demo/reentrant-parallel —— 两个线程抢同一把锁
 *       （不同 threadId = 不同 field = 真正的竞争）</li>
 * </ol>
 *
 * <p>配套 redis-cli 旁路验证：TYPE（hash）/ HGETALL（field=凭证, value=计数）/
 * TTL（看门狗不断重置回 30s 附近）。</p>
 */
@RestController
@Profile({"dev", "dev-docker"})
@RequestMapping("/api/redis-demo")
public class ReentrantLockDemoController {

    private final RedissonClient redisson;
    private final StringRedisTemplate stringRedisTemplate;

    /** @param redisson 加 @Lazy 与 RedissonTaskLock 同款（starter 自动装配，未启用时不阻塞启动）。 */
    public ReentrantLockDemoController(@Lazy RedissonClient redisson,
            StringRedisTemplate stringRedisTemplate) {
        this.redisson = redisson;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 端点 1：同一个线程内串"加锁 → 重入 → 看门狗续期 → 配对释放"全过程。
     * 每一步都返回 Redis 实时快照（type / hash 凭证与计数 / TTL 毫秒），
     * 与 T7 §四的文字描述一一对应——肉眼闭环。
     */
    @GetMapping("/reentrant-lifecycle")
    public Map<String, Object> lifecycle(
            @RequestParam(defaultValue = "lock:demo:reentrant:1") String lockName)
            throws InterruptedException {

        RLock lock = redisson.getLock(lockName);
        Map<String, Object> out = new LinkedHashMap<>();

        // ① 第一次加锁：leaseTime=-1 → 看门狗接管（TTL 30s，每 10s 续）
        boolean first = lock.tryLock(0, -1, TimeUnit.SECONDS);
        out.put("① 第一次 tryLock", first);
        out.put("① 快照（type=HASH / field=uuid:threadId / 计数=1 / ttl≈30000）", snapshot(lockName));

        // ② 同线程重入：field 完全相同（getLockName 只看进程UUID+线程ID）→ 计数 +1
        boolean second = lock.tryLock(0, -1, TimeUnit.SECONDS);
        out.put("② 同线程再 tryLock（重入成功，计数应 1→2）", second);
        out.put("② 快照", snapshot(lockName));

        // ③ 睡 8 秒后看 TTL：如果没人续期应该衰减到 ~22000ms；
        //    看门狗在 10s 节奏上续期 → 实际仍 ≈30000ms —— 续期的直接证据
        Thread.sleep(8000);
        out.put("③ 睡 8 秒后 TTL（★ 看门狗续期证据：仍 ≈30000 而非衰减）", snapshot(lockName));

        // ④ 两次 unlock 配对：计数 2→1→0，到 0 才真正 DEL
        lock.unlock();
        out.put("④ 第一次 unlock（计数 2→1，锁还在）", snapshot(lockName));
        lock.unlock();
        out.put("④ 第二次 unlock（计数 1→0 → key 被删除）", snapshot(lockName));
        return out;
    }

    /**
     * 端点 2：两个线程抢同一把锁——不同 threadId = 不同 field = 真正的竞争。
     * 线程 A 持锁 2.5 秒；主线程（线程 B）在 A 持有期间 tryLock(0) → 立即失败。
     */
    @GetMapping("/reentrant-parallel")
    public Map<String, Object> parallel(
            @RequestParam(defaultValue = "lock:demo:parallel:1") String lockName)
            throws InterruptedException {

        RLock lock = redisson.getLock(lockName);
        Map<String, Object> out = new LinkedHashMap<>();
        CountDownLatch aReady = new CountDownLatch(1);
        AtomicBoolean aHeld = new AtomicBoolean(false);

        Thread threadA = new Thread(() -> {
            try {
                boolean ok = lock.tryLock(0, -1, TimeUnit.SECONDS);
                aHeld.set(ok);
                aReady.countDown();          // A 已抢到，通知主线程开抢
                Thread.sleep(2500);          // A 持锁 2.5 秒
                if (ok) {
                    lock.unlock();           // A 在自己线程里释放（isHeldByCurrentThread=true）
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "thread-A");
        threadA.start();
        aReady.await();                      // 等 A 先拿到锁

        boolean bGot = lock.tryLock(0, -1, TimeUnit.SECONDS);   // 主线程 B 立即尝试 → 失败
        out.put("线程A tryLock", aHeld.get());
        out.put("线程B(主线程) A持有期间 tryLock(0)", bGot);     // false ← 不同 field = 真竞争
        out.put("线程B isHeldByCurrentThread", lock.isHeldByCurrentThread()); // false
        out.put("竞争期间快照（A 的 field 在，B 没有自己的 field）", snapshot(lockName));

        threadA.join();                      // 等 A 释放
        boolean again = lock.tryLock(0, -1, TimeUnit.SECONDS);
        out.put("A 释放后线程B 再 tryLock", again);
        if (again) {
            lock.unlock();
        }
        out.put("结论", "同线程重入（field 相同）vs 跨线程竞争（field 不同）——一端点看全");
        return out;
    }

    /** Redis 实时快照：锁的 type / Hash 内容（凭证:计数）/ 剩余 TTL 毫秒。
     *  ★ 必须用 StringRedisTemplate（String 编码）读——锁的 Hash 是锁 Lua 用 HINCRBY 写的
     *    【原生整数】，不是 Kryo 格式；用 RMap 默认 codec（Kryo5）解码会报
     *    "unregistered class ID"（读写两端方言不一致——J10 §6 的活案例，2026-11-23 实测踩坑）。 */
    private Map<String, Object> snapshot(String lockName) {
        Map<String, Object> d = new LinkedHashMap<>();
        try {
            d.put("type", redisson.getKeys().getType(lockName).name());     // HASH / NONE
            Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(lockName);
            d.put("hash(field=凭证 → 计数)", entries);                      // {"uuid:42": "1"} 原生可读
            d.put("ttlMs", redisson.getKeys().remainTimeToLive(lockName));  // -1=无过期 0=不存在
        } catch (Exception e) {
            d.put("note", "key 不存在或已删除");
        }
        return d;
    }
}
