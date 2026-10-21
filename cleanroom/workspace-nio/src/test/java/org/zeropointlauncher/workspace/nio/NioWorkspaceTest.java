/*
MIT License

Copyright (c) 2023 初夏同学

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/


package org.zeropointlauncher.workspace.nio;

import static org.junit.jupiter.api.Assertions.*;
import static org.zeropointlauncher.layerfs.LayerFs.*;
import static org.zeropointlauncher.layerfs.WorkspaceModel.*;
import static org.zeropointlauncher.workspace.nio.WorkspaceException.Code.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.zeropointlauncher.layerfs.LayerResolver;
import org.zeropointlauncher.layerfs.LogicalPath;

class NioWorkspaceTest {
    @TempDir Path temp;
    private static final NamePolicy NAMES = NamePolicy.windows();
    private static RevisionId id(String name) { return new RevisionId(name, "r1"); }
    private static Revision revision(String name, String parent, Entry... entries) {
        return new Revision(id(name), name, Optional.ofNullable(parent).map(NioWorkspaceTest::id),
                new Layer(name, id(name), List.of(entries), List.of()), List.of());
    }
    private static Resolution resolve(Revision... revisions) {
        return new LayerResolver().resolve(revisions[revisions.length - 1].id(), List.of(revisions), List.of());
    }
    private static ArtifactSource source(Map<String, String> bytes) {
        return reference -> {
            String value = bytes.get(reference);
            if (value == null) throw new IOException("Synthetic artifact is missing");
            return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
        };
    }
    private static ArtifactSource source() { return source(Map.of("ref", "original", "other", "other")); }
    private static void error(WorkspaceException.Code code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(WorkspaceException.class, action).code());
    }
    private static Change change(NioWorkspace.Prepared result, String path) {
        return result.plan().delta().get(LogicalPath.of(path)).change();
    }
    private NioWorkspace.Prepared prepare(Path root, Revision... revisions) throws WorkspaceException {
        return new NioWorkspace().prepare(root, revisions[revisions.length - 1].id(), resolve(revisions), source(), NAMES);
    }

    @Test @DisplayName("FS-05 / FS-15 / FS-06 (unpublished delta): preset, A, B and a new child use the same API and isolated files")
    void realPrivateCopiesPreserveReopenedDeltaAndDoNotBecomeInheritedContent() throws Exception {
        Path artifact = temp.resolve("immutable-artifact");
        Files.writeString(artifact, "preset config\n");
        ArtifactSource artifacts = reference -> reference.equals("preset") ? Files.newInputStream(artifact) : source().open(reference);
        Revision base = revision("base", null, Entry.file("config/game.cfg", "ref"), Entry.file("mods/old.jar", "other"), Entry.directory("empty"));
        Revision preset = revision("preset", "base", Entry.file("config/game.cfg", "preset"));
        Revision a = revision("A", "preset");
        Revision b = revision("B", "preset");
        Revision child = revision("C", "A");
        List<Revision> graph = List.of(base, preset, a, b, child);
        LayerResolver resolver = new LayerResolver();
        NioWorkspace workspace = new NioWorkspace();
        for (Revision instance : List.of(preset, a, b)) {
            workspace.prepare(temp.resolve(instance.id().instanceId()), instance.id(), resolver.resolve(instance.id(), graph, List.of()), artifacts, NAMES);
        }
        Path rootA = temp.resolve("A");
        Path rootB = temp.resolve("B");
        assertFalse(Files.isSameFile(artifact, rootA.resolve("config/game.cfg")));
        assertFalse(Files.isSameFile(rootA.resolve("config/game.cfg"), rootB.resolve("config/game.cfg")));
        assertEquals("preset config\n", Files.readString(rootA.resolve("config/game.cfg")));
        FileTime originalTime = Files.getLastModifiedTime(rootA.resolve("config/game.cfg"));
        Files.writeString(rootA.resolve("config/game.cfg"), "custom config\n"); // same length
        Files.setLastModifiedTime(rootA.resolve("config/game.cfg"), originalTime);
        Files.writeString(rootA.resolve("unknown.txt"), "keep unknown data");
        Files.delete(rootA.resolve("mods/old.jar"));
        Files.createDirectory(rootA.resolve("new-empty"));
        ArtifactSource mustNotFetchAgain = reference -> { throw new AssertionError("Same immutable baseline must not refetch content"); };
        NioWorkspace.Prepared reopened = new NioWorkspace().prepare(rootA, a.id(), resolver.resolve(a.id(), graph, List.of()), mustNotFetchAgain, NAMES);
        assertEquals(Decision.PRESERVE, reopened.plan().decision());
        assertEquals(Change.MODIFIED, change(reopened, "config/game.cfg"));
        assertEquals(Change.ADDED, change(reopened, "unknown.txt"));
        assertEquals(Change.ADDED, change(reopened, "new-empty"));
        assertEquals(Change.DELETED, change(reopened, "mods/old.jar"));
        assertEquals("custom config\n", Files.readString(rootA.resolve("config/game.cfg")));
        assertEquals("keep unknown data", Files.readString(rootA.resolve("unknown.txt")));
        assertFalse(Files.exists(rootA.resolve("mods/old.jar")));
        assertTrue(Files.isDirectory(rootA.resolve("empty")));
        assertEquals("preset config\n", Files.readString(rootB.resolve("config/game.cfg")));
        assertTrue(Files.exists(rootB.resolve("mods/old.jar")));
        Files.writeString(rootB.resolve("config/game.cfg"), "B changed\n");
        new NioWorkspace().prepare(rootB, b.id(), resolver.resolve(b.id(), graph, List.of()), mustNotFetchAgain, NAMES);
        assertEquals("custom config\n", Files.readString(rootA.resolve("config/game.cfg")));
        assertEquals("preset config\n", Files.readString(artifact));
        assertEquals("preset config\n", Files.readString(temp.resolve("preset/config/game.cfg")));
        new NioWorkspace().prepare(temp.resolve("C"), child.id(), resolver.resolve(child.id(), graph, List.of()), artifacts, NAMES);
        assertEquals("preset config\n", Files.readString(temp.resolve("C/config/game.cfg")));
        assertFalse(Files.exists(temp.resolve("C/unknown.txt")));
        assertTrue(Files.exists(temp.resolve("C/mods/old.jar")));
    }

    @Test @DisplayName("FS-05: file/directory changes and unknown descendants are preserved, including empty directories")
    void typeChangesAreDeltaNotRepairInstructions() throws Exception {
        Revision revision = revision("instance", null, Entry.file("was-file", "ref"), Entry.directory("was-directory"), Entry.directory("deleted-empty"));
        Path root = temp.resolve("instance");
        prepare(root, revision);
        Files.delete(root.resolve("was-file"));
        Files.createDirectory(root.resolve("was-file"));
        Files.writeString(root.resolve("was-file/unknown"), "user child");
        Files.delete(root.resolve("was-directory"));
        Files.writeString(root.resolve("was-directory"), "user file");
        Files.delete(root.resolve("deleted-empty"));
        NioWorkspace.Prepared result = prepare(root, revision);
        assertEquals(Change.TYPE_CHANGED, change(result, "was-file"));
        assertEquals(Change.TYPE_CHANGED, change(result, "was-directory"));
        assertEquals(Change.ADDED, change(result, "was-file/unknown"));
        assertEquals(Change.DELETED, change(result, "deleted-empty"));
        assertEquals("user child", Files.readString(root.resolve("was-file/unknown")));
        assertEquals("user file", Files.readString(root.resolve("was-directory")));
        assertFalse(Files.exists(root.resolve("deleted-empty")));
    }

    @Test @DisplayName("FS-05: target or reference changes are rejected before writes even if artifact bytes would match")
    void changedBaselineDoesNotOverwritePrivateDelta() throws Exception {
        Revision original = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("instance");
        prepare(root, original);
        Files.writeString(root.resolve("x"), "private bytes");
        byte[] stateBefore = Files.readAllBytes(NioWorkspace.stateDirectory(root).resolve("manifest.bin"));
        ArtifactSource noFetch = reference -> { throw new AssertionError("Changed baseline should be rejected before fetching"); };
        error(BASELINE_CHANGED, () -> new NioWorkspace().prepare(root, new RevisionId("instance", "r2"), resolve(original), noFetch, NAMES));
        Revision changedReference = revision("instance", null, Entry.file("x", "different-ref-same-bytes"));
        error(BASELINE_CHANGED, () -> new NioWorkspace().prepare(root, original.id(), resolve(changedReference), noFetch, NAMES));
        assertEquals("private bytes", Files.readString(root.resolve("x")));
        assertArrayEquals(stateBefore, Files.readAllBytes(NioWorkspace.stateDirectory(root).resolve("manifest.bin")));
    }

    @Test @DisplayName("FS-05: unmarked existing directories are never adopted, whether empty or nonempty")
    void unmanagedNonemptyDirectoryIsPreserved() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path unknown = Files.createDirectory(temp.resolve("unknown"));
        Files.writeString(unknown.resolve("user.txt"), "real user data");
        error(UNMANAGED_WORKSPACE, () -> prepare(unknown, revision));
        assertEquals("real user data", Files.readString(unknown.resolve("user.txt")));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(unknown)));
        Path empty = Files.createDirectory(temp.resolve("empty"));
        error(UNMANAGED_WORKSPACE, () -> prepare(empty, revision));
        assertFalse(Files.exists(empty.resolve("x")));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(empty)));
    }

    @Test @DisplayName("FS-05: missing and interrupted artifact reads leave an explicit incomplete state, not ready")
    void missingOrInterruptedArtifactNeverBecomesReady() throws Exception {
        Revision revision = revision("instance", null, Entry.file("config/x", "missing"));
        Path missing = temp.resolve("missing");
        error(MISSING_ARTIFACT, () -> prepare(missing, revision));
        assertFalse(Files.exists(missing));
        assertTrue(Files.exists(NioWorkspace.stateDirectory(missing).resolve("pending")));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(missing).resolve("manifest.bin")));
        error(INCOMPLETE_WORKSPACE, () -> new NioWorkspace().prepare(missing, revision.id(), resolve(revision), source(Map.of("missing", "now available")), NAMES));
        Path interrupted = temp.resolve("interrupted");
        ArtifactSource broken = reference -> new InputStream() {
            private int reads;
            @Override public int read() throws IOException {
                if (reads++ < 3) return 'x';
                throw new IOException("Injected artifact failure");
            }
        };
        error(MISSING_ARTIFACT, () -> new NioWorkspace().prepare(interrupted, revision.id(), resolve(revision), broken, NAMES));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(interrupted).resolve("manifest.bin")));
    }

    @Test @DisplayName("FS-05: a copy-stage failure preserves the incomplete runtime and never commits a ready manifest")
    void failedInitialCopyIsNotFalseReady() throws Exception {
        Revision revision = revision("instance", null, Entry.file("config/a", "a"), Entry.file("config/b", "b"));
        Path root = temp.resolve("instance");
        ArtifactSource removeStagedInput = reference -> {
            if (reference.equals("b")) Files.delete(NioWorkspace.stateDirectory(root).resolve("staging/0"));
            return new ByteArrayInputStream(reference.getBytes(StandardCharsets.UTF_8));
        };
        error(IO_FAILURE, () -> new NioWorkspace().prepare(root, revision.id(), resolve(revision), removeStagedInput, NAMES));
        assertTrue(Files.isDirectory(root.resolve("config")), "The copy phase really started");
        assertTrue(Files.exists(NioWorkspace.stateDirectory(root).resolve("pending")));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(root).resolve("manifest.bin")));
        error(INCOMPLETE_WORKSPACE, () -> prepare(root, revision));
    }

    @Test @DisplayName("FS-05: externally created initial content is not overwritten or cleaned")
    void competitiveInitialWritesAreRejected() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("instance");
        ArtifactSource injectUnrelatedContent = reference -> {
            Files.createDirectory(root);
            Files.writeString(root.resolve("x"), "external content");
            return source().open(reference);
        };
        error(EXTERNAL_CHANGE, () -> new NioWorkspace().prepare(root, revision.id(), resolve(revision), injectUnrelatedContent, NAMES));
        assertEquals("external content", Files.readString(root.resolve("x")));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(root).resolve("manifest.bin")));
    }

    @Test @DisplayName("FS-05: malformed, bit-corrupted and unknown-schema state never triggers overwrite or fallback adoption")
    void damagedStateIsRejectedWithoutChangingFiles() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        for (String kind : List.of("malformed", "corrupt", "unknown")) {
            Path root = temp.resolve(kind);
            prepare(root, revision);
            Files.writeString(root.resolve("x"), "private");
            Path manifest = NioWorkspace.stateDirectory(root).resolve("manifest.bin");
            byte[] bytes = Files.readAllBytes(manifest);
            if (kind.equals("malformed")) bytes = new byte[] {1, 2, 3};
            else if (kind.equals("unknown")) ByteBuffer.wrap(bytes).putInt(4, 2);
            else bytes[bytes.length - 1] ^= 1;
            Files.write(manifest, bytes);
            error(kind.equals("unknown") ? UNKNOWN_SCHEMA : INVALID_STATE, () -> prepare(root, revision));
            assertEquals("private", Files.readString(root.resolve("x")));
            assertArrayEquals(bytes, Files.readAllBytes(manifest));
        }
    }

    @Test @DisplayName("FS-05: unknown state files and missing manifests are not reinterpreted as valid management markers")
    void unknownStateAndMissingManifestAreRejected() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("instance");
        prepare(root, revision);
        Path state = NioWorkspace.stateDirectory(root);
        Files.writeString(state.resolve("unknown"), "not ours");
        error(INVALID_STATE, () -> prepare(root, revision));
        Files.delete(state.resolve("unknown"));
        Files.delete(state.resolve("manifest.bin"));
        error(INVALID_STATE, () -> prepare(root, revision));
        assertEquals("original", Files.readString(root.resolve("x")));
    }

    @Test @DisplayName("FS-05: missing state after private deletion of every file never recreates the deleted baseline")
    void missingManifestCannotReinitializeAnEmptyManagedTree() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("instance");
        prepare(root, revision);
        Files.delete(root.resolve("x"));
        Path manifest = NioWorkspace.stateDirectory(root).resolve("manifest.bin");
        Files.delete(manifest);
        error(INVALID_STATE, () -> prepare(root, revision));
        assertFalse(Files.exists(root.resolve("x")));
        assertFalse(Files.exists(manifest));
        assertFalse(Files.exists(NioWorkspace.stateDirectory(root).resolve("pending")));
    }

    @Test @DisplayName("FS-05: losing the whole state directory cannot resurrect private deletions in an existing empty tree")
    void entireLostStateCannotReinitializeAnEmptyTree() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("instance");
        prepare(root, revision);
        Files.delete(root.resolve("x"));
        Path state = NioWorkspace.stateDirectory(root);
        Files.delete(state.resolve("manifest.bin"));
        Files.delete(state.resolve("lock"));
        Files.delete(state);
        error(UNMANAGED_WORKSPACE, () -> prepare(root, revision));
        assertFalse(Files.exists(root.resolve("x")));
        assertFalse(Files.exists(state));
    }

    @Test @EnabledOnOs(OS.WINDOWS)
    @DisplayName("FS-05 / FS-11: Windows dotted and short-name aliases cannot choose a second workspace state or lock")
    void windowsDirectoryAliasesCannotReinitializeDeletedContent() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("Long Workspace Name");
        prepare(root, revision);
        Files.delete(root.resolve("x"));
        Path dotted = temp.resolve("Long Workspace Name.");
        assertTrue(Files.isSameFile(root, dotted), "Exercise the real Windows alias, not merely a string check");
        error(NAME_CONFLICT, () -> prepare(dotted, revision));
        assertFalse(Files.exists(root.resolve("x")));
        String script = "[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false); "
                + "$f=New-Object -ComObject Scripting.FileSystemObject; $f.GetFolder('"
                + root.toString().replace("'", "''") + "').ShortPath";
        String shortName = powershell(script).strip();
        Path shortPath = Path.of(shortName);
        if (!root.toRealPath().toString().equalsIgnoreCase(shortName)) {
            assertTrue(Files.isSameFile(root, shortPath));
            error(INVALID_LOCATION, () -> prepare(shortPath, revision));
            assertFalse(Files.exists(root.resolve("x")));
        }
        assertEquals(Change.DELETED, change(prepare(root, revision), "x"));
    }

    @Test @DisplayName("FS-05 / FS-11: target naming conflicts are rejected before artifact access or runtime creation")
    void targetNameRestrictionsAreIndependentOfLogicalParsing() throws Exception {
        for (List<Entry> entries : List.of(List.of(Entry.file("config/A", "ref"), Entry.file("config/a", "ref")),
                List.of(Entry.file("CON.txt", "ref")), List.of(Entry.file("trailing.", "ref")),
                List.of(Entry.file("bad:name", "ref")))) {
            Revision revision = revision("instance", null, entries.toArray(Entry[]::new));
            Path root = temp.resolve("names-" + Math.abs(entries.hashCode()));
            error(NAME_CONFLICT, () -> new NioWorkspace().prepare(root, revision.id(), resolve(revision),
                    reference -> { throw new AssertionError("Names must be validated first"); }, NAMES));
            assertFalse(Files.exists(root));
            assertFalse(Files.exists(NioWorkspace.stateDirectory(root)));
        }
        error(NAME_CONFLICT, () -> NamePolicy.macOsDefault().validate(List.of(LogicalPath.of("café"), LogicalPath.of("cafe\u0301"))));
        assertDoesNotThrow(() -> NamePolicy.linux().validate(List.of(LogicalPath.of("A"), LogicalPath.of("a"))));
    }

    @Test @DisplayName("FS-05 / FS-11: directory links and Windows junctions are rejected at every managed boundary")
    void linksAtWorkspaceAncestorsStateLockAndRuntimeAreNeverFollowed() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("sentinel"), "preserve outside");
        Path rootLink = temp.resolve("root-link");
        directoryLink(rootLink, outside);
        error(UNSAFE_ENTRY, () -> prepare(rootLink, revision));
        Path parentLink = temp.resolve("parent-link");
        directoryLink(parentLink, outside);
        error(UNSAFE_ENTRY, () -> prepare(parentLink.resolve("workspace"), revision));
        Path rootState = temp.resolve("state-link-root");
        directoryLink(NioWorkspace.stateDirectory(rootState), outside);
        error(UNSAFE_ENTRY, () -> prepare(rootState, revision));
        Path rootLock = temp.resolve("lock-link-root");
        Files.createDirectory(NioWorkspace.stateDirectory(rootLock));
        directoryLink(NioWorkspace.stateDirectory(rootLock).resolve("lock"), outside);
        error(UNSAFE_ENTRY, () -> prepare(rootLock, revision));
        Path managed = temp.resolve("managed");
        prepare(managed, revision);
        directoryLink(managed.resolve("external"), outside);
        error(UNSAFE_ENTRY, () -> prepare(managed, revision));
        assertEquals("preserve outside", Files.readString(outside.resolve("sentinel")));
        assertFalse(Files.exists(outside.resolve("workspace")));
        assertFalse(Files.exists(outside.resolve("lock")));
    }

    @Test @DisplayName("FS-05 / FS-11: a linked ready manifest is refused instead of read as local state")
    void specialManifestIsRejected() throws Exception {
        Revision revision = revision("instance", null, Entry.file("x", "ref"));
        Path root = temp.resolve("instance");
        prepare(root, revision);
        Path manifest = NioWorkspace.stateDirectory(root).resolve("manifest.bin");
        Files.delete(manifest);
        Path outside = Files.createDirectory(temp.resolve("outside"));
        directoryLink(manifest, outside);
        error(UNSAFE_ENTRY, () -> prepare(root, revision));
        assertEquals("original", Files.readString(root.resolve("x")));
    }

    @Test @DisplayName("FS-05: concurrent first preparations share the fixed state lock and only one publishes")
    void concurrentFirstPreparationIsExclusive() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            Revision revision = revision("instance", null, Entry.file("x", "ref"));
            Path root = temp.resolve("instance");
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ArtifactSource waiting = reference -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Test release timed out");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                return source().open(reference);
            };
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = workers.submit(() -> new NioWorkspace().prepare(root, revision.id(), resolve(revision), waiting, NAMES));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    error(BUSY, () -> prepare(root, revision));
                } finally { release.countDown(); }
                assertEquals(Decision.INITIAL_COPY, first.get(5, TimeUnit.SECONDS).plan().decision());
            }
            assertEquals("original", Files.readString(root.resolve("x")));
        });
    }

    @Test @DisplayName("FS-05: repeated references are fetched once but copied to independent files, with external metadata only")
    void repeatedReferencesDoNotCreateSharedWritableFilesOrLeakReferences() throws Exception {
        String opaque = "opaque-content-reference-not-for-state";
        Revision revision = revision("instance", null, Entry.file("a", opaque), Entry.file("b", opaque));
        Path root = temp.resolve("instance");
        AtomicInteger calls = new AtomicInteger();
        NioWorkspace.Prepared result = new NioWorkspace().prepare(root, revision.id(), resolve(revision), reference -> {
            calls.incrementAndGet();
            return new ByteArrayInputStream("same bytes".getBytes(StandardCharsets.UTF_8));
        }, NAMES);
        assertEquals(1, calls.get());
        assertFalse(Files.isSameFile(root.resolve("a"), root.resolve("b")));
        Files.writeString(root.resolve("a"), "change a");
        assertEquals("same bytes", Files.readString(root.resolve("b")));
        try (var children = Files.list(root)) { assertEquals(List.of("a", "b"), children.map(p -> p.getFileName().toString()).sorted().toList()); }
        String metadata = Files.readString(NioWorkspace.stateDirectory(root).resolve("manifest.bin"), StandardCharsets.ISO_8859_1);
        assertFalse(metadata.contains(opaque));
        assertFalse(metadata.contains(root.toAbsolutePath().toString()));
        assertThrows(UnsupportedOperationException.class, () -> result.current().clear());
    }

    @Test @DisplayName("FS-05: empty baselines are valid, and deleting every managed entry remains a private delta")
    void emptyAndCompletelyDeletedWorkspacesRemainWellDefined() throws Exception {
        Revision empty = revision("empty", null);
        assertTrue(prepare(temp.resolve("empty"), empty).current().isEmpty());
        assertEquals(Decision.PRESERVE, prepare(temp.resolve("empty"), empty).plan().decision());
        Revision file = revision("file", null, Entry.file("x", "ref"));
        Path root = temp.resolve("file");
        prepare(root, file);
        Files.delete(root.resolve("x"));
        assertEquals(Change.DELETED, change(prepare(root, file), "x"));
        assertFalse(Files.exists(root.resolve("x")));
    }

    @Test @DisplayName("FS-05: rejected resolver results and ambiguous workspace dot paths never create state")
    void invalidResolutionAndLocationsHaveNoRuntimeEffects() throws Exception {
        Revision conflict = revision("conflict", null, Entry.file("node", "ref"), Entry.file("node/child", "other"));
        Path root = temp.resolve("conflict");
        error(INVALID_RESOLUTION, () -> prepare(root, conflict));
        assertFalse(Files.exists(root));
        Revision valid = revision("valid", null, Entry.file("x", "ref"));
        error(INVALID_LOCATION, () -> prepare(temp.resolve("unused/../valid"), valid));
        assertFalse(Files.exists(temp.resolve("valid")));
    }

    private static void directoryLink(Path link, Path target) throws Exception {
        if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
            String script = "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -LiteralPath '"
                    + link.toString().replace("'", "''") + "' -Target '" + target.toString().replace("'", "''") + "' | Out-Null";
            // New-Item uses -Path rather than -LiteralPath in Windows PowerShell 5.
            script = script.replace("-LiteralPath", "-Path");
            powershell(script);
        } else Files.createSymbolicLink(link, target.toAbsolutePath());
    }

    private static String powershell(String script) throws Exception {
        script = "$ProgressPreference='SilentlyContinue'; $ErrorActionPreference='Stop'; " + script;
        String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                .redirectErrorStream(true).start();
        String result;
        try (InputStream output = process.getInputStream()) { result = new String(output.readAllBytes(), StandardCharsets.UTF_8); }
        assertEquals(0, process.waitFor(), "Isolated Windows filesystem fixture creation must succeed");
        return result;
    }
}
