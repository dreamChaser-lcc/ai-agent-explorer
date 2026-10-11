package com.aiexplorer.researchagent.infrastructure.persistence.repository;

import com.aiexplorer.researchagent.infrastructure.persistence.entity.AuditLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 审计日志仓储（场景⑤）。
 *
 * <p>审计的使用形态是"只进不出"——MVP 只借 JpaRepository 的 save 写入；
 * 查询（审计界面 / 报表）留给未来的场景按需补自定义查询。</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
}
