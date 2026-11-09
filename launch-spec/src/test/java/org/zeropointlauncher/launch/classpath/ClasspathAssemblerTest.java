package org.zeropointlauncher.launch.classpath;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.Library;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.model.RuleContext;
import org.zeropointlauncher.launch.parse.ManifestFragmentParser;
import org.zeropointlauncher.launch.resolve.ManifestSelector;
import org.zeropointlauncher.launch.resolve.SelectedLaunchInputs;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathAssemblerTest {
    private static final String CLIENT_JAR = "versions/1.20.1/1.20.1.jar";

    private final ManifestFragmentParser parser = new ManifestFragmentParser();

    @Test
    void modernClassifierArtifactsEnterClasspathBeforeClientJar() throws Exception {
        var selected = ManifestSelector.select(
                fixture("versions/1.20.1-trimmed.json"),
                context("linux", "x86_64"));

        var plan = ClasspathAssembler.assemble(selected, new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(
                "com/google/code/gson/gson/2.10/gson-2.10.jar",
                "org/lwjgl/lwjgl/3.3.1/lwjgl-3.3.1-natives-linux.jar",
                "io/netty/netty-transport-native-epoll/4.1.82.Final/"
                        + "netty-transport-native-epoll-4.1.82.Final-linux-x86_64.jar",
                CLIENT_JAR
        ), plan.entries());
        assertEquals(Optional.of(CLIENT_JAR), plan.clientJar());
        assertEquals(plan.entries(), plan.relativePaths());
    }

    @Test
    void oneTwelveUsesArtifactAndSkipsClassifierOnlyNatives() throws Exception {
        var selected = ManifestSelector.select(
                fixture("versions/1.12.2-trimmed.json"),
                context("windows", "x86_64"));

        var plan = ClasspathAssembler.assemble(selected, new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(
                "net/sf/jopt-simple/jopt-simple/5.0.3/jopt-simple-5.0.3.jar",
                "org/lwjgl/lwjgl/lwjgl-platform/2.9.4-nightly-20150209/"
                        + "lwjgl-platform-2.9.4-nightly-20150209.jar",
                CLIENT_JAR
        ), plan.entries());
        assertFalse(plan.entries().stream()
                .anyMatch(entry -> entry.contains("jinput-platform")));
        assertFalse(plan.entries().stream()
                .anyMatch(entry -> entry.contains("-natives-windows.jar")));
    }

    @Test
    void oneSevenTenNativeOnlyLibrariesNeverEnterClasspath() throws Exception {
        var selected = ManifestSelector.select(
                fixture("versions/1.7.10-trimmed.json"),
                context("windows", "x86_64"));

        assertFalse(selected.libraries().isEmpty());
        assertTrue(selected.libraries().stream().allMatch(library -> library.artifact() == null));

        var plan = ClasspathAssembler.assemble(selected, new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(CLIENT_JAR), plan.entries());
    }

    @Test
    void missingDownloadsFallsBackToMavenRelativePath() {
        var plain = nameOnly("com.example:legacy-lib:1.2.3");
        var classified = nameOnly("com.example:legacy-native:1.2.3:natives-linux@zip");
        var artifact = artifactLibrary("com.example:modern:2.0", "custom/renamed.jar");
        var emptyDownloads = new Library(
                "com.example:empty-downloads:1",
                null,
                new Library.Downloads(null, Map.of()),
                null,
                List.of(),
                null);

        var plan = ClasspathAssembler.assemble(
                inputs(plain, classified, artifact, emptyDownloads),
                new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(
                "com/example/legacy-lib/1.2.3/legacy-lib-1.2.3.jar",
                "com/example/legacy-native/1.2.3/legacy-native-1.2.3-natives-linux.zip",
                "custom/renamed.jar",
                "com/example/empty-downloads/1/empty-downloads-1.jar",
                CLIENT_JAR
        ), plan.entries());
    }

    @Test
    void artifactPathWinsOverCoordinateDerivation() {
        var artifact = artifactLibrary("com.example:mapped:1", "custom/renamed.jar");

        var plan = ClasspathAssembler.assemble(
                inputs(artifact), new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of("custom/renamed.jar", CLIENT_JAR), plan.entries());
    }

    @Test
    void nativeMappingsWithoutArtifactAreSkippedEvenWithoutDownloads() {
        var nativesOnly = new Library(
                "com.example:native-map-only:1",
                null,
                null,
                Map.of("linux", "natives-linux"),
                List.of(),
                null);
        var classifiersOnly = new Library(
                "com.example:classifier-only:1",
                null,
                new Library.Downloads(null,
                        Map.of("natives-linux", artifact("native.jar"))),
                Map.of("linux", "natives-linux"),
                List.of(),
                null);

        var plan = ClasspathAssembler.assemble(
                inputs(nativesOnly, classifiersOnly), new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(CLIENT_JAR), plan.entries());
    }

    @Test
    void preservesDeclarationOrderDuplicatesAndAppendsClientJarLast() {
        var first = artifactLibrary("com.example:first:1", "com/example/first-1.jar");
        var second = nameOnly("com.example:second:2");

        var plan = ClasspathAssembler.assemble(
                inputs(first, second, first), new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(
                "com/example/first-1.jar",
                "com/example/second/2/second-2.jar",
                "com/example/first-1.jar",
                CLIENT_JAR
        ), plan.entries());
    }

    @Test
    void clientJarIsAppendedEvenWhenItDuplicatesAnEntry() {
        var artifact = artifactLibrary("com.example:client:1", "shared/client.jar");

        var plan = ClasspathAssembler.assemble(
                inputs(artifact), new ClientJarRef("shared/client.jar"));

        assertEquals(List.of("shared/client.jar", "shared/client.jar"), plan.entries());
    }

    @Test
    void emptyInputsStillProduceClientJarLast() {
        var plan = ClasspathAssembler.assemble(inputs(), new ClientJarRef(CLIENT_JAR));

        assertEquals(List.of(CLIENT_JAR), plan.entries());
        assertEquals(Optional.of(CLIENT_JAR), plan.clientJar());
    }

    @Test
    void unparseableLegacyCoordinateFailsWithLibraryName() {
        var malformed = nameOnly("not-a-coordinate");

        var error = assertThrows(IllegalArgumentException.class,
                () -> ClasspathAssembler.assemble(
                        inputs(malformed), new ClientJarRef(CLIENT_JAR)));

        assertTrue(error.getMessage().contains("not-a-coordinate"));
    }

    @Test
    void planAndClientJarReferenceAreImmutableValues() {
        var plan = ClasspathAssembler.assemble(
                inputs(), new ClientJarRef("  versions/client.jar  "));

        assertEquals("versions/client.jar", plan.entries().getFirst());
        assertThrows(UnsupportedOperationException.class,
                () -> plan.entries().add("other.jar"));
        assertThrows(NullPointerException.class, () -> new ClientJarRef(null));
        assertThrows(IllegalArgumentException.class, () -> new ClientJarRef("   "));
        assertEquals("versions/client.jar", new ClientJarRef("versions/client.jar").path());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> ClasspathAssembler.assemble(null, new ClientJarRef(CLIENT_JAR)));
        assertThrows(NullPointerException.class,
                () -> ClasspathAssembler.assemble(inputs(), null));
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

    private static SelectedLaunchInputs inputs(Library... libraries) {
        return new SelectedLaunchInputs(List.of(libraries), List.of(), List.of());
    }

    private static Library nameOnly(String name) {
        return new Library(name, null, null, null, List.of(), null);
    }

    private static Library artifactLibrary(String name, String path) {
        return new Library(
                name,
                null,
                new Library.Downloads(artifact(path), Map.of()),
                null,
                List.of(),
                null);
    }

    private static Library.DownloadArtifact artifact(String path) {
        return new Library.DownloadArtifact(path, "sha1", 1L, "https://example.test/" + path);
    }
}
