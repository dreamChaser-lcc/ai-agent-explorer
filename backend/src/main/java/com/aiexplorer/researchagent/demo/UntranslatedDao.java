package com.aiexplorer.researchagent.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * J4/J7 异常翻译演示 B：【只标 @Component】的 DAO（对照组）。
 *
 * <p>代码与 {@link TranslatedDao} 逐字相同，唯一差异：注解是 @Component。
 * 没有 @Repository → Spring 不织入异常翻译代理 →
 * 抛出来的异常是 Hibernate/JPA 家族的（jakarta.persistence.PersistenceException），
 * 上层 catch Spring 家族会扑空。</p>
 */
@Component
public class UntranslatedDao {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 执行同一条必然失败的 SQL——但异常【不会被翻译】，原样往上抛。
     */
    public void executeBadSql() {
        entityManager
                .createNativeQuery("SELECT * FROM table_that_does_not_exist_xyz")
                .getResultList();
    }
}
