package com.miniclaudecode.tool.file;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class ReadFileTool implements Tool {

    @Override
    public String name() { return "read_file"; }

    @Override
    public String description() {
        return "Read the contents of a file. Returns the file content with line numbers.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file_path", Map.of("type", "string",
                                "description", "The path to the file to read")
                ),
                "required", List.of("file_path")
        );
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public boolean isConcurrencySafe() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        String filePath = (String) args.get("file_path");
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = ctx.cwd().resolve(filePath);
        }
        if (!Files.exists(path)) {
            return ToolResult.error("File not found: " + path);
        }
        if (!Files.isReadable(path)) {
            return ToolResult.error("File not readable: " + path);
        }
        List<String> lines = Files.readAllLines(path);
        var sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(String.format("%6d\t%s%n", i + 1, lines.get(i)));
        }
        return ToolResult.ok(sb.toString());
    }
}
