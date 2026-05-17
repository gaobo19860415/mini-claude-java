package com.miniclaudecode.tool.file;

import com.miniclaudecode.tool.PermissionMode;
import com.miniclaudecode.tool.ToolContext;
import com.miniclaudecode.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    private final ReadFileTool tool = new ReadFileTool();

    @Test
    void shouldReadFileWithLineNumbers() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3");

        ToolResult result = tool.execute(
                Map.of("file_path", file.toString()),
                ctx());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("     1\tline1", "     2\tline2", "     3\tline3");
    }

    @Test
    void shouldReturnErrorForMissingFile() throws Exception {
        ToolResult result = tool.execute(
                Map.of("file_path", tempDir.resolve("nonexistent.txt").toString()),
                ctx());

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("File not found");
    }

    @Test
    void shouldResolveRelativePath() throws Exception {
        Path file = tempDir.resolve("relative.txt");
        Files.writeString(file, "content");

        ToolResult result = tool.execute(
                Map.of("file_path", "relative.txt"),
                ctx());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("content");
    }

    @Test
    void shouldBeReadOnly() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.isConcurrencySafe()).isTrue();
    }

    @Test
    void shouldHaveValidSchema() {
        assertThat(tool.inputSchema()).containsKeys("type", "properties", "required");
    }

    private ToolContext ctx() {
        return new ToolContext(PermissionMode.DEFAULT, tempDir, "test-session",
                Set.of(), Map.of(), "test-model");
    }
}
