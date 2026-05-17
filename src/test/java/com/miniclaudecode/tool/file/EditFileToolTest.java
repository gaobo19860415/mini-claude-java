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

class EditFileToolTest {

    @TempDir
    Path tempDir;

    private final EditFileTool tool = new EditFileTool();

    @Test
    void shouldReplaceExactString() throws Exception {
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "hello world");

        tool.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "hello",
                "new_string", "goodbye"
        ), ctx());

        assertThat(Files.readString(file)).isEqualTo("goodbye world");
    }

    @Test
    void shouldReturnErrorWhenOldStringNotFound() throws Exception {
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "hello world");

        ToolResult result = tool.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "nonexistent",
                "new_string", "replacement"
        ), ctx());

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("not found");
    }

    @Test
    void shouldReturnErrorForNonUniqueMatch() throws Exception {
        Path file = tempDir.resolve("edit.txt");
        Files.writeString(file, "dup xxx dup");

        ToolResult result = tool.execute(Map.of(
                "file_path", file.toString(),
                "old_string", "dup",
                "new_string", "replaced"
        ), ctx());

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("not unique");
    }

    @Test
    void shouldNeedConfirm() {
        assertThat(tool.needsConfirm()).isTrue();
    }

    private ToolContext ctx() {
        return new ToolContext(PermissionMode.DEFAULT, tempDir, "test-session",
                Set.of(), Map.of(), "test-model");
    }
}
