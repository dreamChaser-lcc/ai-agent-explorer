package com.aiexplorer.researchagent.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis Lua 脚本的统一声明处（教学用，与 redisDemo 包配套）。
 *
 * <p>对照 {@code RedisPlaygroundController} 里的内嵌写法：
 * 那里是把脚本字符串直接写在 Java 里，适合 3~5 行的极短脚本；
 * 这里从 {@code src/main/resources/lua/} 目录加载，适合更长、需要 if/循环、
 * 或多处复用的脚本。两种方式在运行期完全等价（Spring 都走 EVALSHA）。</p>
 *
 * <p>放在 resources 下的好处：语法高亮、可单独 lint、能用
 * {@code redis-cli --eval lua/deduct_stock.lua key , arg} 脱离应用直接调试、
 * 修改脚本多数情况下无需重新编译。</p>
 */
@Configuration
@Profile({"dev", "dev-docker"})
public class RedisScriptConfig {

    /**
     * 库存扣减脚本（秒杀防超卖）。
     *
     * <p>{@link DefaultRedisScript#setLocation} 指向 classpath 资源；
     * {@code setResultType(Long.class)} 声明返回值类型，Spring 会据此把
     * Redis 的 integer 响应转成 {@code Long}。</p>
     */
    @Bean
    public RedisScript<Long> deductStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/deduct_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
