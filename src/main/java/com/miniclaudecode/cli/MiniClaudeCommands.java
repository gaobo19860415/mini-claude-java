package com.miniclaudecode.cli;

import com.miniclaudecode.agent.AgentContext;
import com.miniclaudecode.agent.AgentOptions;
import com.miniclaudecode.agent.AgentService;
import com.miniclaudecode.agent.AgentService.AgentResult;
import com.miniclaudecode.mcp.McpManager;
import com.miniclaudecode.memory.MemoryService;
import com.miniclaudecode.skill.SkillService;
import com.miniclaudecode.subagent.SubAgentService;
import com.miniclaudecode.tool.PermissionMode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * Spring Shell REPL commands + ApplicationRunner for CLI flag parsing.
 */
@ShellComponent
public class MiniClaudeCommands implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MiniClaudeCommands.class);

    private final AgentService agentService;
    private final MemoryService memoryService;
    private final SkillService skillService;
    private final SubAgentService subAgentService;
    private final McpManager mcpManager;
    private final TerminalUI ui;

    private AgentContext ctx;
    private PermissionMode permissionMode = PermissionMode.DEFAULT;
    private String model = System.getenv().getOrDefault("MINI_CLAUDE_MODEL", "claude-opus-4-6");
    private boolean thinking = false;
    private Double maxCostUsd;
    private Integer maxTurns;
    private volatile boolean initialized = false;

    public MiniClaudeCommands(AgentService agentService, MemoryService memoryService,
                              SkillService skillService, SubAgentService subAgentService,
                              McpManager mcpManager, TerminalUI ui) {
        this.agentService = agentService;
        this.memoryService = memoryService;
        this.skillService = skillService;
        this.subAgentService = subAgentService;
        this.mcpManager = mcpManager;
        this.ui = ui;
    }

    // ---- Eager init: ensures context is ready before shell accepts input ----

    @PostConstruct
    void init() {
        doInit();
    }

    // ---- CLI flag overrides (runs after @PostConstruct) ----

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("yolo")) permissionMode = PermissionMode.BYPASS_PERMISSIONS;
        if (args.containsOption("plan")) permissionMode = PermissionMode.PLAN;
        if (args.containsOption("accept-edits")) permissionMode = PermissionMode.ACCEPT_EDITS;
        if (args.containsOption("dont-ask")) permissionMode = PermissionMode.DONT_ASK;
        if (args.containsOption("thinking")) thinking = true;
        if (args.containsOption("model")) model = args.getOptionValues("model").get(0);
        if (args.containsOption("max-cost"))
            maxCostUsd = Double.parseDouble(args.getOptionValues("max-cost").get(0));
        if (args.containsOption("max-turns"))
            maxTurns = Integer.parseInt(args.getOptionValues("max-turns").get(0));

        // Re-init with CLI overrides
        doInit();
    }

    private void doInit() {
        String cwd = System.getProperty("user.dir");
        var options = new AgentOptions(permissionMode, model, thinking,
                maxCostUsd, maxTurns, false, null);
        this.ctx = agentService.createContext(options, cwd);

        memoryService.init(cwd);
        skillService.discover(cwd);
        subAgentService.discover(cwd);
        mcpManager.loadAndConnect();

        this.initialized = true;
        ui.printWelcome(model);
    }

    // ---- Commands ----

    @ShellMethod(key = {"", "chat"}, value = "Send a message to the agent")
    public String chat(@ShellOption(defaultValue = "") String message) {
        if (message.isBlank()) {
            return "Type a message to chat with the agent. Usage: chat <your message>";
        }

        if (message.startsWith("/")) {
            return handleSlashCommand(message);
        }

        // Memory recall
        try {
            var memories = memoryService.recallAsync(message).get();
            if (!memories.isEmpty()) {
                String memText = memoryService.formatForInjection(memories);
                ctx.setSystemPrompt(ctx.systemPrompt() + memText);
            }
        } catch (Exception e) {
            log.debug("Memory recall skipped: {}", e.getMessage());
        }

        AgentResult result = agentService.chat(message, ctx);
        if (result.text() != null && !result.text().isBlank()) {
            ui.printAssistant(result.text());
            System.out.println();
        }
        ui.printCost(ctx.getCurrentCostUsd(), result.inputTokens(), result.outputTokens());
        return "";
    }

    private String handleSlashCommand(String input) {
        String[] parts = input.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        return switch (cmd) {
            case "/help" -> """
                    Available commands:
                      /help          - Show this help
                      /clear         - Clear conversation history
                      /cost          - Show token usage and cost
                      /compact       - Compact conversation context
                      /memory        - Show loaded memories
                      /skills        - List available skills
                      /plan          - Toggle plan mode
                      /exit, /quit   - Exit the application
                    """;
            case "/clear" -> { agentService.clearHistory(ctx); yield "Conversation cleared."; }
            case "/cost" -> String.format("Tokens: %d in / %d out | Cost: $%.4f | Turns: %d",
                    ctx.totalInputTokens(), ctx.totalOutputTokens(),
                    ctx.getCurrentCostUsd(), ctx.currentTurns());
            case "/compact" -> { agentService.clearHistory(ctx); yield "Conversation compacted."; }
            case "/memory" -> formatMemories();
            case "/skills" -> formatSkills();
            case "/plan" -> { agentService.togglePlanMode(ctx); yield "Plan mode: " + ctx.permissionMode(); }
            case "/exit", "/quit" -> { System.exit(0); yield ""; }
            default -> "Unknown command: " + cmd + ". Type /help for available commands.";
        };
    }

    private String formatMemories() {
        var memories = memoryService.getCache();
        if (memories.isEmpty()) return "No memories loaded.";
        var sb = new StringBuilder("Memories (" + memories.size() + "):\n");
        for (var m : memories)
            sb.append("  - [").append(m.type()).append("] ").append(m.name()).append("\n");
        return sb.toString().trim();
    }

    private String formatSkills() {
        var skills = skillService.all();
        if (skills.isEmpty()) return "No skills discovered.";
        var sb = new StringBuilder("Skills (" + skills.size() + "):\n");
        for (var s : skills.values())
            sb.append("  - ").append(s.name()).append(": ").append(s.description()).append("\n");
        return sb.toString().trim();
    }

    // ---- Availability ----

    public Availability chatAvailability() {
        return initialized && ctx != null ? Availability.available()
                : Availability.unavailable("Agent not initialized");
    }
}
