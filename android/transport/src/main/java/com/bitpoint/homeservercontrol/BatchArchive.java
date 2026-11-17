package com.bitpoint.homeservercontrol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Expands the archives that LocalLS senders build from many small files.
 *
 * <p>Only names produced by LocalLS are accepted, entries stay in the archive's
 * own folder, and nothing is published before every size and checksum matched.
 * The archive is deleted only after all files were written.</p>
 */
final class BatchArchive {
    static final int MAX_FILES = 500, MAX_ENTRIES = 2000;
    static final long MAX_FILE = 16L * 1024 * 1024, MAX_TOTAL = 256L * 1024 * 1024;
    private static final Pattern NAME = Pattern.compile(
            "LocalLS-\\d{8}-\\d{6}-[a-f0-9]{8}\\.localls\\.zip", Pattern.CASE_INSENSITIVE);
    private static final int COPY = 64 * 1024;

    private BatchArchive() {}

    static final class Published {
        final String name;
        final long bytes, modified;

        Published(String name, long bytes, long modified) {
            this.name = name;
            this.bytes = bytes;
            this.modified = modified;
        }
    }

    static boolean isArchive(Path file) {
        Path name = file.getFileName();
        return name != null && NAME.matcher(name.toString()).matches();
    }

    /** Extracts {@code archive} next to itself, then removes the archive. */
    static List<Published> expand(Path archive) throws IOException {
        Path folder = archive.getParent();
        if (folder == null) throw new IOException("Archive has no folder");
        if (!Files.isRegularFile(archive, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Archive is not a file");
        Path staging = folder.resolve(".localls-extract-" + UUID.randomUUID());
        Files.createDirectory(staging);
        List<Published> published = new ArrayList<>();
        try {
            List<String> names = extract(archive, staging);
            if (names.isEmpty()) throw new IOException("Archive is empty");
            for (int index = 0; index < names.size(); index++) {
                String name = names.get(index);
                Path staged = staging.resolve(Integer.toString(index));
                Path target = folder.resolve(name);
                if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                    // The same bytes are already there: the duplicate is dropped.
                    if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) && identical(staged, target)) {
                        Files.delete(staged);
                        continue;
                    }
                    target = available(folder, name);
                }
                if (Files.isSymbolicLink(target) || Files.isSymbolicLink(target.getParent())) throw new IOException("Link target");
                Files.move(staged, target);
                published.add(new Published(target.getFileName().toString(),
                        Files.size(target), Files.getLastModifiedTime(target, LinkOption.NOFOLLOW_LINKS).toMillis()));
            }
        } catch (IOException | RuntimeException error) {
            remove(staging);
            throw error instanceof IOException ? (IOException) error : new IOException(error);
        }
        remove(staging);
        Files.deleteIfExists(archive);
        return published;
    }

    private static List<String> extract(Path archive, Path staging) throws IOException {
        List<String> names = new ArrayList<>();
        long total = 0;
        try (InputStream raw = Files.newInputStream(archive, StandardOpenOption.READ);
             ZipInputStream zip = new ZipInputStream(raw, StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) continue;
                if (names.size() >= MAX_FILES || names.size() >= MAX_ENTRIES) throw new IOException("Too many files");
                // name() rejects separators, control characters and dot names, so an
                // entry can never leave the folder the archive was published into.
                String name = TransferPaths.name(entry.getName());
                if (name.startsWith(".localls-") || name.startsWith(".import-")) throw new IOException("Reserved name");
                Path file = staging.resolve(Integer.toString(names.size()));
                CRC32 crc = new CRC32();
                long size = 0;
                try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                    byte[] buffer = new byte[COPY];
                    for (int read; (read = zip.read(buffer)) > 0; ) {
                        size += read;
                        total += read;
                        if (size > MAX_FILE || total > MAX_TOTAL) throw new IOException("Archive is too large");
                        crc.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                if (entry.getSize() > 0 && entry.getSize() != size) throw new IOException("Size mismatch");
                if (entry.getCrc() != 0 && entry.getCrc() != crc.getValue()) throw new IOException("Checksum mismatch");
                names.add(name);
            }
        }
        return names;
    }

    private static Path available(Path folder, String name) throws IOException {
        for (int number = 1; number < 10000; number++) {
            Path candidate = folder.resolve(TransferPaths.duplicate(name, number));
            if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) return candidate;
        }
        throw new IOException("No free name");
    }

    private static boolean identical(Path left, Path right) throws IOException {
        if (Files.size(left) != Files.size(right)) return false;
        try (InputStream one = Files.newInputStream(left); InputStream two = Files.newInputStream(right)) {
            byte[] a = new byte[COPY], b = new byte[COPY];
            while (true) {
                int first = one.read(a), second = two.read(b);
                if (first != second) return false;
                if (first < 0) return true;
                for (int index = 0; index < first; index++) if (a[index] != b[index]) return false;
                if (first == 0) return true;
            }
        }
    }

    private static void remove(Path folder) {
        try (DirectoryStream<Path> listing = Files.newDirectoryStream(folder)) {
            for (Path item : listing) try { Files.deleteIfExists(item); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
        try { Files.deleteIfExists(folder); } catch (Exception ignored) { }
    }
}
