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


package org.zeropointlauncher.workspace.nio;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.zeropointlauncher.workspace.nio.WorkspaceException.Code.*;
import static org.zeropointlauncher.layerfs.WorkspaceModel.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.zeropointlauncher.layerfs.LayerFs.*;
import org.zeropointlauncher.layerfs.LogicalPath;
import org.zeropointlauncher.layerfs.WorkspacePlanner;

/** Private copying and same-baseline reinspection. No links, upgrades, deletion or automatic recovery. */
public final class NioWorkspace {
    private static final Set<String> STATE_NAMES = Set.of("lock", "pending", "staging", "manifest.bin");
    private final WorkspacePlanner planner = new WorkspacePlanner();

    public record Prepared(Baseline baseline, Map<LogicalPath, Node> current, Plan plan) {
        public Prepared { current = Collections.unmodifiableMap(new TreeMap<>(current)); }
    }

    /** The parent must already exist. State is a fixed sibling, so callers cannot bypass a workspace lock. */
    public static Path stateDirectory(Path workspace) {
        Path root = workspace.toAbsolutePath().normalize();
        if (root.getParent() == null) throw new IllegalArgumentException("A workspace cannot be a filesystem root");
        return root.resolveSibling("." + root.getFileName() + ".zpl-state");
    }

    public Prepared prepare(Path workspace, RevisionId target, Resolution resolution,
                            ArtifactSource artifacts, NamePolicy targetNames) throws WorkspaceException {
        if (!resolution.valid()) throw new WorkspaceException(INVALID_RESOLUTION, null, "不能准备含冲突的内容树");
        for (Path segment : workspace) {
            if (segment.toString().equals(".") || segment.toString().equals("..")) {
                throw new WorkspaceException(INVALID_LOCATION, null, "工作区位置不能包含点路径段");
            }
        }
        Path root = workspace.toAbsolutePath().normalize();
        if (root.getParent() == null) throw new WorkspaceException(INVALID_LOCATION, null, "工作区不能是文件系统根目录");
        try {
            if (NamePolicy.hostDefault().windowsNames()) {
                for (Path segment : root) NamePolicy.hostDefault().validate(List.of(LogicalPath.of(segment.toString())));
            }
            Map<LogicalPath, Node> shape = shape(resolution);
            StateManifest.validateTree(shape);
            validateNames(shape.keySet(), targetNames);
            String revisionIdentity = StateManifest.revisionIdentity(target);
            String resolutionIdentity = StateManifest.resolutionIdentity(resolution);
            safeAncestors(root.getParent());
            // A verified ordinary ancestor may use an OS-provided short name (for example TEMP).
            // Canonicalize its identity before deriving either runtime or state/lock locations.
            root = root.getParent().toRealPath(NOFOLLOW_LINKS).resolve(root.getFileName());
            Path state = stateDirectory(root);
            if (attributes(root) != null) {
                requireDirectory(root, null);
                rejectAlias(root);
            }
            BasicFileAttributes stateAttributes = attributes(state);
            boolean createdState = false;
            if (stateAttributes == null && attributes(root) != null) {
                throw new WorkspaceException(UNMANAGED_WORKSPACE, null, "既有目录没有受管状态；即使为空也拒绝接管");
            }
            if (stateAttributes == null) {
                try { Files.createDirectory(state); createdState = true; }
                catch (FileAlreadyExistsException concurrentCreator) { requireDirectory(state, null); }
            } else requireDirectory(state, null);
            try (Lease ignored = lock(state)) {
                safeAncestors(state);
                validateStateEntries(state);
                Path manifest = state.resolve("manifest.bin");
                if (attributes(state.resolve("pending")) != null || attributes(state.resolve("staging")) != null) {
                    throw new WorkspaceException(INCOMPLETE_WORKSPACE, null, "首次准备未完成；保留现场，不能作为就绪工作区");
                }
                if (attributes(manifest) != null) {
                    requireFile(manifest, null);
                    Baseline saved = StateManifest.read(manifest);
                    Baseline desired = new Baseline(revisionIdentity, resolutionIdentity, saved.entries());
                    if (planner.plan(Optional.of(saved), desired, Map.of()).decision() == Decision.REJECT_BASELINE_CHANGE) {
                        throw new WorkspaceException(BASELINE_CHANGED, null, "目标修订或解析内容已改变；本入口不执行升级");
                    }
                    requireDirectory(root, null);
                    Map<LogicalPath, Node> current = scan(root, targetNames);
                    return new Prepared(saved, current, planner.plan(Optional.of(saved), desired, current));
                }
                if (!createdState) {
                    throw new WorkspaceException(INVALID_STATE, null, "既有状态缺少有效基线清单；拒绝重新初始化");
                }
                if (attributes(root) != null) {
                    throw new WorkspaceException(UNMANAGED_WORKSPACE, null, "首次准备要求原本不存在的运行目录");
                }
                // This marker precedes every artifact and runtime write. Failure deliberately remains incomplete.
                try (var marker = Files.newByteChannel(state.resolve("pending"), StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, NOFOLLOW_LINKS)) { }
                Path staging = Files.createDirectory(state.resolve("staging"));
                Map<String, Acquired> acquired = acquire(resolution, artifacts, staging);
                Map<LogicalPath, Node> baselineEntries = new TreeMap<>();
                resolution.visible().forEach((path, visible) -> baselineEntries.put(path,
                        visible.entry().kind() == Kind.DIRECTORY ? Node.directory()
                                : Node.file(acquired.get(visible.entry().content().orElseThrow()).identity())));
                Baseline baseline = new Baseline(revisionIdentity, resolutionIdentity, baselineEntries);
                Files.createDirectory(root);
                copyInitial(root, resolution, acquired);
                Map<LogicalPath, Node> current = scan(root, targetNames);
                if (!current.equals(baseline.entries())) {
                    throw new WorkspaceException(EXTERNAL_CHANGE, null, "复制后实际内容与基线不同；未标记就绪");
                }
                // Only known, operation-owned staging files are removed; the runtime tree is never cleaned.
                for (Acquired artifact : acquired.values()) Files.delete(artifact.staged());
                Files.delete(staging);
                StateManifest.write(manifest, baseline);
                Files.delete(state.resolve("pending"));
                return new Prepared(baseline, current, planner.plan(Optional.empty(), baseline, Map.of()));
            }
        } catch (WorkspaceException e) { throw e; }
        catch (FileAlreadyExistsException e) { throw new WorkspaceException(EXTERNAL_CHANGE, null, "目标发生竞争性创建；未覆盖已有内容", e); }
        catch (IOException e) { throw new WorkspaceException(IO_FAILURE, null, "工作区准备失败；未报告就绪", e); }
    }

