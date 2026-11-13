package org.zeropointlauncher.parse;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.model.RuleContext;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;
import org.zeropointlauncher.launch.rule.RuleEvaluator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 夹具由官方 1.12.2 / 1.20.1 版本清单裁剪而来，只保留本解析器消费的字段。
 */
class RealManifestFixtureTest {
    private static final String LWJGL_PLATFORM =
            "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209";

    private final ManifestFragmentParser parser = new ManifestFragmentParser();

    @Test
    void parsesOfficialOneTwelveShape() throws Exception {
        var fragment = parse("versions/1.12.2-trimmed.json");

        assertEquals("1.12.2", fragment.id());
        assertEquals("net.minecraft.client.main.Main", fragment.mainClass());
        assertNull(fragment.arguments());
        assertEquals(18, fragment.gameArguments().size());
        assertEquals(new LaunchArgument.Direct("--username"),
                fragment.gameArguments().get(0));
        assertEquals(new LaunchArgument.Direct("${auth_player_name}"),
                fragment.gameArguments().get(1));
        assertEquals(4, fragment.libraries().size());

        var lwjglPlatform = fragment.libraries().get(1);
        assertEquals(LWJGL_PLATFORM, lwjglPlatform.name());
        assertEquals(3, lwjglPlatform.classifiers().size());
        assertEquals(2, lwjglPlatform.rules().size());
        assertNotNull(lwjglPlatform.artifact());
    }

    @Test
    void oneTwelvePlatformRulesKeepWindowsAndRemoveMacOs() throws Exception {
        var fragment = parse("versions/1.12.2-trimmed.json");
        var lwjglPlatform = fragment.libraries().stream()
                .filter(library -> LWJGL_PLATFORM.equals(library.name()))
                .findFirst()
                .orElseThrow();

        assertTrue(RuleEvaluator.evaluate(
                lwjglPlatform.rules(), context("windows", "x86_64", Map.of())));
        assertTrue(RuleEvaluator.evaluate(
                lwjglPlatform.rules(), context("linux", "x86_64", Map.of())));
        assertFalse(RuleEvaluator.evaluate(
                lwjglPlatform.rules(), context("osx", "x86_64", Map.of())));
        assertFalse(RuleEvaluator.evaluate(
                lwjglPlatform.rules(), context("osx", "arm64", Map.of())));
    }

    @Test
    void parsesOfficialOneTwentyShape() throws Exception {
        var fragment = parse("versions/1.20.1-trimmed.json");

        assertEquals("1.20.1", fragment.id());
        assertEquals("net.minecraft.client.main.Main", fragment.mainClass());
        assertEquals(28, fragment.gameArguments().size());
        assertEquals(11, fragment.jvmArguments().size());
        assertEquals(3, fragment.libraries().size());
        assertTrue(fragment.libraries().stream().allMatch(library -> library.artifact() != null));
    }

    @Test
    void oneTwentyArchitectureAndFeatureRulesExpandArguments() throws Exception {
        var fragment = parse("versions/1.20.1-trimmed.json");

        var x86Jvm = expand(fragment.jvmArguments(), context("windows", "x86", Map.of()));
        var x64Jvm = expand(fragment.jvmArguments(), context("windows", "x86_64", Map.of()));
        assertTrue(x86Jvm.contains("-Xss1M"));
        assertFalse(x64Jvm.contains("-Xss1M"));

        var resolutionOff = expand(fragment.gameArguments(),
                context("windows", "x86_64", Map.of()));
        assertFalse(resolutionOff.contains("--width"));
        assertFalse(resolutionOff.contains("${resolution_width}"));

        var resolutionOn = expand(fragment.gameArguments(),
                context("windows", "x86_64", Map.of("has_custom_resolution", true)));
        assertTrue(resolutionOn.contains("--width"));
        assertTrue(resolutionOn.contains("${resolution_width}"));
        assertTrue(resolutionOn.contains("--height"));
        assertTrue(resolutionOn.contains("${resolution_height}"));
    }

    @Test
    void sharedParserIsSafeForConcurrentUse() throws Exception {
        byte[] bytes = resource("versions/1.20.1-trimmed.json");

        var parsed = IntStream.range(0, 64)
                .parallel()
                .mapToObj(i -> parser.parse(bytes))
                .toList();

        assertTrue(parsed.stream().allMatch(fragment -> "1.20.1".equals(fragment.id())));
    }

    private ManifestFragment parse(String resource) throws IOException {
        return parser.parse(resource(resource));
    }

    private byte[] resource(String name) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }

    private static RuleContext context(
            String osName,
            String osArch,
            Map<String, Boolean> features
    ) {
        return new RuleContext(osName, osArch, Optional.empty(), features);
    }

    private static List<String> expand(
            List<LaunchArgument> arguments,
            RuleContext context
    ) {
        var expanded = new ArrayList<String>();
        for (LaunchArgument argument : arguments) {
            switch (argument) {
                case LaunchArgument.Direct direct -> expanded.add(direct.value());
                case LaunchArgument.Conditional conditional -> {
                    if (RuleEvaluator.evaluate(conditional.rules(), context)) {
                        expanded.addAll(conditional.value());
                    }
                }
            }
        }
        return List.copyOf(expanded);
    }
}
