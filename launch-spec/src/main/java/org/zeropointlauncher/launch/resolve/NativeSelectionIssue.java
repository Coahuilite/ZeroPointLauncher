package org.zeropointlauncher.launch.resolve;

/**
 * 旧版 native 选择过程中无法安全决定解压目标的诊断记录。
 */
public record NativeSelectionIssue(
        String libraryName,
        String targetOsKey,
        String message
) {
    public NativeSelectionIssue {
        libraryName = libraryName == null ? "" : libraryName;
        targetOsKey = targetOsKey == null ? "" : targetOsKey;
        message = message == null ? "" : message;
    }
}
