package org.zeropointlauncher.launch.parse;

import java.util.Objects;

/**
 * 单条解析告警。求值器不消费告警，只由清单读入边界暴露给调用方。
 */
public record ParseWarning(ParseWarningCode code, String message) {
    public ParseWarning {
        Objects.requireNonNull(code, "code");
        message = message == null ? "" : message;
    }
}
