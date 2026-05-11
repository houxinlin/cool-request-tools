package dev.coolrequest.tool.converter;

import dev.coolrequest.tool.converter.codec.CaseCodec;
import dev.coolrequest.tool.converter.codec.ColorCodec;
import dev.coolrequest.tool.converter.codec.HashCodec;
import dev.coolrequest.tool.converter.codec.MiscCodec;
import dev.coolrequest.tool.converter.codec.NumberCodec;
import dev.coolrequest.tool.converter.codec.StructuredCodec;
import dev.coolrequest.tool.converter.codec.TextCodec;
import dev.coolrequest.tool.converter.codec.TimeCodec;
import dev.coolrequest.tool.converter.model.Categories;
import dev.coolrequest.tool.converter.model.ConverterRegistry;
import dev.coolrequest.tool.converter.model.Format;

public final class ConverterFormats {

    public static final Format PLAIN_TEXT = new Format("text.plain", "原文本", Categories.TEXT, "原始文本");

    public static final Format URL_ENCODED = new Format("text.url", "URL 编码", Categories.TEXT, "百分号编码");
    public static final Format BASE64 = new Format("text.base64", "Base64", Categories.TEXT, "标准 Base64");
    public static final Format BASE64_URL = new Format("text.base64url", "Base64URL", Categories.TEXT, "URL 安全 Base64");
    public static final Format HEX_BYTES = new Format("text.hex", "Hex 字节", Categories.TEXT, "UTF-8 字节十六进制");
    public static final Format BINARY_BYTES = new Format("text.binary", "二进制字节", Categories.TEXT, "UTF-8 字节二进制");
    public static final Format ASCII_CODES = new Format("text.ascii", "ASCII 码点", Categories.TEXT, "字符 Unicode 码点");
    public static final Format UNICODE_ESCAPE = new Format("text.unicode", "Unicode 转义", Categories.TEXT, "\\uXXXX 风格");
    public static final Format HTML_ENTITY = new Format("text.html-entity", "HTML 实体", Categories.TEXT, "HTML 转义实体");
    public static final Format ROT13 = new Format("text.rot13", "ROT13", Categories.TEXT, "凯撒 13 位");
    public static final Format REVERSED = new Format("text.reverse", "反转字符串", Categories.TEXT, "字符顺序反转");
    public static final Format JAVA_STRING_ESCAPED = new Format("text.java-escape", "Java 字符串", Categories.TEXT, "Java 字面量转义");

    public static final Format MD5 = new Format("hash.md5", "MD5", Categories.HASH, "MD5 摘要");
    public static final Format SHA1 = new Format("hash.sha1", "SHA-1", Categories.HASH, "SHA-1 摘要");
    public static final Format SHA224 = new Format("hash.sha224", "SHA-224", Categories.HASH, "SHA-224 摘要");
    public static final Format SHA256 = new Format("hash.sha256", "SHA-256", Categories.HASH, "SHA-256 摘要");
    public static final Format SHA384 = new Format("hash.sha384", "SHA-384", Categories.HASH, "SHA-384 摘要");
    public static final Format SHA512 = new Format("hash.sha512", "SHA-512", Categories.HASH, "SHA-512 摘要");
    public static final Format CRC32 = new Format("hash.crc32", "CRC32", Categories.HASH, "CRC32 校验");

    public static final Format DEC_NUMBER = new Format("num.dec", "十进制", Categories.NUMBER, "十进制整数");
    public static final Format HEX_NUMBER = new Format("num.hex", "十六进制", Categories.NUMBER, "16 进制整数");
    public static final Format OCT_NUMBER = new Format("num.oct", "八进制", Categories.NUMBER, "8 进制整数");
    public static final Format BIN_NUMBER = new Format("num.bin", "二进制", Categories.NUMBER, "2 进制整数");
    public static final Format BASE36_NUMBER = new Format("num.base36", "Base36", Categories.NUMBER, "36 进制整数");
    public static final Format ROMAN_NUMBER = new Format("num.roman", "罗马数字", Categories.NUMBER, "1-3999 范围");
    public static final Format CHINESE_LOWER = new Format("num.zh-lower", "中文小写", Categories.NUMBER, "一二三 风格");
    public static final Format CHINESE_UPPER = new Format("num.zh-upper", "中文大写", Categories.NUMBER, "壹贰叁 风格");

