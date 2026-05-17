package com.miniclaudecode.tool.file;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class WriteFileTool implements Tool {

    private final MtimeTracker mtimeTracker = new MtimeTracker();

    @Override
    public String name() { return "write_file"; }

    @Override
    public String description() {
        return "Write content to a file. Creates the file if it doesn't exist, overwrites if it does.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file_path", Map.of("type", "string",
                                "description", "The path to the file to write"),
                        "content", Map.of("type", "string",
                                "description", "The content to write to the file")
                ),
                "required", List.of("file_path", "content")
        );
    }

    @Override
    public boolean needsConfirm() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        String filePath = (String) args.get("file_path");
        String content = (String) args.get("content");
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = ctx.cwd().resolve(filePath);
        }

        // Check for external modifications
        if (Files.exists(path)) {
            String conflict = new MtimeTracker().checkConflict(path);
            if (conflict != null) {
                return ToolResult.error(conflict);
            }
        }

        // Ensure parent directories exist
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, content);
        long mtime = Files.getLastModifiedTime(path).toMillis();
        mtimeTracker.recordRead(path);
        return ToolResult.ok("File written successfully: " + path, mtime);
    }
}
