package com.miniclaudecode.tool.search;

import com.miniclaudecode.tool.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Deferred tool that allows the LLM to discover other deferred tools by name.
 */
@Component
public class ToolSearchTool implements Tool {

    private final ToolRegistry registry;

    public ToolSearchTool(@Lazy ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() { return "tool_search"; }

    @Override
    public String description() {
        return "Search for available tools. Returns tool names and descriptions.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string",
                                "description", "Search query or tool name to look up")
                ),
                "required", List.of()
        );
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public boolean isConcurrencySafe() { return true; }

    @Override
    public boolean isDeferred() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String query = (String) args.getOrDefault("query", "");
        var result = new StringBuilder();
        for (var entry : registry.getAllDefinitions()) {
            String name = entry.get("name").toString();
            String desc = entry.get("description").toString();
            if (query.isEmpty() || name.contains(query) || desc.contains(query)) {
                result.append(String.format("- **%s**: %s%n", name, desc));
            }
        }
        if (result.isEmpty()) {
            return ToolResult.ok("No tools match query: " + query);
        }
        return ToolResult.ok(result.toString());
    }
}
