package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonMini {

    private JsonMini() {}

    static Object parse(String input) {
        Parser p = new Parser(input);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (p.pos < p.src.length()) throw new ConversionException("末尾存在无效字符");
        return v;
    }

    static void write(Object v, StringBuilder sb, int indent, boolean pretty) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            if (m.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append('{');
            if (pretty) sb.append('\n');
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ > 0) {
                    sb.append(',');
                    if (pretty) sb.append('\n');
                }
                if (pretty) appendIndent(sb, indent + 1);
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(pretty ? ": " : ":");
                write(e.getValue(), sb, indent + 1, pretty);
            }
            if (pretty) {
                sb.append('\n');
                appendIndent(sb, indent);
            }
            sb.append('}');
        } else if (v instanceof List) {
            List<?> l = (List<?>) v;
            if (l.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append('[');
            if (pretty) sb.append('\n');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                    if (pretty) sb.append('\n');
                }
                if (pretty) appendIndent(sb, indent + 1);
                write(l.get(i), sb, indent + 1, pretty);
            }
            if (pretty) {
                sb.append('\n');
                appendIndent(sb, indent);
            }
            sb.append(']');
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
        } else {
            writeString(String.valueOf(v), sb);
        }
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        final String src;
        int pos;

        Parser(String src) { this.src = src; this.pos = 0; }

        void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        Object parseValue() {
            skipWs();
            if (pos >= src.length()) throw new ConversionException("意外的结束");
            char c = src.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') return parseNull();
            return parseNumber();
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { pos++; return m; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                expect(':');
                Object value = parseValue();
                m.put(key, value);
                skipWs();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return m; }
                throw new ConversionException("期待 ',' 或 '}'");
            }
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> l = new ArrayList<>();
            skipWs();
            if (peek() == ']') { pos++; return l; }
            while (true) {
                Object v = parseValue();
                l.add(v);
                skipWs();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return l; }
                throw new ConversionException("期待 ',' 或 ']'");
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new ConversionException("非法转义");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new ConversionException("非法\\u转义");
                            sb.append((char) Integer.parseInt(src.substring(pos, pos + 4), 16));
                            pos += 4;
                            break;
                        default: throw new ConversionException("非法转义: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new ConversionException("字符串未关闭");
        }

        Object parseBool() {
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new ConversionException("非法标识符");
        }

        Object parseNull() {
            if (src.startsWith("null", pos)) { pos += 4; return null; }
            throw new ConversionException("非法标识符");
        }

        Number parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length() && (Character.isDigit(src.charAt(pos))
                    || "+-.eE".indexOf(src.charAt(pos)) >= 0)) pos++;
            String s = src.substring(start, pos);
            if (s.isEmpty()) throw new ConversionException("非法的数值");
            try {
                if (s.contains(".") || s.contains("e") || s.contains("E")) return Double.parseDouble(s);
                long v = Long.parseLong(s);
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) return (int) v;
                return v;
            } catch (NumberFormatException e) {
                throw new ConversionException("非法的数值: " + s);
            }
        }

        void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new ConversionException("期待 '" + c + "'");
            }
            pos++;
        }

        char peek() {
            if (pos >= src.length()) throw new ConversionException("意外的结束");
            return src.charAt(pos);
        }
    }
}
