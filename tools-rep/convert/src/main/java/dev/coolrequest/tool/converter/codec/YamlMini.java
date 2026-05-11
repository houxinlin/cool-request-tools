package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class YamlMini {

    private YamlMini() {}

    static void write(Object v, StringBuilder sb, int indent) {
        if (v == null) {
            sb.append("null\n");
            return;
        }
        if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                sb.append("{}\n");
                return;
            }
            for (Map.Entry<?, ?> e : m.entrySet()) {
                appendIndent(sb, indent);
                sb.append(e.getKey()).append(':');
                Object val = e.getValue();
                if (val instanceof Map && !((Map<?, ?>) val).isEmpty()) {
                    sb.append('\n');
                    write(val, sb, indent + 1);
                } else if (val instanceof List && !((List<?>) val).isEmpty()) {
                    sb.append('\n');
                    for (Object item : (List<?>) val) {
                        appendIndent(sb, indent);
                        sb.append("- ");
                        if (item instanceof Map || item instanceof List) {
                            sb.append('\n');
                            write(item, sb, indent + 1);
                        } else {
                            sb.append(scalar(item)).append('\n');
                        }
                    }
                } else {
                    sb.append(' ').append(scalar(val)).append('\n');
                }
            }
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                sb.append("[]\n");
                return;
            }
            for (Object item : l) {
                appendIndent(sb, indent);
                sb.append("- ");
                if (item instanceof Map || item instanceof List) {
                    sb.append('\n');
                    write(item, sb, indent + 1);
                } else {
                    sb.append(scalar(item)).append('\n');
                }
            }
        } else {
            appendIndent(sb, indent);
            sb.append(scalar(v)).append('\n');
        }
    }

    private static String scalar(Object v) {
        if (v == null) return "null";
        if (v instanceof Boolean || v instanceof Number) return v.toString();
        String s = String.valueOf(v);
        if (s.isEmpty()) return "\"\"";
        if (s.contains(":") || s.contains("#") || s.contains("\n") || s.contains("\"")
                || s.startsWith("-") || s.startsWith(" ") || s.endsWith(" ")) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return s;
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    static Object parse(String input) {
        String[] lines = input.split("\\r?\\n");
        List<String> filtered = new ArrayList<>();
        for (String line : lines) {
            int hash = indexOfUnquoted(line, '#');
            String noComment = hash >= 0 ? line.substring(0, hash) : line;
            if (noComment.trim().isEmpty()) continue;
            filtered.add(stripTrailing(noComment));
        }
        int[] idx = new int[]{0};
        return parseBlock(filtered, idx, 0);
    }

    private static int indexOfUnquoted(String line, char ch) {
        boolean inQuote = false;
        char q = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == q) inQuote = false;
            } else {
                if (c == '"' || c == '\'') { inQuote = true; q = c; }
                else if (c == ch) return i;
            }
        }
        return -1;
    }

    private static String stripTrailing(String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }

    private static Object parseBlock(List<String> lines, int[] idx, int indent) {
        if (idx[0] >= lines.size()) return null;
        String first = lines.get(idx[0]);
        int firstIndent = countIndent(first);
        if (firstIndent < indent) return null;
        String trimmed = first.substring(firstIndent);
        if (trimmed.startsWith("- ") || trimmed.equals("-")) {
            return parseList(lines, idx, firstIndent);
        }
        return parseMap(lines, idx, firstIndent);
    }

    private static Map<String, Object> parseMap(List<String> lines, int[] idx, int indent) {
        Map<String, Object> map = new LinkedHashMap<>();
        while (idx[0] < lines.size()) {
            String line = lines.get(idx[0]);
            int curIndent = countIndent(line);
            if (curIndent < indent) break;
            if (curIndent > indent) throw new ConversionException("YAML缩进错误: " + line);
            String body = line.substring(curIndent);
            if (body.startsWith("- ")) break;
            int colon = indexOfUnquoted(body, ':');
            if (colon < 0) throw new ConversionException("YAML语法错误: " + line);
            String key = unquote(body.substring(0, colon).trim());
            String valuePart = body.substring(colon + 1).trim();
            idx[0]++;
            if (valuePart.isEmpty()) {
                if (idx[0] < lines.size() && countIndent(lines.get(idx[0])) > indent) {
                    map.put(key, parseBlock(lines, idx, countIndent(lines.get(idx[0]))));
                } else {
                    map.put(key, null);
                }
            } else {
                map.put(key, parseScalar(valuePart));
            }
        }
        return map;
    }

    private static List<Object> parseList(List<String> lines, int[] idx, int indent) {
        List<Object> list = new ArrayList<>();
        while (idx[0] < lines.size()) {
            String line = lines.get(idx[0]);
            int curIndent = countIndent(line);
            if (curIndent < indent) break;
            String body = line.substring(curIndent);
            if (!body.startsWith("-")) break;
            String after = body.length() > 1 ? body.substring(1).trim() : "";
            idx[0]++;
            if (after.isEmpty()) {
                if (idx[0] < lines.size() && countIndent(lines.get(idx[0])) > indent) {
                    list.add(parseBlock(lines, idx, countIndent(lines.get(idx[0]))));
                } else {
                    list.add(null);
                }
            } else if (after.contains(":") && indexOfUnquoted(after, ':') >= 0) {
                int savedIdx = idx[0];
                List<String> synthetic = new ArrayList<>();
                String spaces = "";
                for (int i = 0; i < indent + 2; i++) spaces += " ";
                synthetic.add(spaces + after);
                while (savedIdx < lines.size() && countIndent(lines.get(savedIdx)) > indent) {
                    synthetic.add(lines.get(savedIdx));
                    savedIdx++;
                }
                int[] subIdx = new int[]{0};
                list.add(parseMap(synthetic, subIdx, indent + 2));
                idx[0] = savedIdx;
            } else {
                list.add(parseScalar(after));
            }
        }
        return list;
    }

    private static int countIndent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static Object parseScalar(String s) {
        s = s.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\""))
                || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1);
        }
        if (s.equals("null") || s.equals("~") || s.isEmpty()) return null;
        if (s.equals("true")) return Boolean.TRUE;
        if (s.equals("false")) return Boolean.FALSE;
        try {
            if (s.contains(".")) return Double.parseDouble(s);
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
        }
        return s;
    }
}
