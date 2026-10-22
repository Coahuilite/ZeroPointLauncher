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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrivateWorkspaceDemoTest {
    @TempDir Path temp;

    @Test @DisplayName("Demo cleanup removes only an owned ordinary experiment tree")
    void ordinaryExperimentCanBeCleaned() throws Exception {
        Path owned = Files.createDirectory(temp.resolve("owned"));
        Files.writeString(owned.resolve("file"), "synthetic");
        Files.createDirectories(owned.resolve("empty/nested"));
        PrivateWorkspaceDemo.deleteOwnedExperiment(owned);
        assertFalse(Files.exists(owned));
        assertTrue(Files.isDirectory(temp));
    }

    @Test @DisplayName("Demo cleanup rejects an inserted junction or symbolic directory before deleting any sample data")
    void externalDirectoryLinkIsNotTraversedOrDeleted() throws Exception {
        Path owned = Files.createDirectory(temp.resolve("owned"));
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(owned.resolve("sample"), "preserve sample");
        Files.writeString(outside.resolve("sentinel"), "preserve outside");
        Path link = owned.resolve("external");
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            String script = "$ProgressPreference='SilentlyContinue'; $ErrorActionPreference='Stop'; "
                    + "New-Item -ItemType Junction -Path '" + link.toString().replace("'", "''")
                    + "' -Target '" + outside.toString().replace("'", "''") + "' | Out-Null";
            String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
                    .redirectErrorStream(true).start();
            try (var output = process.getInputStream()) { output.transferTo(java.io.OutputStream.nullOutputStream()); }
            assertEquals(0, process.waitFor(), "Creating an isolated junction must succeed");
        } else Files.createSymbolicLink(link, outside.toAbsolutePath());
        assertThrows(IOException.class, () -> PrivateWorkspaceDemo.deleteOwnedExperiment(owned));
        assertEquals("preserve outside", Files.readString(outside.resolve("sentinel")));
        assertEquals("preserve sample", Files.readString(owned.resolve("sample")));
        assertTrue(Files.exists(link, java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }
}
