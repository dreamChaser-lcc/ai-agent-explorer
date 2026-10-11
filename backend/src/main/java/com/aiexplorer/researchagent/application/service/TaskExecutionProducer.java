package com.aiexplorer.researchagent.application.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 任务执行消息生产者（场景⑥）——"下单台"。
 *
 * <p>职责只有一件事：把"某任务需要执行"这件事写成一条消息（挂上传送带）。</p>
 *
 * <p>★ 轻消息原则：只发 taskId（不带任务内容）——
 * 任务数据永远以数据库为准（"任务活在数据库里，消息只是敲门砖"——场景④ 思想续集）。
 * 好处：消息小、无冗余、消费时读到的永远是最新状态。</p>
 */
@Service
public class TaskExecutionProducer {

    /** Topic 名（消费者共用该常量——避免两处写死字符串漂移）。 */
    public static final String TOPIC = "research-task-execution";

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TaskExecutionProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 发布"任务执行请求"消息（调用即返回——不等执行完）。
     *
     * <p>key = taskId → 同一任务的消息进同一分区（分区内串行——思考题 2 的"串行消费"保障）。</p>
     */
    public void publish(UUID taskId) {
        String payload = taskId.toString();
        kafkaTemplate.send(TOPIC, payload, payload); // send(topic, key, value)
        log.info("[MQ] 已发送任务执行消息 topic={} taskId={}", TOPIC, payload);
    }
}
