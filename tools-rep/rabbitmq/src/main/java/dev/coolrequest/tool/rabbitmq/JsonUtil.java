package dev.coolrequest.tool.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具：对齐主插件（cool-request-vip）的做法——
 * 高亮用平台 JSON 插件的 {@code JsonFileType.INSTANCE}（主插件 CoolRequestEditorTextField 同款，
 * 工具 jar 走宿主插件类加载器，运行时可解析，实测主插件生产环境即如此工作）；
 * 格式化用 Jackson readValue + writerWithDefaultPrettyPrinter（主插件 ObjectMapperUtils.printer 同款）。
 */
public final class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private JsonUtil() {
    }

    /** 快速判断是否值得按 JSON 处理（首个非空白字符为 { 或 [）。 */
    public static boolean isJsonLike(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            return c == '{' || c == '[';
        }
        return false;
    }

    /**
     * 校验并格式化 JSON（与主插件 ObjectMapperUtils.printer 同实现）。
     * 非法时抛 IllegalArgumentException（Jackson 异常的 message 含出错位置）。
     */
    public static String prettyPrint(String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new IllegalArgumentException("内容为空，不是 JSON");
        }
        try {
            Object obj = MAPPER.readValue(json, Object.class);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.length() > 300) msg = msg.substring(0, 300);
            throw new IllegalArgumentException(msg == null ? "JSON 不合法" : msg, e);
        }
    }
}
