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
 * 负责维护任务级别的 SSE 连接并推送实时事件。
 */
@Service
public class TaskProgressStreamService {

    private final Map<UUID, List<SseEmitter>> emittersByTaskId = new ConcurrentHashMap<>();

    /**
     * 为指定任务建立 SSE 订阅连接。
     */
    public SseEmitter subscribe(UUID taskId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByTaskId.computeIfAbsent(taskId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(exception -> removeEmitter(taskId, emitter));

        sendEvent(taskId, emitter, "connected", Map.of(
                "taskId", taskId,
                "message", "SSE 连接已建立"
        ));
        return emitter;
    }

    /**
     * 向任务对应的所有订阅连接广播事件。
     */
    public void publish(UUID taskId, String eventName, Object payload) {
        List<SseEmitter> emitters = emittersByTaskId.get(taskId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            sendEvent(taskId, emitter, eventName, payload);
        }
    }

    /**
     * 向单个订阅连接推送事件，失败时自动移除失效连接。
     */
    private void sendEvent(UUID taskId, SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException exception) {
            removeEmitter(taskId, emitter);
        }
    }

    /**
     * 移除已关闭或失效的 SSE 连接。
     */
    private void removeEmitter(UUID taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByTaskId.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByTaskId.remove(taskId);
        }
    }
}
