package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TimeCodec {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeCodec() {}

    public static String secondsToDateTime(String input) {
        long seconds = parseLong(input);
        return Instant.ofEpochSecond(seconds).atZone(ZoneId.systemDefault())
                .toLocalDateTime().format(LOCAL);
    }

    public static String millisecondsToDateTime(String input) {
        long millis = parseLong(input);
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .toLocalDateTime().format(LOCAL);
    }

    public static String dateTimeToSeconds(String input) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(input.trim(), LOCAL);
            return String.valueOf(ldt.atZone(ZoneId.systemDefault()).toEpochSecond());
        } catch (DateTimeParseException e) {
            throw new ConversionException("日期格式应为 yyyy-MM-dd HH:mm:ss", e);
        }
    }

    public static String dateTimeToMilliseconds(String input) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(input.trim(), LOCAL);
            return String.valueOf(ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } catch (DateTimeParseException e) {
            throw new ConversionException("日期格式应为 yyyy-MM-dd HH:mm:ss", e);
        }
    }

    public static String secondsToIso(String input) {
        long seconds = parseLong(input);
        return Instant.ofEpochSecond(seconds).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static String isoToSeconds(String input) {
        try {
            return String.valueOf(Instant.parse(input.trim()).getEpochSecond());
        } catch (DateTimeParseException e) {
            throw new ConversionException("ISO-8601格式错误（如 2025-01-01T00:00:00Z）", e);
        }
    }

    public static String millisecondsToIso(String input) {
        long millis = parseLong(input);
        return Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public static String isoToMilliseconds(String input) {
        try {
            return String.valueOf(Instant.parse(input.trim()).toEpochMilli());
        } catch (DateTimeParseException e) {
            throw new ConversionException("ISO-8601格式错误（如 2025-01-01T00:00:00Z）", e);
        }
    }

    public static String secondsToMilliseconds(String input) {
        return String.valueOf(parseLong(input) * 1000L);
    }

    public static String millisecondsToSeconds(String input) {
        return String.valueOf(parseLong(input) / 1000L);
    }

    public static String now(boolean ms) {
        long t = System.currentTimeMillis();
        return String.valueOf(ms ? t : t / 1000L);
    }

    private static long parseLong(String input) {
        try {
            return Long.parseLong(input.trim());
        } catch (NumberFormatException e) {
            throw new ConversionException("无法解析为整数时间戳", e);
        }
    }
}
