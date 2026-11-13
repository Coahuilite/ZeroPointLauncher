package org.zeropointlauncher.launch.resolve;

import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.RuleContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 旧版（1.7.10 及更早）native 选择器。
 * <p>
 * 纯函数：只消费已通过规则筛选的库列表与目标环境事实，不读取磁盘、不记录日志。
 * 缺失映射或无法安全选择时通过 {@link NativeSelectionIssue} 表达，不抛出阻断异常。
 */
public final class LegacyNativeSelector {
    private static final String ARCH_PLACEHOLDER = "${arch}";
    private static final Set<String> KNOWN_OS_KEYS = Set.of("windows", "linux", "osx");

    private LegacyNativeSelector() {
    }

    /**
     * 按 {@code natives} 映射计算目标平台 classifier。
     *
     * @param libraries 已通过版本规则筛选的库，保持原始顺序
     * @param context   目标环境；缺少 OS 事实会记录 issue，已知 OS 不在映射中则静默跳过
     * @return 解压请求与选择问题
     */
    public static NativeSelectionResult select(List<Library> libraries, RuleContext context) {
        var requests = new ArrayList<NativeExtractRequest>();
        var issues = new ArrayList<NativeSelectionIssue>();

        if (libraries == null || libraries.isEmpty()) {
            return new NativeSelectionResult(requests, issues);
        }

        String osKey = context == null ? null : context.osName();
        String osArch = context == null ? null : context.osArch();

        for (Library library : libraries) {
            if (library == null) {
                continue;
            }

            Map<String, String> natives = library.natives();
            if (natives == null || natives.isEmpty()) {
                continue;
            }

            if (osKey == null || osKey.isBlank() || !KNOWN_OS_KEYS.contains(osKey)) {
                issues.add(new NativeSelectionIssue(
                        library.name(),
                        osKey == null ? "" : osKey,
                        osIssueMessage(osKey)));
                continue;
            }

            String mapping = natives.get(osKey);
            if (mapping == null) {
                continue;
            }

            String classifier = mapping;
            if (mapping.contains(ARCH_PLACEHOLDER)) {
                String bits = archBits(osArch);
                if (bits == null) {
                    issues.add(new NativeSelectionIssue(
                            library.name(),
                            osKey,
                            "当前架构 " + displayArch(osArch)
                                    + " 缺乏旧版 native 兼容依据，无法展开 " + mapping));
                    continue;
                }
                classifier = mapping.replace(ARCH_PLACEHOLDER, bits);
            }

            if (classifier == null || classifier.isBlank() || hasUnresolvedPlaceholder(classifier)) {
                issues.add(new NativeSelectionIssue(
                        library.name(),
                        osKey,
                        "展开后的 classifier '" + classifier
                                + "' 为空或仍包含未识别占位符"));
                continue;
            }

            Map<String, Library.DownloadArtifact> classifiers = library.classifiers();
            if (classifiers == null || classifiers.isEmpty()) {
                requests.add(new NativeExtractRequest(
                        library, classifier, Optional.empty(), library.extractExcludes()));
                continue;
            }

            Library.DownloadArtifact artifact = classifiers.get(classifier);
            if (artifact == null) {
                issues.add(new NativeSelectionIssue(
                        library.name(),
                        osKey,
                        "classifier " + classifier
                                + " 缺少对应的 downloads.classifiers 条目"));
                continue;
            }

            requests.add(new NativeExtractRequest(
                    library, classifier, Optional.of(artifact), library.extractExcludes()));
        }

        return new NativeSelectionResult(requests, issues);
    }

    private static String archBits(String osArch) {
        return switch (osArch == null ? "" : osArch) {
            case "x86" -> "32";
            case "x86_64" -> "64";
            default -> null;
        };
    }

    private static String displayArch(String osArch) {
        return osArch == null ? "" : osArch;
    }

    private static String osIssueMessage(String osKey) {
        if (osKey == null || osKey.isBlank()) {
            return "缺少判定目标 OS 所需事实，无法选择旧版 native";
        }
        return "目标 OS '" + osKey + "' 不在已知范围内，无法选择旧版 native";
    }

    private static boolean hasUnresolvedPlaceholder(String classifier) {
        return classifier.contains("${");
    }
}
