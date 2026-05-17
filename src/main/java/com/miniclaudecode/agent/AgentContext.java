package com.miniclaudecode.agent;

import com.miniclaudecode.tool.MtimeTracker;
import com.miniclaudecode.tool.PermissionMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-session state container — message history, token tracking, budget state.
 * NOT a Spring Bean — created per conversation.
 */
public class AgentContext {

    private final String sessionId;
    private final String model;
    private final boolean isSubAgent;
    private PermissionMode permissionMode;
    private PermissionMode prePlanMode;

    // Message history (using Spring AI Message types or raw maps)
    private final List<Object> messages = new ArrayList<>();

    // Token tracking
    private int totalInputTokens = 0;
    private int totalOutputTokens = 0;
    private int lastInputTokenCount = 0;

    // Budget
    private final double maxCostUsd;
    private final int maxTurns;
    private int currentTurns = 0;

    // Compression state
    private long lastApiCallTime = 0;

    // Write conflict detection
    private final MtimeTracker mtimeTracker = new MtimeTracker();

    // Confirmed paths in this session
    private final Set<String> confirmedPaths = new HashSet<>();

    // System prompt
    private String systemPrompt;

    // Sub-agent output buffer
    private final StringBuilder outputBuffer = new StringBuilder();

    // Plan mode state
    private String planFilePath;
    private boolean contextCleared = false;

    public AgentContext(String sessionId, AgentOptions options, String systemPrompt) {
        this.sessionId = sessionId;
        this.model = options.model();
        this.isSubAgent = options.isSubAgent();
        this.permissionMode = options.permissionMode();
        this.maxCostUsd = options.maxCostUsd() != null ? options.maxCostUsd() : 0;
        this.maxTurns = options.maxTurns() != null ? options.maxTurns() : 0;
        this.systemPrompt = systemPrompt;
    }

    // ---- Getters / Setters ----

    public String sessionId() { return sessionId; }
    public String model() { return model; }
    public boolean isSubAgent() { return isSubAgent; }
    public PermissionMode permissionMode() { return permissionMode; }
    public void setPermissionMode(PermissionMode mode) { this.permissionMode = mode; }
    public PermissionMode prePlanMode() { return prePlanMode; }
    public void setPrePlanMode(PermissionMode mode) { this.prePlanMode = mode; }
    public List<Object> messages() { return messages; }
    public int totalInputTokens() { return totalInputTokens; }
    public void addInputTokens(int n) { this.totalInputTokens += n; }
    public int totalOutputTokens() { return totalOutputTokens; }
    public void addOutputTokens(int n) { this.totalOutputTokens += n; }
    public int lastInputTokenCount() { return lastInputTokenCount; }
    public void setLastInputTokenCount(int n) { this.lastInputTokenCount = n; }
    public int currentTurns() { return currentTurns; }
    public void incrementTurns() { this.currentTurns++; }
    public long lastApiCallTime() { return lastApiCallTime; }
    public void setLastApiCallTime(long t) { this.lastApiCallTime = t; }
    public MtimeTracker mtimeTracker() { return mtimeTracker; }
    public Set<String> confirmedPaths() { return confirmedPaths; }
    public String systemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String sp) { this.systemPrompt = sp; }
    public StringBuilder outputBuffer() { return outputBuffer; }
    public String planFilePath() { return planFilePath; }
    public void setPlanFilePath(String p) { this.planFilePath = p; }
    public boolean contextCleared() { return contextCleared; }
    public void setContextCleared(boolean c) { this.contextCleared = c; }

    // ---- Budget ----

    public double getCurrentCostUsd() {
        return (totalInputTokens / 1_000_000.0) * 3.0 +
                (totalOutputTokens / 1_000_000.0) * 15.0;
    }

    public boolean isBudgetExceeded() {
        if (maxCostUsd > 0 && getCurrentCostUsd() >= maxCostUsd) return true;
        if (maxTurns > 0 && currentTurns >= maxTurns) return true;
        return false;
    }

    public String budgetExceededReason() {
        if (maxCostUsd > 0 && getCurrentCostUsd() >= maxCostUsd) {
            return String.format("Cost limit reached ($%.4f >= $%.2f)", getCurrentCostUsd(), maxCostUsd);
        }
        if (maxTurns > 0 && currentTurns >= maxTurns) {
            return String.format("Turn limit reached (%d >= %d)", currentTurns, maxTurns);
        }
        return null;
    }
}
