package org.zeropointlauncher.parse;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;

import java.io.IOException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 夹具由官方 1.12.2 / 1.20.1 版本清单裁剪而来，只保留本解析器消费的字段。
 */
class RealManifestFixtureTest {
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
        assertEquals("org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209", lwjglPlatform.name());
        assertEquals(3, lwjglPlatform.classifiers().size());
        assertEquals(2, lwjglPlatform.rules().size());
        assertNotNull(lwjglPlatform.artifact());
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
}
