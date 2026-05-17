package com.miniclaudecode.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the system prompt with @include resolution, .claude/rules/ auto-loading,
 * git context, and template variable substitution.
 */
@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);
    private static final Pattern INCLUDE_REGEX = Pattern.compile("^@(\\.\\/[^\\s]+|~\\/[^\\s]+|\\/[^\\s]+)$", Pattern.MULTILINE);
    private static final int MAX_INCLUDE_DEPTH = 5;

    @Value("${user.home}")
    private String userHome;

    /** Build the full system prompt with all dynamic sections resolved. */
    public String build(String cwd, String model) {
        String template = loadTemplate();
        String date = LocalDate.now().toString();
        String platform = System.getProperty("os.name") + " " + System.getProperty("os.arch");
        String shell = System.getProperty("os.name").toLowerCase().contains("win")
                ? System.getenv().getOrDefault("ComSpec", "cmd.exe")
                : System.getenv().getOrDefault("SHELL", "/bin/sh");
        String gitContext = buildGitContext(cwd);
        String claudeMd = loadClaudeMd(cwd);
        String deferredSection = buildDeferredSection();

        return template
                .replace("{{cwd}}", cwd)
                .replace("{{date}}", date)
                .replace("{{platform}}", platform)
                .replace("{{shell}}", shell)
                .replace("{{git_context}}", gitContext)
                .replace("{{claude_md}}", claudeMd)
                .replace("{{memory}}", "")    // injected at runtime by AgentService
                .replace("{{skills}}", "")    // injected at runtime
                .replace("{{agents}}", "")    // injected at runtime
                .replace("{{deferred_tools}}", deferredSection);
    }

    // ---- Template loading ----

    private String loadTemplate() {
        try {
            var resource = new ClassPathResource("system_prompt.md");
            try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
                var sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            log.warn("Could not load system_prompt.md, using minimal template");
            return "You are a coding assistant. Working directory: {{cwd}}.";
        }
    }

    // ---- @include resolution ----

    String resolveIncludes(String content, String baseDir, Set<String> visited, int depth) {
        if (depth >= MAX_INCLUDE_DEPTH) return content;

        var matcher = INCLUDE_REGEX.matcher(content);
        var sb = new StringBuilder();
        while (matcher.find()) {
            String rawPath = matcher.group(1);
            String resolved = resolvePath(rawPath, baseDir).normalize().toString();
            if (visited.contains(resolved)) {
                matcher.appendReplacement(sb, "<!-- circular: " + rawPath + " -->");
                continue;
            }
            if (!Files.exists(Path.of(resolved))) {
                matcher.appendReplacement(sb, "<!-- not found: " + rawPath + " -->");
                continue;
            }
            try {
                visited.add(resolved);
                String included = Files.readString(Path.of(resolved));
                included = resolveIncludes(included,
                        Path.of(resolved).getParent().toString(), visited, depth + 1);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(included));
            } catch (IOException e) {
                matcher.appendReplacement(sb, "<!-- error reading: " + rawPath + " -->");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Path resolvePath(String rawPath, String baseDir) {
        if (rawPath.startsWith("~/")) {
            return Paths.get(userHome, rawPath.substring(2));
        } else if (rawPath.startsWith("/")) {
            return Paths.get(rawPath);
        } else {
            return Paths.get(baseDir, rawPath);
        }
    }

    // ---- CLAUDE.md loader ----

    String loadClaudeMd(String cwd) {
        var parts = new ArrayList<String>();
        String dir = cwd;
        while (true) {
            Path file = Paths.get(dir, "CLAUDE.md");
            if (Files.exists(file)) {
                try {
                    String content = Files.readString(file);
                    content = resolveIncludes(content, dir, new HashSet<>(), 0);
                    parts.add(0, content);
                } catch (IOException e) {
                    // skip
                }
            }
            Path parent = Paths.get(dir).getParent();
            if (parent == null || parent.toString().equals(dir)) break;
            dir = parent.toString();
        }

        String rules = loadRulesDir(Paths.get(cwd, ".claude", "rules").toString());
        String claudeMd = parts.isEmpty() ? ""
                : "\n\n# Project Instructions (CLAUDE.md)\n" + String.join("\n\n---\n\n", parts);
        return claudeMd + rules;
    }

    // ---- .claude/rules/*.md loader ----

    private String loadRulesDir(String rulesDir) {
        Path dir = Paths.get(rulesDir);
        if (!Files.isDirectory(dir)) return "";
        try (var stream = Files.list(dir)) {
            var files = stream
                    .filter(f -> f.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
            if (files.isEmpty()) return "";
            var parts = new ArrayList<String>();
            for (Path file : files) {
                try {
                    String content = Files.readString(file);
                    content = resolveIncludes(content, rulesDir, new HashSet<>(), 0);
                    parts.add("<!-- rule: " + file.getFileName() + " -->\n" + content);
                } catch (IOException e) {
                    // skip
                }
            }
            return parts.isEmpty() ? "" : "\n\n## Rules\n" + String.join("\n\n", parts);
        } catch (IOException e) {
            return "";
        }
    }

    // ---- Git context ----

    String buildGitContext(String cwd) {
        try {
            var pb = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD");
            pb.directory(Paths.get(cwd).toFile());
            String branch = runCommand(pb).trim();

            pb = new ProcessBuilder("git", "log", "--oneline", "-5");
            pb.directory(Paths.get(cwd).toFile());
            String log = runCommand(pb).trim();

            pb = new ProcessBuilder("git", "status", "--short");
            pb.directory(Paths.get(cwd).toFile());
            String status = runCommand(pb).trim();

            var result = new StringBuilder();
            result.append("\nGit branch: ").append(branch);
            if (!log.isEmpty()) result.append("\nRecent commits:\n").append(log);
            if (!status.isEmpty()) result.append("\nGit status:\n").append(status);
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String runCommand(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        var sb = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return "";
        }
        return sb.toString();
    }

    // ---- Deferred tools ----

    private String buildDeferredSection() {
        // Populated at runtime by AgentService
        return "";
    }

    /** Build a placeholder deferred section with actual tool names. */
    public String buildDeferredSection(List<String> deferredNames) {
        if (deferredNames == null || deferredNames.isEmpty()) return "";
        return "\n\nThe following deferred tools are available via tool_search: " +
                String.join(", ", deferredNames) +
                ". Use tool_search to fetch their full schemas when needed.";
    }
}
