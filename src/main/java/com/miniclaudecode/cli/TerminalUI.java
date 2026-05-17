package com.miniclaudecode.cli;

import org.springframework.stereotype.Component;

/**
 * Terminal output with ANSI color codes, spinner, and formatting.
 */
@Component
public class TerminalUI {

    // ANSI escape codes
    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";
    private static final String DIM = "\033[2m";
    private static final String CYAN = "\033[36m";
    private static final String GREEN = "\033[32m";
    private static final String YELLOW = "\033[33m";
    private static final String RED = "\033[31m";
    private static final String BLUE = "\033[34m";
    private static final String MAGENTA = "\033[35m";

    public void printAssistant(String text) {
        System.out.print(text);
    }

    public void printInfo(String text) {
        System.out.println(DIM + text + RESET);
    }

    public void printSuccess(String text) {
        System.out.println(GREEN + text + RESET);
    }

    public void printWarning(String text) {
        System.out.println(YELLOW + text + RESET);
    }

    public void printError(String text) {
        System.out.println(RED + text + RESET);
    }

    public void printToolCall(String toolName, String args) {
        System.out.println(CYAN + BOLD + "  → " + toolName + RESET + DIM +
                " " + truncate(args, 80) + RESET);
    }

    public void printToolResult(String summary) {
        System.out.println(DIM + "  ← " + truncate(summary, 120) + RESET);
    }

    public void printConfirmation(String message) {
        System.out.println(YELLOW + "  ? " + message + RESET);
    }

    public void printDivider() {
        System.out.println(DIM + "─".repeat(60) + RESET);
    }

    public void printCost(double costUsd, int inputTokens, int outputTokens) {
        System.out.println(DIM + String.format(
                "  Cost: $%.4f | Tokens: %d in / %d out", costUsd, inputTokens, outputTokens) + RESET);
    }

    public void printRetry(int attempt, int maxRetries, String reason) {
        System.out.println(YELLOW + String.format(
                "  Retry %d/%d (%s)...", attempt, maxRetries, reason) + RESET);
    }

    public void printWelcome(String model) {
        System.out.println(BOLD + BLUE + "Mini Claude Code" + RESET + DIM +
                " (Java) — " + model + RESET);
        System.out.println(DIM + "Type a message or /help for commands." + RESET);
    }

    public void printGoodbye() {
        System.out.println(DIM + "Goodbye!" + RESET);
    }

    public void printSubAgentStart(String type) {
        System.out.println(MAGENTA + "  [sub-agent: " + type + "] starting..." + RESET);
    }

    public void printSubAgentEnd(String type) {
        System.out.println(MAGENTA + "  [sub-agent: " + type + "] finished." + RESET);
    }

    // ---- Spinner (simplified) ----

    private boolean spinning = false;
    private Thread spinnerThread;

    public void startSpinner(String message) {
        spinning = true;
        spinnerThread = new Thread(() -> {
            String[] frames = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
            int i = 0;
            while (spinning) {
                System.out.print("\r" + CYAN + frames[i % frames.length] + RESET + " " + message);
                i++;
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
            System.out.print("\r" + " ".repeat(message.length() + 3) + "\r");
        });
        spinnerThread.setDaemon(true);
        spinnerThread.start();
    }

    public void stopSpinner() {
        spinning = false;
        if (spinnerThread != null) {
            spinnerThread.interrupt();
        }
    }

    // ---- Utility ----

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        String oneLine = text.replace("\n", "\\n");
        return oneLine.length() > maxLen ? oneLine.substring(0, maxLen) + "..." : oneLine;
    }
}
