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

class GrepSearchToolTest {

    @TempDir
    Path tempDir;

    private final GrepSearchTool tool = new GrepSearchTool();

    @Test
    void shouldFindMatchingLines() throws Exception {
        Path file = tempDir.resolve("search.txt");
        Files.writeString(file, "hello world\nfoo bar\nhello again");

        ToolContext ctx = new ToolContext(PermissionMode.DEFAULT, tempDir,
                "test", Set.of(), Map.of(), "test");

        var result = tool.execute(Map.of("pattern", "hello", "path", tempDir.toString()), ctx);

        // grep/rg should find matching lines
        assertThat(result.content()).contains("hello");
    }

    @Test
    void shouldReturnOkForNoMatches() throws Exception {
        Path file = tempDir.resolve("search.txt");
        Files.writeString(file, "nothing here");

        ToolContext ctx = new ToolContext(PermissionMode.DEFAULT, tempDir,
                "test", Set.of(), Map.of(), "test");

        var result = tool.execute(Map.of("pattern", "ZZZZ_NOT_FOUND", "path", tempDir.toString()), ctx);

        assertThat(result.content()).containsIgnoringCase("No matches found");
    }

    @Test
    void shouldBeReadOnly() {
        assertThat(tool.isReadOnly()).isTrue();
        assertThat(tool.isConcurrencySafe()).isTrue();
    }
}
