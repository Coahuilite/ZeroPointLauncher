package org.zeropointlauncher.launch.model;

import java.util.Map;
import java.util.Optional;

/**
 * 规则求值时使用的宿主事实快照。
 * <p>
 * {@code osName} 与 {@code osArch} 由调用方预先归一化；集合字段防御性拷贝为不可变 Map。
 * 缺失的版本或功能开关以空值表示，不会隐式补全。
 */
public record RuleContext(
        String osName,
        String osArch,
        Optional<String> osVersion,
        Map<String, Boolean> features
) {
    public RuleContext {
        osName = osName == null ? "" : osName;
        osArch = osArch == null ? "" : osArch;
        osVersion = osVersion == null
                ? Optional.empty()
                : osVersion.map(String::strip).filter(version -> !version.isEmpty());
        features = features == null ? Map.of() : Map.copyOf(features);
    }
}
