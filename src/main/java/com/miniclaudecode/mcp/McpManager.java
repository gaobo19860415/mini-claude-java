package com.miniclaudecode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Manages MCP server lifecycle — load .mcp.json, connect servers, discover tools.
 */
@Component
public class McpManager {

    private static final Logger log = LoggerFactory.getLogger(McpManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final Map<String, McpConnection> connections = new LinkedHashMap<>();
    private final List<Map<String, Object>> mcpToolDefs = new ArrayList<>();

    /** Load .mcp.json and connect to all configured servers. */
    public void loadAndConnect() {
        Path configPath = Paths.get(".mcp.json");
        if (!Files.exists(configPath)) {
            log.debug("No .mcp.json found, skipping MCP");
            return;
        }
        try {
            String content = Files.readString(configPath);
            var root = mapper.readTree(content);
            var servers = root.get("mcpServers");
            if (servers == null || !servers.isObject()) return;

            var fields = servers.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String name = entry.getKey();
                var config = entry.getValue();
                connectServer(name, config);
            }
        } catch (IOException e) {
            log.warn("Failed to load .mcp.json: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void connectServer(String name, JsonNode config) {
        String command = config.get("command").asText();
        List<String> args = new ArrayList<>();
        if (config.has("args") && config.get("args").isArray()) {
            config.get("args").forEach(a -> args.add(a.asText()));
        }
        Map<String, String> env = new HashMap<>();
        if (config.has("env") && config.get("env").isObject()) {
            config.get("env").fields().forEachRemaining(
                    e -> env.put(e.getKey(), e.getValue().asText()));
        }

        try {
            McpConnection conn = new McpConnection(name, command, args, env);
            conn.connect();
            connections.put(name, conn);

            // Discover tools
            List<Map<String, Object>> tools = conn.listTools();
            mcpToolDefs.addAll(tools);
            log.info("MCP '{}': discovered {} tools", name, tools.size());
        } catch (IOException e) {
            log.warn("Failed to connect MCP server '{}': {}", name, e.getMessage());
        }
    }

    /** Get all tool definitions from connected MCP servers. */
    public List<Map<String, Object>> getToolDefinitions() {
        return mcpToolDefs;
    }

    /** Execute a tool on a connected MCP server. */
    public String callTool(String fullName, Map<String, Object> arguments) {
        for (var entry : connections.entrySet()) {
            String prefix = "mcp__" + entry.getKey() + "__";
            if (fullName.startsWith(prefix)) {
                try {
                    return entry.getValue().callTool(fullName, arguments);
                } catch (IOException e) {
                    return "MCP tool call failed: " + e.getMessage();
                }
            }
        }
        return "No MCP server found for tool: " + fullName;
    }

    @PreDestroy
    public void shutdown() {
        for (var entry : connections.entrySet()) {
            log.info("Shutting down MCP server '{}'", entry.getKey());
            entry.getValue().close();
        }
        connections.clear();
        mcpToolDefs.clear();
    }
}
