package org.zeropointlauncher.launch.resolve;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.model.RuleContext;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LegacyNativeSelectorTest {
    private static final String LWJGL = "org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209";
    private static final String JINPUT = "net.java.jinput:jinput-platform:2.0.5";
    private static final String OBJC = "ca.weblite:java-objc-bridge:1.0.0";

    private final ManifestFragmentParser parser = new ManifestFragmentParser();

    @Test
    void oneTwelveWindowsSelectsJinputNativeArtifact() throws Exception {
        var fragment = fixture("versions/1.12.2-trimmed.json");
        var context = context("windows", "x86_64");
        var selected = ManifestSelector.select(fragment, context);

        var result = LegacyNativeSelector.select(selected.libraries(), context);
        var request = result.requests().stream()
                .filter(entry -> JINPUT.equals(entry.library().name()))
                .findFirst()
                .orElseThrow();

        assertEquals("natives-windows", request.classifier());
        var artifact = request.artifact().orElseThrow();
        assertEquals("net/java/jinput/jinput-platform/2.0.5/jinput-platform-2.0.5-natives-windows.jar",
                artifact.path());
        assertEquals("385ee093e01f587f30ee1c8a2ee7d408fd732e16", artifact.sha1());
        assertEquals(155179L, artifact.size());
        assertEquals("https://libraries.minecraft.net/net/java/jinput/jinput-platform/2.0.5/"
                + "jinput-platform-2.0.5-natives-windows.jar", artifact.url());
        assertEquals(List.of("META-INF/"), request.extractExcludes());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void oneTwelveMacOsExcludedLwjglNeverProducesNativeRequest() throws Exception {
        var fragment = fixture("versions/1.12.2-trimmed.json");
        var context = context("osx", "x86_64");
        var selected = ManifestSelector.select(fragment, context);

        assertFalse(selected.libraries().stream()
                .anyMatch(library -> LWJGL.equals(library.name())));

        var result = LegacyNativeSelector.select(selected.libraries(), context);
        assertFalse(result.requests().stream()
                .anyMatch(request -> LWJGL.equals(request.library().name())));
        assertTrue(result.requests().stream()
                .anyMatch(request -> JINPUT.equals(request.library().name())));
        assertTrue(result.requests().stream()
                .anyMatch(request -> OBJC.equals(request.library().name())));
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void oneSevenTenLiteralNativeWithoutClassifiersKeepsCoordinate() {
        var library = library(
                "org.lwjgl.lwjgl:lwjgl-platform:2.9.1",
                Map.of("windows", "natives-windows"),
                Map.of());

        var result = LegacyNativeSelector.select(
                List.of(library), context("windows", "x86_64"));

        assertEquals(1, result.requests().size());
        var request = result.requests().getFirst();
        assertSame(library, request.library());
        assertEquals("natives-windows", request.classifier());
        assertTrue(request.artifact().isEmpty());
        assertEquals(List.of(), request.extractExcludes());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void oneSevenTenArchPlaceholderSelectsDistinctClassifiers() {
        var library = library(
                "tv.twitch:twitch-platform:5.16",
                Map.of("windows", "natives-windows-${arch}"),
                Map.of());

        var x86 = LegacyNativeSelector.select(
                List.of(library), context("windows", "x86"));
        assertEquals("natives-windows-32", x86.requests().getFirst().classifier());
        assertTrue(x86.requests().getFirst().artifact().isEmpty());
        assertTrue(x86.issues().isEmpty());

        var x64 = LegacyNativeSelector.select(
                List.of(library), context("windows", "x86_64"));
        assertEquals("natives-windows-64", x64.requests().getFirst().classifier());
        assertTrue(x64.requests().getFirst().artifact().isEmpty());
        assertTrue(x64.issues().isEmpty());

        assertNotEquals(
                x86.requests().getFirst().classifier(),
                x64.requests().getFirst().classifier());
    }

    @Test
    void archPlaceholderWithClassifierDictionaryUsesExpandedArtifact() {
        var library = library(
                "example:arch-native:1",
                Map.of("windows", "natives-windows-${arch}"),
                Map.of("natives-windows-64", artifact("win64.jar")));

        var result = LegacyNativeSelector.select(
                List.of(library), context("windows", "x86_64"));

        assertEquals(1, result.requests().size());
        var request = result.requests().getFirst();
        assertEquals("natives-windows-64", request.classifier());
        assertEquals("win64.jar", request.artifact().orElseThrow().path());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void unsupportedArchWithPlaceholderProducesIssueOnly() {
        var library = library(
                "tv.twitch:twitch-platform:5.16",
                Map.of("windows", "natives-windows-${arch}"),
                Map.of());

        for (String arch : List.of("arm64", "aarch64")) {
            var result = LegacyNativeSelector.select(
                    List.of(library), context("windows", arch));

            assertTrue(result.requests().isEmpty());
            assertEquals(1, result.issues().size());
            var issue = result.issues().getFirst();
            assertEquals(library.name(), issue.libraryName());
            assertEquals("windows", issue.targetOsKey());
            assertTrue(issue.message().contains(arch));
        }
    }

    @Test
    void classifierDictionaryMissProducesIssueOnly() {
        var library = library(
                "example:missing-native:1",
                Map.of("windows", "natives-windows"),
                Map.of("natives-linux", artifact("linux.jar")));

        var result = LegacyNativeSelector.select(
                List.of(library), context("windows", "x86_64"));

        assertTrue(result.requests().isEmpty());
        assertEquals(1, result.issues().size());
        assertTrue(result.issues().getFirst().message().contains("natives-windows"));
    }

    @Test
    void missingOrUnknownOsFactProducesIssue() {
        var library = library(
                "example:windows-only:1",
                Map.of("windows", "natives-windows"),
                Map.of());

        var missing = LegacyNativeSelector.select(
                List.of(library), context("", "x86_64"));
        assertTrue(missing.requests().isEmpty());
        assertEquals(1, missing.issues().size());
        assertEquals("", missing.issues().getFirst().targetOsKey());
        assertTrue(missing.issues().getFirst().message().contains("OS"));

        var unknown = LegacyNativeSelector.select(
                List.of(library), context("freebsd", "x86_64"));
        assertTrue(unknown.requests().isEmpty());
        assertEquals(1, unknown.issues().size());
        assertEquals("freebsd", unknown.issues().getFirst().targetOsKey());
        assertTrue(unknown.issues().getFirst().message().contains("freebsd"));
    }

    @Test
    void blankOrUnresolvedClassifierProducesIssueOnly() {
        var blank = library(
                "example:blank-classifier:1",
                Map.of("windows", "   "),
                Map.of());
        var blankResult = LegacyNativeSelector.select(
                List.of(blank), context("windows", "x86_64"));
        assertTrue(blankResult.requests().isEmpty());
        assertEquals(1, blankResult.issues().size());
        assertTrue(blankResult.issues().getFirst().message().contains("classifier"));

        var unresolved = library(
                "example:unresolved-classifier:1",
                Map.of("windows", "natives-windows-${flavor}"),
                Map.of());
        var unresolvedResult = LegacyNativeSelector.select(
                List.of(unresolved), context("windows", "x86_64"));
        assertTrue(unresolvedResult.requests().isEmpty());
        assertEquals(1, unresolvedResult.issues().size());
        assertTrue(unresolvedResult.issues().getFirst().message().contains("${flavor}"));

        var mixed = library(
                "example:mixed-classifier:1",
                Map.of("windows", "natives-windows-${arch}-${flavor}"),
                Map.of());
        var mixedResult = LegacyNativeSelector.select(
                List.of(mixed), context("windows", "x86_64"));
        assertTrue(mixedResult.requests().isEmpty());
        assertEquals(1, mixedResult.issues().size());
        assertTrue(mixedResult.issues().getFirst().message().contains("${flavor}"));
    }

    @Test
    void oneSevenTenOfficialFixtureSelectsWindowsNativePaths() throws Exception {
        var fragment = fixture("versions/1.7.10-trimmed.json");

        var x86Context = context("windows", "x86");
        var x86Selected = ManifestSelector.select(fragment, x86Context);
        var x86 = LegacyNativeSelector.select(x86Selected.libraries(), x86Context);

        assertEquals(2, x86.requests().size());
        assertTrue(x86.issues().isEmpty());

        var lwjgl = requestFor(x86, "org.lwjgl.lwjgl:lwjgl-platform:2.9.1");
        assertEquals("natives-windows", lwjgl.classifier());
        assertEquals("org/lwjgl/lwjgl/lwjgl-platform/2.9.1/lwjgl-platform-2.9.1-natives-windows.jar",
                lwjgl.artifact().orElseThrow().path());
        assertEquals("4c517eca808522457dd95ee8fc1fbcdbb602efbe",
                lwjgl.artifact().orElseThrow().sha1());

        var twitch32 = requestFor(x86, "tv.twitch:twitch-platform:5.16");
        assertEquals("natives-windows-32", twitch32.classifier());
        assertEquals("tv/twitch/twitch-platform/5.16/twitch-platform-5.16-natives-windows-32.jar",
                twitch32.artifact().orElseThrow().path());
        assertEquals("7c6affe439099806a4f552da14c42f9d643d8b23",
                twitch32.artifact().orElseThrow().sha1());

        var x64Context = context("windows", "x86_64");
        var x64Selected = ManifestSelector.select(fragment, x64Context);
        var x64 = LegacyNativeSelector.select(x64Selected.libraries(), x64Context);

        assertEquals(2, x64.requests().size());
        assertTrue(x64.issues().isEmpty());

        assertEquals("natives-windows", requestFor(
                x64, "org.lwjgl.lwjgl:lwjgl-platform:2.9.1").classifier());

        var twitch64 = requestFor(x64, "tv.twitch:twitch-platform:5.16");
        assertEquals("natives-windows-64", twitch64.classifier());
        assertEquals("tv/twitch/twitch-platform/5.16/twitch-platform-5.16-natives-windows-64.jar",
                twitch64.artifact().orElseThrow().path());
        assertEquals("39d0c3d363735b4785598e0e7fbf8297c706a9f9",
                twitch64.artifact().orElseThrow().sha1());
    }

    @Test
    void knownOsAbsentFromMappingIsSilentlySkipped() {
        var library = library(
                "example:windows-only:1",
                Map.of("windows", "natives-windows"),
                Map.of());

        var result = LegacyNativeSelector.select(
                List.of(library), context("linux", "x86_64"));

        assertTrue(result.requests().isEmpty());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void oneTwentyFilteredLibrariesNeverBecomeLegacyNatives() throws Exception {
        var fragment = fixture("versions/1.20.1-trimmed.json");
        var context = context("linux", "x86_64");
        var selected = ManifestSelector.select(fragment, context);

        var result = LegacyNativeSelector.select(selected.libraries(), context);

        assertTrue(result.requests().isEmpty());
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void nativeExtractRequestCopiesExcludes() {
        var excludes = new ArrayList<>(List.of("META-INF/"));
        var request = new NativeExtractRequest(
                library("example:test:1", Map.of("windows", "natives-windows"), Map.of()),
                "natives-windows",
                Optional.empty(),
                excludes);

        excludes.add("other/");
        assertEquals(List.of("META-INF/"), request.extractExcludes());
        assertThrows(UnsupportedOperationException.class,
                () -> request.extractExcludes().add("other/"));
    }

    private ManifestFragment fixture(String resource) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return parser.parse(stream.readAllBytes());
        }
    }

    private static RuleContext context(String osName, String osArch) {
        return new RuleContext(osName, osArch, Optional.empty(), Map.of());
    }

    private static NativeExtractRequest requestFor(
            NativeSelectionResult result,
            String libraryName
    ) {
        return result.requests().stream()
                .filter(request -> libraryName.equals(request.library().name()))
                .findFirst()
                .orElseThrow();
    }

    private static Library library(
            String name,
            Map<String, String> natives,
            Map<String, Library.DownloadArtifact> classifiers
    ) {
        return new Library(
                name,
                null,
                new Library.Downloads(null, classifiers),
                natives,
                List.of(),
                new Library.Extract(List.of())
        );
    }

    private static Library.DownloadArtifact artifact(String path) {
        return new Library.DownloadArtifact(
                path, "sha1", 1L, "https://example.test/" + path);
    }
}
