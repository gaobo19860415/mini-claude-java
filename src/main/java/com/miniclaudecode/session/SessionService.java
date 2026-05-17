package com.miniclaudecode.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Session persistence — save/restore/list to ~/.mini-claude/sessions/.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private Path sessionsDir;

    public SessionService() {
        this.sessionsDir = Paths.get(System.getProperty("user.home"),
                ".mini-claude", "sessions");
    }

    /** Save session data to disk. */
    public void save(String sessionId, Map<String, Object> data) {
        try {
            Files.createDirectories(sessionsDir);
            Path file = sessionsDir.resolve(sessionId + ".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
            log.debug("Session {} saved", sessionId);
        } catch (IOException e) {
            log.warn("Failed to save session {}: {}", sessionId, e.getMessage());
        }
    }

    /** Load session data from disk. Returns null if not found. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> load(String sessionId) {
        Path file = sessionsDir.resolve(sessionId + ".json");
        if (!Files.exists(file)) return null;
        try {
            return mapper.readValue(file.toFile(), Map.class);
        } catch (IOException e) {
            log.warn("Failed to load session {}: {}", sessionId, e.getMessage());
            return null;
        }
    }

    /** List all saved session IDs, most recent first. */
    public List<String> listSessions() {
        if (!Files.isDirectory(sessionsDir)) return List.of();
        try (var stream = Files.list(sessionsDir)) {
            return stream
                    .filter(f -> f.getFileName().toString().endsWith(".json"))
                    .sorted((a, b) -> {
                        try {
                            return Long.compare(
                                    Files.getLastModifiedTime(b).toMillis(),
                                    Files.getLastModifiedTime(a).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .map(f -> f.getFileName().toString().replace(".json", ""))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Get the most recent session ID. */
    public String latestSessionId() {
        var list = listSessions();
        return list.isEmpty() ? null : list.get(0);
    }
}
