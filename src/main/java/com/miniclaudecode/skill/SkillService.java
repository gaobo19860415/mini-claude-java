package com.miniclaudecode.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// Scans .claude/skills/&#42;/SKILL.md and ~/.claude/skills/&#42;/SKILL.md.
// Supports inline (prompt injection) and fork (sub-agent) execution modes.
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);
    private static final Pattern YAML_KEY = Pattern.compile("^(\\w+):\\s*(.*)$");

    private final Map<String, SkillDefinition> skills = new LinkedHashMap<>();
    private boolean loaded = false;

    public void discover(String cwd) {
        skills.clear();
        // Project-level skills
        scanDir(Paths.get(cwd, ".claude", "skills"));
        // User-level skills
        scanDir(Paths.get(System.getProperty("user.home"), ".claude", "skills"));
        loaded = true;
        log.info("Discovered {} skills", skills.size());
    }

    private void scanDir(Path skillsDir) {
        if (!Files.isDirectory(skillsDir)) return;
        try (Stream<Path> dirs = Files.list(skillsDir)) {
            for (Path dir : dirs.toList()) {
                if (!Files.isDirectory(dir)) continue;
                Path skillFile = dir.resolve("SKILL.md");
                if (!Files.exists(skillFile)) continue;
                try {
                    String content = Files.readString(skillFile);
                    SkillDefinition skill = parseSkill(content);
                    if (skill != null) {
                        skills.put(skill.name(), skill);
                        log.debug("Loaded skill: {}", skill.name());
                    }
                } catch (IOException e) {
                    log.warn("Failed to read skill: {}", skillFile);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan skills dir: {}", skillsDir);
        }
    }

    private SkillDefinition parseSkill(String content) {
        Matcher m = FRONTMATTER_PATTERN.matcher(content);
        if (!m.find()) return null;
        String fm = m.group(1);
        String body = m.group(2).trim();

        var fields = new HashMap<String, String>();
        for (String line : fm.split("\n")) {
            Matcher ym = YAML_KEY.matcher(line);
            if (ym.find()) {
                String key = ym.group(1).trim();
                String val = ym.group(2).trim();
                fields.put(key, val);
            }
        }

        String name = fields.getOrDefault("name", "");
        String desc = fields.getOrDefault("description", "");
        String context = fields.getOrDefault("context", "inline");
        boolean userInvocable = "true".equalsIgnoreCase(fields.getOrDefault("userInvocable", "true"));
        List<String> allowedTools = parseList(fields.get("allowedTools"));

        return new SkillDefinition(name, desc, context, userInvocable, allowedTools, body);
    }

    private List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.replaceAll("[\\[\\]]", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public SkillDefinition findByName(String name) {
        return skills.get(name);
    }

    public Map<String, SkillDefinition> all() { return skills; }

    /** Build skill descriptions for system prompt injection. */
    public String buildDescriptions() {
        if (skills.isEmpty()) return "";
        var sb = new StringBuilder("\n\n## Available Skills\n");
        for (var skill : skills.values()) {
            sb.append("- **").append(skill.name()).append("**: ")
                    .append(skill.description())
                    .append(" (mode: ").append(skill.context()).append(")\n");
        }
        return sb.toString();
    }

    public boolean isLoaded() { return loaded; }
}
