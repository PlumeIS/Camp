package cn.plumc.camp;

import cn.plumc.camp.export.CampWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class CampWorkbookTest {
    @TempDir Path directory;

    @Test void workbookHasOneSheetColorGroupsFrozenHeaderAndLiteralUserText() throws Exception {
        List<String> red = cells("红色阵营", "=HYPERLINK(\"https://example.org\")\n理由 & <tag> \"测试\" 😀\u0000");
        Path output = directory.resolve("camps.xlsx");
        CampWorkbook.write(output, List.of(new CampWorkbook.Row(0xff5555, red), new CampWorkbook.Row(0xff5555, cells("红色阵营", "member")),
                new CampWorkbook.Row(0x0000aa, cells("蓝色阵营", "member"))));
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertEquals(6, zip.size());
            Document workbook = xml(zip, "xl/workbook.xml");
            assertEquals(1, workbook.getElementsByTagName("sheet").getLength());
            Document sheet = xml(zip, "xl/worksheets/sheet1.xml");
            assertEquals(4, sheet.getElementsByTagName("row").getLength());
            assertEquals(0, sheet.getElementsByTagName("f").getLength());
            assertEquals("frozen", sheet.getElementsByTagName("pane").item(0).getAttributes().getNamedItem("state").getNodeValue());
            assertEquals("A1:O4", sheet.getElementsByTagName("autoFilter").item(0).getAttributes().getNamedItem("ref").getNodeValue());
            assertEquals(red.get(1).replace("\u0000", ""), sheet.getElementsByTagName("t").item(16).getTextContent());
            assertEquals("2", sheet.getElementsByTagName("c").item(15).getAttributes().getNamedItem("s").getNodeValue());
            assertEquals("2", sheet.getElementsByTagName("c").item(30).getAttributes().getNamedItem("s").getNodeValue());
            assertEquals("3", sheet.getElementsByTagName("c").item(45).getAttributes().getNamedItem("s").getNodeValue());
            Document styles = xml(zip, "xl/styles.xml");
            assertEquals("FFFF5555", styles.getElementsByTagName("fgColor").item(1).getAttributes().getNamedItem("rgb").getNodeValue());
            assertEquals("FF0000AA", styles.getElementsByTagName("fgColor").item(2).getAttributes().getNamedItem("rgb").getNodeValue());
            for (int i = 0; i < styles.getElementsByTagName("alignment").getLength(); i++) {
                var alignment = styles.getElementsByTagName("alignment").item(i).getAttributes();
                assertEquals("center", alignment.getNamedItem("horizontal").getNodeValue());
                assertEquals("center", alignment.getNamedItem("vertical").getNodeValue());
            }
        }
        // Stable fixture for a second, independent workbook reader.
        Path fixture = Path.of("target", "verification", "camps.xlsx");
        Files.createDirectories(fixture.getParent());
        Files.copy(output, fixture, StandardCopyOption.REPLACE_EXISTING);
    }

    @Test void emptyExportIsStillReadableAndInvalidOversizeCellsLeaveNoPartialFile() throws Exception {
        Path output = directory.resolve("empty.xlsx");
        CampWorkbook.write(output, List.of());
        try (ZipFile zip = new ZipFile(output.toFile())) { assertEquals(1, xml(zip, "xl/worksheets/sheet1.xml").getElementsByTagName("row").getLength()); }
        Path invalid = directory.resolve("invalid.xlsx");
        assertThrows(java.io.IOException.class, () -> CampWorkbook.write(invalid, List.of(new CampWorkbook.Row(0, cells("camp", "x".repeat(32768))))));
        assertFalse(Files.exists(invalid));
    }

    @Test void publicCampFieldsMergeByMemberCountWithoutMergingPlayersOrDifferentCamps() throws Exception {
        List<CampWorkbook.Row> rows = List.of(
                publicRow("a", "Owner", "拥有者"), publicRow("a", "Admin", "管理员"), publicRow("a", "Member", "成员"),
                publicRow("b", "Solo", "成员"), publicRow("c", "", ""));
        Path output = directory.resolve("public.xlsx");
        CampWorkbook.write(output, CampWorkbook.PUBLIC_HEADERS, rows);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            Document sheet = xml(zip, "xl/worksheets/sheet1.xml");
            assertEquals(Set.of("A2:A4", "B2:B4", "C2:C4", "D2:D4"), mergedRanges(sheet));
            assertEquals("a", cell(sheet, "A2"));
            assertEquals("", cell(sheet, "A3"));
            assertEquals("Owner", cell(sheet, "E2"));
            assertEquals("Admin", cell(sheet, "E3"));
            assertEquals("Member", cell(sheet, "E4"));
            assertEquals("管理员", cell(sheet, "F3"));
            assertEquals("b", cell(sheet, "A5"));
            assertEquals("Solo", cell(sheet, "E5"));
            assertEquals("c", cell(sheet, "A6"));
            assertEquals("", cell(sheet, "E6"));
        }
        saveFixture(output, "camps-merged.xlsx");
    }

    @Test void fullWorkbookMergesSharedCampDataAndPreservesEveryPlayersPrivateFields() throws Exception {
        List<CampWorkbook.Row> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<String> cells = new ArrayList<>(List.of("a", "阵营", "red", "Player" + i, "uuid-" + i, "成员",
                    Integer.toString(3 - i), "否", "否", "是", "否", "否", "填写密钥", "reason-" + i, "加入密钥: private-key"));
            rows.add(new CampWorkbook.Row(0xff5555, cells));
        }
        Path output = directory.resolve("full.xlsx");
        CampWorkbook.write(output, rows);
        try (ZipFile zip = new ZipFile(output.toFile())) {
            Document sheet = xml(zip, "xl/worksheets/sheet1.xml");
            assertEquals(Set.of("A2:A4", "B2:B4", "C2:C4", "M2:M4", "O2:O4"), mergedRanges(sheet));
            for (int i = 0; i < 3; i++) {
                assertEquals("Player" + i, cell(sheet, "D" + (i + 2)));
                assertEquals("uuid-" + i, cell(sheet, "E" + (i + 2)));
                assertEquals(Integer.toString(3 - i), cell(sheet, "G" + (i + 2)));
                assertEquals("reason-" + i, cell(sheet, "N" + (i + 2)));
            }
            assertEquals("加入密钥: private-key", cell(sheet, "O2"));
        }
        saveFixture(output, "camps-all-merged.xlsx");
    }

    private CampWorkbook.Row publicRow(String id, String player, String role) {
        // Identical names and colors across camps must not cause cross-camp merges.
        return new CampWorkbook.Row(0xff5555, List.of(id, "阵营", "red", "申请加入，需管理员审核", player, role));
    }

    private Set<String> mergedRanges(Document sheet) {
        Set<String> ranges = new HashSet<>();
        var nodes = sheet.getElementsByTagName("mergeCell");
        for (int i = 0; i < nodes.getLength(); i++) ranges.add(nodes.item(i).getAttributes().getNamedItem("ref").getNodeValue());
        return ranges;
    }

    private String cell(Document sheet, String reference) {
        var nodes = sheet.getElementsByTagName("c");
        for (int i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (reference.equals(node.getAttributes().getNamedItem("r").getNodeValue())) return node.getTextContent();
        }
        fail("Missing cell " + reference);
        return "";
    }

    private void saveFixture(Path output, String filename) throws Exception {
        Path fixture = Path.of("target", "verification", filename);
        Files.createDirectories(fixture.getParent());
        Files.copy(output, fixture, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<String> cells(String id, String value) {
        List<String> cells = new ArrayList<>(Collections.nCopies(CampWorkbook.HEADERS.size(), ""));
        cells.set(0, id); cells.set(1, value);
        return cells;
    }

    private Document xml(ZipFile zip, String entry) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var input = zip.getInputStream(zip.getEntry(entry))) { return factory.newDocumentBuilder().parse(input); }
    }
}
