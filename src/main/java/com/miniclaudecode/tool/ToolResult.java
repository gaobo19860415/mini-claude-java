package com.miniclaudecode.tool;

/**
 * Result from a tool execution.
 */
public record ToolResult(String content, boolean isError, Long mtimeMs) {

    public static ToolResult ok(String content) {
        return new ToolResult(content, false, null);
    }

    public static ToolResult ok(String content, long mtimeMs) {
        return new ToolResult(content, false, mtimeMs);
    }

    public static ToolResult error(String content) {
        return new ToolResult(content, true, null);
    }
}
