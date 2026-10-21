package com.aiexplorer.researchagent.infrastructure.tx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * SP4 教学演示：事务传播行为对比（REQUIRED vs REQUIRES_NEW）。可随时删除。
 *
 * 两组实验结构完全相同，唯一差别是内层方法的传播行为：
 *   outerRequired   + innerRequired   （默认 REQUIRED）→ 内外层同一条命 → 内层抛异常，整体回滚
 *   outerRequiresNew + innerRequiresNew（REQUIRES_NEW）→ 内层独立事务 → 内层回滚，外层照常提交
 *
 * @Lazy 注入自身代理：this 调用不走事务代理（SP2 已实测），必须用代理调用。
 */
@Service
public class TransactionDemoService {

    private static final Logger log = LoggerFactory.getLogger(TransactionDemoService.class);

    private final JdbcTemplate jdbcTemplate;
    private final TransactionDemoService self;

    public TransactionDemoService(JdbcTemplate jdbcTemplate, @Lazy TransactionDemoService self) {
        this.jdbcTemplate = jdbcTemplate;
        this.self = self;
    }

    // ==================== 场景 A：内外层都是 REQUIRED ====================

    /** 外层（默认 REQUIRED）：插一行 → 调内层（抛异常，被捕获）→ 再插一行。 */
    @Transactional
    public void outerRequired() {
        jdbcTemplate.update("INSERT INTO sp4_tx_demo(label) VALUES (?)", "outer-required");
        try {
            self.innerRequired();
        } catch (RuntimeException exception) {
            log.warn("[SP4-A] 外层捕获内层异常（REQUIRED）：{}", exception.getMessage());
        }
        jdbcTemplate.update("INSERT INTO sp4_tx_demo(label) VALUES (?)", "after-required");
    }

    /** 内层（默认 REQUIRED）：加入外层事务，然后故意抛异常。 */
    @Transactional
    public void innerRequired() {
        jdbcTemplate.update("INSERT INTO sp4_tx_demo(label) VALUES (?)", "inner-required");
        throw new IllegalStateException("inner-required 故意失败");
    }

    // ==================== 场景 B：内层改为 REQUIRES_NEW ====================

    /** 外层（默认 REQUIRED）：插一行 → 调内层（独立事务，抛异常被捕获）→ 再插一行。 */
    @Transactional
    public void outerRequiresNew() {
        jdbcTemplate.update("INSERT INTO sp4_tx_demo(label) VALUES (?)", "outer-new");
        try {
            self.innerRequiresNew();
        } catch (RuntimeException exception) {
            log.warn("[SP4-B] 外层捕获内层异常（REQUIRES_NEW）：{}", exception.getMessage());
        }
        jdbcTemplate.update("INSERT INTO sp4_tx_demo(label) VALUES (?)", "after-new");
    }

    /** 内层（REQUIRES_NEW）：新建独立事务，然后故意抛异常——只回滚自己。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void innerRequiresNew() {
        jdbcTemplate.update("INSERT INTO sp4_tx_demo(label) VALUES (?)", "inner-new");
        throw new IllegalStateException("inner-new 故意失败");
    }
}