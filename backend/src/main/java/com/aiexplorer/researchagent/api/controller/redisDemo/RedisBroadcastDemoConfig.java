package com.aiexplorer.researchagent.api.controller.redisDemo;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 广播型演示（Pub/Sub）的订阅端配置——教学用，可删。
 *
 * <p>为什么单独成类：{@link RedisMessageListenerContainer} 是长期运行的容器级 Bean，
 * 负责维护订阅连接、后台线程收消息；Controller 只该负责 HTTP 入口，把订阅逻辑放进去会污染职责。</p>
 *
 * <p>★ Pub/Sub 的关键特性（demo 里能亲眼看到）：</p>
 * <ul>
 *   <li><b>不持久化</b>：消息发布后立刻广播给"当时在线"的订阅者，之后 Redis 里什么都不留；</li>
 *   <li><b>没有确认机制</b>：订阅者收到与否，发布方无从得知；</li>
 *   <li><b>订阅者不在线就丢</b>：PUBLISH 返回的接收者数量为 0 时，消息直接丢弃。</li>
 * </ul>
 *
 * <p>配套讲义：{@code docs/Redis实战/10·四大角色深入——广播型与计算型实战.md} §1</p>
 */
@Configuration
@Profile({"dev", "dev-docker"})
public class RedisBroadcastDemoConfig {

    /** 演示用频道名 */
    public static final String BROADCAST_CHANNEL = "demo:broadcast:channel";

    /** 本实例收到的消息缓冲（仅演示用，最多保留最近 20 条） */
    private static final Deque<String> RECEIVED_MESSAGES = new ConcurrentLinkedDeque<>();

    /** 缓冲上限：避免长时间演示把内存撑大 */
    private static final int MAX_BUFFER_SIZE = 20;

    /**
     * Pub/Sub 订阅容器：应用启动时订阅 {@link #BROADCAST_CHANNEL}，
     * 收到消息后写入内存缓冲，供 Controller 通过接口读出来查看。
     */
    @Bean
    public RedisMessageListenerContainer redisBroadcastListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) -> {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            RECEIVED_MESSAGES.addFirst("[" + LocalTime.now().withNano(0) + "] " + body);
            while (RECEIVED_MESSAGES.size() > MAX_BUFFER_SIZE) {
                RECEIVED_MESSAGES.removeLast();
            }
        }, new ChannelTopic(BROADCAST_CHANNEL));
        return container;
    }

    /** 供 Controller 读取当前实例收到的消息（最新在前） */
    public static List<String> snapshotReceivedMessages() {
        return List.copyOf(RECEIVED_MESSAGES);
    }
}
