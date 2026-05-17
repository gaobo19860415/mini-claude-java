package com.miniclaudecode.subagent;

import com.miniclaudecode.agent.AgentOptions;
import com.miniclaudecode.agent.AgentService;
import com.miniclaudecode.agent.AgentContext;
import com.miniclaudecode.tool.PermissionMode;
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

// Fork-return sub-agent service. 3 built-in types + custom from .claude/agents/&#42;.md.
@Service
public class SubAgentService {

    private static final Logger log = LoggerFactory.getLogger(SubAgentService.class);
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\n(.*?)\\n---\\s*\\n(.*)", Pattern.DOTALL);
    private static final Pattern YAML_KEY = Pattern.compile("^(\\w+):\\s*(.*)$");

    private final AgentService agentService;
    private final Map<String, SubAgentConfig> customAgents = new LinkedHashMap<>();

    public SubAgentService(AgentService agentService) {
        this.agentService = agentService;
    }

    // Discover custom agents from .claude/agents/&#42;.md.
    public void discover(String cwd) {
        customAgents.clear();
        Path agentsDir = Paths.get(cwd, ".claude", "agents");
        if (!Files.isDirectory(agentsDir)) return;
        try (var files = Files.list(agentsDir)) {
            for (Path file : files.toList()) {
                if (!file.getFileName().toString().endsWith(".md")) continue;
                try {
                    String content = Files.readString(file);
                    Matcher m = FRONTMATTER_PATTERN.matcher(content);
                    if (!m.find()) continue;
                    String fm = m.group(1);
                    String body = m.group(2).trim();

                    var fields = new HashMap<String, String>();
                    for (String line : fm.split("\n")) {
                        Matcher ym = YAML_KEY.matcher(line);
                        if (ym.find()) fields.put(ym.group(1).trim(), ym.group(2).trim());
                    }

                    String name = file.getFileName().toString().replace(".md", "");
                    String desc = fields.getOrDefault("description", "");
                    List<String> tools = parseList(fields.get("allowedTools"));

                    customAgents.put(name, new SubAgentConfig(name, desc, body, tools));
                    log.debug("Loaded custom agent: {}", name);
                } catch (IOException e) {
                    log.warn("Failed to read agent: {}", file);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to scan agents dir: {}", agentsDir);
        }
        log.info("Discovered {} custom agents", customAgents.size());
    }

    /** Get config by type (built-in or custom). */
    public SubAgentConfig getConfig(String type) {
        SubAgentConfig builtIn = SubAgentConfig.builtIn(type);
        if (builtIn != null) return builtIn;
        return customAgents.get(type);
    }

    /** Run a sub-agent and return text output. */
    public AgentService.AgentResult run(String type, String prompt, AgentContext parentCtx) {
        SubAgentConfig config = getConfig(type);
        if (config == null) {
            return new AgentService.AgentResult(
                    "Unknown sub-agent type: " + type, 0, 0, 0);
        }

        var subOptions = new AgentOptions(
                parentCtx.permissionMode(), parentCtx.model(),
                false, null, null, true,
                config.systemPrompt()
        );

        var subCtx = agentService.createContext(subOptions,
                System.getProperty("user.dir"));

        log.info("Spawning sub-agent '{}' (type={})", config.type(), type);
        AgentService.AgentResult result = agentService.chat(prompt, subCtx);
        log.info("Sub-agent '{}' finished: {} turns, {} tokens",
                type, result.turns(), result.inputTokens() + result.outputTokens());

        return result;
    }

    /** Build agent descriptions for system prompt injection. */
    public String buildAgentDescriptions() {
        var sb = new StringBuilder("\n\n## Available Sub-Agent Types\n");
        for (String type : List.of("explore", "plan", "general")) {
            SubAgentConfig c = SubAgentConfig.builtIn(type);
            sb.append("- **").append(c.type()).append("**: ").append(c.description()).append("\n");
        }
        for (var c : customAgents.values()) {
            sb.append("- **").append(c.type()).append("**: ").append(c.description()).append("\n");
        }
        return sb.toString();
    }

    private List<String> parseList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.replaceAll("[\\[\\]]", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
