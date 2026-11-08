package org.zeropointlauncher.resolve;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.resolve.ManifestInheritance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ManifestInheritanceTest {
    @Test
    void resolveInheritance() {
        var base = fragment("base", null, "test.BaseMain");
        var loader = fragment("loader", "base", "test.LoaderMain");
        var child = fragment("child", "loader", null);

        var manifests = Map.of("base", base, "loader", loader, "child", child);
        var inheritance = new ManifestInheritance();

        assertEquals(List.of(base, loader, child), inheritance.resolveChain("child", manifests));
        assertEquals("test.LoaderMain", inheritance.resolveMainClass("child", manifests));
    }

    @Test
    void rootMainClassIsUsedWhenChildDeclaresNone() {
        var base = fragment("base", null, "test.BaseMain");
        var child = fragment("child", "base", null);

        assertEquals("test.BaseMain", new ManifestInheritance()
                .resolveMainClass("child", Map.of("base", base, "child", child)));
    }

    @Test
    void detectsCycle() {
        var a = fragment("a", "b", null);
        var b = fragment("b", "a", null);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new ManifestInheritance().resolveChain("a", Map.of("a", a, "b", b)));
        assertTrue(error.getMessage().contains("成环"));
    }

    @Test
    void missingParentIsReported() {
        var child = fragment("child", "ghost", null);

        var error = assertThrows(IllegalArgumentException.class,
                () -> new ManifestInheritance().resolveChain("child", Map.of("child", child)));
        assertTrue(error.getMessage().contains("ghost"));
    }

    @Test
    void chainIsImmutable() {
        var base = fragment("base", null, "test.BaseMain");
        var child = fragment("child", "base", null);

        var chain = new ManifestInheritance().resolveChain("child", Map.of("base", base, "child", child));
        assertThrows(UnsupportedOperationException.class, () -> chain.add(base));
    }

    @Test
    void blankMainClassIsTreatedAsAbsent() {
        var child = new ManifestFragment("child", "base", "", null, null, List.of());
        var base = fragment("base", null, "test.BaseMain");

        assertEquals("test.BaseMain", new ManifestInheritance()
                .resolveMainClass("child", Map.of("base", base, "child", child)));
    }

    private static ManifestFragment fragment(String id, String inheritsFrom, String mainClass) {
        return new ManifestFragment(id, inheritsFrom, mainClass, null, null, List.of());
    }
}
