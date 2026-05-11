package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class MiscCodec {

    private MiscCodec() {}

    private static final Map<Character, String> MORSE = new HashMap<>();
    private static final Map<String, Character> MORSE_REV = new HashMap<>();

    static {
        String[][] table = {
                {"A", ".-"}, {"B", "-..."}, {"C", "-.-."}, {"D", "-.."},
                {"E", "."}, {"F", "..-."}, {"G", "--."}, {"H", "...."},
                {"I", ".."}, {"J", ".---"}, {"K", "-.-"}, {"L", ".-.."},
                {"M", "--"}, {"N", "-."}, {"O", "---"}, {"P", ".--."},
                {"Q", "--.-"}, {"R", ".-."}, {"S", "..."}, {"T", "-"},
                {"U", "..-"}, {"V", "...-"}, {"W", ".--"}, {"X", "-..-"},
                {"Y", "-.--"}, {"Z", "--.."},
                {"0", "-----"}, {"1", ".----"}, {"2", "..---"}, {"3", "...--"},
                {"4", "....-"}, {"5", "....."}, {"6", "-...."}, {"7", "--..."},
                {"8", "---.."}, {"9", "----."},
                {".", ".-.-.-"}, {",", "--..--"}, {"?", "..--.."}, {"!", "-.-.--"},
                {"-", "-....-"}, {"/", "-..-."}, {"@", ".--.-."}, {"(", "-.--."}, {")", "-.--.-"},
                {":", "---..."}, {";", "-.-.-."}, {"=", "-...-"}, {"+", ".-.-."}
        };
        for (String[] row : table) {
            MORSE.put(row[0].charAt(0), row[1]);
            MORSE_REV.put(row[1], row[0].charAt(0));
        }
    }

    public static String textToMorse(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = Character.toUpperCase(input.charAt(i));
            if (c == ' ') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '/') sb.append(" / ");
                continue;
            }
            String code = MORSE.get(c);
            if (code == null) continue;
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ') sb.append(' ');
            sb.append(code);
        }
        return sb.toString().trim();
    }

    public static String morseToText(String input) {
        StringBuilder sb = new StringBuilder();
        String[] words = input.trim().split("\\s*/\\s*");
        for (int w = 0; w < words.length; w++) {
            if (w > 0) sb.append(' ');
            for (String letter : words[w].trim().split("\\s+")) {
                if (letter.isEmpty()) continue;
                Character c = MORSE_REV.get(letter);
                if (c == null) throw new ConversionException("非法的摩尔斯码: " + letter);
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String randomUuid(String input) {
        return UUID.randomUUID().toString();
    }

    public static String uuidNoDashes(String input) {
        String s = input.trim();
        try {
            return UUID.fromString(s).toString().replace("-", "");
        } catch (IllegalArgumentException e) {
            throw new ConversionException("非法的UUID", e);
        }
    }

    public static String addDashesToUuid(String input) {
        String s = input.trim().toLowerCase();
        if (s.length() != 32) throw new ConversionException("UUID应为32位十六进制");
        return s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                + "-" + s.substring(16, 20) + "-" + s.substring(20, 32);
    }

    public static String jwtDecode(String input) {
        String s = input.trim();
        String[] parts = s.split("\\.");
        if (parts.length < 2) throw new ConversionException("JWT至少应包含两段（用.分隔）");
        String header = decodeJwtSegment(parts[0]);
        String payload = decodeJwtSegment(parts[1]);
        StringBuilder sb = new StringBuilder();
        sb.append("// Header\n");
        sb.append(StructuredCodec.prettyJson(header)).append("\n\n");
        sb.append("// Payload\n");
        sb.append(StructuredCodec.prettyJson(payload));
        if (parts.length > 2) {
            sb.append("\n\n// Signature\n").append(parts[2]);
        }
        return sb.toString();
    }

    private static String decodeJwtSegment(String segment) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(padBase64(segment));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ConversionException("非法的JWT段", e);
        }
    }

    private static String padBase64(String s) {
        int rem = s.length() % 4;
        if (rem == 0) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() % 4 != 0) sb.append('=');
        return sb.toString();
    }

    public static String stripWhitespace(String input) {
        return input.replaceAll("\\s+", "");
    }

    public static String stripBlankLines(String input) {
        return input.replaceAll("(?m)^\\s*\\r?\\n", "");
    }

    public static String escapeJavaString(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unescapeJavaString(String input) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if (c == '\\' && i + 1 < input.length()) {
                char n = input.charAt(i + 1);
                switch (n) {
                    case '"': sb.append('"'); i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case 'n': sb.append('\n'); i += 2; continue;
                    case 'r': sb.append('\r'); i += 2; continue;
                    case 't': sb.append('\t'); i += 2; continue;
                    case 'b': sb.append('\b'); i += 2; continue;
                    case 'f': sb.append('\f'); i += 2; continue;
                    case 'u':
                        if (i + 6 <= input.length()) {
                            try {
                                sb.append((char) Integer.parseInt(input.substring(i + 2, i + 6), 16));
                                i += 6;
                                continue;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        break;
                    default:
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
