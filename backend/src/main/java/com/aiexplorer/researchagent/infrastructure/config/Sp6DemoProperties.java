package com.aiexplorer.researchagent.infrastructure.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SP6 教学演示：配置优先级实验载体（可删）。
 *
 * 同一个 sp6.demo.name，会在四个地方被设置：
 *   主 yml（from-main-yml） / dev yml（from-dev-yml） / 环境变量（SP6_DEMO_NAME） / 命令行（--sp6.demo.name）
 * 启动后由 Sp6DemoRunner 打印"最终谁赢了"。
 */
@ConfigurationProperties(prefix = "sp6.demo")
public class Sp6DemoProperties {

    /** 配置名——用于观察优先级。 */
    private String name = "code-default";

    /** 来源标记。 */
    private String source = "code-default";

    /** 集合绑定演示。 */
    private List<String> tags = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}