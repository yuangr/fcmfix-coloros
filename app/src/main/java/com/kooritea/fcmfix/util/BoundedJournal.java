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
    /** Read only, bounded report appendix. Called as the journal owner on a background thread. */
    public static String exportTail(File directory, int byteLimitPerFile) {
        if (byteLimitPerFile <= 0 || byteLimitPerFile > 65536) throw new IllegalArgumentException("export budget");
        StringBuilder text = new StringBuilder();
        for (String name : new String[]{"night-previous.log", "night-current.log"}) {
            File file = new File(directory, name);
            if (!file.isFile()) continue;
            text.append("\n== ").append(name).append(" （每文件仅末尾最多 ")
                    .append(byteLimitPerFile).append(" 字节；不是完整夜间日志） ==\n");
            try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(file, "r")) {
                long length = in.length();
                long start = Math.max(0, length - byteLimitPerFile);
                in.seek(start);
                byte[] bytes = new byte[(int) Math.min(length, byteLimitPerFile)];
                in.readFully(bytes);
                if (start > 0) text.append("[前部已截断；首行可能不完整]\n");
                text.append(new String(bytes, StandardCharsets.UTF_8)).append('\n');
            } catch (IOException e) {
                text.append("读取失败：").append(e.getClass().getSimpleName()).append('\n');
            }
        }
        return text.length() == 0 ? "尚无夜间日志\n" : text.toString();
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
