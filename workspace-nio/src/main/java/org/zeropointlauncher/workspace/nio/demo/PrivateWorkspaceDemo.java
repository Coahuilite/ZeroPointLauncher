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


package org.zeropointlauncher.workspace.nio.demo;

import static org.zeropointlauncher.layerfs.LayerFs.*;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zeropointlauncher.layerfs.LayerResolver;
import org.zeropointlauncher.workspace.nio.ArtifactSource;
import org.zeropointlauncher.workspace.nio.NamePolicy;
import org.zeropointlauncher.workspace.nio.NioWorkspace;

/** A real-file, self-checking story. No game, account, existing instance or user-selected directory is touched. */
public final class PrivateWorkspaceDemo {
    private PrivateWorkspaceDemo() {}

    public static void main(String[] args) throws IOException {
        boolean keep = args.length == 1 && args[0].equals("--keep");
        if (args.length != 0 && !keep) throw new IllegalArgumentException("用法：不传参数，或 --keep 保留新的实验样本");
        Path experiment;
        if (keep) {
            Path parent = Path.of("build", "workspace-stories");
            createSafeExperimentParent(parent);
            experiment = Files.createTempDirectory(parent, "story-");
        } else experiment = Files.createTempDirectory("zpl-private-story-");
        try {
            runStory(experiment);
            if (keep) System.out.println("保留新的合成样本（相对 workspace-nio 模块）：" + experiment.toString().replace('\\', '/'));
        } finally {
            if (!keep) deleteOwnedExperiment(experiment);
        }
        if (!keep) System.out.println("实验通过；仅本次创建的临时样本已清理。");
    }

    private static void runStory(Path experiment) throws IOException {
        Path artifacts = Files.createDirectory(experiment.resolve("artifacts"));
        Map<String, Path> files = Map.of("base-config", artifacts.resolve("base.cfg"),
                "preset-config", artifacts.resolve("preset.cfg"), "old-mod", artifacts.resolve("old.jar"));
        Files.writeString(files.get("base-config"), "base settings\n");
        Files.writeString(files.get("preset-config"), "shared preset settings\n");
        Files.writeString(files.get("old-mod"), "synthetic mod bytes\n");
        ArtifactSource source = reference -> {
            Path file = files.get(reference);
            if (file == null) throw new IOException("合成制品不存在");
            return Files.newInputStream(file);
        };
        Revision base = revision("base", null, List.of(Entry.file("config/game.cfg", "base-config"),
                Entry.file("mods/old.jar", "old-mod"), Entry.directory("empty")));
        Revision preset = revision("preset", "base", List.of(Entry.file("config/game.cfg", "preset-config")));
        Revision a = revision("A", "preset", List.of());
        Revision b = revision("B", "preset", List.of());
        Revision child = revision("C", "A", List.of());
        List<Revision> revisions = List.of(base, preset, a, b, child);
        LayerResolver resolver = new LayerResolver();
        NamePolicy names = NamePolicy.windows();
        NioWorkspace workspace = new NioWorkspace();
        for (Revision instance : List.of(preset, a, b)) {
            workspace.prepare(experiment.resolve(instance.id().instanceId()), instance.id(),
                    resolver.resolve(instance.id(), revisions, List.of()), source, names);
        }
        Path rootA = experiment.resolve("A");
        Path rootB = experiment.resolve("B");
        check(!Files.isSameFile(rootA.resolve("config/game.cfg"), rootB.resolve("config/game.cfg")), "A/B 必须是独立文件");
        check(!Files.isSameFile(rootA.resolve("config/game.cfg"), files.get("preset-config")), "运行文件不能链接到源制品");
        System.out.println("1. 基础实例 → 玩家共享预设实例 → A/B；预设本身也已准备，文件均为私有复制。");

        Files.writeString(rootA.resolve("config/game.cfg"), "A custom settings\n");
        Files.writeString(rootA.resolve("my-notes.txt"), "A unpublished file\n");
        Files.delete(rootA.resolve("mods/old.jar"));
        Files.createDirectory(rootA.resolve("user-empty"));
        NioWorkspace.Prepared reopenedA = new NioWorkspace().prepare(rootA, a.id(),
                resolver.resolve(a.id(), revisions, List.of()), source, names);
        check(Files.readString(rootA.resolve("config/game.cfg")).equals("A custom settings\n"), "A 修改保留");
        check(Files.exists(rootA.resolve("my-notes.txt")), "A 新增保留");
        check(!Files.exists(rootA.resolve("mods/old.jar")), "A 删除保留");
        check(Files.isDirectory(rootA.resolve("user-empty")), "A 空目录保留");
        check(Files.readString(rootB.resolve("config/game.cfg")).equals("shared preset settings\n"), "B 尚未改变");
        check(Files.exists(rootB.resolve("mods/old.jar")), "B 不继承 A 删除");
        System.out.println("2. 重开持久状态并再次准备 A，保留未发布 delta：");
        reopenedA.plan().delta().forEach((path, delta) -> System.out.println("   " + delta.change() + " " + path));

        Files.writeString(rootB.resolve("config/game.cfg"), "B custom settings\n");
        new NioWorkspace().prepare(rootB, b.id(), resolver.resolve(b.id(), revisions, List.of()), source, names);
        check(Files.readString(rootA.resolve("config/game.cfg")).equals("A custom settings\n"), "B 修改不影响 A");
        check(Files.readString(files.get("preset-config")).equals("shared preset settings\n"), "原制品不变");
        check(Files.readString(experiment.resolve("preset/config/game.cfg")).equals("shared preset settings\n"), "预设工作区不变");
        Path rootC = experiment.resolve("C");
        new NioWorkspace().prepare(rootC, child.id(), resolver.resolve(child.id(), revisions, List.of()), source, names);
        check(Files.readString(rootC.resolve("config/game.cfg")).equals("shared preset settings\n"), "C 只继承 A 的声明内容");
        check(Files.exists(rootC.resolve("mods/old.jar")) && !Files.exists(rootC.resolve("my-notes.txt")), "未发布 delta 不继承");
        System.out.println("3. 修改 B 仍不影响 A/预设/制品；从 A 修订新建 C，C 不继承 A 未发布的运行 delta。");
        System.out.println("全部断言通过：新增、修改、删除、空目录和双向隔离均得到真实文件验证。");
    }

