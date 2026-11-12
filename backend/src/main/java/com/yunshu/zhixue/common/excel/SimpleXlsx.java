package com.yunshu.zhixue.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 极简 xlsx 读写（不引入 POI）。
 *
 * <p>只处理题库导入这一种场景：读第一个工作表的所有单元格文本、按行返回；
 * 写出时全部使用 inlineStr，不写样式、公式与共享字符串。
 * Excel / WPS / LibreOffice 都能正常打开，也都能被本类读回。</p>
 *
 * <p>不做的事：日期格式、合并单元格、公式求值、多工作表选择——导入模板里都不需要。</p>
 */
public final class SimpleXlsx {

    private static final Pattern ROW = Pattern.compile("<row[^>]*>(.*?)</row>", Pattern.DOTALL);
    private static final Pattern CELL = Pattern.compile("<c\\b([^>]*)/?>(.*?)</c>", Pattern.DOTALL);
    private static final Pattern CELL_REF = Pattern.compile("r=\"([A-Z]+)\\d+\"");
    private static final Pattern CELL_TYPE = Pattern.compile("t=\"([a-zA-Z]+)\"");
    private static final Pattern VALUE = Pattern.compile("<v>(.*?)</v>", Pattern.DOTALL);
    private static final Pattern TEXT = Pattern.compile("<t\\b[^>]*>(.*?)</t>", Pattern.DOTALL);
    private static final Pattern SHARED_ITEM = Pattern.compile("<si[^>]*>(.*?)</si>", Pattern.DOTALL);

    private SimpleXlsx() {
    }

    // ------------------------------------------------------------------ 读

