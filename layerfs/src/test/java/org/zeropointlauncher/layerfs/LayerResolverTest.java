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


package org.zeropointlauncher.layerfs;

import static org.junit.jupiter.api.Assertions.*;
import static org.zeropointlauncher.layerfs.LayerFs.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayerResolverTest {
    private final LayerResolver resolver = new LayerResolver();

    private static RevisionId id(String instance, String revision) { return new RevisionId(instance, revision); }
    private static RevisionId id(String instance) { return id(instance, "r1"); }
    private static Layer layer(String source, List<Entry> entries, Rule... rules) {
        return new Layer(source, id(source), entries, List.of(rules));
    }
    private static Revision revision(String source, String parent, List<Entry> entries, Rule... rules) {
        return new Revision(id(source), source, Optional.ofNullable(parent).map(LayerResolverTest::id),
                layer(source, entries, rules), List.of());
    }
    private Resolution resolve(Revision... revisions) {
        return resolver.resolve(revisions[revisions.length - 1].id(), List.of(revisions), List.of());
    }
    private static VisibleEntry entry(Resolution result, String path) {
        assertTrue(result.valid(), () -> result.diagnostics().toString());
        VisibleEntry entry = result.visible().get(LogicalPath.of(path));
        assertNotNull(entry, path);
        return entry;
    }
    private static void content(Resolution result, String path, String value) {
        assertEquals(Optional.of(value), entry(result, path).entry().content());
    }
    private static void code(Resolution result, Code code) {
        assertFalse(result.valid());
        assertTrue(result.visible().isEmpty(), "A conflicted result must not expose a usable tree");
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code() == code), result.diagnostics().toString());
    }
    private static Exclude exclude(String id, String source, Scope scope) {
        return new Exclude(id, source, Optional.ofNullable(scope));
    }

    @Test @DisplayName("FS-01 / FS-04: logical paths reject traversal and host syntax without host normalization")
    void logicalPathsHaveAnExplicitHostIndependentGrammar() {
        for (String invalid : List.of("", "/absolute", "a/", "a//b", ".", "..", "a/./b", "a/../b",
                "C:/sandbox/file", "c:relative", "\\server\\share", "a\\b", "bad\0name")) {
            assertThrows(IllegalArgumentException.class, () -> LogicalPath.of(invalid), invalid);
        }
        assertEquals("配置/A.cfg", LogicalPath.of("配置/A.cfg").value());
        assertNotEquals(LogicalPath.of("config/A.cfg"), LogicalPath.of("config/a.cfg"));
        assertEquals(List.of(LogicalPath.of("one"), LogicalPath.of("one/two")),
                LogicalPath.of("one/two/three").ancestors());
        assertTrue(LogicalPath.of("config").contains(LogicalPath.of("config/a")));
        assertFalse(LogicalPath.of("config").contains(LogicalPath.of("configuration/a")));
        // Host reserved names and case equivalence are materialization concerns.
        assertEquals("CON", LogicalPath.of("CON").value());
    }

    @Test @DisplayName("FS-01 / FS-04: ASCII drive prefixes are rejected even when followed by line breaks")
    void drivePrefixValidationDoesNotDependOnRegexDotMatching() {
        for (String invalid : List.of("C:\nfile", "z:\rfile", "A:", "Z:\n\rfile", "a:relative")) {
            assertThrows(IllegalArgumentException.class, () -> LogicalPath.of(invalid));
        }
        for (String valid : List.of("folder/name\npart", "é:relative", "1:relative", "folder/a:b")) {
            assertEquals(valid, LogicalPath.of(valid).value());
        }
    }

    @Test @DisplayName("FS-01: missing target and missing fixed parent are diagnosed")
    void missingRevisions() {
        code(resolver.resolve(id("missing"), List.of(), List.of()), Code.MISSING_REVISION);
        code(resolve(revision("child", "missing", List.of())), Code.MISSING_REVISION);
    }

    @Test @DisplayName("FS-01: direct and multi-revision cycles are rejected")
    void cycles() {
        code(resolve(revision("self", "self", List.of())), Code.INHERITANCE_CYCLE);
        code(resolve(revision("a", "c", List.of()), revision("b", "a", List.of()),
                revision("c", "b", List.of())), Code.INHERITANCE_CYCLE);
    }

    @Test @DisplayName("FS-01: stable identities survive rename and revision input order changes")
    void renameAndRepositoryOrderDoNotChangeIdentity() {
        Revision base = revision("base", null, List.of(Entry.file("x", "base")));
        Revision child = revision("child", "base", List.of(Entry.file("y", "child")));
        Resolution expected = resolve(base, child);
        Revision renamed = new Revision(base.id(), "renamed display only", base.parent(), base.layer(), base.sharedLayers());
        assertEquals(expected, resolver.resolve(child.id(), List.of(child, renamed), List.of()));
        code(resolver.resolve(child.id(), List.of(base, base, child), List.of()), Code.DUPLICATE_REVISION);
    }

    @Test @DisplayName("FS-01: a 20000 revision chain does not consume the recursive call stack")
    void deepChainIsIterative() {
        List<Revision> chain = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            chain.add(revision("r" + i, i == 0 ? null : "r" + (i - 1),
                    i == 0 ? List.of(Entry.file("deep/file", "root")) : List.of()));
        }
        Resolution result = resolver.resolve(chain.getLast().id(), chain, List.of());
        content(result, "deep/file", "root");
        assertEquals(20_000, result.composition().size());
    }

    @Test @DisplayName("FS-14: publishing a new parent revision never retargets a fixed child")
    void fixedParentRevisionDoesNotDrift() {
        Revision old = revision("base", null, List.of(Entry.file("x", "old")));
        Revision newer = new Revision(id("base", "r2"), "base", Optional.empty(),
                new Layer("base-r2", id("base", "r2"), List.of(Entry.file("x", "new")), List.of()), List.of());
        Revision child = revision("child", "base", List.of());
        content(resolver.resolve(child.id(), List.of(old, newer, child), List.of()), "x", "old");
        Revision upgraded = new Revision(id("child", "r2"), "child", Optional.of(newer.id()),
                new Layer("child-r2", id("child", "r2"), List.of(), List.of()), List.of());
        content(resolver.resolve(upgraded.id(), List.of(old, newer, child, upgraded), List.of()), "x", "new");
    }

    @Test @DisplayName("FS-02: shared manifest order precedes oldest ancestor through current revision")
    void compositionOrderAndProvenance() {
        Layer s1 = layer("shared-one", List.of(Entry.file("x", "s1")));
        Layer s2 = layer("shared-two", List.of(Entry.file("x", "s2")));
        Revision base = revision("base", null, List.of(Entry.file("x", "a")));
        Revision middle = revision("middle", "base", List.of(Entry.file("x", "b")));
        Revision child = new Revision(id("child"), "child", Optional.of(middle.id()),
                layer("child", List.of(Entry.file("x", "c"))), List.of(s1.id(), s2.id()));
        Resolution result = resolver.resolve(child.id(), List.of(child, middle, base), List.of(s2, s1));
        assertEquals(List.of("shared-one", "shared-two", "base", "middle", "child"), result.composition());
        content(result, "x", "c");
        assertEquals(new Origin("child", id("child")), entry(result, "x").origin());
        List<Candidate> candidates = result.explain("x").orElseThrow().candidates();
        assertEquals(5, candidates.size());
        assertEquals(Outcome.SHADOWED, candidates.getFirst().outcome());
        assertEquals("child", candidates.getFirst().reasons().getFirst().action().layer());
        assertEquals(Outcome.VISIBLE, candidates.getLast().outcome());
    }

    @Test @DisplayName("FS-02: shared manifests are complete, explicit and do not follow parent manifests")
    void childDoesNotDynamicallyInheritSharedManifest() {
        Layer shared = layer("shared", List.of(Entry.file("only-shared", "s")));
        Revision parent = new Revision(id("parent"), "parent", Optional.empty(), layer("parent", List.of()), List.of("shared"));
        Revision child = revision("child", "parent", List.of());
        Resolution result = resolver.resolve(child.id(), List.of(parent, child), List.of(shared));
        assertTrue(result.valid());
        assertEquals(List.of("parent", "child"), result.composition());
        assertTrue(result.visible().isEmpty());
        Revision duplicate = new Revision(child.id(), "child", child.parent(), child.layer(), List.of("shared", "shared"));
        code(resolver.resolve(child.id(), List.of(parent, duplicate), List.of(shared)), Code.DUPLICATE_LAYER);
        Revision missing = new Revision(child.id(), "child", child.parent(), child.layer(), List.of("missing"));
        code(resolver.resolve(child.id(), List.of(parent, missing), List.of(shared)), Code.MISSING_SHARED_LAYER);
    }

    @Test @DisplayName("FS-02: duplicate source identity in ancestry, shared definitions and mixed composition is rejected")
    void duplicateLayerIdentities() {
        Revision base = revision("base", null, List.of());
        Revision child = new Revision(id("child"), "child", Optional.of(base.id()),
                new Layer("base", id("child"), List.of(), List.of()), List.of());
        code(resolve(base, child), Code.DUPLICATE_LAYER);
        Layer shared = layer("s", List.of());
        code(resolver.resolve(base.id(), List.of(base), List.of(shared, shared)), Code.DUPLICATE_LAYER);
        Revision withSharedOwn = new Revision(base.id(), "base", Optional.empty(), base.layer(), List.of("base"));
        code(resolver.resolve(base.id(), List.of(withSharedOwn), List.of(base.layer())), Code.DUPLICATE_LAYER);
    }

    @Test @DisplayName("FS-03: excluding the winning source reveals lower candidates without changing inputs")
    void exclusionRevealsOriginalCandidate() {
        Revision a = revision("a", null, List.of(Entry.file("config/x.cfg", "A")));
        Revision b = revision("b", "a", List.of(Entry.file("config/x.cfg", "B")));
        Revision c = revision("c", "b", List.of(), exclude("skip-b", "b", Scope.single("config/x.cfg")));
        Resolution result = resolve(a, b, c);
        content(result, "config/x.cfg", "A");
        Candidate excluded = result.explain("config/x.cfg").orElseThrow().candidates().get(1);
        assertEquals(Outcome.EXCLUDED, excluded.outcome());
        assertEquals(Optional.of("skip-b"), excluded.reasons().getFirst().action().ruleId());
        content(resolve(a, b), "config/x.cfg", "B");
    }

    @Test @DisplayName("FS-03: source selection receives declaring priority and preserves its original provenance")
    void selectedContentKeepsOriginAndCanBeExcludedLater() {
        Revision a = revision("a", null, List.of(Entry.file("x", "A")));
        Revision b = revision("b", "a", List.of(Entry.file("x", "B")));
        Revision c = revision("c", "b", List.of(), new Select("choose-a", "a", Scope.single("x")));
        Resolution result = resolve(a, b, c);
        content(result, "x", "A");
        VisibleEntry winner = entry(result, "x");
        assertEquals("a", winner.origin().layer());
        assertEquals(ActionKind.SELECT, winner.action().kind());
        assertEquals(id("c"), winner.action().declaration());
        assertEquals(Optional.of("choose-a"), winner.action().ruleId());
        Revision d = revision("d", "c", List.of(), exclude("exclude-origin", "a", null));
        Resolution excluded = resolve(a, b, c, d);
        content(excluded, "x", "B");
        assertEquals(2, excluded.explain("x").orElseThrow().candidates().stream()
                .filter(candidate -> candidate.outcome() == Outcome.EXCLUDED).count());
    }

    @Test @DisplayName("FS-03: selection cannot refer to absent, future, current or excluded source content")
    void selectionFailureModes() {
        Revision a = revision("a", null, List.of(Entry.file("x", "A")));
        code(resolve(a, revision("b", "a", List.of(), new Select("missing", "a", Scope.single("missing")))),
                Code.MISSING_SELECTION);
        code(resolve(a, revision("b", "a", List.of(), new Select("future", "c", Scope.single("x"))),
                revision("c", "b", List.of(Entry.file("x", "C")))), Code.INVALID_SOURCE);
        code(resolve(a, revision("b", "a", List.of(Entry.file("y", "B")),
                new Select("current", "b", Scope.single("x")))), Code.INVALID_SOURCE);
        code(resolve(a, revision("b", "a", List.of(), new Select("chosen", "a", Scope.single("x")),
                exclude("excluded", "a", Scope.single("x")))), Code.EXCLUDED_SELECTION);
        code(resolve(a, revision("b", "a", List.of(), exclude("future", "c", null))), Code.INVALID_SOURCE);
    }

    @Test @DisplayName("FS-03: a deletion hides only low-layer content and descendants can add again")
    void deletionAndReaddition() {
        Revision a = revision("a", null, List.of(Entry.file("mods/old.jar", "old"), Entry.file("mods/keep.jar", "keep")));
        Revision b = revision("b", "a", List.of(), new Delete("remove-old", Scope.single("mods/old.jar")));
        Resolution deleted = resolve(a, b);
        assertTrue(deleted.valid());
        assertFalse(deleted.visible().containsKey(LogicalPath.of("mods/old.jar")));
        Candidate trace = deleted.explain("mods/old.jar").orElseThrow().candidates().getFirst();
        assertEquals(Outcome.HIDDEN, trace.outcome());
        assertEquals(ReasonKind.DELETED, trace.reasons().getFirst().kind());
        content(deleted, "mods/keep.jar", "keep");
        content(resolve(a, b, revision("c", "b", List.of(Entry.file("mods/old.jar", "new")))), "mods/old.jar", "new");
        // Selection may explicitly promote an existing, non-excluded original after a tombstone.
        content(resolve(a, b, revision("c", "b", List.of(),
                new Select("restore", "a", Scope.single("mods/old.jar")))), "mods/old.jar", "old");
    }

    @Test @DisplayName("FS-03: single-path and subtree scopes do not use depth or string-prefix shortcuts")
    void scopedExclusionAndDeletion() {
        Revision a = revision("a", null, List.of(Entry.file("one/two/three/four", "deep"),
                Entry.file("one/two/other", "other"), Entry.file("one/twosome/file", "safe")));
        Revision b = revision("b", "a", List.of(), exclude("branch", "a", Scope.subtree("one/two/three")),
                new Delete("single", Scope.single("one/two/other")));
        Resolution result = resolve(a, b);
        content(result, "one/twosome/file", "safe");
        assertFalse(result.visible().containsKey(LogicalPath.of("one/two/three/four")));
        assertFalse(result.visible().containsKey(LogicalPath.of("one/two")), "No phantom implicit empty directory");
        assertEquals(Kind.DIRECTORY, entry(result, "one").entry().kind());
    }

    @Test @DisplayName("FS-03: exclude-all is source-local and does not ban later additions")
    void excludingAllPathsDoesNotBanDescendants() {
        Revision a = revision("a", null, List.of(Entry.file("x", "a"), Entry.file("nested/y", "a")));
        Revision b = revision("b", "a", List.of(Entry.file("x", "b")), exclude("all-a", "a", null));
        Resolution result = resolve(a, b);
        content(result, "x", "b");
        assertFalse(result.visible().containsKey(LogicalPath.of("nested")));
        assertTrue(resolve(a).valid());
    }

    @Test @DisplayName("FS-03: same-layer overlapping delete, content, selection and replacement are diagnosed")
    void contradictoryOperationsNeverUseFieldOrder() {
        Revision a = revision("a", null, List.of(Entry.file("x/y", "a")));
        code(resolve(a, revision("b", "a", List.of(Entry.file("x/y", "b")),
                new Delete("delete", Scope.subtree("x")))), Code.CONTRADICTORY_ACTIONS);
        code(resolve(a, revision("b", "a", List.of(Entry.file("x/y", "b")),
                new Delete("delete", Scope.single("x")))), Code.CONTRADICTORY_ACTIONS);
        code(resolve(a, revision("b", "a", List.of(), new Select("select", "a", Scope.subtree("x")),
                new Delete("delete", Scope.single("x/y")))), Code.CONTRADICTORY_ACTIONS);
        code(resolve(a, revision("b", "a", List.of(Entry.file("x/new", "b")),
                new Replace("replace", LogicalPath.of("x"), List.of()))), Code.CONTRADICTORY_ACTIONS);
        code(resolve(a, revision("b", "a", List.of(), new Select("same", "a", Scope.single("x/y")),
                new Select("same", "a", Scope.single("x/y")))), Code.DUPLICATE_RULE);
        // Rule identifiers are opaque; an identifier resembling an internal content label grants no exemption.
        code(resolve(a, revision("b", "a", List.of(Entry.file("x/y", "b")),
                new Delete("content:trick", Scope.subtree("x")))), Code.CONTRADICTORY_ACTIONS);
    }

    @Test @DisplayName("FS-04: ordinary directories merge deep files, rather than implicitly replace subtrees")
    void ordinaryDeepDirectoryMerge() {
        Revision a = revision("a", null, List.of(Entry.file("one/two/three/x", "a"), Entry.file("one/two/keep", "keep")));
        Resolution result = resolve(a, revision("b", "a", List.of(Entry.file("one/two/three/x", "b"),
                Entry.file("one/two/three/new", "new"))));
        content(result, "one/two/three/x", "b");
        content(result, "one/two/keep", "keep");
        content(result, "one/two/three/new", "new");
        assertTrue(entry(result, "one/two/three").implicitDirectory());
    }

    @Test @DisplayName("FS-04: file/directory conflicts are explicit in both directions and within a layer")
    void typeConflicts() {
        Revision file = revision("a", null, List.of(Entry.file("node", "file")));
        code(resolve(file, revision("b", "a", List.of(Entry.file("node/deep/child", "child")))), Code.TYPE_CONFLICT);
        Revision dir = revision("a", null, List.of(Entry.file("node/deep/child", "child")));
        code(resolve(dir, revision("b", "a", List.of(Entry.file("node", "file")))), Code.TYPE_CONFLICT);
        code(resolve(revision("a", null, List.of(Entry.file("node", "file"), Entry.file("node/child", "child")))),
                Code.TYPE_CONFLICT);
        code(resolve(revision("a", null, List.of(Entry.file("same", "file"), Entry.directory("same")))), Code.TYPE_CONFLICT);
        code(resolve(revision("a", null, List.of(Entry.file("same", "first"), Entry.file("same", "second")))),
                Code.DUPLICATE_ENTRY);
    }

    @Test @DisplayName("FS-03 / FS-04: final source exclusion can resolve conflicting shared alternatives")
    void finalExclusionResolvesCrossLayerTypeConflict() {
        Layer file = layer("shared-file", List.of(Entry.file("node", "file")));
        Layer directory = layer("shared-directory", List.of(Entry.file("node/child", "child")));
        Revision unresolved = new Revision(id("target"), "target", Optional.empty(), layer("target", List.of()),
                List.of(file.id(), directory.id()));
        code(resolver.resolve(unresolved.id(), List.of(unresolved), List.of(file, directory)), Code.TYPE_CONFLICT);
        Revision resolved = new Revision(unresolved.id(), "target", Optional.empty(),
                layer("target", List.of(), exclude("exclude-directory", directory.id(), null)), unresolved.sharedLayers());
        Resolution result = resolver.resolve(resolved.id(), List.of(resolved), List.of(directory, file));
        content(result, "node", "file");
        assertEquals(1, result.visible().size());
        assertEquals(Outcome.EXCLUDED, result.explain("node/child").orElseThrow().candidates().getFirst().outcome());
    }

    @Test @DisplayName("FS-04: descendant subtree replacement can resolve conflicting ancestor alternatives")
    void finalReplacementResolvesCrossLayerTypeConflict() {
        Revision a = revision("a", null, List.of(Entry.file("node", "old")));
        Revision b = revision("b", "a", List.of(Entry.file("node/child", "old-child")));
        code(resolve(a, b), Code.TYPE_CONFLICT);
        Revision c = revision("c", "b", List.of(), new Replace("resolve-node", LogicalPath.of("node"),
                List.of(Entry.file("node", "new"))));
        Resolution result = resolve(a, b, c);
        content(result, "node", "new");
        assertEquals(1, result.visible().size());
        assertEquals(ReasonKind.REPLACED,
                result.explain("node/child").orElseThrow().candidates().getFirst().reasons().getFirst().kind());
    }

    @Test @DisplayName("FS-04: a descendant cannot conceal an internally contradictory layer declaration")
    void sameLayerTypeConflictRemainsAnErrorAfterReplacement() {
        Revision a = revision("a", null, List.of(Entry.file("node", "file")),
                new Replace("contradictory-child", LogicalPath.of("node/child"), List.of(Entry.file("node/child", "child"))));
        Revision b = revision("b", "a", List.of(), new Replace("later-hide", LogicalPath.of("node"), List.of()));
        code(resolve(a, b), Code.TYPE_CONFLICT);
        Revision internallyInvalid = revision("a", null,
                List.of(Entry.file("node", "file"), Entry.file("node/child", "child")));
        code(resolve(internallyInvalid, b), Code.TYPE_CONFLICT);
    }

    @Test @DisplayName("FS-04: explicit subtree replacement converts types and leaves no hidden old descendants")
    void explicitReplacementConvertsTypes() {
        Revision a = revision("a", null, List.of(Entry.file("node/deep/old", "old"), Entry.file("node2/keep", "keep")));
        Revision b = revision("b", "a", List.of(), new Replace("to-file", LogicalPath.of("node"), List.of(Entry.file("node", "new"))));
        Resolution file = resolve(a, b);
        content(file, "node", "new");
        content(file, "node2/keep", "keep");
        assertFalse(file.visible().containsKey(LogicalPath.of("node/deep/old")));
        assertEquals(ReasonKind.REPLACED, file.explain("node/deep/old").orElseThrow().candidates().getFirst().reasons().getFirst().kind());
        Revision c = revision("c", "b", List.of(), new Replace("to-directory", LogicalPath.of("node"),
                List.of(Entry.file("node/deeper/new", "child"))));
        Resolution directory = resolve(a, b, c);
        content(directory, "node/deeper/new", "child");
        assertEquals(Kind.DIRECTORY, entry(directory, "node").entry().kind());
        code(resolve(a, revision("b", "a", List.of(), new Replace("outside", LogicalPath.of("node"),
                List.of(Entry.file("other/file", "bad"))))), Code.REPLACEMENT_OUTSIDE_SUBTREE);
    }

    @Test @DisplayName("FS-03 / FS-04: subtree selection and replacement support empty directories and all descendants")
    void subtreeSelectionAndEmptyReplacement() {
        Revision a = revision("a", null, List.of(Entry.directory("empty"), Entry.file("tree/deep/x", "a")));
        Revision b = revision("b", "a", List.of(Entry.file("tree/deep/x", "b")));
        Revision c = revision("c", "b", List.of(), new Select("tree-a", "a", Scope.subtree("tree")),
                new Select("empty-a", "a", Scope.single("empty")));
        Resolution result = resolve(a, b, c);
        content(result, "tree/deep/x", "a");
        assertEquals(Kind.DIRECTORY, entry(result, "empty").entry().kind());
        Resolution empty = resolve(a, b, c, revision("d", "c", List.of(),
                new Replace("remove-tree", LogicalPath.of("tree"), List.of())));
        assertTrue(empty.valid());
        assertFalse(empty.visible().containsKey(LogicalPath.of("tree")));
    }

    @Test @DisplayName("FS-03 / FS-04: deleting one directory entry does not implicitly delete its children")
    void singleDirectoryScopeKeepsChildrenAndStructuralParents() {
        Revision a = revision("a", null, List.of(Entry.directory("tree"), Entry.file("tree/deep/x", "a")));
        Resolution deleted = resolve(a, revision("b", "a", List.of(), new Delete("directory-only", Scope.single("tree"))));
        content(deleted, "tree/deep/x", "a");
        assertEquals(Kind.DIRECTORY, entry(deleted, "tree").entry().kind());
        Resolution excluded = resolve(a, revision("b", "a", List.of(), exclude("directory-only", "a", Scope.single("tree"))));
        content(excluded, "tree/deep/x", "a");
        assertEquals(Kind.DIRECTORY, entry(excluded, "tree").entry().kind());
    }

    @Test @DisplayName("FS-03 / FS-04: excluded implicit subtrees cannot be selected and later subtree writes remain legal")
    void excludedImplicitDirectoriesAndSubtreeReaddition() {
        Revision a = revision("a", null, List.of(Entry.file("tree/deep/file", "a")));
        code(resolve(a, revision("b", "a", List.of(), exclude("exclude-file", "a", Scope.single("tree/deep/file")),
                new Select("choose-directory", "a", Scope.subtree("tree")))), Code.EXCLUDED_SELECTION);
        Revision b = revision("b", "a", List.of(), new Delete("delete-tree", Scope.subtree("tree")));
        Resolution result = resolve(a, b, revision("c", "b", List.of(Entry.file("tree/new/deep/file", "c"))));
        content(result, "tree/new/deep/file", "c");
        assertFalse(result.visible().containsKey(LogicalPath.of("tree/deep")));
    }

    @Test @DisplayName("FS-04: logical case distinctions and long segment chains survive resolution unchanged")
    void caseAndPathDepthAreNotHostDependent() {
        String deep = String.join("/", Collections.nCopies(200, "segment")) + "/file";
        Resolution result = resolve(revision("a", null, List.of(Entry.file("config/A.cfg", "upper"),
                Entry.file("config/a.cfg", "lower"), Entry.file(deep, "deep"))));
        content(result, "config/A.cfg", "upper");
        content(result, "config/a.cfg", "lower");
        content(result, deep, "deep");
        assertEquals(200, LogicalPath.of(deep).ancestors().size());
    }

    @Test @DisplayName("FS-01 through FS-04: entry, rule, repository and shared catalog order do not change resolution")
    void deterministicUnderInputPermutations() {
        List<Entry> entries = List.of(Entry.file("tree/a", "a"), Entry.file("tree/b", "b"), Entry.file("other/c", "c"));
        List<Rule> rules = List.of(exclude("skip-b", "base", Scope.single("tree/b")),
                new Select("select-a", "base", Scope.single("tree/a")), new Delete("delete-c", Scope.single("other/c")));
        Revision base = revision("base", null, entries);
        Revision target = revision("target", "base", List.of(Entry.file("tree/new", "n")), rules.toArray(Rule[]::new));
        Resolution expected = resolve(base, target);
        Random random = new Random(17);
        for (int i = 0; i < 12; i++) {
            List<Entry> shuffledEntries = new ArrayList<>(entries);
            List<Rule> shuffledRules = new ArrayList<>(rules);
            Collections.shuffle(shuffledEntries, random);
            Collections.shuffle(shuffledRules, random);
            Revision shuffledBase = revision("base", null, shuffledEntries);
            Revision shuffledTarget = revision("target", "base", target.layer().entries(), shuffledRules.toArray(Rule[]::new));
            assertEquals(expected, resolver.resolve(target.id(), List.of(shuffledTarget, shuffledBase), List.of()));
        }
    }

    @Test @DisplayName("FS-03 / FS-04: conflicting input permutations yield identical structured diagnostics")
    void deterministicInvalidInputs() {
        List<Entry> entries = List.of(Entry.file("x", "file"), Entry.file("x/child", "child"), Entry.directory("x"));
        Revision first = revision("a", null, entries);
        List<Entry> reversed = new ArrayList<>(entries);
        Collections.reverse(reversed);
        assertEquals(resolve(first), resolve(revision("a", null, reversed)));
        Revision base = revision("base", null, List.of(Entry.file("x", "a")));
        Rule one = new Select("duplicate", "base", Scope.single("x"));
        Rule two = new Delete("duplicate", Scope.single("x"));
        assertEquals(resolve(base, revision("child", "base", List.of(), one, two)),
                resolve(base, revision("child", "base", List.of(), two, one)));
    }

    @Test @DisplayName("FS-01 through FS-04: input lists and all published result collections are immutable snapshots")
    void immutableSnapshots() {
        List<Entry> entries = new ArrayList<>(List.of(Entry.file("x", "a")));
        List<Rule> rules = new ArrayList<>();
        Layer layer = new Layer("a", id("a"), entries, rules);
        List<String> shares = new ArrayList<>();
        Revision a = new Revision(id("a"), "a", Optional.empty(), layer, shares);
        entries.clear();
        rules.add(new Delete("later", Scope.single("x")));
        shares.add("missing");
        Resolution result = resolve(a);
        content(result, "x", "a");
        assertThrows(UnsupportedOperationException.class, () -> result.visible().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.explanations().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.composition().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.diagnostics().clear());
        Explanation explanation = result.explain("x").orElseThrow();
        assertThrows(UnsupportedOperationException.class, () -> explanation.candidates().clear());
        assertThrows(UnsupportedOperationException.class, () -> explanation.candidates().getFirst().reasons().clear());
        assertThrows(UnsupportedOperationException.class, () -> a.layer().entries().clear());
        List<Entry> replacementEntries = new ArrayList<>(List.of(Entry.file("x", "b")));
        Replace replace = new Replace("replace", LogicalPath.of("x"), replacementEntries);
        replacementEntries.clear();
        content(resolve(a, revision("b", "a", List.of(), replace)), "x", "b");
        assertEquals(Map.of(LogicalPath.of("x"), entry(result, "x")), result.visible());
    }
}
