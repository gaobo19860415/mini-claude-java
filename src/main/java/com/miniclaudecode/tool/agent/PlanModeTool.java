package com.miniclaudecode.tool.agent;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Plan mode toggle tool. Entering plan mode switches to read-only.
 * Exiting plan mode returns to the previous permission mode.
 */
@Component
public class PlanModeTool implements Tool {

    @Override
    public String name() { return "plan_mode"; }

    @Override
    public String description() {
        return "Enter or exit plan mode. In plan mode, only read-only tools are available. " +
                "Use this for designing implementation plans before writing code.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(),
                "required", List.of()
        );
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        // The actual mode toggle is handled by AgentService
        return ToolResult.ok("Plan mode toggle — handled by AgentService. " +
                "Current mode: " + ctx.permissionMode());
    }
}
