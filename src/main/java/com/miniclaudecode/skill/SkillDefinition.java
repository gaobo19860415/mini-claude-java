package com.miniclaudecode.skill;

import java.util.List;

/**
 * Parsed skill definition from SKILL.md YAML frontmatter.
 */
public record SkillDefinition(
        String name,
        String description,
        String context,       // "inline" or "fork"
        boolean userInvocable,
        List<String> allowedTools,
        String prompt         // Body of the SKILL.md
) {
    public boolean isInline() { return "inline".equals(context); }
    public boolean isFork() { return "fork".equals(context); }
}
