package cn.plumc.camp.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal OOXML writer. All user data is an inline string, never an Excel formula. */
public final class CampWorkbook {
    public static final List<String> HEADERS = List.of("阵营ID", "阵营名称", "阵营颜色", "玩家名称", "玩家UUID", "状态",
            "权限等级", "拥有者", "管理员", "正式成员", "待处理", "黑名单", "加入规则", "申请理由", "阵营属性");
    public static final List<String> PUBLIC_HEADERS = List.of("id", "名称", "颜色", "加入条件", "成员", "成员身份");
    public record Row(int rgb, List<String> cells) {
        public Row { cells = List.copyOf(cells); }
    }
    private record Merge(int column, int firstRow, int lastRow) {
        String reference() { return "" + (char) ('A' + column) + firstRow + ":" + (char) ('A' + column) + lastRow; }
    }
    private static final String NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private CampWorkbook() {}

    public static void write(Path destination, List<Row> rows) throws IOException {
        write(destination, HEADERS, rows);
    }

    public static void write(Path destination, List<String> headers, List<Row> rows) throws IOException {
        if (headers.isEmpty() || headers.size() > 26) throw new IOException("Excel 表头列数错误。");
        if (rows.size() > 1_048_575) throw new IOException("Excel 单表最多支持 1,048,575 行数据。");
        for (Row row : rows) {
            if (row.cells().size() != headers.size()) throw new IOException("Excel 列数错误。");
            for (String cell : row.cells()) if (cell.length() > 32767) throw new IOException("阵营属性超过 Excel 单元格 32767 字符限制。");
        }
        List<Merge> merges = merges(headers, rows);
        int[] continuationMasks = new int[rows.size()];
        for (Merge merge : merges) for (int index = merge.firstRow() - 1; index <= merge.lastRow() - 2; index++)
            continuationMasks[index] |= 1 << merge.column();
        Path parent = destination.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "camp-export-", ".tmp");
        try {
            Map<Integer, Integer> colors = new LinkedHashMap<>();
            for (Row row : rows) colors.computeIfAbsent(row.rgb() & 0xffffff, rgb -> colors.size() + 2);
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                part(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/></Types>");
                part(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                part(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"" + NS + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"阵营总表\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                part(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
                part(zip, "xl/styles.xml", styles(colors));
                zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
                String range = "A1:" + (char) ('A' + headers.size() - 1) + (rows.size() + 1);
                writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"" + NS + "\"><dimension ref=\"" + range + "\"/>"
                        + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>"
                        + "<sheetFormatPr defaultRowHeight=\"24\"/><cols>");
                for (int i = 0; i < headers.size(); i++) {
                    int width = switch (headers.get(i)) {
                        case "玩家UUID" -> 38;
                        case "阵营属性" -> 65;
                        case "申请理由" -> 36;
                        case "加入条件" -> 30;
                        default -> 22;
                    };
                    writer.write("<col min=\"" + (i + 1) + "\" max=\"" + (i + 1) + "\" width=\"" + width + "\" customWidth=\"1\"/>");
                }
                writer.write("</cols><sheetData>");
                row(writer, 1, 1, headers);
                int number = 2;
                for (Row row : rows) {
                    row(writer, number, colors.get(row.rgb() & 0xffffff), row.cells(), continuationMasks[number - 2]);
                    number++;
                }
                writer.write("</sheetData><autoFilter ref=\"" + range + "\"/>");
                if (!merges.isEmpty()) {
                    writer.write("<mergeCells count=\"" + merges.size() + "\">");
                    for (Merge merge : merges) writer.write("<mergeCell ref=\"" + merge.reference() + "\"/>");
                    writer.write("</mergeCells>");
                }
                writer.write("</worksheet>");
                writer.flush();
                zip.closeEntry();
            }
            try { Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, destination); }
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void row(BufferedWriter writer, int number, int style, List<String> cells) throws IOException {
        row(writer, number, style, cells, 0);
    }

    private static void row(BufferedWriter writer, int number, int style, List<String> cells, int continuationMask) throws IOException {
        writer.write("<row r=\"" + number + "\">");
        for (int col = 0; col < cells.size(); col++) {
            if ((continuationMask & (1 << col)) != 0) {
                writer.write("<c r=\"" + (char) ('A' + col) + number + "\" s=\"" + style + "\"/>");
                continue;
            }
            writer.write("<c r=\"" + (char) ('A' + col) + number + "\" s=\"" + style + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">");
            writer.write(xml(cells.get(col)));
            writer.write("</t></is></c>");
        }
        writer.write("</row>");
    }

    private static List<Merge> merges(List<String> headers, List<Row> rows) {
        List<Integer> columns = headers.equals(PUBLIC_HEADERS) ? List.of(0, 1, 2, 3)
                : headers.equals(HEADERS) ? List.of(0, 1, 2, 12, 14) : List.of();
        List<Merge> merges = new ArrayList<>();
        for (int start = 0; start < rows.size();) {
            String id = rows.get(start).cells().get(0);
            int end = start + 1;
            while (!id.isEmpty() && end < rows.size() && id.equals(rows.get(end).cells().get(0))) end++;
            if (end - start > 1) for (int column : columns) {
                String value = rows.get(start).cells().get(column);
                boolean same = true;
                for (int index = start + 1; index < end; index++) {
                    if (!value.equals(rows.get(index).cells().get(column))) { same = false; break; }
                }
                // Never discard differing values, even if an external caller supplies inconsistent camp data.
                if (same) merges.add(new Merge(column, start + 2, end + 1));
            }
            start = end;
        }
        return merges;
    }

    private static String styles(Map<Integer, Integer> colors) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><styleSheet xmlns=\"" + NS + "\"><fonts count=\"3\">"
                + "<font><sz val=\"11\"/><color rgb=\"FF111111\"/><name val=\"Calibri\"/></font>"
                + "<font><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font>"
                + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"" + (colors.size() + 3) + "\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF263445\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        for (int color : colors.keySet()) xml.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF")
                .append(String.format(Locale.ROOT, "%06X", color)).append("\"/><bgColor indexed=\"64\"/></patternFill></fill>");
        xml.append("</fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"")
                .append(colors.size() + 2).append("\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>");
        xml.append(xf(2, 2));
        int fill = 3;
        for (int color : colors.keySet()) {
            double light = ((color >> 16) & 255) * 0.299 + ((color >> 8) & 255) * 0.587 + (color & 255) * 0.114;
            xml.append(xf(light < 140 ? 1 : 0, fill++));
        }
        return xml.append("</cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>").toString();
    }

    private static String xf(int font, int fill) {
        return "<xf numFmtId=\"49\" fontId=\"" + font + "\" fillId=\"" + fill + "\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\" applyNumberFormat=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>";
    }

    private static String xml(String value) {
        StringBuilder result = new StringBuilder();
        value.codePoints().filter(c -> c == 9 || c == 10 || c == 13 || (c >= 32 && c <= 0xd7ff) || (c >= 0xe000 && c <= 0xfffd) || (c >= 0x10000 && c <= 0x10ffff))
                .forEach(c -> {
                    switch (c) {
                        case '&' -> result.append("&amp;");
                        case '<' -> result.append("&lt;");
                        case '>' -> result.append("&gt;");
                        case '"' -> result.append("&quot;");
                        case '\r' -> result.append("&#13;");
                        default -> result.appendCodePoint(c);
                    }
                });
        return result.toString();
    }

    private static void part(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
