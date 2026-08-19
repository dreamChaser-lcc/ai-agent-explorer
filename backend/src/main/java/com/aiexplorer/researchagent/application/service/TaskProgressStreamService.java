package com.aiexplorer.researchagent.application.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 任务进度实时推送服务（SSE）。
 *
 * 职责：
 *   1. 维护每个任务对应的 SSE 连接列表（一个任务可以被多个前端订阅）
 *   2. 订阅成功时发送 "connected" 确认事件
 *   3. 发布事件时向所有订阅连接广播
 *   4. 连接断开、超时、出错时自动清理
 *
 * 前端用法：
 *   const eventSource = new EventSource("http://localhost:8080/api/tasks/{id}/stream");
 *   eventSource.addEventListener("plan-generated", (e) => { ... });
 *   eventSource.addEventListener("step-completed", (e) => { ... });
 */
@Service // 注册为 Spring Bean
public class TaskProgressStreamService {

    /**
     * 任务 ID → SSE 连接列表 的映射表。
     *
     * ConcurrentHashMap：线程安全的 HashMap，多线程同时读写不需要额外加锁。
     * CopyOnWriteArrayList：写操作（add/remove）会复制整个数组，
     *                       读操作直接读原数组不加锁，适合读多写少的场景。
     *
     * 一个 taskId 对应多个 SseEmitter，因为可能存在多个前端页面同时订阅同一个任务。
     */
    private final Map<UUID, List<SseEmitter>> emittersByTaskId = new ConcurrentHashMap<>();

    /**
     * 为指定任务建立 SSE 订阅连接。
     *
     * @param taskId 任务 UUID
     * @return SseEmitter（Controller 直接返回给前端）
     */
    public SseEmitter subscribe(UUID taskId) {
        // 创建 SSE 发射器，0L = 永不超时（也可以设毫秒值如 30 * 60 * 1000L = 30 分钟）
        SseEmitter emitter = new SseEmitter(0L);

        // computeIfAbsent：如果 key 不存在就创建一个新的 CopyOnWriteArrayList
        // 然后把 emitter 加入列表
        emittersByTaskId.computeIfAbsent(taskId,
                key -> new CopyOnWriteArrayList<>()).add(emitter);

        // 注册三个回调，在连接断开时自动清理
        emitter.onCompletion(() -> removeEmitter(taskId, emitter));  // 前端主动关闭
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));     // 连接超时
        emitter.onError(exception -> removeEmitter(taskId, emitter)); // 传输异常

        // 建立连接后立即发送一个确认事件，前端可据此确认连接状态
        sendEvent(taskId, emitter, "connected", Map.of(
                "taskId", taskId,
                "message", "SSE 连接已建立"
        ));
        return emitter;
    }

    /**
     * 向指定任务的所有订阅连接广播事件。
     *
     * 编排器中各处调用此方法：
     *   - 任务状态变化时 → publish(taskId, "task-status", ...)
     *   - 步骤开始时     → publish(taskId, "step-running", ...)
     *   - 步骤完成时     → publish(taskId, "step-completed", ...)
     *   - 计划生成时     → publish(taskId, "plan-generated", ...)
     *
     * @param taskId    任务 UUID
     * @param eventName 事件名称（前端用 addEventListener 匹配）
     * @param payload   事件数据（会被自动序列化为 JSON）
     */
    public void publish(UUID taskId, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByTaskId.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return; // 没有订阅者，不发送（节省资源）
        }

        // 遍历所有连接，逐个发送
        for (SseEmitter emitter : emitters) {
            sendEvent(taskId, emitter, eventName, payload);
        }
    }

    /**
     * 向单个订阅连接推送事件。
     * 如果发送失败（连接已断开），自动移除该连接。
     */
    private void sendEvent(UUID taskId, SseEmitter emitter,
                            String eventName, Object payload) {
        try {
            // emitter.send() 是核心方法：
            //   SseEmitter.event() 创建一个事件构建器
            //   .name(eventName) 设置事件名（前端用 addEventListener 监听）
            //   .data(payload) 设置事件数据（自动转为 JSON）
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(payload));
        } catch (IOException exception) {
            removeEmitter(taskId, emitter); // 发送失败 = 连接已失效，清理掉
        }
    }

    /**
     * 移除已关闭或失效的 SSE 连接。
     * 如果某个任务的所有连接都已移除，也清理该任务的 key。
     */
    private void removeEmitter(UUID taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByTaskId.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter); // 从列表中移除
        if (emitters.isEmpty()) {
            emittersByTaskId.remove(taskId); // 列表空了就清理 key
        }
    }
}
