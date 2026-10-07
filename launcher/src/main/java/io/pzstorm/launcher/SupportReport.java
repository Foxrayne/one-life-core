package io.pzstorm.launcher;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.zip.*;

/** Bounded local export only. No network requests, configuration or credential files. */
public final class SupportReport {
    private static final int MAX_BYTES = 1024 * 1024;

    private SupportReport() {}

    public static Path export(Path directory, String description, List<Path> logs)
            throws IOException {
        if (description == null || description.trim().length() < 11)
            throw new IllegalArgumentException("Describe what happened in at least 11 characters.");
        if (description.length() > 16000)
            throw new IllegalArgumentException("Description is too long.");
        Files.createDirectories(directory);
        Path file = Files.createTempFile(directory, "OneLife-support-", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            write(
                    zip,
                    "description.txt",
                    LogScrubber.scrub(description).getBytes(StandardCharsets.UTF_8));
            int index = 0;
            for (Path log : logs.stream().limit(8).toList()) {
                if (!Files.isRegularFile(log, LinkOption.NOFOLLOW_LINKS)) continue;
                byte[] data;
                try (FileChannel channel = FileChannel.open(log, StandardOpenOption.READ)) {
                    long size = channel.size();
                    channel.position(Math.max(0, size - MAX_BYTES));
                    ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(size, MAX_BYTES));
                    while (buffer.hasRemaining() && channel.read(buffer) > 0) {}
                    data = new byte[buffer.position()];
                    buffer.flip();
                    buffer.get(data);
                } catch (IOException ex) {
                    continue;
                }
                write(zip, (++index) + "-" + log.getFileName(), LogScrubber.scrub(data));
            }
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(file);
            throw ex;
        }
        return file;
    }

    private static void write(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }
}
