package com.miniclaudecode.tool.agent;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Sub-agent tool — fork-return pattern.
 * The actual execution is handled by AgentService; this tool provides the interface.
 */
@Component
public class SubAgentTool implements Tool {

    @Override
    public String name() { return "sub_agent"; }

    @Override
    public String description() {
        return "Launch a sub-agent to handle complex, multi-step tasks autonomously. " +
                "Available types: general-purpose, explore, plan.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "description", Map.of("type", "string",
                                "description", "A short description of the task"),
                        "prompt", Map.of("type", "string",
                                "description", "The task for the sub-agent to perform"),
                        "subagent_type", Map.of("type", "string",
                                "description", "The type of sub-agent: general-purpose, explore, or plan")
                ),
                "required", List.of("description", "prompt", "subagent_type")
        );
    }

    @Override
    public boolean isDeferred() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        // Actual sub-agent execution handled by AgentService
        String type = (String) args.get("subagent_type");
        String prompt = (String) args.get("prompt");
        return ToolResult.ok("Sub-agent (" + type + ") execution stub — " +
                "handled by AgentService. Prompt: " + prompt.substring(0, Math.min(prompt.length(), 100)));
    }
}
