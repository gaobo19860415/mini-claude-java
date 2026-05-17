package com.miniclaudecode.tool.shell;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class RunShellTool implements Tool {

    private static final int MAX_OUTPUT_LINES = 1000;
    private static final long TIMEOUT_SECONDS = 60;

    // 16 dangerous command patterns (mirrors Claude Code)
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("rm\\s+-rf\\s+/"),
            Pattern.compile("rm\\s+-rf\\s+~"),
            Pattern.compile("rm\\s+-rf\\s+\\$HOME"),
            Pattern.compile("rm\\s+-rf\\s+\\."),
            Pattern.compile("rm\\s+-rf\\s+\\*"),
            Pattern.compile("rm\\s+-rf\\s+/\\*"),
            Pattern.compile(">:\\s*/dev/"),
            Pattern.compile("dd\\s+if="),
            Pattern.compile("mkfs\\."),
            Pattern.compile(">\\s*/etc/"),
            Pattern.compile("chmod\\s+777\\s+/"),
            Pattern.compile("git\\s+push\\s+--force.*origin\\s+(main|master)"),
            Pattern.compile("curl.*\\|\\s*(ba)?sh"),
            Pattern.compile("wget.*\\|\\s*(ba)?sh"),
            Pattern.compile("sudo\\s+rm"),
            Pattern.compile("eval\\s+")
    );

    private static final Set<String> WINDOWS_DANGEROUS = Set.of(
            "rmdir /s /q C:", "del /f /s C:", "format C:",
            "rmdir /s /q %HOMEDRIVE%", "del /f /s %HOMEDRIVE%"
    );

    @Override
    public String name() { return "run_shell"; }

    @Override
    public String description() {
        return "Run a shell command and return the output. " +
                "Dangerous commands (rm -rf /, curl | sh, etc.) are blocked.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of("type", "string",
                                "description", "The shell command to run"),
                        "cwd", Map.of("type", "string",
                                "description", "Working directory for the command")
                ),
                "required", List.of("command")
        );
    }

    @Override
    public boolean needsConfirm() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        String command = (String) args.get("command");
        String cwd = (String) args.getOrDefault("cwd", ctx.cwd().toString());

        // Check dangerous commands
        String danger = checkDangerous(command);
        if (danger != null) {
            return ToolResult.error("Dangerous command blocked: " + danger + ". Command: " + command);
        }

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        ProcessBuilder pb;
        if (isWindows) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            pb = new ProcessBuilder("sh", "-c", command);
        }
        pb.directory(ctx.cwd().resolve(cwd).toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        var output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < MAX_OUTPUT_LINES) {
                output.append(line).append("\n");
                count++;
            }
            if (count >= MAX_OUTPUT_LINES) {
                output.append("[... output truncated at " + MAX_OUTPUT_LINES + " lines]");
            }
        }

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolResult.error("Command timed out after " + TIMEOUT_SECONDS + "s: " + command);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            return ToolResult.error("Exit code " + exitCode + ":\n" + output);
        }
        return ToolResult.ok(output.toString());
    }

    private String checkDangerous(String command) {
        for (Pattern p : DANGEROUS_PATTERNS) {
            if (p.matcher(command).find()) {
                return p.pattern();
            }
        }
        String upper = command.toUpperCase();
        for (String d : WINDOWS_DANGEROUS) {
            if (upper.contains(d.toUpperCase())) {
                return d;
            }
        }
        return null;
    }
}
