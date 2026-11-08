package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

/**
 * 规则中的操作系统条件。空 {@link Optional} 表示该条件未声明。
 * <p>
 * 非空字符串按原文保留，不做空白裁剪或归一化；空白 name/arch 由 JSON 边界判定为畸形，
 * 空白 version 仍是已声明的正则条件，不会退化为“未声明”。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsRule(
        Optional<String> name,
        Optional<String> arch,
        Optional<String> version
) {
    public OsRule {
        name = name == null ? Optional.empty() : name;
        arch = arch == null ? Optional.empty() : arch;
        version = version == null ? Optional.empty() : version;
    }
}