    /** 读第一个工作表：按行返回，行内按列补齐（缺列为空串） */
    public static List<List<String>> readFirstSheet(byte[] xlsx) {
        Map<String, byte[]> entries = unzip(xlsx);
        List<String> shared = parseSharedStrings(entries.get("xl/sharedStrings.xml"));
        // 工作表按文件名排序取第一个（sheet1.xml），不解析 workbook.xml 的可见性
        String sheetName = entries.keySet().stream()
                .filter(name -> name.startsWith("xl/worksheets/sheet"))
                .sorted()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("xlsx 缺少工作表"));
        return parseSheet(new String(entries.get(sheetName), StandardCharsets.UTF_8), shared);
    }

    private static List<List<String>> parseSheet(String xml, List<String> shared) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = ROW.matcher(xml);
        while (rowMatcher.find()) {
            Map<Integer, String> cells = new LinkedHashMap<>();
            Matcher cellMatcher = CELL.matcher(rowMatcher.group(1));
            int fallback = 0;
            while (cellMatcher.find()) {
                String attributes = cellMatcher.group(1);
                String body = cellMatcher.group(2);
                int index = fallback;
                Matcher refMatcher = CELL_REF.matcher(attributes);
                if (refMatcher.find()) {
                    index = columnIndex(refMatcher.group(1));
                }
                cells.put(index, cellText(attributes, body, shared));
                fallback = index + 1;
            }
            if (cells.isEmpty()) {
                continue;
            }
            int width = cells.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
            List<String> row = new ArrayList<>(width);
            for (int i = 0; i < width; i++) {
                row.add(cells.getOrDefault(i, ""));
            }
            rows.add(row);
        }
        return rows;
    }

    /** 单元格取值：共享字符串按索引取，内联字符串取 t，数字/布尔取 v 原文 */
    private static String cellText(String attributes, String body, List<String> shared) {
        Matcher typeMatcher = CELL_TYPE.matcher(attributes);
        String type = typeMatcher.find() ? typeMatcher.group(1) : "n";
        if ("inlineStr".equals(type)) {
            return textOf(body).trim();
        }
        Matcher valueMatcher = VALUE.matcher(body);
        if (!valueMatcher.find()) {
            return textOf(body).trim();
        }
        String raw = unescape(valueMatcher.group(1));
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(raw.trim());
                return index >= 0 && index < shared.size() ? shared.get(index) : "";
            } catch (NumberFormatException ex) {
                return "";
            }
        }
        if ("n".equals(type)) {
            // Excel 把纯数字写成 123 或 123.0，导入时按整数形态还原
            return raw.endsWith(".0") ? raw.substring(0, raw.length() - 2) : raw;
        }
        return raw;
    }

    private static String textOf(String body) {
        StringBuilder sb = new StringBuilder();
        Matcher matcher = TEXT.matcher(body);
        while (matcher.find()) {
            sb.append(unescape(matcher.group(1)));
        }
        return sb.toString();
    }

    private static List<String> parseSharedStrings(byte[] xml) {
        List<String> items = new ArrayList<>();
        if (xml == null) {
            return items;
        }
        Matcher matcher = SHARED_ITEM.matcher(new String(xml, StandardCharsets.UTF_8));
        while (matcher.find()) {
            items.add(textOf(matcher.group(1)));
        }
        return items;
    }

    private static Map<String, byte[]> unzip(byte[] bytes) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entries.put(entry.getName(), readAll(zip));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return entries;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** A→0, B→1, AA→26 */
    private static int columnIndex(String letters) {
        int index = 0;
        for (int i = 0; i < letters.length(); i++) {
            index = index * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return index - 1;
    }

    private static String columnName(int index) {
        StringBuilder sb = new StringBuilder();
        int value = index + 1;
        while (value > 0) {
            int remainder = (value - 1) % 26;
            sb.insert(0, (char) ('A' + remainder));
            value = (value - 1) / 26;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ 写

    public record Sheet(String name, List<String[]> rows) {
    }

    /** 写出多工作表 xlsx；单元格全部按 inlineStr 写，值里的 XML 特殊字符会转义 */
    public static byte[] write(List<Sheet> sheets) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                putEntry(zip, "[Content_Types].xml", contentTypes(sheets.size()));
                putEntry(zip, "_rels/.rels",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                                + "<Relationship Id=\"rId1\" "
                                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                                + "Target=\"xl/workbook.xml\"/></Relationships>");
                putEntry(zip, "xl/workbook.xml", workbook(sheets));
                putEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
                for (int i = 0; i < sheets.size(); i++) {
                    putEntry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheet(sheets.get(i)));
                }
            }
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypes(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        sb.append("</Types>");
        return sb.toString();
    }

    private static String workbook(List<Sheet> sheets) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escape(sheets.get(i).name()))
                    .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets></workbook>");
        return sb.toString();
    }

    private static String workbookRels(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" ")
                    .append("Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        sb.append("</Relationships>");
        return sb.toString();
    }

    private static String sheet(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        List<String[]> rows = sheet.rows();
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            String[] row = rows.get(r);
            for (int c = 0; c < row.length; c++) {
                String value = row[c] == null ? "" : row[c];
                sb.append("<c r=\"").append(columnName(c)).append(r + 1)
                        .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(value)).append("</t></is></c>");
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                // XML 1.0 不允许的控制字符直接丢弃，避免发出的文件打不开
                default -> {
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static String unescape(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch != '&') {
                sb.append(ch);
                i++;
                continue;
            }
            int end = value.indexOf(';', i);
            if (end < 0) {
                sb.append(ch);
                i++;
                continue;
            }
            String entity = value.substring(i + 1, end);
            switch (entity) {
                case "amp" -> sb.append('&');
                case "lt" -> sb.append('<');
                case "gt" -> sb.append('>');
                case "quot" -> sb.append('"');
                case "apos" -> sb.append('\'');
                default -> {
                    if (entity.startsWith("#x") || entity.startsWith("#X")) {
                        sb.append((char) Integer.parseInt(entity.substring(2), 16));
                    } else if (entity.startsWith("#")) {
                        sb.append((char) Integer.parseInt(entity.substring(1)));
                    } else {
                        sb.append('&').append(entity).append(';');
                    }
                }
            }
            i = end + 1;
        }
        return sb.toString();
    }
}
