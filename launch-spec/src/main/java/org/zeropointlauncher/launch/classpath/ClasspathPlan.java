package org.zeropointlauncher.launch.classpath;

import java.util.List;
import java.util.Optional;

/**
 * 有序 classpath 逻辑计划。
 * <p>
 * 条目均为相对路径，保留调用方给定的声明顺序与重复项；不包含平台分隔符。
 * {@link ClasspathAssembler} 产出的计划最后一项固定为客户端 JAR。
 */
public record ClasspathPlan(List<String> entries) {
    public ClasspathPlan {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** 以输出语义命名的等价视图。 */
    public List<String> relativePaths() {
        return entries;
    }

    /** 装配器产出的计划最后一项；空计划返回空。 */
    public Optional<String> clientJar() {
        return entries.isEmpty()
                ? Optional.empty()
                : Optional.of(entries.getLast());
    }
}
