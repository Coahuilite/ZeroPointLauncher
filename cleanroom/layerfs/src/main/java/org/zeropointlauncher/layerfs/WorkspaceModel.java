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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Content snapshots and unpublished workspace differences, separate from inheritable revisions. */
public final class WorkspaceModel {
    private WorkspaceModel() {}

    public record Node(LayerFs.Kind kind, Optional<String> contentIdentity) {
        public Node {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(contentIdentity, "contentIdentity");
            if ((kind == LayerFs.Kind.FILE) != contentIdentity.isPresent()) {
                throw new IllegalArgumentException("Only files have a content identity");
            }
            contentIdentity.ifPresent(identity -> {
                if (identity.isBlank()) throw new IllegalArgumentException("Empty content identity");
            });
        }
        public static Node file(String identity) { return new Node(LayerFs.Kind.FILE, Optional.of(identity)); }
        public static Node directory() { return new Node(LayerFs.Kind.DIRECTORY, Optional.empty()); }
    }

    public record Baseline(String revisionIdentity, String resolutionIdentity, Map<LogicalPath, Node> entries) {
        public Baseline {
            Objects.requireNonNull(revisionIdentity, "revisionIdentity");
            Objects.requireNonNull(resolutionIdentity, "resolutionIdentity");
            entries = snapshot(entries);
        }
    }

    public enum Change { ADDED, MODIFIED, DELETED, TYPE_CHANGED }
    public record Delta(Change change, Optional<Node> baseline, Optional<Node> current) {}
    public enum Decision { INITIAL_COPY, PRESERVE, REJECT_BASELINE_CHANGE }
    public record Plan(Decision decision, Map<LogicalPath, Delta> delta) {
        public Plan { delta = snapshot(delta); }
    }

    static <T> Map<LogicalPath, T> snapshot(Map<LogicalPath, T> entries) {
        Objects.requireNonNull(entries, "entries");
        TreeMap<LogicalPath, T> copy = new TreeMap<>();
        entries.forEach((path, value) -> copy.put(Objects.requireNonNull(path), Objects.requireNonNull(value)));
        return Collections.unmodifiableMap(copy);
    }
}
