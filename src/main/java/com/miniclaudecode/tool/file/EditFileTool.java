package com.miniclaudecode.tool.file;

import com.miniclaudecode.tool.*;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Component
public class EditFileTool implements Tool {

    @Override
    public String name() { return "edit_file"; }

    @Override
    public String description() {
        return "Edit a file by replacing an exact string match with new content. " +
                "The old_string must match exactly (including whitespace and indentation).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file_path", Map.of("type", "string",
                                "description", "The path to the file to edit"),
                        "old_string", Map.of("type", "string",
                                "description", "The exact string to find and replace"),
                        "new_string", Map.of("type", "string",
                                "description", "The string to replace it with")
                ),
                "required", List.of("file_path", "old_string", "new_string")
        );
    }

    @Override
    public boolean needsConfirm() { return true; }

    @Override
    public ToolResult execute(Map<String, Object> args, ToolContext ctx) throws Exception {
        String filePath = (String) args.get("file_path");
        String oldStr = (String) args.get("old_string");
        String newStr = (String) args.get("new_string");
        Path path = Paths.get(filePath);
        if (!path.isAbsolute()) {
            path = ctx.cwd().resolve(filePath);
        }

        if (!Files.exists(path)) {
            return ToolResult.error("File not found: " + path);
        }

        // Check for external modifications
        MtimeTracker mtimeTracker = new MtimeTracker();
        String conflict = mtimeTracker.checkConflict(path);
        if (conflict != null) {
            return ToolResult.error(conflict);
        }

        String content = Files.readString(path);

        // Uniqueness check
        int firstIdx = content.indexOf(oldStr);
        if (firstIdx == -1) {
            return ToolResult.error("old_string not found in file: " + path);
        }
        int secondIdx = content.indexOf(oldStr, firstIdx + 1);
        if (secondIdx != -1) {
            return ToolResult.error("old_string is not unique — found at positions " +
                    firstIdx + " and " + secondIdx + ". Provide more context to make it unique.");
        }

        String result = content.replace(oldStr, newStr);
        Files.writeString(path, result);

        long mtime = Files.getLastModifiedTime(path).toMillis();
        return ToolResult.ok("Edit applied successfully to " + path, mtime);
    }
}