    public static final Format TIMESTAMP_S = new Format("time.s", "时间戳（秒）", Categories.TIME, "Unix 秒");
    public static final Format TIMESTAMP_MS = new Format("time.ms", "时间戳（毫秒）", Categories.TIME, "Unix 毫秒");
    public static final Format DATETIME = new Format("time.datetime", "日期时间", Categories.TIME, "yyyy-MM-dd HH:mm:ss");
    public static final Format ISO8601 = new Format("time.iso", "ISO-8601 (UTC)", Categories.TIME, "ISO 标准 UTC");

    public static final Format COLOR_HEX = new Format("color.hex", "Hex 颜色", Categories.COLOR, "#RRGGBB");
    public static final Format COLOR_RGB = new Format("color.rgb", "RGB 颜色", Categories.COLOR, "rgb(R,G,B)");
    public static final Format COLOR_HSL = new Format("color.hsl", "HSL 颜色", Categories.COLOR, "hsl(H,S%,L%)");
    public static final Format COLOR_HSV = new Format("color.hsv", "HSV 颜色", Categories.COLOR, "hsv(H,S%,V%)");
    public static final Format COLOR_CMYK = new Format("color.cmyk", "CMYK 颜色", Categories.COLOR, "cmyk(C,M,Y,K)");

    public static final Format CASE_UPPER = new Format("case.upper", "全大写", Categories.CASE, "ABC");
    public static final Format CASE_LOWER = new Format("case.lower", "全小写", Categories.CASE, "abc");
    public static final Format CASE_CAMEL = new Format("case.camel", "camelCase", Categories.CASE, "驼峰，首字母小写");
    public static final Format CASE_PASCAL = new Format("case.pascal", "PascalCase", Categories.CASE, "驼峰，首字母大写");
    public static final Format CASE_SNAKE = new Format("case.snake", "snake_case", Categories.CASE, "下划线小写");
    public static final Format CASE_SCREAM = new Format("case.scream", "SCREAMING_SNAKE", Categories.CASE, "下划线大写");
    public static final Format CASE_KEBAB = new Format("case.kebab", "kebab-case", Categories.CASE, "中划线小写");
    public static final Format CASE_DOT = new Format("case.dot", "dot.case", Categories.CASE, "点分小写");
    public static final Format CASE_TITLE = new Format("case.title", "Title Case", Categories.CASE, "首字母大写");
    public static final Format CASE_SWAP = new Format("case.swap", "大小写互换", Categories.CASE, "swapCase");

    public static final Format JSON_PRETTY = new Format("struct.json-pretty", "JSON（美化）", Categories.STRUCTURED, "缩进 JSON");
    public static final Format JSON_COMPACT = new Format("struct.json-compact", "JSON（压缩）", Categories.STRUCTURED, "紧凑 JSON");
    public static final Format YAML = new Format("struct.yaml", "YAML", Categories.STRUCTURED, "YAML 格式");
    public static final Format XML = new Format("struct.xml", "XML", Categories.STRUCTURED, "XML 格式");
    public static final Format QUERY_STRING = new Format("struct.query", "Query String", Categories.STRUCTURED, "URL 查询串");
    public static final Format CSV = new Format("struct.csv", "CSV", Categories.STRUCTURED, "逗号分隔");

    public static final Format MORSE = new Format("misc.morse", "摩尔斯码", Categories.MISC, "国际摩尔斯电码");
    public static final Format UUID_DASHED = new Format("misc.uuid-dashed", "UUID（带横线）", Categories.MISC, "标准 UUID");
    public static final Format UUID_PLAIN = new Format("misc.uuid-plain", "UUID（无横线）", Categories.MISC, "32 位十六进制");
    public static final Format JWT_DECODED = new Format("misc.jwt", "JWT 解析", Categories.MISC, "Header & Payload");
    public static final Format STRIPPED_WS = new Format("misc.no-ws", "去除空白", Categories.MISC, "删除所有空白");
    public static final Format STRIPPED_BLANK_LINES = new Format("misc.no-blank-lines", "去除空行", Categories.MISC, "删除空白行");

    public static void register(ConverterRegistry registry) {
        registerTextEncodings(registry);
        registerHashes(registry);
        registerNumbers(registry);
        registerTimes(registry);
        registerColors(registry);
        registerCases(registry);
        registerStructured(registry);
        registerMisc(registry);
    }

