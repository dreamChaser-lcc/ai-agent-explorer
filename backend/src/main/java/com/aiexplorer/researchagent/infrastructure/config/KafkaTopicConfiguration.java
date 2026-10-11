package com.aiexplorer.researchagent.infrastructure.config;

import com.aiexplorer.researchagent.application.service.TaskExecutionProducer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka Topic 配置（场景⑥）。
 *
 * <p>用 {@code @Bean NewTopic} 声明主题——应用启动时 Spring 的 KafkaAdmin 自动创建
 * （比"手工在 broker 上建 / 靠 broker 自动建"更工程化：主题定义随代码走、可版本化）。</p>
 */
@Configuration
public class KafkaTopicConfiguration {

    @Bean
    public NewTopic researchTaskExecutionTopic() {
        return TopicBuilder.name(TaskExecutionProducer.TOPIC)
                .partitions(3)  // 3 个分区：消费者并行度的上限（同 taskId 的消息按 key 哈希进固定分区——同任务有序）
                .replicas(1)    // 开发环境单节点——副本数 1（生产集群需 >= 3 保高可用）
                .build();
    }
}
