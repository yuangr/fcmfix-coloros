package com.kooritea.fcmfix.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Two bounded files. Call on a worker, never from system_server's hook or main thread. */
public final class BoundedJournal {
    private final File current, previous;
    private final int limit;
    public BoundedJournal(File directory, int limit) throws IOException {
        if (limit < 4096) throw new IllegalArgumentException("journal limit too small");
        this.limit = limit;
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("cannot create journal directory");
        directory.setReadable(false, false); directory.setReadable(true, true);
        directory.setWritable(false, false); directory.setWritable(true, true);
        directory.setExecutable(false, false); directory.setExecutable(true, true);
        current = new File(directory, "night-current.log");
        previous = new File(directory, "night-previous.log");
    }
    public synchronized void append(String text) throws IOException {
        String line = String.valueOf(text).replace("\r", "\\r").replace("\n", "\\n");
        if (line.length() > 1024) line = line.substring(0, 1024) + "…[truncated]";
        byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
        if (current.isFile() && current.length() + bytes.length > limit) {
            Files.move(current.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (FileOutputStream out = new FileOutputStream(current, true)) { out.write(bytes); }
        current.setReadable(false, false); current.setReadable(true, true);
        current.setWritable(false, false); current.setWritable(true, true);
    }
}