    private static void registerTextEncodings(ConverterRegistry r) {
        r.registerConverter(PLAIN_TEXT, URL_ENCODED, TextCodec::urlEncode);
        r.registerConverter(URL_ENCODED, PLAIN_TEXT, TextCodec::urlDecode);
        r.registerConverter(PLAIN_TEXT, BASE64, TextCodec::base64Encode);
        r.registerConverter(BASE64, PLAIN_TEXT, TextCodec::base64Decode);
        r.registerConverter(PLAIN_TEXT, BASE64_URL, TextCodec::base64UrlEncode);
        r.registerConverter(BASE64_URL, PLAIN_TEXT, TextCodec::base64UrlDecode);
        r.registerConverter(PLAIN_TEXT, HEX_BYTES, TextCodec::hexBytesEncode);
        r.registerConverter(HEX_BYTES, PLAIN_TEXT, TextCodec::hexBytesDecode);
        r.registerConverter(PLAIN_TEXT, BINARY_BYTES, TextCodec::binaryBytesEncode);
        r.registerConverter(BINARY_BYTES, PLAIN_TEXT, TextCodec::binaryBytesDecode);
        r.registerConverter(PLAIN_TEXT, ASCII_CODES, TextCodec::asciiCodesEncode);
        r.registerConverter(ASCII_CODES, PLAIN_TEXT, TextCodec::asciiCodesDecode);
        r.registerConverter(PLAIN_TEXT, UNICODE_ESCAPE, TextCodec::unicodeEscape);
        r.registerConverter(UNICODE_ESCAPE, PLAIN_TEXT, TextCodec::unicodeUnescape);
        r.registerConverter(PLAIN_TEXT, HTML_ENTITY, TextCodec::htmlEntityEncode);
        r.registerConverter(HTML_ENTITY, PLAIN_TEXT, TextCodec::htmlEntityDecode);
        r.registerConverter(PLAIN_TEXT, ROT13, TextCodec::rot13);
        r.registerConverter(ROT13, PLAIN_TEXT, TextCodec::rot13);
        r.registerConverter(PLAIN_TEXT, REVERSED, TextCodec::reverse);
        r.registerConverter(REVERSED, PLAIN_TEXT, TextCodec::reverse);
        r.registerConverter(PLAIN_TEXT, JAVA_STRING_ESCAPED, MiscCodec::escapeJavaString);
        r.registerConverter(JAVA_STRING_ESCAPED, PLAIN_TEXT, MiscCodec::unescapeJavaString);
    }

    private static void registerHashes(ConverterRegistry r) {
        r.registerConverter(PLAIN_TEXT, MD5, HashCodec::md5);
        r.registerConverter(PLAIN_TEXT, SHA1, HashCodec::sha1);
        r.registerConverter(PLAIN_TEXT, SHA224, HashCodec::sha224);
        r.registerConverter(PLAIN_TEXT, SHA256, HashCodec::sha256);
        r.registerConverter(PLAIN_TEXT, SHA384, HashCodec::sha384);
        r.registerConverter(PLAIN_TEXT, SHA512, HashCodec::sha512);
        r.registerConverter(PLAIN_TEXT, CRC32, HashCodec::crc32);
    }

    private static void registerNumbers(ConverterRegistry r) {
        Format[] bases = {DEC_NUMBER, HEX_NUMBER, OCT_NUMBER, BIN_NUMBER, BASE36_NUMBER};
        int[] radixes = {10, 16, 8, 2, 36};
        for (int i = 0; i < bases.length; i++) {
            for (int j = 0; j < bases.length; j++) {
                if (i == j) continue;
                final int fromR = radixes[i];
                final int toR = radixes[j];
                r.registerConverter(bases[i], bases[j],
                        input -> NumberCodec.toBase(NumberCodec.fromBase(input, fromR), toR));
            }
        }
        r.registerConverter(DEC_NUMBER, ROMAN_NUMBER, NumberCodec::toRoman);
        r.registerConverter(ROMAN_NUMBER, DEC_NUMBER, NumberCodec::fromRoman);
        r.registerConverter(DEC_NUMBER, CHINESE_LOWER, input -> NumberCodec.numberToChinese(input, true));
        r.registerConverter(DEC_NUMBER, CHINESE_UPPER, input -> NumberCodec.numberToChinese(input, false));
    }

