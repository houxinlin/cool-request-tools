package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.util.ArrayList;
import java.util.List;

public final class CaseCodec {

    private CaseCodec() {}

    public static String toUpper(String input) {
        return input.toUpperCase();
    }

    public static String toLower(String input) {
        return input.toLowerCase();
    }

    public static String toCamelCase(String input) {
        List<String> tokens = tokenize(input);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i).toLowerCase();
            if (i == 0) sb.append(t);
            else sb.append(capitalize(t));
        }
        return sb.toString();
    }

    public static String toPascalCase(String input) {
        List<String> tokens = tokenize(input);
        StringBuilder sb = new StringBuilder();
        for (String t : tokens) sb.append(capitalize(t.toLowerCase()));
        return sb.toString();
    }

    public static String toSnakeCase(String input) {
        return String.join("_", lowerTokens(tokenize(input)));
    }

    public static String toUpperSnakeCase(String input) {
        StringBuilder sb = new StringBuilder();
        List<String> tokens = tokenize(input);
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append('_');
            sb.append(tokens.get(i).toUpperCase());
        }
        return sb.toString();
    }

    public static String toKebabCase(String input) {
        return String.join("-", lowerTokens(tokenize(input)));
    }

    public static String toDotCase(String input) {
        return String.join(".", lowerTokens(tokenize(input)));
    }

    public static String toTitleCase(String input) {
        List<String> tokens = tokenize(input);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(capitalize(tokens.get(i).toLowerCase()));
        }
        return sb.toString();
    }

    public static String swapCase(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isUpperCase(c)) sb.append(Character.toLowerCase(c));
            else if (Character.isLowerCase(c)) sb.append(Character.toUpperCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    private static List<String> tokenize(String input) {
        if (input == null || input.isEmpty()) throw new ConversionException("输入为空");
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '_' || c == '-' || c == '.' || c == ' ' || c == '/' || c == '\t') {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else if (Character.isUpperCase(c) && cur.length() > 0
                    && Character.isLowerCase(cur.charAt(cur.length() - 1))) {
                out.add(cur.toString());
                cur.setLength(0);
                cur.append(c);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) throw new ConversionException("无法识别词元");
        return out;
    }

    private static List<String> lowerTokens(List<String> tokens) {
        List<String> out = new ArrayList<>(tokens.size());
        for (String t : tokens) out.add(t.toLowerCase());
        return out;
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
