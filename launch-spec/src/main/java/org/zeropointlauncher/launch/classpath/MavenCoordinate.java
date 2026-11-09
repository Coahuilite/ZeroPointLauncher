package org.zeropointlauncher.launch.classpath;

import java.util.Objects;
import java.util.Optional;

/**
 * 版本清单中声明的 Maven 坐标。
 * <p>
 * 纯值对象：只描述坐标本身与标准仓库相对布局，不访问磁盘、不拼接平台路径分隔符。
 */
public record MavenCoordinate(
        String groupId,
        String artifactId,
        String version,
        Optional<String> classifier,
        String extension
) {
    private static final String DEFAULT_EXTENSION = "jar";

    public MavenCoordinate {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(classifier, "classifier");
        Objects.requireNonNull(extension, "extension");
    }

    /**
     * 解析形如 {@code group:artifact:version[:classifier][@extension]} 的坐标字符串。
     * <p>
     * 扩展名缺省为 {@code jar}；空组件、缺少必需字段或多余字段都会抛出
     * {@link IllegalArgumentException}，避免把错误坐标静默推导成无效路径。
     */
    public static MavenCoordinate parse(String rawCoordinate) {
        Objects.requireNonNull(rawCoordinate, "rawCoordinate");

        String raw = rawCoordinate.strip();
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Maven 坐标不能为空");
        }

        String extension = DEFAULT_EXTENSION;
        String coordinatePart = raw;
        int extensionSeparator = raw.lastIndexOf('@');
        if (extensionSeparator >= 0) {
            coordinatePart = raw.substring(0, extensionSeparator);
            extension = raw.substring(extensionSeparator + 1).strip();
            if (extension.isEmpty()) {
                throw new IllegalArgumentException("Maven 坐标扩展名不能为空: " + rawCoordinate);
            }
            if (coordinatePart.indexOf('@') >= 0) {
                throw new IllegalArgumentException(
                        "Maven 坐标只能包含一个扩展名分隔符 '@': " + rawCoordinate);
            }
        }

        String[] parts = coordinatePart.split(":", -1);
        if (parts.length < 3 || parts.length > 4) {
            throw new IllegalArgumentException(
                    "Maven 坐标必须为 group:artifact:version[:classifier][@extension]: "
                            + rawCoordinate);
        }

        String groupId = requireComponent(parts[0], "groupId", rawCoordinate);
        String artifactId = requireComponent(parts[1], "artifactId", rawCoordinate);
        String version = requireComponent(parts[2], "version", rawCoordinate);
        Optional<String> classifier = parts.length == 4
                ? Optional.of(requireComponent(parts[3], "classifier", rawCoordinate))
                : Optional.empty();

        return new MavenCoordinate(groupId, artifactId, version, classifier, extension);
    }

    /**
     * 推导标准 Maven 仓库相对路径，始终使用 {@code /} 分隔，由宿主完成平台编码。
     */
    public String toRelativePath() {
        var path = new StringBuilder();
        path.append(groupId.replace('.', '/'))
                .append('/').append(artifactId)
                .append('/').append(version)
                .append('/').append(artifactId)
                .append('-').append(version);
        classifier.ifPresent(value -> path.append('-').append(value));
        path.append('.').append(extension);
        return path.toString();
    }

    private static String requireComponent(String value, String component, String rawCoordinate) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(
                    "Maven 坐标缺少 " + component + ": " + rawCoordinate);
        }
        return stripped;
    }
}
