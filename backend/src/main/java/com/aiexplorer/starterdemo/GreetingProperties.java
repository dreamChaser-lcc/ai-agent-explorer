package com.aiexplorer.starterdemo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SP5 教学演示：mini-starter 的配置属性。
 *
 * 前缀 app.greeting，默认值写在字段上（不配置也能用——"约定优于配置"）。
 * 注意：本类在 com.aiexplorer.starterdemo 包，【不在】应用的 @ComponentScan 范围内。
 */
@ConfigurationProperties(prefix = "app.greeting")
public class GreetingProperties {

    /** 问候语前缀。 */
    private String prefix = "[auto] Hello, ";

    /** 问候语后缀。 */
    private String suffix = "!";

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}