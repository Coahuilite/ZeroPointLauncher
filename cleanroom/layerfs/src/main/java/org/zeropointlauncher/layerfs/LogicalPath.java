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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A host-independent, case-sensitive relative path. No disk normalization occurs. */
public record LogicalPath(String value) implements Comparable<LogicalPath> {
    public LogicalPath {
        Objects.requireNonNull(value, "value");
        boolean drivePrefix = value.length() >= 2 && value.charAt(1) == ':'
                && ((value.charAt(0) >= 'A' && value.charAt(0) <= 'Z')
                || (value.charAt(0) >= 'a' && value.charAt(0) <= 'z'));
        if (value.isEmpty() || value.indexOf('\0') >= 0 || value.indexOf('\\') >= 0
                || drivePrefix) {
            throw new IllegalArgumentException("Not a logical relative path: " + value);
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Not a logical relative path: " + value);
            }
        }
    }

    public static LogicalPath of(String value) {
        return new LogicalPath(value);
    }

    public boolean contains(LogicalPath other) {
        return equals(other) || other.value.startsWith(value + "/");
    }

    public List<LogicalPath> ancestors() {
        List<LogicalPath> result = new ArrayList<>();
        for (int slash = value.indexOf('/'); slash >= 0; slash = value.indexOf('/', slash + 1)) {
            result.add(of(value.substring(0, slash)));
        }
        return List.copyOf(result);
    }

    @Override
    public int compareTo(LogicalPath other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
