package com.aiexplorer.researchagent.application.service;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 任务执行消息消费者（场景⑥）——"工厂取单台"。
 *
 * <p>{@code @KafkaListener} —— "申请书 + 外包工"模式第 4 次见面
 * （@Transactional / @AuditLog / @Scheduled 同族）：Kafka 基础设施读到它 →
 * 启动消费者 → 消息到达时自动调本方法。</p>
 *
 * <p>★ 为什么敢直接在这里跑长任务（executeTask）——三层底气（思考题 2 的答案）：
 * <ol>
 *   <li>{@code executeTask} 自带的【状态守卫】（shouldStop——重复消费时终态任务直接跳过）；</li>
 *   <li>同一 Partition 内【串行消费】（同一条消息不会被两个消费者同时处理）；</li>
 *   <li>至少一次 + 幂等消费 = 事实上的"恰好一次"效果。</li>
 * </ol>
 * 注意：单条消息处理时长可能超过 Kafka 默认 max.poll.interval.ms（5 分钟）——
 * 已在 application.yml 调大至 1 小时（长任务护航），否则会被误判"消费者死亡"→ 重平衡 → 重复消费。</p>
 */
@Component
public class TaskExecutionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionConsumer.class);

    private final ResearchTaskOrchestrator researchTaskOrchestrator;

    public TaskExecutionConsumer(ResearchTaskOrchestrator researchTaskOrchestrator) {
        this.researchTaskOrchestrator = researchTaskOrchestrator;
    }

    /**
     * 消费"任务执行请求"——轻消息：只有 taskId；任务数据现场从数据库取（读到最新状态）。
     */
    @KafkaListener(topics = TaskExecutionProducer.TOPIC)
    public void onTaskExecutionRequested(String taskIdText) {
        log.info("[MQ] 收到任务执行消息 taskId={}", taskIdText);
        UUID taskId = UUID.fromString(taskIdText);
        researchTaskOrchestrator.executeTask(taskId); // 内部第一件事：状态守卫（shouldStop）
        log.info("[MQ] 任务执行消息处理完毕 taskId={}", taskIdText);
    }
}
