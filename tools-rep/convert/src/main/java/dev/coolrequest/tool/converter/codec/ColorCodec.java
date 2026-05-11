package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

public final class ColorCodec {

    private ColorCodec() {}

    public static String hexToRgb(String input) {
        int[] rgb = parseHex(input);
        return String.format("rgb(%d, %d, %d)", rgb[0], rgb[1], rgb[2]);
    }

    public static String rgbToHex(String input) {
        int[] rgb = parseRgb(input);
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    public static String hexToHsl(String input) {
        int[] rgb = parseHex(input);
        return rgbToHslString(rgb[0], rgb[1], rgb[2]);
    }

    public static String hslToHex(String input) {
        int[] rgb = parseHsl(input);
        return String.format("#%02x%02x%02x", rgb[0], rgb[1], rgb[2]);
    }

    public static String rgbToHsl(String input) {
        int[] rgb = parseRgb(input);
        return rgbToHslString(rgb[0], rgb[1], rgb[2]);
    }

    public static String hslToRgb(String input) {
        int[] rgb = parseHsl(input);
        return String.format("rgb(%d, %d, %d)", rgb[0], rgb[1], rgb[2]);
    }

    public static String hexToHsv(String input) {
        int[] rgb = parseHex(input);
        return rgbToHsvString(rgb[0], rgb[1], rgb[2]);
    }

    public static String hexToCmyk(String input) {
        int[] rgb = parseHex(input);
        return rgbToCmykString(rgb[0], rgb[1], rgb[2]);
    }

    private static int[] parseHex(String input) {
        String s = input.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            char[] c = s.toCharArray();
            s = "" + c[0] + c[0] + c[1] + c[1] + c[2] + c[2];
        }
        if (s.length() != 6) throw new ConversionException("Hex颜色应为 #RGB 或 #RRGGBB");
        try {
            int r = Integer.parseInt(s.substring(0, 2), 16);
            int g = Integer.parseInt(s.substring(2, 4), 16);
            int b = Integer.parseInt(s.substring(4, 6), 16);
            return new int[]{r, g, b};
        } catch (NumberFormatException e) {
            throw new ConversionException("非法的Hex颜色", e);
        }
    }

    private static int[] parseRgb(String input) {
        String s = input.trim().toLowerCase().replace("rgba", "rgb");
        int l = s.indexOf('('), r = s.indexOf(')');
        if (l < 0 || r < 0) throw new ConversionException("RGB格式应为 rgb(R,G,B)");
        String[] parts = s.substring(l + 1, r).split(",");
        if (parts.length < 3) throw new ConversionException("RGB格式应为 rgb(R,G,B)");
        try {
            int R = clamp(Integer.parseInt(parts[0].trim()));
            int G = clamp(Integer.parseInt(parts[1].trim()));
            int B = clamp(Integer.parseInt(parts[2].trim()));
            return new int[]{R, G, B};
        } catch (NumberFormatException e) {
            throw new ConversionException("非法的RGB值", e);
        }
    }

    private static int[] parseHsl(String input) {
        String s = input.trim().toLowerCase().replace("hsla", "hsl");
        int l = s.indexOf('('), r = s.indexOf(')');
        if (l < 0 || r < 0) throw new ConversionException("HSL格式应为 hsl(H,S%,L%)");
        String[] parts = s.substring(l + 1, r).split(",");
        if (parts.length < 3) throw new ConversionException("HSL格式应为 hsl(H,S%,L%)");
        try {
            float h = Float.parseFloat(parts[0].trim());
            float sf = Float.parseFloat(parts[1].trim().replace("%", "")) / 100f;
            float lf = Float.parseFloat(parts[2].trim().replace("%", "")) / 100f;
            return hslToRgb(h, sf, lf);
        } catch (NumberFormatException e) {
            throw new ConversionException("非法的HSL值", e);
        }
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static String rgbToHslString(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float h, s, l = (max + min) / 2f;
        if (max == min) {
            h = s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == rf) h = (gf - bf) / d + (gf < bf ? 6 : 0);
            else if (max == gf) h = (bf - rf) / d + 2;
            else h = (rf - gf) / d + 4;
            h /= 6;
        }
        return String.format("hsl(%d, %d%%, %d%%)", Math.round(h * 360), Math.round(s * 100), Math.round(l * 100));
    }

    private static int[] hslToRgb(float h, float s, float l) {
        h = ((h % 360) + 360) % 360 / 360f;
        float r, g, b;
        if (s == 0) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hue2rgb(p, q, h + 1f / 3f);
            g = hue2rgb(p, q, h);
            b = hue2rgb(p, q, h - 1f / 3f);
        }
        return new int[]{Math.round(r * 255), Math.round(g * 255), Math.round(b * 255)};
    }

    private static float hue2rgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private static String rgbToHsvString(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float max = Math.max(rf, Math.max(gf, bf));
        float min = Math.min(rf, Math.min(gf, bf));
        float d = max - min;
        float h = 0, s = max == 0 ? 0 : d / max, v = max;
        if (d != 0) {
            if (max == rf) h = (gf - bf) / d + (gf < bf ? 6 : 0);
            else if (max == gf) h = (bf - rf) / d + 2;
            else h = (rf - gf) / d + 4;
            h /= 6;
        }
        return String.format("hsv(%d, %d%%, %d%%)", Math.round(h * 360), Math.round(s * 100), Math.round(v * 100));
    }

    private static String rgbToCmykString(int r, int g, int b) {
        float rf = r / 255f, gf = g / 255f, bf = b / 255f;
        float k = 1 - Math.max(rf, Math.max(gf, bf));
        if (k == 1) return "cmyk(0%, 0%, 0%, 100%)";
        float c = (1 - rf - k) / (1 - k);
        float m = (1 - gf - k) / (1 - k);
        float y = (1 - bf - k) / (1 - k);
        return String.format("cmyk(%d%%, %d%%, %d%%, %d%%)",
                Math.round(c * 100), Math.round(m * 100), Math.round(y * 100), Math.round(k * 100));
    }
}
