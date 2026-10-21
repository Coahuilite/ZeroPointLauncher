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

import static org.zeropointlauncher.layerfs.LayerFs.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/** Pure, stateless resolver. Neither graph traversal nor path processing uses recursion. */
public final class LayerResolver {
    public Resolution resolve(RevisionId target, Collection<Revision> revisions, Collection<Layer> sharedLayers) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<RevisionId, Revision> repository = new TreeMap<>();
        for (Revision revision : revisions) {
            if (repository.putIfAbsent(revision.id(), revision) != null) {
                diagnostic(diagnostics, Code.DUPLICATE_REVISION, null, revision.id(),
                        List.of(revision.id().toString()), "Revision identity is duplicated");
            }
        }
        Map<String, Layer> shared = new TreeMap<>();
        for (Layer layer : sharedLayers) {
            if (shared.putIfAbsent(layer.id(), layer) != null) {
                diagnostic(diagnostics, Code.DUPLICATE_LAYER, null, null,
                        List.of(layer.id()), "Shared layer identity is duplicated");
            }
        }
        List<Revision> chain = new ArrayList<>();
        Set<RevisionId> visited = new HashSet<>();
        RevisionId cursor = target;
        while (cursor != null) {
            if (!visited.add(cursor)) {
                diagnostic(diagnostics, Code.INHERITANCE_CYCLE, null, cursor,
                        chain.stream().map(r -> r.id().toString()).toList(), "Parent revisions form a cycle");
                break;
            }
            Revision revision = repository.get(cursor);
            if (revision == null) {
                diagnostic(diagnostics, Code.MISSING_REVISION, null, cursor,
                        List.of(cursor.toString()), "A fixed revision reference does not exist");
                break;
            }
            chain.add(revision);
            cursor = revision.parent().orElse(null);
        }
        if (!diagnostics.isEmpty()) {
            return result(List.of(), new TreeMap<>(), diagnostics);
        }
        List<Layer> composition = new ArrayList<>();
        // Only the target revision owns the complete shared list; parent lists are not followed.
        for (String source : chain.getFirst().sharedLayers()) {
            Layer layer = shared.get(source);
            if (layer == null) {
                diagnostic(diagnostics, Code.MISSING_SHARED_LAYER, null, target,
                        List.of(source), "Explicit shared layer does not exist");
            } else {
                composition.add(layer);
            }
        }
        Collections.reverse(chain);
        chain.forEach(revision -> composition.add(revision.layer()));
        Set<String> usedLayers = new HashSet<>();
        for (Layer layer : composition) {
            if (!usedLayers.add(layer.id())) {
                diagnostic(diagnostics, Code.DUPLICATE_LAYER, null, layer.declaration(),
                        List.of(layer.id()), "A layer occurs more than once in the composition");
            }
        }
        List<String> order = composition.stream().map(Layer::id).toList();
        if (!diagnostics.isEmpty()) {
            return result(order, new TreeMap<>(), diagnostics);
        }

