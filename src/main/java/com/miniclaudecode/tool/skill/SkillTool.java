package com.miniclaudecode.tool.skill;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Skill execution tool — injects skill prompts or forks sub-agents.
 * Actual skill resolution is handled by AgentService / SkillService.
 */
@Component
public class SkillTool implements Tool {

    @Override
    public String name() { return "skill"; }

    @Override
    public String description() {
        return "Execute a skill. Skills provide specialized capabilities and domain knowledge. " +
                "Skills are discovered from .claude/skills/*/SKILL.md.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "skill", Map.of("type", "string",
                                "description", "The skill name to execute"),
                        "args", Map.of("type", "string",
                                "description", "Optional arguments for the skill")
                ),
                "required", List.of("skill")
        );
    }

    @Override
    public boolean isDeferred() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) {
        String skillName = (String) args.get("skill");
        String skillArgs = (String) args.getOrDefault("args", "");
        return ToolResult.ok("Skill '" + skillName + "' execution — handled by SkillService. " +
                "Args: " + skillArgs);
    }
}
