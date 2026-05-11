package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StructuredCodec {

    private StructuredCodec() {}

    public static String prettyJson(String input) {
        Object parsed = JsonMini.parse(input);
        StringBuilder sb = new StringBuilder();
        JsonMini.write(parsed, sb, 0, true);
        return sb.toString();
    }

    public static String compactJson(String input) {
        Object parsed = JsonMini.parse(input);
        StringBuilder sb = new StringBuilder();
        JsonMini.write(parsed, sb, 0, false);
        return sb.toString();
    }

    public static String jsonToYaml(String input) {
        Object parsed = JsonMini.parse(input);
        StringBuilder sb = new StringBuilder();
        YamlMini.write(parsed, sb, 0);
        return sb.toString().trim();
    }

    public static String yamlToJson(String input) {
        Object parsed = YamlMini.parse(input);
        StringBuilder sb = new StringBuilder();
        JsonMini.write(parsed, sb, 0, true);
        return sb.toString();
    }

    public static String jsonToXml(String input) {
        Object parsed = JsonMini.parse(input);
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        XmlMini.write("root", parsed, sb, 0);
        return sb.toString();
    }

    public static String queryToJson(String input) {
        String s = input.trim();
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(q + 1);
        Map<String, Object> map = new LinkedHashMap<>();
        if (s.isEmpty()) return "{}";
        for (String pair : s.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            key = TextCodec.urlDecode(key);
            value = TextCodec.urlDecode(value);
            if (map.containsKey(key)) {
                Object existing = map.get(key);
                if (existing instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) existing;
                    list.add(value);
                } else {
                    List<Object> list = new ArrayList<>();
                    list.add(existing);
                    list.add(value);
                    map.put(key, list);
                }
            } else {
                map.put(key, value);
            }
        }
        StringBuilder sb = new StringBuilder();
        JsonMini.write(map, sb, 0, true);
        return sb.toString();
    }

    public static String jsonToQuery(String input) {
        Object parsed = JsonMini.parse(input);
        if (!(parsed instanceof Map)) throw new ConversionException("根节点必须是对象");
        Map<?, ?> map = (Map<?, ?>) parsed;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List) {
                for (Object item : (List<?>) v) {
                    appendQuery(sb, String.valueOf(e.getKey()), String.valueOf(item));
                }
            } else {
                appendQuery(sb, String.valueOf(e.getKey()), v == null ? "" : String.valueOf(v));
            }
        }
        return sb.toString();
    }

    private static void appendQuery(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) sb.append('&');
        sb.append(TextCodec.urlEncode(key)).append('=').append(TextCodec.urlEncode(value));
    }

    public static String csvToJson(String input) {
        String[] lines = input.split("\\r?\\n");
        if (lines.length < 1) return "[]";
        List<String> headers = parseCsvLine(lines[0]);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            List<String> cells = parseCsvLine(lines[i]);
            Map<String, Object> row = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                row.put(headers.get(j), j < cells.size() ? cells.get(j) : "");
            }
            rows.add(row);
        }
        StringBuilder sb = new StringBuilder();
        JsonMini.write(rows, sb, 0, true);
        return sb.toString();
    }

    public static String jsonToCsv(String input) {
        Object parsed = JsonMini.parse(input);
        if (!(parsed instanceof List)) throw new ConversionException("根节点必须是数组");
        List<?> list = (List<?>) parsed;
        if (list.isEmpty()) return "";
        List<String> headers = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map) {
                for (Object k : ((Map<?, ?>) row).keySet()) {
                    String key = String.valueOf(k);
                    if (!headers.contains(key)) headers.add(key);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(headers.get(i)));
        }
        sb.append('\n');
        for (Object row : list) {
            if (!(row instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) row;
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(',');
                Object v = m.get(headers.get(i));
                sb.append(escapeCsv(v == null ? "" : String.valueOf(v)));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else if (c == '"') {
                    inQuotes = true;
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
