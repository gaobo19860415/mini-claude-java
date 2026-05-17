package com.miniclaudecode.agent;

import com.miniclaudecode.prompt.PromptBuilder;
import com.miniclaudecode.tool.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentServiceTest {

    @TempDir
    Path tempDir;

    private AgentService agentService;
    private ChatModel chatModel;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        toolRegistry = new ToolRegistry(List.of(
                new StubReadTool()
        ));
        PromptBuilder promptBuilder = new PromptBuilder();
        ContextCompressor compressor = new ContextCompressor();
        agentService = new AgentService(chatModel, toolRegistry, promptBuilder, compressor);
    }

    @Test
    void shouldCreateContextWithOptions() {
        AgentOptions options = AgentOptions.defaults();
        AgentContext ctx = agentService.createContext(options, tempDir.toString());

        assertThat(ctx.sessionId()).isNotEmpty();
        assertThat(ctx.permissionMode()).isEqualTo(PermissionMode.DEFAULT);
    }

    @Test
    void shouldClearHistory() {
        AgentContext ctx = agentService.createContext(AgentOptions.defaults(), tempDir.toString());
        ctx.messages().add(new org.springframework.ai.chat.messages.UserMessage("hello"));

        agentService.clearHistory(ctx);

        assertThat(ctx.messages()).isEmpty();
    }

    @Test
    void shouldTogglePlanMode() {
        AgentContext ctx = agentService.createContext(AgentOptions.defaults(), tempDir.toString());
        PermissionMode original = ctx.permissionMode();

        agentService.togglePlanMode(ctx);
        assertThat(ctx.permissionMode()).isEqualTo(PermissionMode.PLAN);

        agentService.togglePlanMode(ctx);
        assertThat(ctx.permissionMode()).isEqualTo(original);
    }

    @Test
    void shouldReturnAgentResult() {
        // Mock a simple text response
        AssistantMessage msg = new AssistantMessage("Hello, how can I help?");
        ChatResponse response = new ChatResponse(List.of(new Generation(msg)));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);

        AgentContext ctx = agentService.createContext(AgentOptions.defaults(), tempDir.toString());
        AgentService.AgentResult result = agentService.chat("Hi", ctx);

        assertThat(result.text()).isNotNull();
    }

    // ---- stub tool ----

    private static class StubReadTool implements Tool {
        @Override public String name() { return "read_file"; }
        @Override public String description() { return "Read a file"; }
        @Override public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
        @Override public boolean isReadOnly() { return true; }
        @Override public boolean isConcurrencySafe() { return true; }
        @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
            return ToolResult.ok("stub content");
        }
    }
}
