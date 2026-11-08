package org.zeropointlauncher.resolve;

import org.junit.jupiter.api.Test;
import org.zeropointlauncher.launch.model.ManifestFragment;
import org.zeropointlauncher.launch.resolve.ManifestInheritance;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManifestInheritanceTest {
    @Test
    void resovleInheritance(){
        //三层测试用例
        var base = new ManifestFragment(
                "base", Optional.empty(), Optional.of("test.BaseMain"), Optional.empty(), List.of()
        );
        var loader = new ManifestFragment(
                "loader", Optional.of("base"), Optional.of("test.LoaderMain"), Optional.empty(), List.of()
        );
        var child = new ManifestFragment(
                "child", Optional.of("loader"), Optional.empty(), Optional.empty(), List.of()
        );

        var manifests = Map.of("base", base, "loader", loader, "child", child);
        var inheritance = new ManifestInheritance();

        assertEquals(List.of(base, loader, child), inheritance.resolveChain("child", manifests));
        assertEquals("test.LoaderMain", inheritance.resolveMainClass("child", manifests));
    }
}
