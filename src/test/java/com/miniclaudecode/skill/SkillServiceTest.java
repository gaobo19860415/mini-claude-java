package com.miniclaudecode.skill;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SkillServiceTest {

    @TempDir
    Path tempDir;

    private SkillService service;

    @BeforeEach
    void setUp() {
        service = new SkillService();
    }

    @Test
    void shouldDiscoverSkillsFromProjectDir() throws IOException {
        Path skillDir = tempDir.resolve(".claude/skills/my-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: my-skill
                description: A test skill
                context: inline
                userInvocable: true
                ---

                This is the skill prompt body.
                """);

        service.discover(tempDir.toString());

        // At least one skill discovered (may also include user-level skills)
        assertThat(service.all()).isNotEmpty();
        var skill = service.findByName("my-skill");
        assertThat(skill).isNotNull();
        assertThat(skill.description()).isEqualTo("A test skill");
        assertThat(skill.context()).isEqualTo("inline");
        assertThat(skill.isInline()).isTrue();
    }

    @Test
    void shouldDiscoverForkSkills() throws IOException {
        Path skillDir = tempDir.resolve(".claude/skills/fork-skill");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: fork-skill
                description: A fork-mode skill
                context: fork
                allowedTools: [read_file, grep_search]
                ---

                Fork skill prompt.
                """);

        service.discover(tempDir.toString());

        var skill = service.findByName("fork-skill");
        assertThat(skill).isNotNull();
        assertThat(skill.isFork()).isTrue();
        assertThat(skill.allowedTools()).containsExactly("read_file", "grep_search");
    }

    @Test
    void shouldBuildDescriptions() throws IOException {
        Path skillDir = tempDir.resolve(".claude/skills/test");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: test
                description: A test skill
                context: inline
                ---

                Content.
                """);

        service.discover(tempDir.toString());
        String desc = service.buildDescriptions();

        assertThat(desc).contains("test", "A test skill", "inline");
    }

    @Test
    void shouldReturnEmptyWhenNoSkills() {
        assertThat(service.buildDescriptions()).isEmpty();
    }

    @Test
    void shouldReturnNullForUnknownSkill() {
        service.discover(tempDir.toString());
        assertThat(service.findByName("unknown")).isNull();
    }
}