    private static void registerTimes(ConverterRegistry r) {
        r.registerConverter(TIMESTAMP_S, DATETIME, TimeCodec::secondsToDateTime);
        r.registerConverter(DATETIME, TIMESTAMP_S, TimeCodec::dateTimeToSeconds);
        r.registerConverter(TIMESTAMP_MS, DATETIME, TimeCodec::millisecondsToDateTime);
        r.registerConverter(DATETIME, TIMESTAMP_MS, TimeCodec::dateTimeToMilliseconds);
        r.registerConverter(TIMESTAMP_S, ISO8601, TimeCodec::secondsToIso);
        r.registerConverter(ISO8601, TIMESTAMP_S, TimeCodec::isoToSeconds);
        r.registerConverter(TIMESTAMP_MS, ISO8601, TimeCodec::millisecondsToIso);
        r.registerConverter(ISO8601, TIMESTAMP_MS, TimeCodec::isoToMilliseconds);
        r.registerConverter(TIMESTAMP_S, TIMESTAMP_MS, TimeCodec::secondsToMilliseconds);
        r.registerConverter(TIMESTAMP_MS, TIMESTAMP_S, TimeCodec::millisecondsToSeconds);
        r.registerConverter(DATETIME, ISO8601, input -> TimeCodec.secondsToIso(TimeCodec.dateTimeToSeconds(input)));
        r.registerConverter(ISO8601, DATETIME, input -> TimeCodec.secondsToDateTime(TimeCodec.isoToSeconds(input)));
    }

    private static void registerColors(ConverterRegistry r) {
        r.registerConverter(COLOR_HEX, COLOR_RGB, ColorCodec::hexToRgb);
        r.registerConverter(COLOR_RGB, COLOR_HEX, ColorCodec::rgbToHex);
        r.registerConverter(COLOR_HEX, COLOR_HSL, ColorCodec::hexToHsl);
        r.registerConverter(COLOR_HSL, COLOR_HEX, ColorCodec::hslToHex);
        r.registerConverter(COLOR_RGB, COLOR_HSL, ColorCodec::rgbToHsl);
        r.registerConverter(COLOR_HSL, COLOR_RGB, ColorCodec::hslToRgb);
        r.registerConverter(COLOR_HEX, COLOR_HSV, ColorCodec::hexToHsv);
        r.registerConverter(COLOR_HEX, COLOR_CMYK, ColorCodec::hexToCmyk);
        r.registerConverter(COLOR_RGB, COLOR_HSV, input -> ColorCodec.hexToHsv(ColorCodec.rgbToHex(input)));
        r.registerConverter(COLOR_RGB, COLOR_CMYK, input -> ColorCodec.hexToCmyk(ColorCodec.rgbToHex(input)));
        r.registerConverter(COLOR_HSL, COLOR_HSV, input -> ColorCodec.hexToHsv(ColorCodec.hslToHex(input)));
        r.registerConverter(COLOR_HSL, COLOR_CMYK, input -> ColorCodec.hexToCmyk(ColorCodec.hslToHex(input)));
    }

    private static void registerCases(ConverterRegistry r) {
        r.registerConverter(PLAIN_TEXT, CASE_UPPER, CaseCodec::toUpper);
        r.registerConverter(PLAIN_TEXT, CASE_LOWER, CaseCodec::toLower);
        r.registerConverter(PLAIN_TEXT, CASE_CAMEL, CaseCodec::toCamelCase);
        r.registerConverter(PLAIN_TEXT, CASE_PASCAL, CaseCodec::toPascalCase);
        r.registerConverter(PLAIN_TEXT, CASE_SNAKE, CaseCodec::toSnakeCase);
        r.registerConverter(PLAIN_TEXT, CASE_SCREAM, CaseCodec::toUpperSnakeCase);
        r.registerConverter(PLAIN_TEXT, CASE_KEBAB, CaseCodec::toKebabCase);
        r.registerConverter(PLAIN_TEXT, CASE_DOT, CaseCodec::toDotCase);
        r.registerConverter(PLAIN_TEXT, CASE_TITLE, CaseCodec::toTitleCase);
        r.registerConverter(PLAIN_TEXT, CASE_SWAP, CaseCodec::swapCase);

        Format[] caseFmts = {CASE_UPPER, CASE_LOWER, CASE_CAMEL, CASE_PASCAL, CASE_SNAKE,
                CASE_SCREAM, CASE_KEBAB, CASE_DOT, CASE_TITLE};
        for (Format from : caseFmts) {
            for (Format to : caseFmts) {
                if (from.equals(to)) continue;
                r.registerConverter(from, to, caseDirectConverter(to));
            }
        }
    }