    private static Revision revision(String name, String parent, List<Entry> entries) {
        RevisionId id = new RevisionId(name, "r1");
        return new Revision(id, name, Optional.ofNullable(parent).map(value -> new RevisionId(value, "r1")),
                new Layer(name, id, entries, List.of()), List.of());
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void createSafeExperimentParent(Path parent) throws IOException {
        Path absolute = parent.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path segment : absolute) {
            current = current.resolve(segment);
            try { Files.createDirectory(current); }
            catch (java.nio.file.FileAlreadyExistsException exists) { /* validate below, never reuse an experiment */ }
            BasicFileAttributes attrs = Files.readAttributes(current, BasicFileAttributes.class, java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (!attrs.isDirectory() || attrs.isSymbolicLink() || attrs.isOther()) {
                throw new IOException("实验输出父目录不能是链接或特殊目录");
            }
        }
    }

    static void deleteOwnedExperiment(Path experiment) throws IOException {
        // experiment is exclusively the fresh directory returned by createTempDirectory above, never an argument.
        Path owned = experiment.toAbsolutePath().normalize();
        walkOwnedExperiment(owned, false);
        walkOwnedExperiment(owned, true);
    }

    private static void walkOwnedExperiment(Path owned, boolean delete) throws IOException {
        Files.walkFileTree(owned, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                verifyOwnedEntry(owned, directory, attrs);
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                verifyOwnedEntry(owned, file, attrs);
                if (delete) {
                    // Recheck parents before deletion; an externally inserted junction is not an owned directory.
                    Path current = owned;
                    for (Path segment : owned.relativize(file.getParent())) {
                        current = current.resolve(segment);
                        verifyOwnedEntry(owned, current, Files.readAttributes(current, BasicFileAttributes.class,
                                java.nio.file.LinkOption.NOFOLLOW_LINKS));
                    }
                    verifyOwnedEntry(owned, owned, Files.readAttributes(owned, BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS));
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                if (error != null) throw error;
                if (delete) {
                    verifyOwnedEntry(owned, directory, Files.readAttributes(directory, BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS));
                    Files.delete(directory);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void verifyOwnedEntry(Path owned, Path entry, BasicFileAttributes attrs) throws IOException {
        if (!entry.toAbsolutePath().normalize().startsWith(owned) || attrs.isSymbolicLink() || attrs.isOther()) {
            throw new IOException("实验包含越界或特殊路径；保留样本，不递归清理");
        }
    }
}