        Map<LogicalPath, List<WorkingCandidate>> history = new TreeMap<>();
        Map<String, List<WorkingCandidate>> originals = new LinkedHashMap<>();
        for (Layer layer : composition) {
            int errorsBeforeValidation = diagnostics.size();
            validateLayer(layer, diagnostics);
            if (diagnostics.size() != errorsBeforeValidation) {
                // Do not interpret a contradictory declaration in arbitrary operation order.
                originals.put(layer.id(), List.of());
                continue;
            }
            List<Rule> rules = layer.rules().stream().sorted(Comparator.comparing(Rule::id)).toList();
            for (Rule rule : rules) {
                if (rule instanceof Exclude exclude) {
                    if (!originals.containsKey(exclude.source())) {
                        invalidSource(diagnostics, layer, exclude.id(), exclude.source());
                        continue;
                    }
                    Action action = action(layer, ActionKind.EXCLUDE, exclude.id());
                    forEach(history, candidate -> !candidate.implicit && candidate.origin.layer().equals(exclude.source())
                                    && exclude.scope().map(scope -> scope.matches(candidate.entry.path())).orElse(true),
                            candidate -> candidate.reasons.add(new Reason(ReasonKind.EXCLUDED, action)));
                }
            }
            List<WorkingCandidate> own = new ArrayList<>();
            addEntries(layer.entries(), layer, action(layer, ActionKind.WRITE, null), history, own);
            for (Rule rule : rules) {
                if (rule instanceof Delete delete) {
                    hide(history, delete.scope()::matches, layer, ActionKind.DELETE, delete.id(), ReasonKind.DELETED);
                } else if (rule instanceof Replace replace) {
                    hide(history, replace.root()::contains, layer, ActionKind.REPLACE, replace.id(), ReasonKind.REPLACED);
                    addEntries(replace.entries(), layer, action(layer, ActionKind.REPLACE, replace.id()), history, own);
                } else if (rule instanceof Select select) {
                    List<WorkingCandidate> source = originals.get(select.source());
                    if (source == null) {
                        invalidSource(diagnostics, layer, select.id(), select.source());
                        continue;
                    }
                    List<WorkingCandidate> matches = source.stream()
                            .filter(candidate -> select.scope().matches(candidate.entry.path())).toList();
                    List<WorkingCandidate> selectable = matches.stream().filter(WorkingCandidate::selectable).toList();
                    if (selectable.isEmpty()) {
                        diagnostic(diagnostics, matches.isEmpty() ? Code.MISSING_SELECTION : Code.EXCLUDED_SELECTION,
                                select.scope().path(), layer.declaration(), List.of(select.id(), select.source()),
                                "Selection requires existing, non-excluded source content");
                        continue;
                    }
                    // Selections never invent a new content origin.
                    List<Entry> entries = selectable.stream()
                            .filter(candidate -> !candidate.implicit || !select.scope().subtree())
                            .map(candidate -> candidate.entry).distinct().toList();
                    addEntries(entries, new Origin(select.source(), source.getFirst().origin.revision()),
                            action(layer, ActionKind.SELECT, select.id()), history, new ArrayList<>());
                }
            }
            originals.put(layer.id(), own);
            // Invalid declarations stay invalid even if a descendant later hides their content.
            detectTypes(history, layer.declaration(), diagnostics,
                    candidate -> candidate.action.layer().equals(layer.id()));
        }
        // Cross-layer alternatives may be deliberately resolved by later exclusions or replacement.
        detectTypes(history, target, diagnostics, candidate -> true);
        return result(order, history, diagnostics);
    }

    private static void invalidSource(List<Diagnostic> diagnostics, Layer layer, String rule, String source) {
        diagnostic(diagnostics, Code.INVALID_SOURCE, null, layer.declaration(), List.of(rule, source),
                "A rule may only reference an already composed source");
    }

    private static void hide(Map<LogicalPath, List<WorkingCandidate>> history, Predicate<LogicalPath> scope,
                             Layer layer, ActionKind kind, String id, ReasonKind reason) {
        Action action = action(layer, kind, id);
        // Same-layer overlap is diagnosed separately; hiding only targets lower precedence.
        forEach(history, candidate -> !candidate.implicit && !candidate.action.layer().equals(layer.id())
                        && scope.test(candidate.entry.path()),
                candidate -> candidate.reasons.add(new Reason(reason, action)));
    }

    private static void forEach(Map<LogicalPath, List<WorkingCandidate>> history,
                                Predicate<WorkingCandidate> filter,
                                java.util.function.Consumer<WorkingCandidate> consumer) {
        history.values().forEach(candidates -> candidates.stream().filter(filter).forEach(consumer));
    }

    private static void addEntries(List<Entry> entries, Layer layer, Action action,
                                   Map<LogicalPath, List<WorkingCandidate>> history, List<WorkingCandidate> own) {
        addEntries(entries, new Origin(layer.id(), layer.declaration()), action, history, own);
    }

    private static void addEntries(List<Entry> entries, Origin origin, Action action,
                                   Map<LogicalPath, List<WorkingCandidate>> history, List<WorkingCandidate> own) {
        List<WorkingCandidate> explicit = new ArrayList<>();
        for (Entry entry : entries.stream().sorted(Comparator.comparing(Entry::path)
                .thenComparing(Entry::kind).thenComparing(e -> e.content().orElse(""))).toList()) {
            WorkingCandidate candidate = new WorkingCandidate(entry, origin, action, false, List.of());
            explicit.add(candidate);
            history.computeIfAbsent(entry.path(), ignored -> new ArrayList<>()).add(candidate);
            own.add(candidate);
        }
        Map<LogicalPath, List<WorkingCandidate>> supports = new TreeMap<>();
        for (WorkingCandidate candidate : explicit) {
            for (LogicalPath ancestor : candidate.entry.path().ancestors()) {
                supports.computeIfAbsent(ancestor, ignored -> new ArrayList<>()).add(candidate);
            }
        }
        supports.forEach((path, children) -> {
            WorkingCandidate directory = new WorkingCandidate(Entry.directory(path.value()), origin, action, true, children);
            history.computeIfAbsent(path, ignored -> new ArrayList<>()).add(directory);
            own.add(directory);
        });
    }

    private static void validateLayer(Layer layer, List<Diagnostic> diagnostics) {
        validateEntries(layer.entries(), layer, diagnostics);
        Set<String> ids = new HashSet<>();
        List<Operation> operations = new ArrayList<>();
        layer.entries().forEach(entry -> operations.add(new Operation("content:" + entry.path(),
                new Scope(entry.path(), false), false, true)));
        for (Rule rule : layer.rules().stream().sorted(Comparator.comparing(Rule::id)).toList()) {
            if (!ids.add(rule.id())) {
                diagnostic(diagnostics, Code.DUPLICATE_RULE, null, layer.declaration(), List.of(rule.id()),
                        "Rule identity is duplicated within its declaration");
            }
            if (rule instanceof Select select) {
                operations.add(new Operation(select.id(), select.scope(), false, false));
            } else if (rule instanceof Delete delete) {
                operations.add(new Operation(delete.id(), delete.scope(), true, false));
            } else if (rule instanceof Replace replace) {
                operations.add(new Operation(replace.id(), new Scope(replace.root(), true), false, false));
                validateEntries(replace.entries(), layer, diagnostics);
                replace.entries().stream().filter(entry -> !replace.root().contains(entry.path())).forEach(entry ->
                        diagnostic(diagnostics, Code.REPLACEMENT_OUTSIDE_SUBTREE, entry.path(), layer.declaration(),
                                List.of(replace.id()), "Replacement entry lies outside its root"));
            }
        }
        operations.sort(Comparator.comparing(Operation::id));
        List<Operation> contents = operations.stream().filter(Operation::content).toList();
        List<Operation> actions = operations.stream().filter(operation -> !operation.content()).toList();
        for (int i = 0; i < actions.size(); i++) {
            for (Operation content : contents) {
                checkOverlap(actions.get(i), content, layer, diagnostics);
            }
            for (int j = i + 1; j < actions.size(); j++) {
                checkOverlap(actions.get(i), actions.get(j), layer, diagnostics);
            }
        }
    }

    private static void checkOverlap(Operation a, Operation b, Layer layer, List<Diagnostic> diagnostics) {
        if (a.delete() && b.delete()) return;
        boolean intersects = a.scope().matches(b.scope().path()) || b.scope().matches(a.scope().path());
        boolean deletesStructuralParent = (a.delete() && a.scope().path().contains(b.scope().path()))
                || (b.delete() && b.scope().path().contains(a.scope().path()));
        if (intersects || deletesStructuralParent) {
            diagnostic(diagnostics, Code.CONTRADICTORY_ACTIONS,
                    a.scope().path().compareTo(b.scope().path()) <= 0 ? a.scope().path() : b.scope().path(),
                    layer.declaration(), List.of(a.id(), b.id()).stream().sorted().toList(),
                    "Overlapping same-layer actions require an explicit subtree replacement");
        }
    }

    private static void validateEntries(List<Entry> entries, Layer layer, List<Diagnostic> diagnostics) {
        Map<LogicalPath, Entry> indexed = new TreeMap<>();
        for (Entry entry : entries.stream().sorted(Comparator.comparing(Entry::path)
                .thenComparing(Entry::kind).thenComparing(e -> e.content().orElse(""))).toList()) {
            Entry previous = indexed.putIfAbsent(entry.path(), entry);
            if (previous != null) {
                diagnostic(diagnostics, previous.kind() == entry.kind() ? Code.DUPLICATE_ENTRY : Code.TYPE_CONFLICT,
                        entry.path(), layer.declaration(), List.of(layer.id()), "Multiple entries occupy one logical path");
            }
        }
        for (Entry entry : indexed.values()) {
            for (LogicalPath ancestor : entry.path().ancestors()) {
                Entry parent = indexed.get(ancestor);
                if (parent != null && parent.kind() == Kind.FILE) {
                    diagnostic(diagnostics, Code.TYPE_CONFLICT, ancestor, layer.declaration(),
                            List.of(ancestor.value(), entry.path().value()), "A file cannot contain another entry");
                }
            }
        }
    }

    private static void detectTypes(Map<LogicalPath, List<WorkingCandidate>> history, RevisionId declaration,
                                     List<Diagnostic> diagnostics, Predicate<WorkingCandidate> included) {
        history.forEach((path, candidates) -> {
            List<WorkingCandidate> active = candidates.stream().filter(included).filter(WorkingCandidate::active).toList();
            if (active.stream().map(candidate -> candidate.entry.kind()).distinct().count() > 1) {
                List<String> sources = active.stream().map(candidate -> candidate.origin.layer()).distinct().sorted().toList();
                diagnostic(diagnostics, Code.TYPE_CONFLICT, path, declaration, sources,
                        "File/directory conversion requires an explicit subtree replacement");
            }
        });
    }

    private static Resolution result(List<String> composition, Map<LogicalPath, List<WorkingCandidate>> history,
                                      List<Diagnostic> diagnostics) {
        List<Diagnostic> ordered = diagnostics.stream().distinct().sorted(Comparator.comparing(Diagnostic::code)
                .thenComparing(d -> d.path().map(LogicalPath::value).orElse(""))
                .thenComparing(d -> d.revision().map(RevisionId::toString).orElse(""))
                .thenComparing(d -> d.subjects().toString()).thenComparing(Diagnostic::message)).toList();
        Map<LogicalPath, VisibleEntry> visible = new TreeMap<>();
        Map<LogicalPath, Explanation> explanations = new TreeMap<>();
        history.forEach((path, candidates) -> {
            WorkingCandidate winner = null;
            for (WorkingCandidate candidate : candidates) {
                if (candidate.active()) winner = candidate;
            }
            Optional<VisibleEntry> selected = ordered.isEmpty() && winner != null
                    ? Optional.of(new VisibleEntry(winner.entry, winner.origin, winner.action, winner.implicit))
                    : Optional.empty();
            selected.ifPresent(entry -> visible.put(path, entry));
            List<Candidate> traces = new ArrayList<>();
            for (WorkingCandidate candidate : candidates) {
                List<Reason> reasons = new ArrayList<>(candidate.reasons);
                if (candidate.implicit && reasons.isEmpty() && !candidate.active()) {
                    candidate.supports.forEach(child -> child.reasons.forEach(reason -> {
                        if (!reasons.contains(reason)) reasons.add(reason);
                    }));
                }
                Outcome outcome;
                if (reasons.stream().anyMatch(reason -> reason.kind() == ReasonKind.EXCLUDED)) outcome = Outcome.EXCLUDED;
                else if (!candidate.active()) outcome = Outcome.HIDDEN;
                else if (!ordered.isEmpty()) outcome = Outcome.CONFLICT;
                else if (candidate == winner) outcome = Outcome.VISIBLE;
                else {
                    outcome = Outcome.SHADOWED;
                    reasons.add(new Reason(ReasonKind.SHADOWED, winner.action));
                }
                traces.add(new Candidate(candidate.entry, candidate.origin, candidate.action, candidate.implicit,
                        outcome, reasons));
            }
            explanations.put(path, new Explanation(path, selected, traces));
        });
        return new Resolution(composition, visible, explanations, ordered);
    }

    private static Action action(Layer layer, ActionKind kind, String id) {
        return new Action(kind, layer.id(), layer.declaration(), Optional.ofNullable(id));
    }

    private static void diagnostic(List<Diagnostic> diagnostics, Code code, LogicalPath path,
                                    RevisionId revision, List<String> subjects, String message) {
        diagnostics.add(new Diagnostic(code, Optional.ofNullable(path), Optional.ofNullable(revision), subjects, message));
    }

    private record Operation(String id, Scope scope, boolean delete, boolean content) {}

    private static final class WorkingCandidate {
        final Entry entry;
        final Origin origin;
        final Action action;
        final boolean implicit;
        final List<WorkingCandidate> supports;
        final List<Reason> reasons = new ArrayList<>();

        WorkingCandidate(Entry entry, Origin origin, Action action, boolean implicit, List<WorkingCandidate> supports) {
            this.entry = entry;
            this.origin = origin;
            this.action = action;
            this.implicit = implicit;
            this.supports = List.copyOf(supports);
        }

        boolean active() {
            return reasons.isEmpty() && (!implicit || supports.stream().anyMatch(candidate -> candidate.reasons.isEmpty()));
        }

        boolean selectable() {
            return reasons.stream().noneMatch(reason -> reason.kind() == ReasonKind.EXCLUDED)
                    && (!implicit || supports.stream().anyMatch(candidate -> candidate.reasons.stream()
                    .noneMatch(reason -> reason.kind() == ReasonKind.EXCLUDED)));
        }
    }
}
