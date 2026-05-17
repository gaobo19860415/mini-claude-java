package com.miniclaudecode.agent;

import com.miniclaudecode.tool.PermissionMode;

/**
 * Configuration options for an Agent session.
 */
public record AgentOptions(
        PermissionMode permissionMode,
        String model,
        boolean thinking,
        Double maxCostUsd,
        Integer maxTurns,
        boolean isSubAgent,
        String customSystemPrompt
) {
    public AgentOptions {
        if (permissionMode == null) permissionMode = PermissionMode.DEFAULT;
        if (model == null) model = "claude-opus-4-6";
    }

    public static AgentOptions defaults() {
        return new AgentOptions(PermissionMode.DEFAULT, "claude-opus-4-6",
                false, null, null, false, null);
    }

    public AgentOptions withPermissionMode(PermissionMode mode) {
        return new AgentOptions(mode, model, thinking, maxCostUsd, maxTurns, isSubAgent, customSystemPrompt);
    }

    public AgentOptions withSubAgent(boolean subAgent) {
        return new AgentOptions(permissionMode, model, thinking, maxCostUsd, maxTurns, subAgent, customSystemPrompt);
    }
}
