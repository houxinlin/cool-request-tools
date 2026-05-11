package dev.coolrequest.tool.converter.codec;

import dev.coolrequest.tool.converter.model.ConversionException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.CRC32;

public final class HashCodec {

    private HashCodec() {}

    public static String md5(String input) {
        return digest("MD5", input);
    }

    public static String sha1(String input) {
        return digest("SHA-1", input);
    }

    public static String sha224(String input) {
        return digest("SHA-224", input);
    }

    public static String sha256(String input) {
        return digest("SHA-256", input);
    }

    public static String sha384(String input) {
        return digest("SHA-384", input);
    }

    public static String sha512(String input) {
        return digest("SHA-512", input);
    }

    public static String crc32(String input) {
        CRC32 crc32 = new CRC32();
        crc32.update(input.getBytes(StandardCharsets.UTF_8));
        return String.format("%08x", crc32.getValue());
    }

    private static String digest(String algorithm, String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new ConversionException("哈希算法不可用: " + algorithm, e);
        }
    }
}
