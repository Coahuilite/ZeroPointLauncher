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


package org.zeropointlauncher.layerfs.demo;

import static org.zeropointlauncher.layerfs.LayerFs.*;

import java.util.List;
import java.util.Optional;
import org.zeropointlauncher.layerfs.LayerResolver;

/** Runs only synthetic in-memory content through the same resolver used by callers and tests. */
public final class LayerFsDemo {
    private LayerFsDemo() {}

    public static void main(String[] args) {
        Revision base = revision("base", null, List.of(
                Entry.file("config/ui/language.cfg", "base-language"),
                Entry.file("config/game.cfg", "base-settings"),
                Entry.file("mods/old.jar", "old-mod")));
        Revision translation = revision("translation", "base", List.of(
                Entry.file("config/ui/language.cfg", "translated-language"),
                Entry.file("config/game.cfg", "translated-settings")));
        Revision personal = revision("personal", "translation", List.of(Entry.file("mods/local.jar", "local-mod")),
                new Select("use-original-settings", "base", Scope.single("config/game.cfg")),
                new Delete("remove-old-mod", Scope.single("mods/old.jar")));
        LayerResolver resolver = new LayerResolver();
        Resolution result = resolver.resolve(personal.id(), List.of(base, translation, personal), List.of());
        System.out.println("Three-layer composition: " + String.join(" -> ", result.composition()));
        System.out.println("Visible tree:");
        result.visible().forEach((path, visible) -> System.out.printf("  %-29s %-9s %-22s origin=%s action=%s%n",
                path, visible.entry().kind(), visible.entry().content().orElse("-"),
                visible.origin().layer(), visible.action().kind()));
        for (String path : List.of("config/game.cfg", "mods/old.jar")) {
            System.out.println("Explain " + path + ":");
            result.explain(path).orElseThrow().candidates().forEach(candidate -> {
                System.out.printf("  origin=%s content=%s outcome=%s action=%s declared-by=%s rule=%s%n",
                        candidate.origin().layer(), candidate.entry().content().orElse("-"), candidate.outcome(),
                        candidate.action().kind(), candidate.action().declaration(),
                        candidate.action().ruleId().orElse("-"));
                candidate.reasons().forEach(reason -> System.out.printf("    %s by %s rule=%s%n",
                        reason.kind(), reason.action().layer(), reason.action().ruleId().orElse("-")));
            });
        }
        Revision conflicting = revision("conflict", "translation", List.of(Entry.file("config", "not-a-directory")));
        Resolution rejected = resolver.resolve(conflicting.id(), List.of(base, translation, conflicting), List.of());
        System.out.println("Rejected type-conflict example (usable tree = " + rejected.valid() + "):");
        rejected.diagnostics().forEach(diagnostic -> System.out.printf("  %s path=%s sources=%s: %s%n",
                diagnostic.code(), diagnostic.path().map(Object::toString).orElse("-"),
                diagnostic.subjects(), diagnostic.message()));
    }

    private static Revision revision(String name, String parent, List<Entry> entries, Rule... rules) {
        RevisionId id = new RevisionId(name, "r1");
        return new Revision(id, name, Optional.ofNullable(parent).map(value -> new RevisionId(value, "r1")),
                new Layer(name, id, entries, List.of(rules)), List.of());
    }
}
