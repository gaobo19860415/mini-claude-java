package com.miniclaudecode.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks last-modified timestamps of files read by tools.
 * On write, compares against recorded mtime to detect external modifications.
 */
public class MtimeTracker {

    private final ConcurrentHashMap<String, Long> state = new ConcurrentHashMap<>();

    /** Record the last-modified time of a file after reading it. */
    public void recordRead(Path path) {
        String key = path.toAbsolutePath().toString();
        try {
            state.put(key, Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            // File doesn't exist — treat as new file, no conflict possible
            state.put(key, 0L);
        }
    }

    /**
     * Check if file has been modified since last read.
     * Returns the conflict message, or null if no conflict.
     */
    public String checkConflict(Path path) {
        String key = path.toAbsolutePath().toString();
        Long lastSeen = state.get(key);
        if (lastSeen == null) {
            return null; // never read, no conflict
        }
        try {
            long current = Files.getLastModifiedTime(path).toMillis();
            if (current != lastSeen) {
                return String.format("File %s has been modified since last read " +
                        "(last seen: %d, current: %d). Re-read the file before editing.",
                        key, lastSeen, current);
            }
        } catch (IOException e) {
            // File doesn't exist now — was deleted
            return String.format("File %s has been deleted since last read.", key);
        }
        return null;
    }

    public ConcurrentHashMap<String, Long> getState() {
        return state;
    }
}
