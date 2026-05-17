package com.miniclaudecode.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(List.of(
                new StubTool("read_file", true, true, false),
                new StubTool("write_file", false, false, false),
                new StubTool("tool_search", true, true, true)
        ));
    }

    @Test
    void shouldFindToolByName() {
        assertThat(registry.findByName("read_file")).isNotNull();
        assertThat(registry.findByName("unknown")).isNull();
    }

    @Test
    void shouldExcludeDeferredFromActiveDefinitions() {
        var defs = registry.getActiveDefinitions();
        assertThat(defs).hasSize(2)
                .extracting(d -> d.get("name"))
                .containsExactlyInAnyOrder("read_file", "write_file");
    }

    @Test
    void shouldIncludeDeferredInAllDefinitions() {
        var defs = registry.getAllDefinitions();
        assertThat(defs).hasSize(3);
    }

    @Test
    void shouldReturnDeferredToolNames() {
        assertThat(registry.getDeferredToolNames()).containsExactly("tool_search");
    }

    @Test
    void shouldReturnConcurrencySafeToolNames() {
        assertThat(registry.getConcurrencySafeToolNames()).containsExactlyInAnyOrder("read_file", "tool_search");
    }

    @Test
    void definitionShouldHaveNameDescriptionSchema() {
        var def = registry.getActiveDefinitions().get(0);
        assertThat(def).containsKeys("name", "description", "input_schema");
    }

    // ---- stub ----

    private record StubTool(String name, boolean readOnly, boolean concurrencySafe, boolean deferred) implements Tool {
        @Override public String description() { return name + " description"; }
        @Override public Map<String, Object> inputSchema() { return Map.of("type", "object"); }
        @Override public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
            return ToolResult.ok("stub ok");
        }
        @Override public boolean isReadOnly() { return readOnly; }
        @Override public boolean isConcurrencySafe() { return concurrencySafe; }
        @Override public boolean isDeferred() { return deferred; }
    }
}
