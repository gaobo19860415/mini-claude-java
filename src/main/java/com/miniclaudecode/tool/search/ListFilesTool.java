package com.miniclaudecode.tool.search;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

@Component
public class ListFilesTool implements Tool {

    @Override
    public String name() { return "list_files"; }

    @Override
    public String description() {
        return "List files matching a glob pattern. Returns relative file paths.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "pattern", Map.of("type", "string",
                                "description", "Glob pattern to match (e.g., '**/*.java')"),
                        "path", Map.of("type", "string",
                                "description", "Directory to search in. Defaults to current working directory.")
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
        String basePath = (String) args.getOrDefault("path", ctx.cwd().toString());
        Path base = Paths.get(basePath);
        if (!base.isAbsolute()) {
            base = ctx.cwd().resolve(basePath);
        }

        List<String> matches = new ArrayList<>();
        Path finalBase = base;
        String finalPattern = pattern;
        Files.walkFileTree(finalBase, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = finalBase.relativize(file);
                PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + finalPattern);
                if (m.matches(relative) || m.matches(file.getFileName())) {
                    matches.add(relative.toString());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        if (matches.isEmpty()) {
            return ToolResult.ok("No files matched pattern: " + pattern);
        }
        return ToolResult.ok(String.join("\n", matches));
    }
}
