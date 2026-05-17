package com.miniclaudecode.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MemoryServiceTest {

    @TempDir
    Path tempDir;

    private MemoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryService(mock(ChatModel.class));
        // Override memory dir to use temp dir
        service.init(tempDir.toString());
    }

    @Test
    void shouldSaveAndLoadMemory() {
        var entry = new MemoryEntry(
                "test-memory", "A test memory entry",
                MemoryType.PROJECT, "Memory content body.",
                java.time.Instant.now(), java.time.Instant.now()
        );

        service.save(entry);
        service.loadAll();

        var cache = service.getCache();
        assertThat(cache).hasSize(1);
        assertThat(cache.get(0).name()).isEqualTo("test-memory");
        assertThat(cache.get(0).type()).isEqualTo(MemoryType.PROJECT);
    }

    @Test
    void shouldCreateMemoryDirectory() {
        var entry = new MemoryEntry("init", "init desc", MemoryType.USER,
                "init content", java.time.Instant.now(), java.time.Instant.now());
        service.save(entry);

        assertThat(Files.exists(tempDir)).isTrue();
    }

    @Test
    void shouldHaveFourMemoryTypes() {
        assertThat(MemoryType.values()).containsExactly(
                MemoryType.USER, MemoryType.FEEDBACK, MemoryType.PROJECT, MemoryType.REFERENCE);
    }

    @Test
    void memoryEntrySummaryShouldIncludeTypeAndName() {
        var entry = new MemoryEntry("test", "A description", MemoryType.FEEDBACK,
                "content", java.time.Instant.now(), java.time.Instant.now());
        assertThat(entry.summary()).contains("FEEDBACK", "test", "description");
    }
}
