package org.zeropointlauncher.launch.resolve;

import java.util.List;

/**
 * 旧版 native 选择结果。请求与问题都按输入库的声明顺序排列。
 */
public record NativeSelectionResult(
        List<NativeExtractRequest> requests,
        List<NativeSelectionIssue> issues
) {
    public NativeSelectionResult {
        requests = requests == null ? List.of() : List.copyOf(requests);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
