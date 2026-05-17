package com.miniclaudecode.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Memory system with 4 types, file-based persistence, and semantic recall via sideQuery.
 * Storage: ~/.mini-claude/projects/{SHA256(cwd).substring(0,16)}/memory/
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);
    private static final Pattern YAML_KEY = Pattern.compile("^(\\w+):\\s*(.*)$");

    private final ChatModel chatModel;
    private Path memoryDir;
    private List<MemoryEntry> cache = new ArrayList<>();
    private boolean loaded = false;

    public MemoryService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** Initialize with the project root directory. */
    public void init(String cwd) {
        String hash = sha256Short(cwd);
        this.memoryDir = Paths.get(System.getProperty("user.home"),
                ".mini-claude", "projects", hash, "memory");
        loadAll();
    }

    /** Load all memories from disk. */
    public synchronized void loadAll() {
        cache.clear();
        if (memoryDir == null || !Files.isDirectory(memoryDir)) return;
        try (var stream = Files.list(memoryDir)) {
            for (Path file : stream.sorted().toList()) {
                if (file.getFileName().toString().equals("MEMORY.md")) continue;
                if (!file.getFileName().toString().endsWith(".md")) continue;
                try {
                    String content = Files.readString(file);
                    MemoryEntry entry = parseEntry(content, file);
                    if (entry != null) cache.add(entry);
                } catch (IOException e) {
                    // skip unreadable files
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list memory dir: {}", e.getMessage());
        }
        loaded = true;
        log.info("Loaded {} memories from {}", cache.size(), memoryDir);
    }

    /** Parse a memory .md file with YAML frontmatter. */
    private MemoryEntry parseEntry(String content, Path file) {
        Matcher m = FRONTMATTER_PATTERN.matcher(content);
        if (!m.find()) return null;
        String frontmatter = m.group(1);
        String body = m.group(2).trim();

        var fields = new HashMap<String, String>();
        for (String line : frontmatter.split("\n")) {
            Matcher ym = YAML_KEY.matcher(line);
            if (ym.find()) fields.put(ym.group(1).trim(), ym.group(2).trim());
        }

        String name = fields.getOrDefault("name", file.getFileName().toString().replace(".md", ""));
        String desc = fields.getOrDefault("description", "");
        MemoryType type;
        try {
            type = MemoryType.valueOf(fields.getOrDefault("type", "REFERENCE").toUpperCase());
        } catch (IllegalArgumentException e) {
            type = MemoryType.REFERENCE;
        }

        return new MemoryEntry(name, desc, type, body, Instant.now(), Instant.now());
    }

    /** Save a new memory entry. */
    public void save(MemoryEntry entry) {
        try {
            if (memoryDir == null) return;
            Files.createDirectories(memoryDir);
            String filename = sanitizeFilename(entry.name()) + ".md";
            Path file = memoryDir.resolve(filename);

            String frontmatter = String.format("---\nname: %s\ndescription: %s\ntype: %s\n---\n\n%s",
                    entry.name(), entry.description(), entry.type(), entry.content());
            Files.writeString(file, frontmatter);
            updateIndex(entry, filename);
            cache.add(entry);
        } catch (IOException e) {
            log.warn("Failed to save memory: {}", e.getMessage());
        }
    }

    private void updateIndex(MemoryEntry entry, String filename) throws IOException {
        Path indexPath = memoryDir.resolve("MEMORY.md");
        String line = String.format("- [%s](%s) — %s%n", entry.name(), filename, entry.description());
        if (Files.exists(indexPath)) {
            Files.writeString(indexPath, Files.readString(indexPath) + line);
        } else {
            Files.writeString(indexPath, "# Memory Index\n\n" + line);
        }
    }

    /** Async semantic recall: ask LLM which memories are relevant to the user message. */
    public CompletableFuture<List<MemoryEntry>> recallAsync(String userMessage) {
        return CompletableFuture.supplyAsync(() -> recall(userMessage));
    }

    public List<MemoryEntry> recall(String userMessage) {
        if (cache.isEmpty()) return List.of();
        var summaries = new StringBuilder();
        for (int i = 0; i < cache.size(); i++) {
            summaries.append(i).append(": ").append(cache.get(i).summary()).append("\n");
        }

        String systemPrompt = "You are a memory recall system. Given a list of memories and a user message, " +
                "return the indices of relevant memories as a comma-separated list (e.g., '0,2,5'). " +
                "Only return indices that are clearly relevant. If none are relevant, return 'none'.";

        try {
            var prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage("Memories:\n" + summaries + "\nUser message: " + userMessage)
            ));
            var response = chatModel.call(prompt);
            String result = response.getResult().getOutput().getText();
            if (result == null || result.trim().isEmpty() || result.contains("none")) {
                return List.of();
            }
            return Arrays.stream(result.trim().split("[,\\s]+"))
                    .map(String::trim)
                    .filter(s -> s.matches("\\d+"))
                    .mapToInt(Integer::parseInt)
                    .filter(i -> i >= 0 && i < cache.size())
                    .mapToObj(cache::get)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Memory recall failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Format recalled memories for system prompt injection. */
    public String formatForInjection(List<MemoryEntry> memories) {
        if (memories.isEmpty()) return "";
        var sb = new StringBuilder("\n\n## Relevant Memories\n");
        for (var m : memories) {
            sb.append("- [").append(m.type()).append("] ").append(m.name())
                    .append(": ").append(m.description()).append("\n");
        }
        return sb.toString();
    }

    public List<MemoryEntry> getCache() { return cache; }
    public boolean isLoaded() { return loaded; }

    private String sha256Short(String input) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-.]", "_").toLowerCase();
    }
}
