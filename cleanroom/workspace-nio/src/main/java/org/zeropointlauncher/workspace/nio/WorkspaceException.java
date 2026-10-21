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

import java.io.IOException;
import java.util.Optional;
import org.zeropointlauncher.layerfs.LogicalPath;

/** A failed preparation is never a ready result. Messages contain no machine path or artifact reference. */
public final class WorkspaceException extends IOException {
    public enum Code {
        INVALID_RESOLUTION, INVALID_LOCATION, UNMANAGED_WORKSPACE, INVALID_STATE, UNKNOWN_SCHEMA,
        INCOMPLETE_WORKSPACE, UNSAFE_ENTRY, NAME_CONFLICT, MISSING_ARTIFACT, BASELINE_CHANGED,
        BUSY, IO_FAILURE, EXTERNAL_CHANGE
    }
    private final Code code;
    private final Optional<LogicalPath> logicalPath;

    public WorkspaceException(Code code, LogicalPath path, String message) {
        super(message);
        this.code = code;
        this.logicalPath = Optional.ofNullable(path);
    }

    public WorkspaceException(Code code, LogicalPath path, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.logicalPath = Optional.ofNullable(path);
    }

    public Code code() { return code; }
    public Optional<LogicalPath> logicalPath() { return logicalPath; }
}
