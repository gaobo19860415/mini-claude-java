package com.miniclaudecode.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniclaudecode.prompt.PromptBuilder;
import com.miniclaudecode.tool.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Core Agent Loop — the heart of Mini Claude Code.
 * <p>
 * Flow: user input → build prompt → call LLM → parse response
 * → execute tools (parallel for concurrency-safe) → append results
 * → budget check → repeat until no more tool calls.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final PromptBuilder promptBuilder;
    private final ContextCompressor compressor;
    private final ExecutorService toolExecutor = Executors.newCachedThreadPool();

    public AgentService(ChatModel chatModel, ToolRegistry toolRegistry,
                        PromptBuilder promptBuilder, ContextCompressor compressor) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.compressor = compressor;
    }

    // ---- Main entry point ----

    /**
     * Process one user message and return the assistant's text response.
     */
    public AgentResult chat(String userMessage, AgentContext ctx) {
        ctx.messages().add(new UserMessage(userMessage));
        return runAgentLoop(ctx);
    }

    // ---- Agent loop ----

    @SuppressWarnings("unchecked")
    private AgentResult runAgentLoop(AgentContext ctx) {
        var fullResponse = new StringBuilder();

        while (true) {
            if (ctx.isBudgetExceeded()) {
                String reason = ctx.budgetExceededReason();
                log.info("Budget exceeded: {}", reason);
                fullResponse.append("\n[Budget exceeded: ").append(reason).append("]");
                break;
            }

            ctx.incrementTurns();
            ctx.setLastApiCallTime(System.currentTimeMillis());

            // Build prompt with current message history
            List<Message> promptMessages = new ArrayList<>();
            promptMessages.add(new SystemMessage(ctx.systemPrompt()));
            for (Object msg : ctx.messages()) {
                promptMessages.add((Message) msg);
            }

            // Call LLM
            TurnResult turn;
            try {
                turn = callLLM(ctx, promptMessages);
            } catch (Exception e) {
                log.error("Agent loop error", e);
                fullResponse.append("\n[Error: ").append(e.getMessage()).append("]");
                break;
            }

            // Collect text output
            if (turn.text != null && !turn.text.isEmpty()) {
                fullResponse.append(turn.text);
                log.info("[agentLoop] Turn {} text ({}chars): {}",
                        ctx.currentTurns(), turn.text.length(),
                        turn.text.length() > 200 ? turn.text.substring(0, 200) + "..." : turn.text);
            } else {
                log.info("[agentLoop] Turn {} returned no text", ctx.currentTurns());
            }

            // No more tool calls — agent is done
            if (turn.toolCalls.isEmpty()) {
                log.info("[agentLoop] No tool calls, loop done. fullResponse length={}", fullResponse.length());
                break;
            }

            log.info("[agentLoop] Turn {} has {} tool calls, executing...",
                    ctx.currentTurns(), turn.toolCalls.size());

            // Execute tools
            List<TurnResult.ToolCallResult> results = executeTools(turn.toolCalls, ctx);
            for (var r : results) {
                log.info("[agentLoop] Tool result: name={}, isError={}, contentLen={}",
                        r.toolName(), r.result().isError(),
                        r.result().content() != null ? r.result().content().length() : 0);
            }
            appendToolResults(ctx, results);

            // Run compression if context is filling
            int effectiveWindow = 180_000;
            compressor.runCompressionPipeline(
                    ctx.messages(), ctx.lastInputTokenCount(), effectiveWindow);

            if (ctx.lastInputTokenCount() > effectiveWindow * 0.85) {
                log.info("Context window filling up, auto-compacting...");
                compressor.compactConversation(ctx, chatModel);
            }
        }

        return new AgentResult(fullResponse.toString(),
                ctx.totalInputTokens(), ctx.totalOutputTokens(), ctx.currentTurns());
    }

    // ---- LLM call + tool call extraction ----

    private TurnResult callLLM(AgentContext ctx, List<Message> messages) {
        var textBuf = new StringBuilder();
        var toolCalls = new ArrayList<PendingToolCall>();
        int inputTokens = 0, outputTokens = 0;

        Prompt prompt = new Prompt(messages);
        log.info("[callLLM] Sending {} messages to LLM", messages.size());
        ChatResponse response = chatModel.call(prompt);

        var generations = response.getResults();
        int genCount = generations != null ? generations.size() : 0;
        log.info("[callLLM] Got {} generation(s)", genCount);

        // When the model returns multiple generations (e.g. thinking + answer),
        // iterate all of them: the last one with text is the user-facing answer.
        // Tool calls are collected from all generations.
        if (generations != null) {
            for (int i = 0; i < generations.size(); i++) {
                var gen = generations.get(i);
                var output = gen.getOutput();
                if (!(output instanceof AssistantMessage)) continue;
                AssistantMessage am = (AssistantMessage) output;

                String content = am.getText();
                boolean hasTools = am.hasToolCalls();
                log.info("[callLLM] Generation[{}]: textLen={}, hasToolCalls={}",
                        i, content != null ? content.length() : 0, hasTools);

                // For text: only keep the last generation's text (skip thinking)
                if (content != null && !content.isEmpty()) {
                    if (i < generations.size() - 1) {
                        log.info("[callLLM] Generation[{}] is thinking, skipping text", i);
                    } else {
                        textBuf.append(content);
                    }
                }

                // Tool calls: collect from all generations
                List<AssistantMessage.ToolCall> tcs = am.getToolCalls();
                if (tcs != null) {
                    for (AssistantMessage.ToolCall tc : tcs) {
                        log.info("[callLLM] ToolCall: id={}, name={}", tc.id(), tc.name());
                        Map<String, Object> args = parseArguments(tc.arguments());
                        toolCalls.add(new PendingToolCall(tc.id(), tc.name(), args));
                    }
                }
            }
        }

        // Token usage
        var metadata = response.getMetadata();
        if (metadata != null && metadata.getUsage() != null) {
            var usage = metadata.getUsage();
            inputTokens = Math.toIntExact(usage.getPromptTokens());
            outputTokens = Math.toIntExact(usage.getCompletionTokens());
            log.info("[callLLM] Tokens: in={}, out={}", inputTokens, outputTokens);
        }

        ctx.addInputTokens(inputTokens);
        ctx.addOutputTokens(outputTokens);
        ctx.setLastInputTokenCount(inputTokens);

        // Add assistant message to history
        if (!toolCalls.isEmpty()) {
            List<AssistantMessage.ToolCall> tcs = toolCalls.stream()
                    .map(tc -> new AssistantMessage.ToolCall(
                            tc.id, "function", tc.name, toJson(tc.arguments)))
                    .toList();
            ctx.messages().add(new AssistantMessage(textBuf.toString(), Map.of(), tcs));
        } else if (!textBuf.isEmpty()) {
            ctx.messages().add(new AssistantMessage(textBuf.toString()));
        }

        return new TurnResult(textBuf.toString(), toolCalls);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String args) {
        if (args == null || args.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(args, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> args) {
        try {
            return objectMapper.writeValueAsString(args);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    // ---- Tool execution ----

    private List<TurnResult.ToolCallResult> executeTools(
            List<PendingToolCall> toolCalls, AgentContext ctx) {

        var results = new ArrayList<TurnResult.ToolCallResult>();
        Set<String> concurrentSafe = toolRegistry.getConcurrencySafeToolNames();

        var concurrent = new ArrayList<PendingToolCall>();
        var serial = new ArrayList<PendingToolCall>();
        for (var tc : toolCalls) {
            if (concurrentSafe.contains(tc.name)) {
                concurrent.add(tc);
            } else {
                serial.add(tc);
            }
        }

        // Parallel for concurrency-safe tools
        if (!concurrent.isEmpty()) {
            var futures = concurrent.stream()
                    .map(tc -> CompletableFuture.supplyAsync(
                            () -> executeSingleTool(tc, ctx), toolExecutor))
                    .toList();
            for (var future : futures) {
                try {
                    results.add(future.get(60, TimeUnit.SECONDS));
                } catch (Exception e) {
                    results.add(new TurnResult.ToolCallResult(
                            "", "", ToolResult.error("Tool timeout or error: " + e.getMessage())));
                }
            }
        }

        // Sequential for non-concurrency-safe tools
        for (var tc : serial) {
            results.add(executeSingleTool(tc, ctx));
        }

        return results;
    }

    private TurnResult.ToolCallResult executeSingleTool(PendingToolCall tc, AgentContext ctx) {
        Tool tool = toolRegistry.findByName(tc.name);
        if (tool == null) {
            return new TurnResult.ToolCallResult(tc.id, tc.name,
                    ToolResult.error("Unknown tool: " + tc.name));
        }

        PermissionChecker checker = new PermissionChecker(ctx.permissionMode());
        var decision = checker.check(tc.name, tc.arguments, ctx.confirmedPaths());
        if (decision == PermissionChecker.Decision.DENY) {
            return new TurnResult.ToolCallResult(tc.id, tc.name,
                    ToolResult.error("Permission denied for tool: " + tc.name));
        }

        ToolContext toolCtx = new ToolContext(
                ctx.permissionMode(),
                Path.of(System.getProperty("user.dir")),
                ctx.sessionId(),
                ctx.confirmedPaths(),
                ctx.mtimeTracker().getState(),
                ctx.model()
        );

        try {
            ToolResult result = tool.execute(tc.arguments, toolCtx);
            log.debug("Tool {} executed: isError={}", tc.name, result.isError());
            return new TurnResult.ToolCallResult(tc.id, tc.name, result);
        } catch (Exception e) {
            log.error("Tool {} failed: {}", tc.name, e.getMessage());
            return new TurnResult.ToolCallResult(tc.id, tc.name,
                    ToolResult.error("Tool execution error: " + e.getMessage()));
        }
    }

    // ---- Message history helpers ----

    private void appendToolResults(AgentContext ctx, List<TurnResult.ToolCallResult> results) {
        for (var r : results) {
            ctx.messages().add(new ToolResponseMessage(
                    List.of(new ToolResponseMessage.ToolResponse(
                            r.toolCallId, r.toolName, r.result.content())),
                    Map.of()
            ));
        }
    }

    // ---- Sub-agent execution ----

    public AgentResult runSubAgent(String prompt, AgentContext parentCtx,
                                   List<String> allowedTools) {
        var subCtx = new AgentContext(
                UUID.randomUUID().toString().substring(0, 8),
                new AgentOptions(parentCtx.permissionMode(), parentCtx.model(),
                        false, null, null, true, null),
                "You are a sub-agent. Complete the assigned task and return results."
        );
        return chat(prompt, subCtx);
    }

    // ---- Plan mode ----

    public void togglePlanMode(AgentContext ctx) {
        if (ctx.permissionMode() == PermissionMode.PLAN) {
            ctx.setPermissionMode(ctx.prePlanMode() != null ? ctx.prePlanMode() : PermissionMode.DEFAULT);
            ctx.setPrePlanMode(null);
            ctx.setPlanFilePath(null);
            ctx.setSystemPrompt(promptBuilder.build(
                    System.getProperty("user.dir"), ctx.model()));
            log.info("Exited plan mode → {}", ctx.permissionMode());
        } else {
            ctx.setPrePlanMode(ctx.permissionMode());
            ctx.setPermissionMode(PermissionMode.PLAN);
            ctx.setPlanFilePath(".claude/plans/plan_" + ctx.sessionId() + ".md");
            ctx.setSystemPrompt(ctx.systemPrompt() +
                    "\nYou are in PLAN MODE. Only read-only tools are available. " +
                    "Write your plan to " + ctx.planFilePath());
            log.info("Entered plan mode. Plan file: {}", ctx.planFilePath());
        }
    }

    // ---- Session management ----

    public AgentContext createContext(AgentOptions options, String cwd) {
        String sessionId = UUID.randomUUID().toString().substring(0, 8);
        String systemPrompt = promptBuilder.build(cwd, options.model());
        return new AgentContext(sessionId, options, systemPrompt);
    }

    public void clearHistory(AgentContext ctx) {
        ctx.messages().clear();
        log.info("Conversation cleared.");
    }

    // ---- Data classes ----

    private record PendingToolCall(String id, String name, Map<String, Object> arguments) {}

    private record TurnResult(String text, List<PendingToolCall> toolCalls) {
        record ToolCallResult(String toolCallId, String toolName, ToolResult result) {}
    }

    public record AgentResult(String text, int inputTokens, int outputTokens, int turns) {}
}