    private static Map<LogicalPath, Node> shape(Resolution resolution) throws WorkspaceException {
        Map<LogicalPath, Node> result = new TreeMap<>();
        for (Map.Entry<LogicalPath, VisibleEntry> item : resolution.visible().entrySet()) {
            if (!item.getKey().equals(item.getValue().entry().path())) {
                throw new WorkspaceException(INVALID_RESOLUTION, item.getKey(), "内容树的路径键与条目不一致");
            }
            result.put(item.getKey(), item.getValue().entry().kind() == Kind.DIRECTORY ? Node.directory() : Node.file("unread"));
        }
        return result;
    }

    private static Map<String, Acquired> acquire(Resolution resolution, ArtifactSource source, Path staging) throws IOException {
        Map<String, Acquired> result = new HashMap<>();
        for (Map.Entry<LogicalPath, VisibleEntry> item : resolution.visible().entrySet()) {
            if (item.getValue().entry().kind() != Kind.FILE) continue;
            String reference = item.getValue().entry().content().orElseThrow();
            if (result.containsKey(reference)) continue;
            Path staged = staging.resolve(Integer.toString(result.size()));
            MessageDigest digest = StateManifest.sha256();
            try (InputStream input = source.open(reference)) {
                if (input == null) throw new IOException("Artifact source returned no stream");
                try (OutputStream output = Files.newOutputStream(staged, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE, NOFOLLOW_LINKS)) {
                    transfer(input, output, digest);
                }
            } catch (IOException e) {
                throw new WorkspaceException(MISSING_ARTIFACT, item.getKey(), "无法完整获取此路径所需的不可变制品", e);
            }
            result.put(reference, new Acquired(staged, StateManifest.hex(digest.digest())));
        }
        return result;
    }

