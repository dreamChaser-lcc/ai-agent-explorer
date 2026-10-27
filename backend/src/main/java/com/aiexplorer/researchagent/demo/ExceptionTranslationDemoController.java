package com.aiexplorer.researchagent.demo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * J4/J7 异常翻译演示（仅 dev）：四个端点对照 @Repository 有无的差异。
 *
 * <p>两个 DAO 代码完全相同（执行一条必然失败的 SQL），唯一差异是注解：
 * TranslatedDao 标 @Repository，UntranslatedDao 标 @Component。</p>
 *
 * <p>四端点对比：</p>
 * <ul>
 *   <li>① /repo/class   —— 有 @Repository，Controller 里 catch，返回【异常类名 + cause 链】</li>
 *   <li>② /plain/class  —— 无 @Repository，同样 catch，对照类名</li>
 *   <li>③ /repo/http    —— 有 @Repository，不 catch，走全局处理器（应为 400）</li>
 *   <li>④ /plain/http   —— 无 @Repository，不 catch，落 Exception 兜底（应为 500）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/demo/translate")
@Profile("dev")
@Tag(name = "异常翻译演示（教学）", description = "对照 @Repository 有无时，异常类型与 HTTP 状态的差异")
public class ExceptionTranslationDemoController {

    private final TranslatedDao translatedDao;
    private final UntranslatedDao untranslatedDao;

    public ExceptionTranslationDemoController(
            TranslatedDao translatedDao, UntranslatedDao untranslatedDao) {
        this.translatedDao = translatedDao;
        this.untranslatedDao = untranslatedDao;
    }

    @Operation(summary = "① 有 @Repository：看抛出的异常类名（应为 Spring 家族 BadSqlGrammarException）")
    @GetMapping("/repo/class")
    public Map<String, Object> repoClass() {
        try {
            translatedDao.executeBadSql();
            return Map.of("result", "不应该到达这里（SQL 必然失败）");
        } catch (Exception e) {
            return describe(e);
        }
    }

    @Operation(summary = "② 无 @Repository（@Component）：看异常类名（应为 jakarta.persistence 家族，未翻译）")
    @GetMapping("/plain/class")
    public Map<String, Object> plainClass() {
        try {
            untranslatedDao.executeBadSql();
            return Map.of("result", "不应该到达这里（SQL 必然失败）");
        } catch (Exception e) {
            return describe(e);
        }
    }

    @Operation(summary = "③ 有 @Repository：不 catch，走全局处理器（应返回 400 BAD_SQL）")
    @GetMapping("/repo/http")
    public void repoHttp() {
        translatedDao.executeBadSql();
    }

    @Operation(summary = "④ 无 @Repository：不 catch，落 Exception 兜底（应返回 500）")
    @GetMapping("/plain/http")
    public void plainHttp() {
        untranslatedDao.executeBadSql();
    }

    /** 展示异常类名 + 完整 cause 链（看清"翻译前/后"的家族）。 */
    private Map<String, Object> describe(Exception e) {
        List<String> chain = new ArrayList<>();
        Throwable current = e;
        while (current != null) {
            chain.add(current.getClass().getName());
            current = current.getCause();
        }
        return Map.of(
                "exceptionClass", e.getClass().getName(),
                "causeChain", chain,
                "message", String.valueOf(e.getMessage()));
    }
}
