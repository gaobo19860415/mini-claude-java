package com.miniclaudecode.tool.search;

import com.miniclaudecode.tool.PermissionMode;
import com.miniclaudecode.tool.ToolContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ListFilesToolTest {

    @TempDir
    Path tempDir;

    private final ListFilesTool tool = new ListFilesTool();

    @Test
    void shouldMatchFilesByGlob() throws Exception {
        Files.createFile(tempDir.resolve("test.java"));
        Files.createFile(tempDir.resolve("test.md"));
        Files.createFile(tempDir.resolve("test.txt"));

        ToolContext ctx = new ToolContext(PermissionMode.DEFAULT, tempDir,
                "test", Set.of(), Map.of(), "test");

        var result = tool.execute(Map.of("pattern", "*.java", "path", tempDir.toString()), ctx);

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("test.java");
        assertThat(result.content()).doesNotContain("test.md");
    }

    @Test
    void shouldReturnMessageForNoMatches() throws Exception {
        ToolContext ctx = new ToolContext(PermissionMode.DEFAULT, tempDir,
                "test", Set.of(), Map.of(), "test");

        var result = tool.execute(Map.of("pattern", "*.py", "path", tempDir.toString()), ctx);

        assertThat(result.content()).contains("No files matched");
    }

    @Test
    void shouldBeReadOnly() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.isConcurrencySafe()).isTrue();
    }
}
