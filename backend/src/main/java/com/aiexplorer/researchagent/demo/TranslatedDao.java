package com.aiexplorer.researchagent.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

/**
 * J4/J7 异常翻译演示 A：【标了 @Repository】的 DAO。
 *
 * <p>执行同一条必然失败的 SQL（查询不存在的表）。
 * 因为类上标了 @Repository，Spring 会给它织入"异常翻译代理"——
 * 抛出来的异常会被换成 Spring 家族（BadSqlGrammarException）。</p>
 *
 * <p>与 {@link UntranslatedDao}（@Component 版）逐字对照，唯一差异就是注解。</p>
 */
@Repository
public class TranslatedDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 执行一条必然失败的 SQL（表不存在）。
     * 底层（Hibernate/JDBC）会抛自家异常；本类因 @Repository 会被翻译成 Spring 异常。
     */
    public void executeBadSql() {
        entityManager
                .createNativeQuery("SELECT * FROM table_that_does_not_exist_xyz")
                .getResultList();
    }
}
