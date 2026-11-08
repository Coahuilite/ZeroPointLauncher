package org.zeropointlauncher.launch.parse;

/**
 * 清单解析边界产生的告警类别。
 */
public enum ParseWarningCode {
    /** 规则本身或其已声明条件无法按契约解析。 */
    MALFORMED_RULE,
    /** 规则声明了当前实现不支持的 OS 条件。 */
    UNKNOWN_RULE_CONDITION
}
