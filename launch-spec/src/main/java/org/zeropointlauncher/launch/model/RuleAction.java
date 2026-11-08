package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 版本清单中的规则动作。
 */
public enum RuleAction {
    ALLOW,
    DISALLOW;

    /**
     * JSON 动作词只有 {@code allow} 会启用规则；缺失、未知大小写之外的值统一按保守的
     * {@link #DISALLOW} 处理。
     */
    @JsonCreator
    public static RuleAction fromJson(String value) {
        return "allow".equalsIgnoreCase(value) ? ALLOW : DISALLOW;
    }
}
