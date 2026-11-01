package com.aiexplorer.researchagent.infrastructure.lock;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * T7：Redisson 版任务锁——与手写 DistributedTaskLock（场景⑨ SET NX EX + Lua）
 * 同一业务语义的对照实现，展示"手写四步进化"与"Redisson 开箱能力"的差距。
 *
 * 启用条件：app.lock.redisson=true（默认关闭，不影响现有手写锁与启动；
 * 启用时需容器存在 RedissonClient Bean——redisson-spring-boot-starter 按
 * spring.data.redis 配置自动装配）。
 *
 * 与手写版的关键差异（讲义有完整矩阵）：
 *   看门狗：lock() 不传 leaseTime → 默认 30s TTL，后台每 10s 自动续期；
 *           进程崩溃则续期停止、锁自然过期（不死锁）。
 *   可重入：同线程可再次 lock（Redis Hash：field=线程标识，value=计数），
 *           解锁与加锁次数配对才真正释放。
 *   释放安全：内部 Lua 校验持有者，等价手写 token 方案。
 */
@Component
@ConditionalOnProperty(name = "app.lock.redisson", havingValue = "true")
@ConditionalOnBean(RedissonClient.class)
public class RedissonTaskLock {

    private final RedissonClient redisson;

    /** @param redisson 加 @Lazy 避免未启用 Redisson 时启动报错。 */
    public RedissonTaskLock(@Lazy RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 任务执行锁（对照手写版 tryLock(UUID)）。
     * tryLock(waitTime=0, leaseTime=-1)：不等待、启用看门狗。
     * 手写版固定 TTL 5 分钟——长任务超时锁被别人拿走；Redisson 版不会。
     *
     * @return 持有凭证（RLock 本身）；已被持有则 empty
     */
    public Optional<RLock> tryLock(UUID taskId) {
        RLock lock = redisson.getLock(
                com.aiexplorer.researchagent.shared.constant.RedisConstant.lockTaskExec(taskId));
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, -1, TimeUnit.SECONDS); // -1 = 看门狗接管
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return acquired ? Optional.of(lock) : Optional.empty();
    }

    /** 释放（只放自己持有的锁——内部 Lua 校验持有者，等价手写 token 方案）。 */
    public void unlock(UUID taskId, RLock lock) {
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /** 可重入演示：同线程再次加锁不阻塞（计数+1），unlock 次数配对才真正释放。 */
    public Optional<RLock> reentrantDemo(UUID taskId) {
        return tryLock(taskId);
    }
}