package cn.plumc.camp;

import cn.plumc.camp.export.CampExportFiles;
import cn.plumc.camp.export.CampWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class CampExportFilesTest {
    @TempDir Path directory;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);
    private static final List<CampWorkbook.Row> ALL = List.of(new CampWorkbook.Row(0xff5555,
            List.of("a", "阵营", "red", "Member", "private-uuid", "成员", "1", "否", "否", "是", "否", "否", "填写密钥", "private-reason", "加入密钥: private-key")));
    private static final List<CampWorkbook.Row> PUBLIC = List.of(new CampWorkbook.Row(0xff5555,
            List.of("a", "阵营", "red", "填写密钥加入", "Member", "成员")));

    @Test void pairedFilesHaveMatchingDailyNumbersAndSixColumnPublicSchema() throws Exception {
        var first = CampExportFiles.write(directory, DAY, ALL, PUBLIC);
        assertEquals("camps-all-20261014-1.xlsx", first.all().getFileName().toString());
        assertEquals("camps-20261014-1.xlsx", first.publicFile().getFileName().toString());
        byte[] original = Files.readAllBytes(first.all());
        var second = CampExportFiles.write(directory, DAY, ALL, PUBLIC);
        assertEquals("camps-20261014-2.xlsx", second.publicFile().getFileName().toString());
        assertArrayEquals(original, Files.readAllBytes(first.all()));
        var nextDay = CampExportFiles.write(directory, DAY.plusDays(1), ALL, PUBLIC);
        assertEquals("camps-20261015-1.xlsx", nextDay.publicFile().getFileName().toString());
        try (ZipFile zip = new ZipFile(first.publicFile().toFile())) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            try (var source = zip.getInputStream(zip.getEntry("xl/worksheets/sheet1.xml"))) {
                var sheet = factory.newDocumentBuilder().parse(source);
                assertEquals(12, sheet.getElementsByTagName("c").getLength());
                assertEquals("A1:F2", sheet.getElementsByTagName("autoFilter").item(0).getAttributes().getNamedItem("ref").getNodeValue());
                String text = sheet.getDocumentElement().getTextContent();
                assertFalse(text.contains("private-"));
                assertFalse(text.contains("UUID"));
                for (int i = 0; i < 6; i++) assertEquals(CampWorkbook.PUBLIC_HEADERS.get(i), sheet.getElementsByTagName("t").item(i).getTextContent());
            }
        }
        Path fixture = Path.of("target", "verification");
        Files.createDirectories(fixture);
        Files.copy(first.all(), fixture.resolve(first.all().getFileName()), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(first.publicFile(), fixture.resolve(first.publicFile().getFileName()), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test void numberingContinuesFromEitherExistingFileAndIgnoresOtherDates() throws Exception {
        Files.writeString(directory.resolve("camps-all-20261014-3.xlsx"), "old-full");
        Files.writeString(directory.resolve("camps-20261014-8.xlsx"), "old-public");
        Files.writeString(directory.resolve("camps-20261013-50.xlsx"), "yesterday");
        var pair = CampExportFiles.write(directory, DAY, ALL, PUBLIC);
        assertEquals("camps-all-20261014-9.xlsx", pair.all().getFileName().toString());
        assertEquals("old-full", Files.readString(directory.resolve("camps-all-20261014-3.xlsx")));
        assertEquals("old-public", Files.readString(directory.resolve("camps-20261014-8.xlsx")));
    }

    @Test void failedPublicWorkbookLeavesNoHalfExportOrTemporaryFiles() throws Exception {
        var invalid = List.of(new CampWorkbook.Row(0, List.of("invalid-column-count")));
        assertThrows(java.io.IOException.class, () -> CampExportFiles.write(directory, DAY, ALL, invalid));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void concurrentPairsCannotOverwriteOneAnother() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var results = executor.invokeAll(List.<java.util.concurrent.Callable<CampExportFiles.Pair>>of(
                    () -> CampExportFiles.write(directory, DAY, ALL, PUBLIC),
                    () -> CampExportFiles.write(directory, DAY, ALL, PUBLIC)));
            Set<String> names = new HashSet<>();
            for (var result : results) {
                var pair = result.get();
                assertTrue(Files.exists(pair.all()));
                assertTrue(Files.exists(pair.publicFile()));
                names.add(pair.publicFile().getFileName().toString());
            }
            assertEquals(Set.of("camps-20261014-1.xlsx", "camps-20261014-2.xlsx"), names);
            try (var files = Files.list(directory)) { assertEquals(4, files.count()); }
        } finally { executor.shutdownNow(); }
    }
}
