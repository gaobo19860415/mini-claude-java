package com.miniclaudecode.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Single MCP server connection: ProcessBuilder spawn → JSON-RPC 2.0 over stdio.
 */
public class McpConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpConnection.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String serverName;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;
    private Thread readerThread;
    private final BlockingQueue<JsonNode> pendingResponses = new LinkedBlockingQueue<>();
    private volatile boolean initialized = false;

    public McpConnection(String serverName, String command, List<String> args,
                         Map<String, String> env) {
        this.serverName = serverName;
        this.command = command;
        this.args = args != null ? args : List.of();
        this.env = env != null ? env : Map.of();
    }

    /** Start the MCP server process and perform initialize handshake. */
    public void connect() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);

        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // Start reader thread for stdout
        readerThread = new Thread(this::readLoop, "mcp-" + serverName + "-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        // Initialize handshake
        JsonNode initResp = sendRequest("initialize", Map.of(
                "protocolVersion", "2024-11-05",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "mini-claude", "version", "0.1")
        ));
        if (initResp == null || initResp.has("error")) {
            throw new IOException("MCP initialize failed: " + initResp);
        }
        initialized = true;
        log.info("MCP server '{}' initialized", serverName);

        // Send initialized notification
        sendNotification("notifications/initialized", Map.of());
    }

    /** Send a JSON-RPC request and wait for response. */
    public JsonNode sendRequest(String method, Map<String, Object> params) throws IOException {
        String id = UUID.randomUUID().toString().substring(0, 8);
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", mapper.valueToTree(params));

        writeMessage(request);

        // Wait for matching response
        try {
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                JsonNode msg = pendingResponses.poll(1, TimeUnit.SECONDS);
                if (msg == null) continue;
                if (id.equals(msg.path("id").asText())) {
                    return msg;
                }
                // Put back non-matching messages
                pendingResponses.offer(msg);
            }
            return mapper.createObjectNode().put("error", "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return mapper.createObjectNode().put("error", "interrupted");
        }
    }

    /** Send a JSON-RPC notification (no response expected). */
    public void sendNotification(String method, Map<String, Object> params) throws IOException {
        ObjectNode notif = mapper.createObjectNode();
        notif.put("jsonrpc", "2.0");
        notif.put("method", method);
        if (params != null) {
            notif.set("params", mapper.valueToTree(params));
        }
        writeMessage(notif);
    }

    /** List available tools from the MCP server. */
    public List<Map<String, Object>> listTools() throws IOException {
        JsonNode resp = sendRequest("tools/list", Map.of());
        if (resp.has("error")) {
            log.warn("MCP tools/list failed: {}", resp.path("error"));
            return List.of();
        }
        JsonNode tools = resp.path("result").path("tools");
        if (!tools.isArray()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode tool : tools) {
            Map<String, Object> def = new LinkedHashMap<>();
            def.put("name", "mcp__" + serverName + "__" + tool.path("name").asText());
            def.put("description", tool.path("description").asText(""));
            JsonNode schema = tool.path("inputSchema");
            if (schema.isObject()) {
                def.put("input_schema", mapper.convertValue(schema, Map.class));
            }
            result.add(def);
        }
        return result;
    }

    /** Call a tool on the MCP server. */
    public String callTool(String toolName, Map<String, Object> arguments) throws IOException {
        String remoteName = toolName.replace("mcp__" + serverName + "__", "");
        JsonNode resp = sendRequest("tools/call", Map.of(
                "name", remoteName,
                "arguments", arguments
        ));
        if (resp.has("error")) {
            return "MCP tool error: " + resp.path("error").path("message").asText("unknown");
        }
        JsonNode content = resp.path("result").path("content");
        if (content.isArray()) {
            var sb = new StringBuilder();
            for (JsonNode item : content) {
                if (item.has("text")) sb.append(item.get("text").asText());
            }
            return sb.toString();
        }
        return content.asText();
    }

    /** Is the process still alive? */
    public boolean isAlive() {
        return process != null && process.isAlive();
    }

    @Override
    public void close() {
        try {
            if (writer != null) {
                sendNotification("exit", Map.of());
                writer.close();
            }
        } catch (IOException e) {
            // ignore
        }
        if (process != null) {
            process.destroyForcibly();
            try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        initialized = false;
    }

    // ---- Internal ----

    private void writeMessage(ObjectNode message) throws IOException {
        String json = mapper.writeValueAsString(message);
        synchronized (writer) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }
    }

    private void readLoop() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonNode msg = mapper.readTree(line);
                    pendingResponses.offer(msg);
                } catch (Exception e) {
                    log.debug("MCP non-JSON line: {}", line.substring(0, Math.min(line.length(), 80)));
                }
            }
        } catch (IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                log.debug("MCP reader for '{}' ended: {}", serverName, e.getMessage());
            }
        }
    }
}
