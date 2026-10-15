package cn.plumc.camp.export;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/** Saves a complete pair, using one daily sequence number and preserving previous exports. */
public final class CampExportFiles {
    public record Pair(Path all, Path publicFile) {}
    private CampExportFiles() {}

    public static Pair write(Path directory, LocalDate date, List<CampWorkbook.Row> allRows,
                             List<CampWorkbook.Row> publicRows) throws IOException {
        Files.createDirectories(directory);
        String day = date.format(DateTimeFormatter.BASIC_ISO_DATE);
        Pattern exported = Pattern.compile("camps(?:-all)?-" + day + "-(\\d+)\\.xlsx");
        Pattern reserved = Pattern.compile("\\.camps-" + day + "-(\\d+)\\.lock");
        long sequence = 0;
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                var match = exported.matcher(name);
                if (!match.matches()) match = reserved.matcher(name);
                if (match.matches()) {
                    try { sequence = Math.max(sequence, Long.parseLong(match.group(1))); }
                    catch (NumberFormatException e) { throw new IOException("导出序号超过支持的范围。", e); }
                }
            }
        }
        Path lock;
        Path all;
        Path publicFile;
        while (true) {
            if (sequence == Long.MAX_VALUE) throw new IOException("当天导出序号已用尽。");
            sequence++;
            all = directory.resolve("camps-all-" + day + "-" + sequence + ".xlsx");
            publicFile = directory.resolve("camps-" + day + "-" + sequence + ".xlsx");
            lock = directory.resolve(".camps-" + day + "-" + sequence + ".lock");
            try { Files.createFile(lock); }
            catch (FileAlreadyExistsException e) { continue; }
            if (!Files.exists(all) && !Files.exists(publicFile)) break;
            Files.delete(lock);
        }
        Path staging = null;
        boolean allPublished = false;
        boolean completed = false;
        try {
            staging = Files.createTempDirectory(directory, ".camp-export-");
            Path stagedAll = staging.resolve("all.xlsx"), stagedPublic = staging.resolve("public.xlsx");
            CampWorkbook.write(stagedAll, allRows);
            CampWorkbook.write(stagedPublic, CampWorkbook.PUBLIC_HEADERS, publicRows);
            // Both workbooks are finished before publishing. Moves never replace an existing export.
            Files.move(stagedAll, all);
            allPublished = true;
            Files.move(stagedPublic, publicFile);
            completed = true;
            return new Pair(all, publicFile);
        } finally {
            if (!completed && allPublished) Files.deleteIfExists(all);
            if (staging != null) {
                Files.deleteIfExists(staging.resolve("all.xlsx"));
                Files.deleteIfExists(staging.resolve("public.xlsx"));
                Files.deleteIfExists(staging);
            }
            Files.deleteIfExists(lock);
        }
    }
}
