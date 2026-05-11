package dev.coolrequest.tool.converter.codec;

import java.util.List;
import java.util.Map;

final class XmlMini {

    private XmlMini() {}

    static void write(String tagName, Object v, StringBuilder sb, int indent) {
        String tag = sanitizeTag(tagName);
        if (v == null) {
            appendIndent(sb, indent);
            sb.append('<').append(tag).append("/>\n");
        } else if (v instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) v;
            appendIndent(sb, indent);
            sb.append('<').append(tag).append(">\n");
            for (Map.Entry<?, ?> e : m.entrySet()) {
                write(String.valueOf(e.getKey()), e.getValue(), sb, indent + 1);
            }
            appendIndent(sb, indent);
            sb.append("</").append(tag).append(">\n");
        } else if (v instanceof List) {
            for (Object item : (List<?>) v) {
                write(tag, item, sb, indent);
            }
        } else {
            appendIndent(sb, indent);
            sb.append('<').append(tag).append('>').append(escape(String.valueOf(v)))
                    .append("</").append(tag).append(">\n");
        }
    }

    private static String sanitizeTag(String tagName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tagName.length(); i++) {
            char c = tagName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.') sb.append(c);
            else sb.append('_');
        }
        String s = sb.toString();
        if (s.isEmpty() || !Character.isLetter(s.charAt(0)) && s.charAt(0) != '_') s = "_" + s;
        return s;
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
