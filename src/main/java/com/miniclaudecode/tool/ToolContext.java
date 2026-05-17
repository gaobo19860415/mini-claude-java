package com.miniclaudecode.tool;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Execution context passed to each tool invocation.
 */
public record ToolContext(
        PermissionMode permissionMode,
        Path cwd,
        String sessionId,
        Set<String> confirmedPaths,
        Map<String, Long> readFileState,
        String model) {
}
