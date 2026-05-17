package com.miniclaudecode.subagent;

import com.miniclaudecode.agent.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SubAgentServiceTest {

    @TempDir
    Path tempDir;

    private SubAgentService service;

    @BeforeEach
    void setUp() {
        service = new SubAgentService(mock(AgentService.class));
    }

    @Test
    void shouldReturnBuiltInAgentTypes() {
        assertThat(SubAgentConfig.builtIn("explore")).isNotNull();
        assertThat(SubAgentConfig.builtIn("plan")).isNotNull();
        assertThat(SubAgentConfig.builtIn("general")).isNotNull();
        assertThat(SubAgentConfig.builtIn("unknown")).isNull();
    }

    @Test
    void exploreAgentShouldHaveReadOnlyTools() {
        SubAgentConfig config = SubAgentConfig.builtIn("explore");
        assertThat(config.allowedTools()).contains("read_file", "list_files", "grep_search");
    }

    @Test
    void generalAgentShouldHaveAllTools() {
        SubAgentConfig config = SubAgentConfig.builtIn("general");
        assertThat(config.hasAllTools()).isTrue();
    }

    @Test
    void shouldDiscoverCustomAgents() throws IOException {
        Path agentsDir = tempDir.resolve(".claude/agents");
        Files.createDirectories(agentsDir);
        Files.writeString(agentsDir.resolve("deployer.md"), """
                ---
                description: Deploy the application
                allowedTools: [read_file, run_shell, write_file]
                ---

                You are a deployment agent.
                """);

        service.discover(tempDir.toString());

        SubAgentConfig config = service.getConfig("deployer");
        assertThat(config).isNotNull();
        assertThat(config.description()).isEqualTo("Deploy the application");
    }

    @Test
    void shouldBuildAgentDescriptions() {
        String desc = service.buildAgentDescriptions();
        assertThat(desc).contains("explore", "plan", "general");
    }
}
