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
import static org.zeropointlauncher.layerfs.WorkspaceModel.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkspacePlannerTest {
    private final WorkspacePlanner planner = new WorkspacePlanner();
    private static LogicalPath path(String path) { return LogicalPath.of(path); }

    @Test @DisplayName("FS-05: same-baseline plans preserve added, modified, deleted and type-changed private data")
    void classifyPrivateDelta() {
        Map<LogicalPath, Node> baseline = Map.of(path("modified"), Node.file("old"), path("deleted"), Node.file("old"),
                path("typed"), Node.file("old"), path("same"), Node.file("same"), path("empty"), Node.directory());
        Map<LogicalPath, Node> current = Map.of(path("modified"), Node.file("new"), path("added"), Node.file("new"),
                path("typed"), Node.directory(), path("same"), Node.file("same"), path("empty"), Node.directory());
        Baseline saved = new Baseline("revision", "resolution", baseline);
        Plan result = planner.plan(Optional.of(saved), saved, current);
        assertEquals(Decision.PRESERVE, result.decision());
        assertEquals(Change.MODIFIED, result.delta().get(path("modified")).change());
        assertEquals(Change.DELETED, result.delta().get(path("deleted")).change());
        assertEquals(Change.ADDED, result.delta().get(path("added")).change());
        assertEquals(Change.TYPE_CHANGED, result.delta().get(path("typed")).change());
        assertEquals(4, result.delta().size());
        assertEquals(Optional.empty(), result.delta().get(path("deleted")).current());
    }

    @Test @DisplayName("FS-05: a baseline change in identity or bytes is rejected instead of silently upgrading")
    void changedBaselineIsNotAPreservePlan() {
        Baseline saved = new Baseline("r1", "tree", Map.of(path("x"), Node.file("old")));
        for (Baseline changed : new Baseline[] {
                new Baseline("r2", "tree", saved.entries()), new Baseline("r1", "other", saved.entries()),
                new Baseline("r1", "tree", Map.of(path("x"), Node.file("new"))) }) {
            assertEquals(Decision.REJECT_BASELINE_CHANGE,
                    planner.plan(Optional.of(saved), changed, Map.of()).decision());
        }
    }

    @Test @DisplayName("FS-05: initial copy is allowed only for an empty workspace")
    void initialPreparationHasNoUnmanagedOverwrite() {
        Baseline desired = new Baseline("r1", "tree", Map.of(path("x"), Node.file("bytes")));
        assertEquals(Decision.INITIAL_COPY, planner.plan(Optional.empty(), desired, Map.of()).decision());
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan(Optional.empty(), desired, Map.of(path("unknown"), Node.file("user"))));
    }

    @Test @DisplayName("FS-05: baseline, decisions and delta are immutable snapshots")
    void snapshotsCannotBeMutatedByCallers() {
        Map<LogicalPath, Node> input = new HashMap<>(Map.of(path("x"), Node.file("a")));
        Baseline baseline = new Baseline("r1", "tree", input);
        input.clear();
        assertEquals(1, baseline.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> baseline.entries().clear());
        Plan plan = planner.plan(Optional.of(baseline), baseline, Map.of());
        assertThrows(UnsupportedOperationException.class, () -> plan.delta().clear());
    }
}
