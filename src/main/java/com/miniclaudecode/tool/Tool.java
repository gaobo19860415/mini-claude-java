package com.miniclaudecode.tool;

import java.util.Map;

/**
 * Tool interface — every tool implements this and is auto-discovered by ToolRegistry.
 */
public interface Tool {

    /** Unique tool name, matches the name sent to LLM. */
    String name();

    /** Description sent to LLM in the tool definition. */
    String description();

    /** JSON Schema for the tool's input parameters. */
    Map<String, Object> inputSchema();

    /** Execute the tool with given arguments. */
    ToolResult execute(Map<String, Object> arguments, ToolContext context) throws Exception;

    /** Whether this tool has no side effects (can be auto-approved in acceptEdits mode). */
    default boolean isReadOnly() { return false; }

    /** Whether this tool can run concurrently with other tools. */
    default boolean isConcurrencySafe() { return false; }

    /** Whether this tool always requires user confirmation. */
    default boolean needsConfirm() { return false; }

    /** Whether this tool is deferred (loaded lazily, discovered via ToolSearch). */
    default boolean isDeferred() { return false; }
}
