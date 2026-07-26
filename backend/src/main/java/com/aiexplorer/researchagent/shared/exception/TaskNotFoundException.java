package com.aiexplorer.researchagent.shared.exception;

import java.util.UUID;

/**
 * 表示请求的研究任务不存在。
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(UUID taskId) {
        super("Research task not found: " + taskId);
    }
}
