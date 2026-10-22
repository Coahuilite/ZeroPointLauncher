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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable input and output values for the pure layer resolver. Content references are opaque. */
public final class LayerFs {
    private LayerFs() {}

    private static String identifier(String value) {
        if (Objects.requireNonNull(value, "identifier").isBlank()) {
            throw new IllegalArgumentException("An identifier cannot be blank");
        }
        return value;
    }

    public record RevisionId(String instanceId, String revision) implements Comparable<RevisionId> {
        public RevisionId {
            instanceId = identifier(instanceId);
            revision = identifier(revision);
        }

        @Override
        public int compareTo(RevisionId other) {
            int order = instanceId.compareTo(other.instanceId);
            return order == 0 ? revision.compareTo(other.revision) : order;
        }
    }

    public enum Kind { FILE, DIRECTORY }

    public record Entry(LogicalPath path, Kind kind, Optional<String> content) {
        public Entry {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(content, "content");
            if ((kind == Kind.FILE) != content.isPresent()) {
                throw new IllegalArgumentException("Only files have a content reference");
            }
        }

        public static Entry file(String path, String content) {
            return new Entry(LogicalPath.of(path), Kind.FILE, Optional.of(content));
        }

        public static Entry directory(String path) {
            return new Entry(LogicalPath.of(path), Kind.DIRECTORY, Optional.empty());
        }
    }

    /** SINGLE addresses one entry; SUBTREE includes that entry and all descendants. */
    public record Scope(LogicalPath path, boolean subtree) {
        public Scope {
            Objects.requireNonNull(path, "path");
        }

        public static Scope single(String path) { return new Scope(LogicalPath.of(path), false); }
        public static Scope subtree(String path) { return new Scope(LogicalPath.of(path), true); }
        public boolean matches(LogicalPath candidate) {
            return subtree ? path.contains(candidate) : path.equals(candidate);
        }
    }

    public sealed interface Rule permits Exclude, Select, Delete, Replace {
        String id();
    }

    /** Empty scope excludes all paths of the specified, already composed source. */
    public record Exclude(String id, String source, Optional<Scope> scope) implements Rule {
        public Exclude {
            id = identifier(id);
            source = identifier(source);
            Objects.requireNonNull(scope, "scope");
        }
    }

    public record Select(String id, String source, Scope scope) implements Rule {
        public Select {
            id = identifier(id);
            source = identifier(source);
            Objects.requireNonNull(scope, "scope");
        }
    }

    public record Delete(String id, Scope scope) implements Rule {
        public Delete {
            id = identifier(id);
            Objects.requireNonNull(scope, "scope");
        }
    }

    /** Entries use full logical paths including root, not paths relative to the replacement root. */
    public record Replace(String id, LogicalPath root, List<Entry> entries) implements Rule {
        public Replace {
            id = identifier(id);
            Objects.requireNonNull(root, "root");
            entries = List.copyOf(entries);
        }
    }

    public record Layer(String id, RevisionId declaration, List<Entry> entries, List<Rule> rules) {
        public Layer {
            id = identifier(id);
            Objects.requireNonNull(declaration, "declaration");
            entries = List.copyOf(entries);
            rules = List.copyOf(rules);
        }
    }

    /** sharedLayers is this revision's complete explicit list, never inherited dynamically. */
    public record Revision(RevisionId id, String displayName, Optional<RevisionId> parent,
                           Layer layer, List<String> sharedLayers) {
        public Revision {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(layer, "layer");
            sharedLayers = List.copyOf(sharedLayers);
            if (!id.equals(layer.declaration())) {
                throw new IllegalArgumentException("The content layer must belong to its revision");
            }
            sharedLayers.forEach(LayerFs::identifier);
        }
    }

    public enum ActionKind { WRITE, SELECT, DELETE, REPLACE, EXCLUDE }

    /** Empty ruleId denotes an ordinary content write. Declaration is stable, not a display name. */
    public record Action(ActionKind kind, String layer, RevisionId declaration, Optional<String> ruleId) {}

    public record Origin(String layer, RevisionId revision) {}
    public record VisibleEntry(Entry entry, Origin origin, Action action, boolean implicitDirectory) {}
    public enum Outcome { VISIBLE, SHADOWED, EXCLUDED, HIDDEN, CONFLICT }
    public enum ReasonKind { SHADOWED, EXCLUDED, DELETED, REPLACED }
    public record Reason(ReasonKind kind, Action action) {}
    public record Candidate(Entry entry, Origin origin, Action action, boolean implicitDirectory,
                            Outcome outcome, List<Reason> reasons) {
        public Candidate { reasons = List.copyOf(reasons); }
    }

    public record Explanation(LogicalPath path, Optional<VisibleEntry> winner, List<Candidate> candidates) {
        public Explanation { candidates = List.copyOf(candidates); }
    }

    public enum Code {
        DUPLICATE_REVISION, MISSING_REVISION, INHERITANCE_CYCLE, MISSING_SHARED_LAYER,
        DUPLICATE_LAYER, DUPLICATE_RULE, DUPLICATE_ENTRY, CONTRADICTORY_ACTIONS,
        TYPE_CONFLICT, INVALID_SOURCE, MISSING_SELECTION, EXCLUDED_SELECTION,
        REPLACEMENT_OUTSIDE_SUBTREE
    }

    public record Diagnostic(Code code, Optional<LogicalPath> path, Optional<RevisionId> revision,
                             List<String> subjects, String message) {
        public Diagnostic { subjects = List.copyOf(subjects); }
    }

    /** A conflicted result has no usable tree. Explanations retain candidates from valid declarations. */
    public record Resolution(List<String> composition, Map<LogicalPath, VisibleEntry> visible,
                             Map<LogicalPath, Explanation> explanations, List<Diagnostic> diagnostics) {
        public Resolution {
            composition = List.copyOf(composition);
            visible = Collections.unmodifiableMap(new TreeMap<>(visible));
            explanations = Collections.unmodifiableMap(new TreeMap<>(explanations));
            diagnostics = List.copyOf(diagnostics);
        }

        public boolean valid() { return diagnostics.isEmpty(); }
        public Optional<Explanation> explain(String path) {
            return Optional.ofNullable(explanations.get(LogicalPath.of(path)));
        }
    }
}
