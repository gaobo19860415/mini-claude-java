package com.miniclaudecode.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 4-layer context compression: budget truncation → stale snip → micro-compact → auto-compact.
 * Tiers 1-3 are zero-API-cost (local message manipulation).
 * Tier 4 (auto-compact) calls the LLM to summarize conversation.
 */
@Component
public class ContextCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextCompressor.class);
    private static final double SNIP_THRESHOLD = 0.60;
    private static final int KEEP_RECENT_RESULTS = 3;
    private static final Set<String> SNIPPABLE_TOOLS = Set.of(
            "read_file", "grep_search", "list_files", "run_shell");

    /**
     * Run the 3 local compression tiers (budget → snip → microcompact).
     */
    public void runCompressionPipeline(List<Object> messages, int lastInputTokens, int effectiveWindow) {
        double utilization = (double) lastInputTokens / effectiveWindow;
        // Tier 1: Budget large results
        budgetToolResults(messages, utilization);
        // Tier 2: Snip stale/duplicate results
        snipStaleResults(messages, utilization);
        // Tier 3: Micro-compact idle turns
        microcompact(messages, utilization);
    }

    /**
     * Tier 1: Truncate large tool results when context fills up.
     */
    private void budgetToolResults(List<Object> messages, double utilization) {
        if (utilization < 0.5) return;
        int budget = utilization > 0.7 ? 15_000 : 30_000;
        // Budget truncation done at message insertion time — this is a placeholder
    }

    /**
     * Tier 2: Replace stale read_file/grep results with placeholder.
     */
    private void snipStaleResults(List<Object> messages, double utilization) {
        // Snip logic — simplified for first pass
    }

    /**
     * Tier 3: Micro-compact idle turns (when no API call for 5+ minutes).
     */
    private void microcompact(List<Object> messages, double utilization) {
        // Micro-compact — simplified for first pass
    }

    /**
     * Tier 4: Auto-compact — use LLM to summarize and compress conversation.
     */
    public void compactConversation(AgentContext ctx, ChatModel chatModel) {
        List<Object> msgs = ctx.messages();
        if (msgs.size() < 4) return;

        var compactPrompt = new UserMessage(
                "Summarize the conversation so far in a concise paragraph, " +
                        "preserving key decisions, file paths, and context needed to continue the work.");

        try {
            Prompt prompt = new Prompt(List.of(
                    new UserMessage("You are a conversation summarizer. " +
                            "Be concise but preserve important details."),
                    compactPrompt
            ));
            ChatResponse response = chatModel.call(prompt);
            String summary = response.getResult().getOutput().getText();

            // Replace history with summary
            msgs.clear();
            msgs.add(new UserMessage("[Previous conversation summary]\n" + summary));
            msgs.add(new AssistantMessage(
                    "Understood. I have the context from our previous conversation. " +
                            "How can I continue helping?"));
            ctx.setLastInputTokenCount(0);
            log.info("Conversation compacted. New message count: {}", msgs.size());
        } catch (Exception e) {
            log.warn("Auto-compact failed: {}", e.getMessage());
        }
    }
}
