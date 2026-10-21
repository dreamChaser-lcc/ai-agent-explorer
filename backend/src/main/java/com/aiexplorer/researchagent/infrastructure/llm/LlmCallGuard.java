package com.aiexplorer.researchagent.infrastructure.llm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 下游调用治理层：超时 + 重试 + 降级判定（三件套）。
 *
 * 职责边界：
 *   - 本类只管"怎么调用"：限时、失败重试、判定是否需要降级；
 *   - 不关心"调用结果怎么用"：业务侧的降级兜底（模板计划）在 LlmPlanGenerator 里。
 *
 * 三个设计要点：
 *   1. 超时用 CompletableFuture#get(timeout)：主线程不会被下游无限拖住，
 *      超时后立刻进入重试判定（底层 HttpClient 也有超时，属于双保险）；
 *   2. 重试用"指数退避"：100ms → 200ms → 400ms……避免下游刚抖动就被连续打三下；
 *   3. 只重试"可重试错误"的简化版：当前对超时 / 5xx / 连接失败一律重试，
 *      真实生产应对 4xx（参数错、鉴权错）直接失败——这类错误重试一百次也没用。
 */
public class LlmCallGuard {

    private static final Logger log = LoggerFactory.getLogger(LlmCallGuard.class);

    /** 首次退避基数（毫秒），之后按 2 的幂增长。 */
    private static final long BASE_BACKOFF_MILLISECONDS = 100L;

    private final LlmClient llmClient;
    private final int timeoutSeconds;
    private final int maxRetries;

    /** 专用线程池：LLM 调用是 IO 密集型，不能占用业务线程/ForkJoinPool。 */
    private final ExecutorService callExecutor;

    public LlmCallGuard(LlmClient llmClient, int timeoutSeconds, int maxRetries) {
        this.llmClient = llmClient;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.maxRetries = Math.max(0, maxRetries);
        this.callExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread workerThread = new Thread(runnable, "llm-call-worker");
            workerThread.setDaemon(true);
            return workerThread;
        });
    }

    /**
     * 带治理地调用一次 LLM：超时 + 重试，全部失败则返回 degraded 结果（不抛异常）。
     *
     * @param promptText 提示词
     * @return 调用结果（成功内容 或 降级原因）
     */
    public LlmCallResult call(String promptText) {
        long startMilliseconds = System.currentTimeMillis();
        int attemptCount = 0;
        String lastFailureReason = null;

        // 1 次初始 + maxRetries 次重试
        for (int attemptIndex = 0; attemptIndex <= maxRetries; attemptIndex++) {
            attemptCount++;
            try {
                String contentText = callOnceWithTimeout(promptText);
                long elapsed = System.currentTimeMillis() - startMilliseconds;
                log.info("LLM 调用成功：实现={}，第 {} 次尝试，耗时 {} ms",
                        llmClient.describeImplementation(), attemptCount, elapsed);
                return LlmCallResult.success(contentText, attemptCount, elapsed);
            } catch (Exception exception) {
                lastFailureReason = describeFailure(exception);
                log.warn("LLM 调用失败：第 {}/{} 次尝试，原因：{}",
                        attemptCount, maxRetries + 1, lastFailureReason);
                if (attemptIndex < maxRetries) {
                    sleepQuietly(BASE_BACKOFF_MILLISECONDS << attemptIndex);
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startMilliseconds;
        log.error("LLM 调用全部失败（尝试 {} 次，耗时 {} ms），进入降级：{}",
                attemptCount, elapsed, lastFailureReason);
        return LlmCallResult.degraded(lastFailureReason, attemptCount, elapsed);
    }

    /** 单次调用 + 业务级超时（超时抛 TimeoutException，交给外层重试）。 */
    private String callOnceWithTimeout(String promptText) throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(
                () -> llmClient.complete(promptText), callExecutor);
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            future.cancel(true);
            throw new TimeoutException("调用超过 " + timeoutSeconds + " 秒未返回");
        }
    }

    private String describeFailure(Exception exception) {
        if (exception instanceof TimeoutException) {
            return "超时：" + exception.getMessage();
        }
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getClass().getSimpleName() + "：" + cause.getMessage();
    }

    private void sleepQuietly(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
