package org.zeropointlauncher.launch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.zeropointlauncher.launch.json.JsonSupport;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ManifestFragment(
        String id,
        @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String inheritsFrom,
        @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String mainClass,
        @JsonDeserialize(using = JsonSupport.TextualOnlyDeserializer.class) String minecraftArguments,
        ArgumentsBlock arguments,
        @JsonSetter(nulls = Nulls.AS_EMPTY) List<Library> libraries
) {
    public ManifestFragment {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("清单缺少 id");
        }
        id = id.strip();
        inheritsFrom = blankToNull(inheritsFrom);
        mainClass = blankToNull(mainClass);
        minecraftArguments = blankToNull(minecraftArguments);
        libraries = libraries == null
                ? List.of()
                : libraries.stream().filter(Objects::nonNull).toList();
    }

    /**
     * 求值器唯一入口：1.13+ 的 arguments.game 非空时优先；否则按 vanilla 语义把
     * minecraftArguments 按空白切分（不解析引号，不依赖 shell）。
     */
    public List<LaunchArgument> gameArguments() {
        if (arguments != null && !arguments.game().isEmpty()) {
            return arguments.game();
        }
        if (minecraftArguments == null) {
            return List.of();
        }
        return Arrays.stream(minecraftArguments.split("\\s+"))
                .filter(token -> !token.isEmpty())
                .<LaunchArgument>map(LaunchArgument.Direct::new)
                .toList();
    }

    public List<LaunchArgument> jvmArguments() {
        return arguments == null ? List.of() : arguments.jvm();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public record ArgumentsBlock(
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<LaunchArgument> game,
            @JsonSetter(nulls = Nulls.AS_EMPTY) List<LaunchArgument> jvm
    ) {
        public ArgumentsBlock {
            game = game == null
                    ? List.of()
                    : game.stream().filter(Objects::nonNull).toList();
            jvm = jvm == null
                    ? List.of()
                    : jvm.stream().filter(Objects::nonNull).toList();
        }
    }
}
