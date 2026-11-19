package com.aiexplorer.researchagent.infrastructure.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 消费错误处理（M5 补：之前缺失 = 异常无限重投 → 分区卡死）。
 *
 * <p><b>没有它时会发生什么</b>：{@code @KafkaListener} 方法抛异常 →
 * 容器默认重试 10 次 → 仍失败抛回容器 → 重投 → 结合 max.poll.interval 超时 →
 * Rebalance → 又重投 → 死循环；该分区后续消息全部排队（毒消息瘫痪一个分区）。</p>
 *
 * <p><b>有了它之后</b>：</p>
 * <pre>
 *   异常 → 重试 3 次（间隔 1 秒）
 *        → 仍失败 → 投递到 "原topic.DLT"（死信主题）→ 主链路继续消费
 *        → IllegalArgumentException 等业务不可恢复异常【不重试】直接进 DLT
 * </pre>
 *
 * <p>Spring Boot 会自动把本 Bean（CommonErrorHandler）装配进
 * {@code kafkaListenerContainerFactory}，无需手写工厂。</p>
 */
@Configuration
public class KafkaErrorHandlingConfig {

    /** DLT 后缀：research-task-execution.DLT */
    public static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // 死信投递器：失败消息发到 原topic + .DLT（同分区号，便于排查）
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));

        // 重试策略：间隔 1 秒、最多重试 3 次；之后交给 recoverer 进 DLT
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));

        // 业务不可恢复的异常：不重试，直接进 DLT（例如非法 taskId 导致 UUID.fromString 抛错）
        handler.addNotRetryableExceptions(IllegalArgumentException.class);

        return handler;
    }
}
