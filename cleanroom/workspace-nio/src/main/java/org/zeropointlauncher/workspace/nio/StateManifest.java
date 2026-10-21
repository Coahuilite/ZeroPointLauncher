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

import static org.zeropointlauncher.workspace.nio.WorkspaceException.Code.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import org.zeropointlauncher.layerfs.LayerFs.*;
import org.zeropointlauncher.layerfs.LogicalPath;
import org.zeropointlauncher.layerfs.WorkspaceModel.*;

/** Versioned local state: only logical paths, types and identities, never raw references or machine paths. */
final class StateManifest {
    static final int MAGIC = 0x5A504C57;
    static final int VERSION = 1;
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private StateManifest() {}

    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError("JDK SHA-256 is required", e); }
    }

    static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }

    static String revisionIdentity(RevisionId revision) throws IOException {
        return fingerprint(out -> { out.writeUTF(revision.instanceId()); out.writeUTF(revision.revision()); });
    }

    static String resolutionIdentity(Resolution resolution) throws IOException {
        return fingerprint(out -> {
            out.writeInt(resolution.composition().size());
            for (String source : resolution.composition()) out.writeUTF(source);
            out.writeInt(resolution.visible().size());
            for (Map.Entry<LogicalPath, VisibleEntry> item : resolution.visible().entrySet()) {
                VisibleEntry visible = item.getValue();
                out.writeUTF(item.getKey().value());
                out.writeByte(visible.entry().kind().ordinal());
                out.writeUTF(visible.entry().content().orElse(""));
                out.writeUTF(visible.origin().layer());
                out.writeUTF(visible.origin().revision().instanceId());
                out.writeUTF(visible.origin().revision().revision());
                out.writeUTF(visible.action().kind().name());
                out.writeUTF(visible.action().layer());
                out.writeUTF(visible.action().declaration().instanceId());
                out.writeUTF(visible.action().declaration().revision());
                out.writeBoolean(visible.action().ruleId().isPresent());
                if (visible.action().ruleId().isPresent()) out.writeUTF(visible.action().ruleId().orElseThrow());
                out.writeBoolean(visible.implicitDirectory());
            }
        });
    }

    private static String fingerprint(Encoder encoder) throws IOException {
        MessageDigest digest = sha256();
        try (DataOutputStream out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            encoder.write(out);
        }
        return hex(digest.digest());
    }

    static void write(Path path, Baseline baseline) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeUTF(baseline.revisionIdentity());
            out.writeUTF(baseline.resolutionIdentity());
            out.writeInt(baseline.entries().size());
            for (Map.Entry<LogicalPath, Node> item : baseline.entries().entrySet()) {
                out.writeUTF(item.getKey().value());
                out.writeByte(item.getValue().kind().ordinal());
                if (item.getValue().kind() == Kind.FILE) out.writeUTF(item.getValue().contentIdentity().orElseThrow());
            }
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length + 32 > MAX_BYTES) throw new WorkspaceException(INVALID_STATE, null, "工作区清单超出格式容量");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            writeFully(channel, payload);
            writeFully(channel, sha256().digest(payload));
            channel.force(true);
        }
    }

    private static void writeFully(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    static Baseline read(Path path) throws IOException {
        long size = Files.size(path);
        if (size < 8 || size > MAX_BYTES) throw invalid();
        byte[] bytes;
        try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length != size) throw invalid();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != MAGIC) throw invalid();
            if (in.readInt() != VERSION) throw new WorkspaceException(UNKNOWN_SCHEMA, null, "未知工作区状态版本；拒绝修改");
            if (bytes.length < 40 || !MessageDigest.isEqual(Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length),
                    sha256().digest(Arrays.copyOf(bytes, bytes.length - 32)))) throw invalid();
            String revision = identity(in.readUTF());
            String resolution = identity(in.readUTF());
            int count = in.readInt();
            if (count < 0 || count > 1_000_000) throw invalid();
            Map<LogicalPath, Node> entries = new TreeMap<>();
            for (int i = 0; i < count; i++) {
                LogicalPath logical = LogicalPath.of(in.readUTF());
                int kind = in.readUnsignedByte();
                Node node = switch (kind) {
                    case 0 -> Node.file(identity(in.readUTF()));
                    case 1 -> Node.directory();
                    default -> throw invalid();
                };
                if (entries.putIfAbsent(logical, node) != null) throw invalid();
            }
            if (in.available() != 32) throw invalid();
            validateTree(entries);
            return new Baseline(revision, resolution, entries);
        } catch (WorkspaceException e) { throw e; }
        catch (IOException | IllegalArgumentException e) { throw new WorkspaceException(INVALID_STATE, null, "工作区清单损坏；拒绝修改", e); }
    }

    static void validateTree(Map<LogicalPath, Node> entries) throws WorkspaceException {
        for (LogicalPath path : entries.keySet()) {
            for (LogicalPath ancestor : path.ancestors()) {
                if (!Node.directory().equals(entries.get(ancestor))) throw invalid();
            }
        }
    }

    private static String identity(String value) throws WorkspaceException {
        if (!value.matches("[0-9a-f]{64}")) throw invalid();
        return value;
    }
    private static WorkspaceException invalid() { return new WorkspaceException(INVALID_STATE, null, "工作区清单损坏；拒绝修改"); }
    @FunctionalInterface private interface Encoder { void write(DataOutputStream out) throws IOException; }
}