    private static dev.coolrequest.tool.converter.model.Converter caseDirectConverter(Format to) {
        if (to.equals(CASE_UPPER)) return CaseCodec::toUpper;
        if (to.equals(CASE_LOWER)) return CaseCodec::toLower;
        if (to.equals(CASE_CAMEL)) return CaseCodec::toCamelCase;
        if (to.equals(CASE_PASCAL)) return CaseCodec::toPascalCase;
        if (to.equals(CASE_SNAKE)) return CaseCodec::toSnakeCase;
        if (to.equals(CASE_SCREAM)) return CaseCodec::toUpperSnakeCase;
        if (to.equals(CASE_KEBAB)) return CaseCodec::toKebabCase;
        if (to.equals(CASE_DOT)) return CaseCodec::toDotCase;
        if (to.equals(CASE_TITLE)) return CaseCodec::toTitleCase;
        return input -> input;
    }

    private static void registerStructured(ConverterRegistry r) {
        r.registerConverter(JSON_PRETTY, JSON_COMPACT, StructuredCodec::compactJson);
        r.registerConverter(JSON_COMPACT, JSON_PRETTY, StructuredCodec::prettyJson);
        r.registerConverter(JSON_PRETTY, YAML, StructuredCodec::jsonToYaml);
        r.registerConverter(JSON_COMPACT, YAML, StructuredCodec::jsonToYaml);
        r.registerConverter(YAML, JSON_PRETTY, StructuredCodec::yamlToJson);
        r.registerConverter(YAML, JSON_COMPACT, input -> StructuredCodec.compactJson(StructuredCodec.yamlToJson(input)));
        r.registerConverter(JSON_PRETTY, XML, StructuredCodec::jsonToXml);
        r.registerConverter(JSON_COMPACT, XML, StructuredCodec::jsonToXml);
        r.registerConverter(YAML, XML, input -> StructuredCodec.jsonToXml(StructuredCodec.yamlToJson(input)));
        r.registerConverter(QUERY_STRING, JSON_PRETTY, StructuredCodec::queryToJson);
        r.registerConverter(JSON_PRETTY, QUERY_STRING, StructuredCodec::jsonToQuery);
        r.registerConverter(JSON_COMPACT, QUERY_STRING, StructuredCodec::jsonToQuery);
        r.registerConverter(QUERY_STRING, JSON_COMPACT, input -> StructuredCodec.compactJson(StructuredCodec.queryToJson(input)));
        r.registerConverter(CSV, JSON_PRETTY, StructuredCodec::csvToJson);
        r.registerConverter(CSV, JSON_COMPACT, input -> StructuredCodec.compactJson(StructuredCodec.csvToJson(input)));
        r.registerConverter(JSON_PRETTY, CSV, StructuredCodec::jsonToCsv);
        r.registerConverter(JSON_COMPACT, CSV, StructuredCodec::jsonToCsv);
    }

    private static void registerMisc(ConverterRegistry r) {
        r.registerConverter(PLAIN_TEXT, MORSE, MiscCodec::textToMorse);
        r.registerConverter(MORSE, PLAIN_TEXT, MiscCodec::morseToText);
        r.registerConverter(PLAIN_TEXT, UUID_DASHED, MiscCodec::randomUuid);
        r.registerConverter(UUID_DASHED, UUID_PLAIN, MiscCodec::uuidNoDashes);
        r.registerConverter(UUID_PLAIN, UUID_DASHED, MiscCodec::addDashesToUuid);
        r.registerConverter(PLAIN_TEXT, JWT_DECODED, MiscCodec::jwtDecode);
        r.registerConverter(PLAIN_TEXT, STRIPPED_WS, MiscCodec::stripWhitespace);
        r.registerConverter(PLAIN_TEXT, STRIPPED_BLANK_LINES, MiscCodec::stripBlankLines);
    }

    private ConverterFormats() {}
}
