package com.miniclaudecode.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MtimeTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRecordReadAndDetectNoConflict() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        var tracker = new MtimeTracker();
        tracker.recordRead(file);
        assertThat(tracker.checkConflict(file)).isNull();
    }

    @Test
    void shouldDetectModificationAfterRead() throws IOException, InterruptedException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        var tracker = new MtimeTracker();
        tracker.recordRead(file);

        // Sleep to ensure mtime changes (some FS have 1s resolution)
        Thread.sleep(1001);
        Files.writeString(file, "modified");

        String conflict = tracker.checkConflict(file);
        assertThat(conflict).isNotNull()
                .contains("modified since last read");
    }

    @Test
    void shouldDetectDeletionAfterRead() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");

        var tracker = new MtimeTracker();
        tracker.recordRead(file);
        Files.delete(file);

        String conflict = tracker.checkConflict(file);
        assertThat(conflict).isNotNull()
                .contains("deleted since last read");
    }

    @Test
    void noConflictWhenNeverRead() {
        var tracker = new MtimeTracker();
        assertThat(tracker.checkConflict(tempDir.resolve("unknown.txt"))).isNull();
    }

    @Test
    void shouldTrackMultipleFiles() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "a");
        Files.writeString(file2, "b");

        var tracker = new MtimeTracker();
        tracker.recordRead(file1);
        tracker.recordRead(file2);

        assertThat(tracker.getState()).hasSize(2);
    }
}
