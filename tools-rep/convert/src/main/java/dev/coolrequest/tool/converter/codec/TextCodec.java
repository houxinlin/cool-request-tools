package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class TextCodec {

    private TextCodec() {}

    public static String urlEncode(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    public static String urlDecode(String input) {
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ConversionException("非法的URL编码", e);
        }
    }

    public static String base64Encode(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(String input) {
        try {
            return new String(Base64.getDecoder().decode(input.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ConversionException("非法的Base64", e);
        }
    }

    public static String base64UrlEncode(String input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64UrlDecode(String input) {
        try {
            return new String(Base64.getUrlDecoder().decode(input.trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ConversionException("非法的Base64URL", e);
        }
    }

    public static String hexBytesEncode(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String hexBytesDecode(String input) {
        String s = input.replaceAll("\\s+", "").replace("0x", "");
        if (s.length() % 2 != 0) throw new ConversionException("Hex长度必须为偶数");
        byte[] bytes = new byte[s.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int hi = hexDigit(s.charAt(i * 2));
            int lo = hexDigit(s.charAt(i * 2 + 1));
            bytes[i] = (byte) ((hi << 4) | lo);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new ConversionException("非法的十六进制字符: " + c);
    }

    public static String binaryBytesEncode(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replace(' ', '0'));
        }
        return sb.toString();
    }

    public static String binaryBytesDecode(String input) {
        String[] parts = input.trim().split("\\s+");
        byte[] bytes = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() != 8) throw new ConversionException("每字节必须为8位");
            bytes[i] = (byte) Integer.parseInt(parts[i], 2);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String asciiCodesEncode(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (i > 0) sb.append(' ');
            sb.append((int) input.charAt(i));
        }
        return sb.toString();
    }

    public static String asciiCodesDecode(String input) {
        String[] parts = input.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append((char) Integer.parseInt(p));
        }
        return sb.toString();
    }

    public static String unicodeEscape(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c < 128) sb.append(c);
            else sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }

    public static String unicodeUnescape(String input) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            if (i + 5 < input.length() && input.charAt(i) == '\\' && input.charAt(i + 1) == 'u') {
                String hex = input.substring(i + 2, i + 6);
                try {
                    sb.append((char) Integer.parseInt(hex, 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException e) {
                    // fall through
                }
            }
            sb.append(input.charAt(i));
            i++;
        }
        return sb.toString();
    }

    public static String htmlEntityEncode(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '&': sb.append("&amp;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default:
                    if (c > 127) sb.append("&#").append((int) c).append(';');
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String htmlEntityDecode(String input) {
        String s = input.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&#39;", "'").replace("&nbsp;", " ");
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 2 < s.length() && s.charAt(i) == '&' && s.charAt(i + 1) == '#') {
                int end = s.indexOf(';', i);
                if (end > 0) {
                    String body = s.substring(i + 2, end);
                    try {
                        int code = body.startsWith("x") || body.startsWith("X")
                                ? Integer.parseInt(body.substring(1), 16)
                                : Integer.parseInt(body);
                        sb.appendCodePoint(code);
                        i = end + 1;
                        continue;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            sb.append(s.charAt(i));
            i++;
        }
        return sb.toString().replace("&amp;", "&");
    }

    public static String rot13(String input) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= 'a' && c <= 'z') sb.append((char) ((c - 'a' + 13) % 26 + 'a'));
            else if (c >= 'A' && c <= 'Z') sb.append((char) ((c - 'A' + 13) % 26 + 'A'));
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String reverse(String input) {
        return new StringBuilder(input).reverse().toString();
    }
}
