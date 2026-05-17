package com.miniclaudecode.memory;

import java.time.Instant;

/**
 * A single memory entry with YAML frontmatter fields.
 */
public record MemoryEntry(
        String name,
        String description,
        MemoryType type,
        String content,
        Instant createdAt,
        Instant updatedAt
) {
    public String summary() {
        return String.format("[%s] %s: %s", type, name,
                description.length() > 80 ? description.substring(0, 80) + "..." : description);
    }
}
