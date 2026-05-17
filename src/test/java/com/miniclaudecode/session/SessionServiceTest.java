package com.miniclaudecode.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionServiceTest {

    @TempDir
    Path tempDir;

    private SessionService service;

    @BeforeEach
    void setUp() {
        service = new SessionService();
    }

    @Test
    void shouldSaveAndLoadSession() {
        Map<String, Object> data = Map.of("key", "value", "count", 42);
        service.save("session-1", data);

        Map<String, Object> loaded = service.load("session-1");
        assertThat(loaded).isNotNull()
                .containsEntry("key", "value")
                .containsEntry("count", 42);
    }

    @Test
    void shouldReturnNullForMissingSession() {
        assertThat(service.load("nonexistent")).isNull();
    }

    @Test
    void shouldReturnLatestSessionId() {
        service.save("older", Map.of());
        service.save("newer", Map.of());
        assertThat(service.latestSessionId()).isEqualTo("newer");
    }

    @Test
    void shouldListAllSessions() {
        service.save("a", Map.of());
        service.save("b", Map.of());
        assertThat(service.listSessions()).contains("a", "b");
    }
}
