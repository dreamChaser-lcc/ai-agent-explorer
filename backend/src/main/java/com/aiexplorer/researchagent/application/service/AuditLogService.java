package com.aiexplorer.researchagent.application.service;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.AuditLogEntity;
import com.aiexplorer.researchagent.infrastructure.persistence.repository.AuditLogRepository;
import com.aiexplorer.researchagent.shared.enums.AuditResult;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志服务（场景⑤ 主角之三）——真正"写库的人"。
 *
 * <p>切面管"时机"（什么时候记），本服务管"记录"（怎么记、怎么落库）——
 * 各司其职，将来换存储（文件 / MQ）只动这里。</p>
 *
 * <p>★ 事务设计（关键）：用 {@code REQUIRES_NEW} 开【独立新事务】落库——
 * 业务事务回滚时（方法抛异常），审计记录【不会被一起卷走】。
 * 这正是"失败也要留痕"能成立的底层保障：业务回滚 → 失败记录照旧落库。</p>
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /** MVP 简化：操作者先固定；正式方案 = 从用户/安全上下文（如 ThreadLocal）取。 */
    private static final String CURRENT_ACTOR = "demo-user";

    /** MVP 简化：操作对象类型先固定（未来可扩展为不同业务对象）。 */
    private static final String TARGET_TYPE = "RESEARCH_TASK";

    /** 错误信息落库前的安全长度（列宽 512——超长截断，防止插入直接失败）。 */
    private static final int ERROR_MESSAGE_MAX = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** 记录成功操作。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String action, String targetId) {
        save(action, targetId, AuditResult.SUCCESS, null);
    }

    /** 记录失败操作（失败也要留痕——审计的硬要求）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String targetId, Throwable exception) {
        String message = exception == null ? null : exception.getMessage();
        save(action, targetId, AuditResult.FAILED, truncate(message));
    }

    private void save(String action, String targetId, AuditResult result, String errorMessage) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActor(CURRENT_ACTOR);
        entity.setAction(action);
        entity.setTargetType(TARGET_TYPE);
        entity.setTargetId(targetId);
        entity.setResult(result);
        entity.setErrorMessage(errorMessage);
        entity.setCreatedAt(OffsetDateTime.now()); // 不继承 BaseTimeEntity，创建时间手动填
        auditLogRepository.save(entity);

        // 观测窗：实测时在应用控制台能看到"审计落库"的现场（谁 / 干了什么 / 结果）
        log.info("[审计] actor={} action={} target={} result={}",
                CURRENT_ACTOR, action, targetId, result);
    }

    private String truncate(String text) {
        if (text == null || text.length() <= ERROR_MESSAGE_MAX) {
            return text;
        }
        return text.substring(0, ERROR_MESSAGE_MAX) + "…";
    }
}