    private static void copyInitial(Path root, Resolution resolution, Map<String, Acquired> acquired) throws IOException {
        for (Map.Entry<LogicalPath, VisibleEntry> item : resolution.visible().entrySet()) {
            Path destination = resolve(root, item.getKey());
            safeAncestors(destination.getParent());
            if (item.getValue().entry().kind() == Kind.DIRECTORY) Files.createDirectory(destination);
            else {
                Path staged = acquired.get(item.getValue().entry().content().orElseThrow()).staged();
                requireFile(staged, item.getKey());
                try (InputStream input = Files.newInputStream(staged, NOFOLLOW_LINKS);
                        OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW,
                                StandardOpenOption.WRITE, NOFOLLOW_LINKS)) {
                    input.transferTo(output);
                }
            }
        }
    }

    private static Map<LogicalPath, Node> scan(Path root, NamePolicy targetNames) throws IOException {
        safeAncestors(root);
        Map<LogicalPath, Node> result = new TreeMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                rejectSpecial(attrs, null);
                if (!directory.equals(root)) result.put(logical(root, directory), Node.directory());
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                LogicalPath path = logical(root, file);
                rejectSpecial(attrs, path);
                if (!attrs.isRegularFile()) throw new WorkspaceException(UNSAFE_ENTRY, path, "未知文件类型不能作为普通内容扫描");
                safeAncestors(file.getParent());
                MessageDigest digest = StateManifest.sha256();
                try (InputStream input = Files.newInputStream(file, NOFOLLOW_LINKS)) {
                    transfer(input, OutputStream.nullOutputStream(), digest);
                }
                BasicFileAttributes after = Files.readAttributes(file, BasicFileAttributes.class, NOFOLLOW_LINKS);
                rejectSpecial(after, path);
                if (!java.util.Objects.equals(attrs.fileKey(), after.fileKey()) || attrs.size() != after.size()
                        || !attrs.lastModifiedTime().equals(after.lastModifiedTime())) {
                    throw new WorkspaceException(EXTERNAL_CHANGE, path, "文件在扫描期间变化；请在停止写入后重试");
                }
                result.put(path, Node.file(StateManifest.hex(digest.digest())));
                return FileVisitResult.CONTINUE;
            }
        });
        validateNames(result.keySet(), targetNames);
        return result;
    }

    private static void transfer(InputStream input, OutputStream output, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        for (int count; (count = input.read(buffer)) != -1;) {
            if (count == 0) continue;
            digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
        }
    }

    private static LogicalPath logical(Path root, Path item) throws WorkspaceException {
        try {
            String value = root.relativize(item).toString().replace(item.getFileSystem().getSeparator(), "/");
            return LogicalPath.of(value);
        } catch (IllegalArgumentException e) { throw new WorkspaceException(UNSAFE_ENTRY, null, "运行目录包含非法逻辑路径", e); }
    }

    private static Path resolve(Path root, LogicalPath logical) throws WorkspaceException {
        Path path = root;
        for (String segment : logical.value().split("/")) path = path.resolve(segment);
        if (!path.normalize().startsWith(root) || path.equals(root)) {
            throw new WorkspaceException(UNSAFE_ENTRY, logical, "逻辑路径越过授权工作区");
        }
        return path;
    }

    private static void validateNames(java.util.Collection<LogicalPath> paths, NamePolicy target) throws WorkspaceException {
        target.validate(paths);
        NamePolicy.hostDefault().validate(paths);
    }

    private static void validateStateEntries(Path state) throws IOException {
        try (var entries = Files.newDirectoryStream(state)) {
            for (Path entry : entries) {
                if (!STATE_NAMES.contains(entry.getFileName().toString())) {
                    throw new WorkspaceException(INVALID_STATE, null, "状态目录包含未知内容；拒绝修改");
                }
                BasicFileAttributes attrs = Files.readAttributes(entry, BasicFileAttributes.class, NOFOLLOW_LINKS);
                rejectSpecial(attrs, null);
                if (entry.getFileName().toString().equals("staging")) requireDirectory(entry, null);
                else requireFile(entry, null);
            }
        }
    }

    private static Lease lock(Path state) throws IOException {
        Path path = state.resolve("lock");
        if (attributes(path) != null) requireFile(path, null);
        FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, NOFOLLOW_LINKS);
        try {
            requireFile(path, null);
            FileLock lock;
            try { lock = channel.tryLock(); }
            catch (OverlappingFileLockException e) { throw new WorkspaceException(BUSY, null, "同一工作区正在准备"); }
            if (lock == null) throw new WorkspaceException(BUSY, null, "同一工作区正在由另一进程准备");
            return new Lease(channel, lock);
        } catch (IOException | RuntimeException e) { channel.close(); throw e; }
    }

    private static void safeAncestors(Path directory) throws IOException {
        Path current = directory.getRoot();
        requireDirectory(current, null);
        for (Path segment : directory) {
            current = current.resolve(segment);
            requireDirectory(current, null);
        }
    }

    private static void rejectAlias(Path path) throws IOException {
        String requested = path.toString();
        String actual = path.toRealPath(NOFOLLOW_LINKS).toString();
        boolean same = NamePolicy.hostDefault().caseSensitive() ? requested.equals(actual) : requested.equalsIgnoreCase(actual);
        if (!same) throw new WorkspaceException(INVALID_LOCATION, null, "目录使用非规范别名；请明确指定实际目录名称");
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        try { return Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS); }
        catch (NoSuchFileException e) { return null; }
    }
    private static void requireDirectory(Path path, LogicalPath logical) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
        rejectSpecial(attrs, logical);
        if (!attrs.isDirectory()) throw new WorkspaceException(INVALID_LOCATION, logical, "预期目录的位置不是普通目录");
    }
    private static void requireFile(Path path, LogicalPath logical) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class, NOFOLLOW_LINKS);
        rejectSpecial(attrs, logical);
        if (!attrs.isRegularFile()) throw new WorkspaceException(INVALID_STATE, logical, "预期文件的位置不是普通文件");
    }
    private static void rejectSpecial(BasicFileAttributes attrs, LogicalPath logical) throws WorkspaceException {
        if (attrs.isSymbolicLink() || attrs.isOther()) {
            throw new WorkspaceException(UNSAFE_ENTRY, logical, "拒绝跟随符号链接、junction 或其他特殊文件");
        }
    }
    private record Acquired(Path staged, String identity) {}
    private record Lease(FileChannel channel, FileLock lock) implements AutoCloseable {
        @Override public void close() throws IOException {
            try { lock.release(); } finally { channel.close(); }
        }
    }
}
