package com.miniclaudecode.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Permission checker with 5 modes + declarative allow/deny rules from .claude/settings.json.
 */
public class PermissionChecker {

    private static final Logger log = LoggerFactory.getLogger(PermissionChecker.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Set<String> READ_TOOLS = Set.of("read_file", "list_files", "grep_search", "web_fetch");
    private static final Set<String> EDIT_TOOLS = Set.of("write_file", "edit_file");

    private final PermissionMode mode;
    private final Map<String, List<String>> allowRules;
    private final Map<String, List<String>> denyRules;

    public PermissionChecker(PermissionMode mode) {
        this.mode = mode;
        this.allowRules = new HashMap<>();
        this.denyRules = new HashMap<>();
        loadSettings();
    }

    /** Permission decision for a tool invocation. */
    public enum Decision { ALLOW, ASK, DENY }

    /**
     * Check permission for a tool call.
     */
    public Decision check(String toolName, Map<String, Object> arguments, Set<String> confirmedPaths) {
        // Bypass mode — allow everything
        if (mode == PermissionMode.BYPASS_PERMISSIONS) {
            return Decision.ALLOW;
        }

        // Plan / dontAsk mode — deny writes
        if (mode == PermissionMode.PLAN || mode == PermissionMode.DONT_ASK) {
            if (!READ_TOOLS.contains(toolName)) {
                return Decision.DENY;
            }
            return Decision.ALLOW;
        }

        // acceptEdits mode — auto-approve edit tools
        if (mode == PermissionMode.ACCEPT_EDITS && EDIT_TOOLS.contains(toolName)) {
            return Decision.ALLOW;
        }

        // Check declarative rules
        Decision ruleDecision = checkRules(toolName, arguments);
        if (ruleDecision != null) {
            return ruleDecision;
        }

        // Read tools always allowed in default mode
        if (READ_TOOLS.contains(toolName)) {
            return Decision.ALLOW;
        }

        // Already confirmed this path in this session?
        String filePath = extractFilePath(arguments);
        if (filePath != null && confirmedPaths.contains(filePath)) {
            return Decision.ALLOW;
        }

        // Write/shell — ask user
        return Decision.ASK;
    }

    private Decision checkRules(String toolName, Map<String, Object> arguments) {
        // Check deny rules first
        for (var entry : denyRules.entrySet()) {
            if (matchesRule(toolName, arguments, entry.getKey())) {
                for (String pattern : entry.getValue()) {
                    if (matchesPattern(toolName, arguments, pattern)) {
                        return Decision.DENY;
                    }
                }
            }
        }
        // Check allow rules
        for (var entry : allowRules.entrySet()) {
            if (matchesRule(toolName, arguments, entry.getKey())) {
                for (String pattern : entry.getValue()) {
                    if (matchesPattern(toolName, arguments, pattern)) {
                        return Decision.ALLOW;
                    }
                }
            }
        }
        return null;
    }

    private boolean matchesRule(String toolName, Map<String, Object> arguments, String ruleKey) {
        // Simple rule matching: ruleKey is tool name or "tool:name"
        return ruleKey.equals(toolName) || ruleKey.equals("tool:" + toolName);
    }

    private boolean matchesPattern(String toolName, Map<String, Object> arguments, String pattern) {
        // For now, simple substring match against file_path if present
        String filePath = extractFilePath(arguments);
        if (filePath != null && pattern.contains(filePath)) {
            return true;
        }
        return pattern.equals("*"); // wildcard matches all
    }

    private String extractFilePath(Map<String, Object> arguments) {
        Object path = arguments.get("file_path");
        return path != null ? path.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private void loadSettings() {
        Path settingsPath = Paths.get(".claude/settings.json");
        if (!Files.exists(settingsPath)) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(settingsPath.toFile());
            JsonNode permissions = root.get("permissions");
            if (permissions != null) {
                JsonNode allow = permissions.get("allow");
                if (allow != null) {
                    allow.fields().forEachRemaining(e ->
                            allowRules.put(e.getKey(), mapper.convertValue(e.getValue(), List.class)));
                }
                JsonNode deny = permissions.get("deny");
                if (deny != null) {
                    deny.fields().forEachRemaining(e ->
                            denyRules.put(e.getKey(), mapper.convertValue(e.getValue(), List.class)));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to load .claude/settings.json: {}", e.getMessage());
        }
    }
}
