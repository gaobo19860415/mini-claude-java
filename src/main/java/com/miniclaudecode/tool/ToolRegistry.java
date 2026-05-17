package com.miniclaudecode.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Auto-discovers Tool beans and provides lookup + definition generation for LLM.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final Set<String> concurrencySafeToolNames = ConcurrentHashMap.newKeySet();

    public ToolRegistry(List<Tool> toolBeans) {
        for (Tool tool : toolBeans) {
            register(tool);
        }
        log.info("ToolRegistry initialized with {} tools", tools.size());
    }

    private void register(Tool tool) {
        tools.put(tool.name(), tool);
        if (tool.isConcurrencySafe()) {
            concurrencySafeToolNames.add(tool.name());
        }
    }

    /** Look up a tool by name. Returns null if not found. */
    public Tool findByName(String name) {
        return tools.get(name);
    }

    /** Get all tool definitions for sending to LLM (excludes deferred tools). */
    public List<Map<String, Object>> getActiveDefinitions() {
        return tools.values().stream()
                .filter(t -> !t.isDeferred())
                .map(this::toDefinition)
                .collect(Collectors.toList());
    }

    /** Get all tool definitions including deferred ones. */
    public List<Map<String, Object>> getAllDefinitions() {
        return tools.values().stream()
                .map(this::toDefinition)
                .collect(Collectors.toList());
    }

    /** Get names of deferred tools (loaded lazily). */
    public List<String> getDeferredToolNames() {
        return tools.values().stream()
                .filter(Tool::isDeferred)
                .map(Tool::name)
                .collect(Collectors.toList());
    }

    /** Get names of tools safe for concurrent execution. */
    public Set<String> getConcurrencySafeToolNames() {
        return concurrencySafeToolNames;
    }

    /** Get all registered tool names. */
    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    private Map<String, Object> toDefinition(Tool tool) {
        return Map.of(
                "name", tool.name(),
                "description", tool.description(),
                "input_schema", tool.inputSchema()
        );
    }
}
