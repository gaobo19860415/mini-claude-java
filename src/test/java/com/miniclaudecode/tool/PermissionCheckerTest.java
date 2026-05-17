package com.miniclaudecode.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static com.miniclaudecode.tool.PermissionChecker.Decision.*;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionCheckerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure clean settings for each test
        Path claudeDir = tempDir.resolve(".claude");
        Files.createDirectories(claudeDir);
    }

    @Test
    void bypassModeShouldAllowEverything() {
        var checker = new PermissionChecker(PermissionMode.BYPASS_PERMISSIONS);
        assertThat(checker.check("write_file", Map.of("file_path", "/tmp/test"), Set.of())).isEqualTo(ALLOW);
        assertThat(checker.check("run_shell", Map.of("command", "rm -rf /"), Set.of())).isEqualTo(ALLOW);
    }

    @Test
    void planModeShouldAllowReadsOnly() {
        var checker = new PermissionChecker(PermissionMode.PLAN);
        assertThat(checker.check("read_file", Map.of("file_path", "/tmp/test"), Set.of())).isEqualTo(ALLOW);
        assertThat(checker.check("list_files", Map.of(), Set.of())).isEqualTo(ALLOW);
        assertThat(checker.check("grep_search", Map.of(), Set.of())).isEqualTo(ALLOW);
        assertThat(checker.check("write_file", Map.of("file_path", "/tmp/test"), Set.of())).isEqualTo(DENY);
        assertThat(checker.check("edit_file", Map.of("file_path", "/tmp/test"), Set.of())).isEqualTo(DENY);
        assertThat(checker.check("run_shell", Map.of("command", "ls"), Set.of())).isEqualTo(DENY);
    }

    @Test
    void dontAskModeShouldAllowReadsDenyWrites() {
        var checker = new PermissionChecker(PermissionMode.DONT_ASK);
        assertThat(checker.check("read_file", Map.of(), Set.of())).isEqualTo(ALLOW);
        assertThat(checker.check("write_file", Map.of(), Set.of())).isEqualTo(DENY);
    }

    @Test
    void acceptEditsShouldAutoApproveEditTools() {
        var checker = new PermissionChecker(PermissionMode.ACCEPT_EDITS);
        assertThat(checker.check("write_file", Map.of("file_path", "/tmp/test"), Set.of())).isEqualTo(ALLOW);
        assertThat(checker.check("edit_file", Map.of("file_path", "/tmp/test"), Set.of())).isEqualTo(ALLOW);
    }

    @Test
    void defaultModeShouldAskForWrites() {
        var checker = new PermissionChecker(PermissionMode.DEFAULT);
        assertThat(checker.check("read_file", Map.of(), Set.of())).isEqualTo(ALLOW);
        // run_shell needs confirmation
        assertThat(checker.check("run_shell", Map.of("command", "ls"), Set.of())).isEqualTo(ASK);
    }

    @Test
    void confirmedPathShouldBeAllowed() {
        var checker = new PermissionChecker(PermissionMode.DEFAULT);
        String path = "/tmp/test.txt";
        assertThat(checker.check("write_file", Map.of("file_path", path), Set.of(path))).isEqualTo(ALLOW);
    }
}
