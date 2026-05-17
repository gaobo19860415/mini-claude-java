package com.miniclaudecode.prompt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @TempDir
    Path tempDir;

    private final PromptBuilder builder;

    PromptBuilderTest() {
        // Manual construction since @Value won't be injected in unit test
        builder = new PromptBuilder();
        // Use reflection or just rely on the default template
    }

    @Test
    void shouldReplaceTemplateVariables() {
        String result = builder.build(tempDir.toString(), "test-model");
        assertThat(result).contains(tempDir.toString());
        assertThat(result).doesNotContain("{{cwd}}"); // template vars replaced
        assertThat(result).doesNotContain("{{platform}}");
    }

    @Test
    void shouldResolveIncludeWithCircularDetection() throws IOException {
        Path a = tempDir.resolve("a.md");
        Files.writeString(a, "content from a\n@./b.md");
        Path b = tempDir.resolve("b.md");
        Files.writeString(b, "content from b\n@./a.md");

        String result = builder.resolveIncludes(
                "start\n@./a.md\nend",
                tempDir.toString(),
                new HashSet<>(),
                0
        );

        assertThat(result).contains("content from a");
        assertThat(result).contains("content from b");
        assertThat(result).contains("circular");
    }

    @Test
    void shouldLoadClaudeMdHierarchy() throws IOException {
        // Create CLAUDE.md in temp dir
        Files.writeString(tempDir.resolve("CLAUDE.md"), "# Project CLAUDE.md\nTest content");

        String result = builder.loadClaudeMd(tempDir.toString());
        assertThat(result).contains("Test content");
        assertThat(result).contains("CLAUDE.md");
    }

    @Test
    void shouldLoadRulesDirectory() throws IOException {
        Path rulesDir = tempDir.resolve(".claude").resolve("rules");
        Files.createDirectories(rulesDir);
        Files.writeString(rulesDir.resolve("rule1.md"), "# Rule 1\nRule content");

        String result = builder.loadClaudeMd(tempDir.toString());
        assertThat(result).contains("Rule 1");
        assertThat(result).contains("rule1.md");
    }

    @Test
    void shouldBuildGitContext() throws IOException {
        // In a git repo, should return git info; outside, empty string
        String result = builder.buildGitContext(tempDir.toString());
        // temp dir is typically not a git repo
        assertThat(result).isNotNull();
    }
}
