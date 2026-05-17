package com.miniclaudecode.tool.file;

import com.miniclaudecode.tool.PermissionMode;
import com.miniclaudecode.tool.ToolContext;
import com.miniclaudecode.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WriteFileToolTest {

    @TempDir
    Path tempDir;

    private final WriteFileTool tool = new WriteFileTool();

    @Test
    void shouldWriteNewFile() throws Exception {
        Path file = tempDir.resolve("output.txt");
        ToolResult result = tool.execute(
                Map.of("file_path", file.toString(), "content", "hello world"),
                ctx());

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("hello world");
    }

    @Test
    void shouldOverwriteExistingFile() throws Exception {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        tool.execute(Map.of("file_path", file.toString(), "content", "new content"), ctx());

        assertThat(Files.readString(file)).isEqualTo("new content");
    }

    @Test
    void shouldCreateParentDirectories() throws Exception {
        Path file = tempDir.resolve("deep/nested/output.txt");
        tool.execute(Map.of("file_path", file.toString(), "content", "deep"), ctx());

        assertThat(file).exists();
        assertThat(Files.readString(file)).isEqualTo("deep");
    }

    @Test
    void shouldNeedConfirm() {
        assertThat(tool.needsConfirm()).isTrue();
    }

    @Test
    void shouldNotBeReadOnly() {
        assertThat(tool.isReadOnly()).isFalse();
    }

    private ToolContext ctx() {
        return new ToolContext(PermissionMode.DEFAULT, tempDir, "test-session",
                Set.of(), Map.of(), "test-model");
    }
}
