package com.miniclaudecode.tool.search;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class GrepSearchTool implements Tool {

    @Override
    public String name() { return "grep_search"; }

    @Override
    public String description() {
        return "Search for a pattern in files using ripgrep or grep. " +
                "Returns matching lines with file paths and line numbers.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of("type", "string",
                                "description", "The regex pattern to search for"),
                        "path", Map.of("type", "string",
                                "description", "Directory or file to search in. Defaults to current directory.")
                ),
                "required", List.of("pattern")
        );
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public boolean isConcurrencySafe() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        String pattern = (String) args.get("pattern");
        String searchPath = (String) args.getOrDefault("path", ctx.cwd().toString());
        Path base = Paths.get(searchPath);
        if (!base.isAbsolute()) {
            base = ctx.cwd().resolve(searchPath);
        }

        List<String> cmd = new ArrayList<>();
        // Try rg first, fall back to grep
        if (isCommandAvailable("rg")) {
            cmd.add("rg");
            cmd.add("--line-number");
            cmd.add("--no-heading");
            cmd.add("--color=never");
            cmd.add(pattern);
            cmd.add(base.toString());
        } else {
            cmd.add("grep");
            cmd.add("-rn");
            cmd.add("--color=never");
            cmd.add(pattern);
            cmd.add(base.toString());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        var output = new StringBuilder();
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < 500) {
                output.append(line).append("\n");
                count++;
            }
            if (count >= 500) {
                output.append("[... output truncated at 500 lines]");
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return ToolResult.error("Search timed out after 30 seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode == 1) {
            return ToolResult.ok("No matches found for: " + pattern);
        }
        if (exitCode != 0) {
            return ToolResult.error("Search failed with exit code " + exitCode + ":\n" + output);
        }

        return ToolResult.ok(output.toString());
    }

    private boolean isCommandAvailable(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    System.getProperty("os.name").toLowerCase().contains("win") ? "where" : "which", cmd);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
