package org.zeropointlauncher.launch.parse;

import org.zeropointlauncher.launch.model.ManifestFragment;

import java.util.List;
import java.util.Objects;

/**
 * 带解析告警的清单读取结果。告警只描述读入边界发生的降级，不改变求值器的纯布尔契约。
 */
public record ManifestParseResult(ManifestFragment fragment, List<ParseWarning> warnings) {
    public ManifestParseResult {
        Objects.requireNonNull(fragment, "fragment");
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
