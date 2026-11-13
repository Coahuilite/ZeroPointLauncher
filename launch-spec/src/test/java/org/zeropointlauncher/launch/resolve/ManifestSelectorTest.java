package org.zeropointlauncher.launch.resolve;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.LaunchArgument;
import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.model.OsRule;
import org.zeropointlauncher.launch.model.Rule;
import org.zeropointlauncher.launch.model.RuleAction;
import org.zeropointlauncher.launch.model.RuleContext;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ManifestSelectorTest {
    private static final String JINPUT = "net.java.jinput:jinput-platform:2.0.5";

    private final ManifestFragmentParser parser = new ManifestFragmentParser();

    @Test
    void oneTwelveLibrariesFollowPlatformRules() throws Exception {
        var fragment = fixture("versions/1.12.2-trimmed.json");

        var windows = selectedLibraries(fragment, "windows");
        assertEquals(List.of(
                "net.sf.jopt-simple:jopt-simple:5.0.3",
                "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209",
                JINPUT
        ), names(windows));

        var linux = selectedLibraries(fragment, "linux");
        assertEquals(names(windows), names(linux));

        var macOs = selectedLibraries(fragment, "osx");
        assertEquals(List.of(
                "net.sf.jopt-simple:jopt-simple:5.0.3",
                JINPUT,
                "ca.weblite:java-objc-bridge:1.0.0"
        ), names(macOs));

        for (var selected : List.of(windows, linux, macOs)) {
            var jinput = selected.stream()
                    .filter(library -> JINPUT.equals(library.name()))
                    .findFirst()
                    .orElseThrow();
            assertNull(jinput.artifact());
            assertEquals(3, jinput.classifiers().size());
            assertEquals(Map.of(
                    "linux", "natives-linux",
                    "osx", "natives-osx",
                    "windows", "natives-windows"
            ), jinput.natives());
        }
    }

    @Test
    void oneTwentyLibrariesFollowPlatformRules() throws Exception {
        var fragment = fixture("versions/1.20.1-trimmed.json");

        assertEquals(List.of("com.google.code.gson:gson:2.10"),
                names(selectedLibraries(fragment, "windows")));

        assertEquals(List.of(
                "com.google.code.gson:gson:2.10",
                "org.lwjgl:lwjgl:3.3.1:natives-linux",
                "io.netty:netty-transport-native-epoll:4.1.82.Final:linux-x86_64"
        ), names(selectedLibraries(fragment, "linux")));
    }

    @Test
    void oneTwentyJvmTokensRespectArchitecture() throws Exception {
        var fragment = fixture("versions/1.20.1-trimmed.json");

        var x86 = ManifestSelector.select(fragment, context("windows", "x86")).jvmTokens();
        var x64 = ManifestSelector.select(fragment, context("windows", "x86_64")).jvmTokens();

        assertTrue(x86.contains("-Xss1M"));
        assertFalse(x64.contains("-Xss1M"));

        int cp = x64.indexOf("-cp");
        assertTrue(cp >= 0);
        assertEquals("${classpath}", x64.get(cp + 1));
    }

    @Test
    void oneTwentyCustomResolutionExpandsConsecutively() throws Exception {
        var fragment = fixture("versions/1.20.1-trimmed.json");

        var enabled = ManifestSelector.select(fragment,
                context("windows", "x86_64", Map.of("has_custom_resolution", true)))
                .gameTokens();
        int width = enabled.indexOf("--width");
        assertTrue(width >= 0);
        assertEquals(List.of(
                "--width", "${resolution_width}",
                "--height", "${resolution_height}"
        ), enabled.subList(width, width + 4));

        var disabled = ManifestSelector.select(fragment,
                context("windows", "x86_64", Map.of("has_custom_resolution", false)))
                .gameTokens();
        assertFalse(disabled.contains("--width"));
        assertFalse(disabled.contains("${resolution_width}"));
        assertFalse(disabled.contains("--height"));
        assertFalse(disabled.contains("${resolution_height}"));
    }

    @Test
    void inlineSelectionPreservesOrderAndEdgeCases() {
        var fixture = inlineFixture();
        var selected = ManifestSelector.select(fixture.fragment(), context("windows", "x86_64"));

        assertEquals(List.of(
                fixture.always().name(),
                fixture.windowsOnly().name(),
                fixture.always().name()
        ), names(selected.libraries()));
        assertSame(fixture.always(), selected.libraries().get(0));
        assertSame(fixture.windowsOnly(), selected.libraries().get(1));
        assertSame(fixture.always(), selected.libraries().get(2));
        assertFalse(selected.libraries().contains(fixture.osxOnly()));

        assertEquals(List.of(
                "-Xmx2G",
                "-Dname=Demo Player",
                "-Xenabled",
                "-Xmx2G"
        ), selected.jvmTokens());

        assertEquals(List.of(
                "--first",
                "--enabled",
                "Demo Player",
                "--middle",
                "--malformed-skipped",
                "--demo",
                "--demo"
        ), selected.gameTokens());
    }

    @Test
    void selectedLaunchInputsAreUnmodifiable() {
        var selected = ManifestSelector.select(inlineFixture().fragment(),
                context("windows", "x86_64"));

        assertThrows(UnsupportedOperationException.class,
                () -> selected.libraries().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> selected.jvmTokens().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> selected.gameTokens().add(null));
    }

    private ManifestFragment fixture(String resource) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return parser.parse(stream.readAllBytes());
        }
    }

    private static List<Library> selectedLibraries(ManifestFragment fragment, String osName) {
        return ManifestSelector.select(fragment, context(osName, "x86_64")).libraries();
    }

    private static List<String> names(List<Library> libraries) {
        return libraries.stream().map(Library::name).toList();
    }

    private static RuleContext context(String osName, String osArch) {
        return context(osName, osArch, Map.of());
    }

    private static RuleContext context(
            String osName,
            String osArch,
            Map<String, Boolean> features
    ) {
        return new RuleContext(osName, osArch, Optional.empty(), features);
    }

    private static OsRule os(String name, String arch, String version) {
        return new OsRule(
                Optional.ofNullable(name),
                Optional.ofNullable(arch),
                Optional.ofNullable(version)
        );
    }

    private static InlineFixture inlineFixture() {
        var always = new Library("lib.always:1", null, null, null, List.of(), null);
        var windowsOnly = new Library("lib.windows:2", null, null, null,
                List.of(new Rule(RuleAction.ALLOW,
                        Optional.of(os("windows", null, null)), Map.of())), null);
        var osxOnly = new Library("lib.osx:3", null, null, null,
                List.of(new Rule(RuleAction.ALLOW,
                        Optional.of(os("osx", null, null)), Map.of())), null);

        var gameArguments = List.<LaunchArgument>of(
                new LaunchArgument.Direct("--first"),
                new LaunchArgument.Conditional(
                        List.of(new Rule(RuleAction.ALLOW, Optional.empty(), Map.of())),
                        List.of("--enabled")),
                new LaunchArgument.Direct("Demo Player"),
                new LaunchArgument.Conditional(
                        List.of(new Rule(RuleAction.DISALLOW,
                                Optional.of(os("windows", null, null)), Map.of())),
                        List.of("--disabled")),
                new LaunchArgument.Direct("--middle"),
                new LaunchArgument.Conditional(
                        List.of(new Rule(RuleAction.ALLOW, Optional.empty(), Map.of())),
                        List.of()),
                new LaunchArgument.Conditional(
                        List.of(
                                new Rule(RuleAction.ALLOW, Optional.empty(), Map.of()),
                                Rule.invalid(RuleAction.DISALLOW,
                                        Optional.empty(), Map.of())
                        ),
                        List.of("--malformed-skipped")),
                new LaunchArgument.Conditional(
                        List.of(Rule.invalid(RuleAction.ALLOW,
                                Optional.empty(), Map.of())),
                        List.of("--malformed-allow")),
                new LaunchArgument.Direct("--demo"),
                new LaunchArgument.Direct("--demo")
        );

        var jvmArguments = List.<LaunchArgument>of(
                new LaunchArgument.Direct("-Xmx2G"),
                new LaunchArgument.Conditional(
                        List.of(new Rule(RuleAction.DISALLOW,
                                Optional.of(os("windows", null, null)), Map.of())),
                        List.of("-Xdisabled")),
                new LaunchArgument.Direct("-Dname=Demo Player"),
                new LaunchArgument.Conditional(
                        List.of(new Rule(RuleAction.ALLOW, Optional.empty(), Map.of())),
                        List.of("-Xenabled")),
                new LaunchArgument.Conditional(
                        List.of(new Rule(RuleAction.ALLOW, Optional.empty(), Map.of())),
                        List.of()),
                new LaunchArgument.Direct("-Xmx2G")
        );

        var fragment = new ManifestFragment(
                "inline",
                null,
                null,
                null,
                new ManifestFragment.ArgumentsBlock(gameArguments, jvmArguments),
                List.of(always, windowsOnly, always, osxOnly)
        );
        return new InlineFixture(fragment, always, windowsOnly, osxOnly);
    }

    private record InlineFixture(
            ManifestFragment fragment,
            Library always,
            Library windowsOnly,
            Library osxOnly
    ) {
    }
}
