package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.zeropointlauncher.launch.json.JsonSupport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 依赖库实体定义。
 * <p>
 * 与版本 JSON 的 library 对象同构；集合字段在紧凑构造函数中做防御性拷贝。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Library(
        @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String name,
        @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String url,
        Downloads downloads,
        Map<String, String> natives,
        @JsonDeserialize(contentUsing = JsonSupport.RuleDeserializer.class)
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<Rule> rules,
        Extract extract
) {
    public Library {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("libraries[].name 不能为空");
        }
        name = name.strip();
        url = blankToNull(url);
        natives = copyNonNull(natives);
        rules = rules == null
                ? List.of()
                : rules.stream().map(rule -> rule == null ? Rule.deny() : rule).toList();
    }

    public DownloadArtifact artifact() {
        return downloads == null ? null : downloads.artifact();
    }

    public Map<String, DownloadArtifact> classifiers() {
        return downloads == null ? Map.of() : downloads.classifiers();
    }

    public List<String> extractExcludes() {
        return extract == null ? List.of() : extract.exclude();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Downloads(
            DownloadArtifact artifact,
            @JsonSetter(nulls = Nulls.AS_EMPTY) Map<String, DownloadArtifact> classifiers
    ) {
        public Downloads {
            classifiers = copyNonNull(classifiers);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Extract(
            @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            @JsonSetter(nulls = Nulls.AS_EMPTY)
            List<String> exclude
    ) {
        public Extract {
            exclude = exclude == null ? List.of() : List.copyOf(exclude);
        }
    }

    /**
     * 单个可下载构件。path 是下载落盘布局的唯一硬依赖；sha1/size/url 允许缺失，
     * 由下载层决定是否跳过完整性校验或改走镜像/Maven 推导。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DownloadArtifact(
            @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String path,
            @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String sha1,
            @JsonDeserialize(using = JsonSupport.LenientSizeDeserializer.class) Long size,
            @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String url
    ) {
        public DownloadArtifact {
            if (path == null || path.isBlank()) {
                throw new IllegalArgumentException("downloads.artifact.path 不能为空");
            }
            path = path.strip();
            sha1 = blankToNull(sha1);
            size = size != null && size < 0 ? null : size;
            url = blankToNull(url);
        }

        public boolean hasIntegrity() {
            return sha1 != null && size != null && size > 0;
        }
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static <V> Map<String, V> copyNonNull(Map<String, V> source) {
        if (source == null) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, V>();
        source.forEach((key, value) -> {
            if (key != null && value != null) {
                result.put(key, value);
            }
        });
        return Map.copyOf(result);
    }
}
