package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.math.BigInteger;

public final class NumberCodec {

    private NumberCodec() {}

    public static String toBase(String input, int radix) {
        try {
            BigInteger bi = parseBigInteger(input.trim());
            return bi.toString(radix);
        } catch (NumberFormatException e) {
            throw new ConversionException("无法解析为整数", e);
        }
    }

    public static String fromBase(String input, int radix) {
        try {
            BigInteger bi = new BigInteger(stripPrefix(input.trim(), radix), radix);
            return bi.toString(10);
        } catch (NumberFormatException e) {
            throw new ConversionException("无法以基数 " + radix + " 解析", e);
        }
    }

    private static BigInteger parseBigInteger(String s) {
        s = s.replace("_", "").replace(",", "");
        if (s.startsWith("0x") || s.startsWith("0X")) return new BigInteger(s.substring(2), 16);
        if (s.startsWith("0b") || s.startsWith("0B")) return new BigInteger(s.substring(2), 2);
        if (s.startsWith("0o") || s.startsWith("0O")) return new BigInteger(s.substring(2), 8);
        return new BigInteger(s);
    }

    private static String stripPrefix(String s, int radix) {
        s = s.replace("_", "").replace(",", "").replace(" ", "");
        if (radix == 16 && (s.startsWith("0x") || s.startsWith("0X"))) return s.substring(2);
        if (radix == 2 && (s.startsWith("0b") || s.startsWith("0B"))) return s.substring(2);
        if (radix == 8 && (s.startsWith("0o") || s.startsWith("0O"))) return s.substring(2);
        return s;
    }

    public static String toRoman(String input) {
        int n;
        try {
            n = Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            throw new ConversionException("罗马数字仅支持整数", e);
        }
        if (n <= 0 || n > 3999) throw new ConversionException("罗马数字范围 1-3999");
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            while (n >= values[i]) {
                sb.append(symbols[i]);
                n -= values[i];
            }
        }
        return sb.toString();
    }

    public static String fromRoman(String input) {
        String s = input.trim().toUpperCase();
        if (s.isEmpty()) throw new ConversionException("罗马数字为空");
        int total = 0;
        int prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int cur = romanValue(s.charAt(i));
            if (cur < prev) total -= cur;
            else total += cur;
            prev = cur;
        }
        return String.valueOf(total);
    }

    private static int romanValue(char c) {
        switch (c) {
            case 'I': return 1;
            case 'V': return 5;
            case 'X': return 10;
            case 'L': return 50;
            case 'C': return 100;
            case 'D': return 500;
            case 'M': return 1000;
            default: throw new ConversionException("非法的罗马字符: " + c);
        }
    }

    public static String numberToChinese(String input, boolean simplified) {
        long n;
        try {
            n = Long.parseLong(input.trim().replace("_", "").replace(",", ""));
        } catch (NumberFormatException e) {
            throw new ConversionException("无法解析为整数", e);
        }
        char[] digits = simplified
                ? new char[]{'零','一','二','三','四','五','六','七','八','九'}
                : new char[]{'零','壹','贰','叁','肆','伍','陆','柒','捌','玖'};
        String[] units = simplified
                ? new String[]{"", "十", "百", "千"}
                : new String[]{"", "拾", "佰", "仟"};
        String[] bigUnits = simplified
                ? new String[]{"", "万", "亿", "万亿"}
                : new String[]{"", "万", "亿", "万亿"};
        if (n == 0) return String.valueOf(digits[0]);
        boolean negative = n < 0;
        if (negative) n = -n;
        StringBuilder sb = new StringBuilder();
        int section = 0;
        boolean prevZero = false;
        boolean nonEmpty = false;
        while (n > 0) {
            int part = (int) (n % 10000);
            StringBuilder partSb = new StringBuilder();
            int divisor = 1000;
            int unit = 3;
            boolean partNonEmpty = false;
            boolean partPrevZero = false;
            while (divisor > 0) {
                int d = (part / divisor) % 10;
                if (d == 0) {
                    if (partNonEmpty) partPrevZero = true;
                } else {
                    if (partPrevZero) partSb.append(digits[0]);
                    partSb.append(digits[d]).append(units[unit]);
                    partPrevZero = false;
                    partNonEmpty = true;
                }
                divisor /= 10;
                unit--;
            }
            if (partNonEmpty) {
                if (prevZero && nonEmpty) sb.insert(0, digits[0]);
                sb.insert(0, partSb.toString() + bigUnits[section]);
                nonEmpty = true;
                prevZero = false;
            } else {
                if (nonEmpty) prevZero = true;
            }
            n /= 10000;
            section++;
        }
        if (negative) sb.insert(0, "负");
        return sb.toString();
    }
}
