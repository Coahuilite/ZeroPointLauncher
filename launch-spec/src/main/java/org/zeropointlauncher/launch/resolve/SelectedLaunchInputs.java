package org.zeropointlauncher.launch.resolve;

import org.zeropointlauncher.launch.model.Library;

import java.util.List;

/**
 * 规则筛选后的启动输入快照。三个列表均为不可变视图，保留原始声明顺序与重复项。
 */
public record SelectedLaunchInputs(
        List<Library> libraries,
        List<String> jvmTokens,
        List<String> gameTokens
) {
    public SelectedLaunchInputs {
        libraries = libraries == null ? List.of() : List.copyOf(libraries);
        jvmTokens = jvmTokens == null ? List.of() : List.copyOf(jvmTokens);
        gameTokens = gameTokens == null ? List.of() : List.copyOf(gameTokens);
    }
}
