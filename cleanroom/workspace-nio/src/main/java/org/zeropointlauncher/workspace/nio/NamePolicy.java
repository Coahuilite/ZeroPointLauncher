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

import static org.zeropointlauncher.workspace.nio.WorkspaceException.Code.NAME_CONFLICT;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.zeropointlauncher.layerfs.LogicalPath;

/** Target naming facts; the host's own restrictions are checked in addition to these facts. */
public record NamePolicy(boolean caseSensitive, boolean windowsNames, boolean canonicalUnicodeEquivalence) {
    public static NamePolicy linux() { return new NamePolicy(true, false, false); }
    public static NamePolicy windows() { return new NamePolicy(false, true, false); }
    public static NamePolicy macOsDefault() { return new NamePolicy(false, false, true); }

    static NamePolicy hostDefault() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? windows() : os.contains("mac") ? macOsDefault() : linux();
    }

    public void validate(Collection<LogicalPath> paths) throws WorkspaceException {
        Map<String, LogicalPath> names = new HashMap<>();
        for (LogicalPath path : paths.stream().sorted().toList()) {
            for (String segment : path.value().split("/")) {
                if (windowsNames && invalidWindowsSegment(segment)) {
                    throw new WorkspaceException(NAME_CONFLICT, path, "目标名称规则拒绝此逻辑路径");
                }
            }
            String equivalent = canonicalUnicodeEquivalence
                    ? Normalizer.normalize(path.value(), Normalizer.Form.NFD) : path.value();
            if (!caseSensitive) equivalent = equivalent.toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
            LogicalPath previous = names.putIfAbsent(equivalent, path);
            if (previous != null && !previous.equals(path)) {
                throw new WorkspaceException(NAME_CONFLICT, path, "不同逻辑路径在目标文件系统上同名");
            }
        }
    }

    private static boolean invalidWindowsSegment(String segment) {
        if (segment.endsWith(".") || segment.endsWith(" ")) return true;
        for (char c : segment.toCharArray()) {
            if (c < 32 || "<>:\"|?*".indexOf(c) >= 0) return true;
        }
        String stem = segment.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
        return stem.equals("CON") || stem.equals("PRN") || stem.equals("AUX") || stem.equals("NUL")
                || stem.matches("(?:COM|LPT)[1-9¹²³]");
    }
}
