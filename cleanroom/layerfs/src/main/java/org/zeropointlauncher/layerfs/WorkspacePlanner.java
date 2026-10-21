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

import static org.zeropointlauncher.layerfs.WorkspaceModel.*;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/** This slice never upgrades an applied baseline and never repairs user changes by overwriting them. */
public final class WorkspacePlanner {
    public Plan plan(Optional<Baseline> applied, Baseline desired, Map<LogicalPath, Node> observed) {
        if (applied.isEmpty()) {
            if (!observed.isEmpty()) throw new IllegalArgumentException("Initial copying requires an empty workspace");
            return new Plan(Decision.INITIAL_COPY, Map.of());
        }
        if (!applied.orElseThrow().equals(desired)) {
            return new Plan(Decision.REJECT_BASELINE_CHANGE, Map.of());
        }
        return new Plan(Decision.PRESERVE, compare(desired.entries(), observed));
    }

    public Map<LogicalPath, Delta> compare(Map<LogicalPath, Node> baseline, Map<LogicalPath, Node> current) {
        Map<LogicalPath, Delta> changes = new TreeMap<>();
        TreeSet<LogicalPath> paths = new TreeSet<>(baseline.keySet());
        paths.addAll(current.keySet());
        for (LogicalPath path : paths) {
            Node before = baseline.get(path);
            Node after = current.get(path);
            if (java.util.Objects.equals(before, after)) continue;
            Change change = before == null ? Change.ADDED : after == null ? Change.DELETED
                    : before.kind() != after.kind() ? Change.TYPE_CHANGED : Change.MODIFIED;
            changes.put(path, new Delta(change, Optional.ofNullable(before), Optional.ofNullable(after)));
        }
        return WorkspaceModel.snapshot(changes);
    }
}
