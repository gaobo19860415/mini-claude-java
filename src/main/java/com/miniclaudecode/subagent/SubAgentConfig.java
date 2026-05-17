package com.miniclaudecode.subagent;

import java.util.List;

/**
 * Sub-agent configuration — 3 built-in types + custom from .claude/agents/*.md.
 */
public record SubAgentConfig(
        String type,
        String description,
        String systemPrompt,
        List<String> allowedTools
) {
    // Built-in types
    public static final SubAgentConfig EXPLORE = new SubAgentConfig(
            "explore",
            "Codebase exploration agent — read-only tools only",
            "You are an explore agent. Search the codebase to answer questions. " +
                    "Use read_file, list_files, and grep_search. Report findings concisely.",
            List.of("read_file", "list_files", "grep_search")
    );

    public static final SubAgentConfig PLAN = new SubAgentConfig(
            "plan",
            "Software architect agent for designing implementation plans",
            "You are a plan agent. Design implementation plans. Only use read-only tools.",
            List.of("read_file", "list_files", "grep_search", "web_fetch")
    );

    public static final SubAgentConfig GENERAL = new SubAgentConfig(
            "general",
            "General-purpose sub-agent with full tool access",
            "You are a general-purpose sub-agent. Complete the assigned task and return results.",
            List.of() // empty = all tools
    );

    public static SubAgentConfig builtIn(String type) {
        return switch (type) {
            case "explore" -> EXPLORE;
            case "plan" -> PLAN;
            case "general" -> GENERAL;
            default -> null;
        };
    }

    public boolean hasAllTools() { return allowedTools.isEmpty(); }
}
