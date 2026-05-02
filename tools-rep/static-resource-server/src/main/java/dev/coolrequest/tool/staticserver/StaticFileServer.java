package dev.coolrequest.tool.staticserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * 简易静态资源 HTTP 服务，基于 JDK 自带 com.sun.net.httpserver。
 */
public class StaticFileServer {

    private static final Map<String, String> MIME_MAP = new HashMap<>();

    static {
        MIME_MAP.put("html", "text/html; charset=utf-8");
        MIME_MAP.put("htm", "text/html; charset=utf-8");
        MIME_MAP.put("css", "text/css; charset=utf-8");
        MIME_MAP.put("js", "application/javascript; charset=utf-8");
        MIME_MAP.put("json", "application/json; charset=utf-8");
        MIME_MAP.put("xml", "application/xml; charset=utf-8");
        MIME_MAP.put("txt", "text/plain; charset=utf-8");
        MIME_MAP.put("md", "text/plain; charset=utf-8");
        MIME_MAP.put("png", "image/png");
        MIME_MAP.put("jpg", "image/jpeg");
        MIME_MAP.put("jpeg", "image/jpeg");
        MIME_MAP.put("gif", "image/gif");
        MIME_MAP.put("svg", "image/svg+xml");
        MIME_MAP.put("ico", "image/x-icon");
        MIME_MAP.put("webp", "image/webp");
        MIME_MAP.put("pdf", "application/pdf");
        MIME_MAP.put("zip", "application/zip");
        MIME_MAP.put("apk", "application/vnd.android.package-archive");
        MIME_MAP.put("mp3", "audio/mpeg");
        MIME_MAP.put("mp4", "video/mp4");
        MIME_MAP.put("sha1", "text/plain");
    }

    private final int port;
    private final File rootDir;
    private final BiConsumer<String, String> logger; // (level, message)

    private HttpServer server;
    private volatile boolean running;

    public StaticFileServer(int port, File rootDir, BiConsumer<String, String> logger) {
        this.port = port;
        this.rootDir = rootDir;
        this.logger = logger;
    }

    public synchronized void start() throws IOException {
        if (running) return;
        if (!rootDir.isDirectory()) {
            throw new IOException("目录不存在: " + rootDir.getAbsolutePath());
        }
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new FileHandler());
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "static-server-" + port);
            t.setDaemon(true);
            return t;
        }));
        server.start();
        running = true;
        log("INFO", "服务启动: http://0.0.0.0:" + port + "  目录: " + rootDir.getAbsolutePath());
    }

    public synchronized void stop() {
        if (!running) return;
        try {
            server.stop(0);
        } finally {
            running = false;
            log("INFO", "服务已停止 (端口 " + port + ")");
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public File getRootDir() {
        return rootDir;
    }

    private void log(String level, String msg) {
        if (logger != null) logger.accept(level, msg);
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String rawPath = exchange.getRequestURI().getPath();
            String decodedPath;
            try {
                decodedPath = URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
            } catch (Exception e) {
                decodedPath = rawPath;
            }
            log("REQ", method + " " + decodedPath + "  <- " + exchange.getRemoteAddress());

            try {
                if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                    sendError(exchange, 405, "Method Not Allowed");
                    return;
                }
                Path target = resolveSafely(decodedPath);
                if (target == null) {
                    sendError(exchange, 403, "Forbidden");
                    return;
                }
                File f = target.toFile();
                if (!f.exists()) {
                    sendError(exchange, 404, "Not Found");
                    return;
                }
                if (f.isDirectory()) {
                    if (!rawPath.endsWith("/")) {
                        exchange.getResponseHeaders().add("Location", rawPath + "/");
                        exchange.sendResponseHeaders(301, -1);
                        return;
                    }
                    sendDirectoryListing(exchange, f, decodedPath);
                } else {
                    sendFile(exchange, f);
                }
            } catch (Exception ex) {
                log("ERR", "处理请求异常: " + ex.getMessage());
                try { sendError(exchange, 500, "Internal Server Error"); } catch (IOException ignore) {}
            } finally {
                exchange.close();
            }
        }
    }

    /** 防止路径穿越，将相对路径限定在 rootDir 内 */
    private Path resolveSafely(String urlPath) {
        String rel = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath;
        Path root = rootDir.toPath().toAbsolutePath().normalize();
        Path resolved = root.resolve(rel).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) return null;
        return resolved;
    }

    private void sendFile(HttpExchange ex, File file) throws IOException {
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        String mime = MIME_MAP.getOrDefault(ext, "application/octet-stream");

        ex.getResponseHeaders().add("Content-Type", mime);
        ex.getResponseHeaders().add("Content-Disposition",
                isInlineMime(mime) ? "inline" : "attachment; filename=\"" + file.getName() + "\"");
        long len = file.length();
        ex.sendResponseHeaders(200, len);
        if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) return;
        try (OutputStream os = ex.getResponseBody()) {
            Files.copy(file.toPath(), os);
        }
    }

    private boolean isInlineMime(String mime) {
        return mime.startsWith("text/") || mime.startsWith("image/")
                || mime.startsWith("video/") || mime.startsWith("audio/")
                || mime.equals("application/pdf")
                || mime.startsWith("application/json")
                || mime.startsWith("application/javascript");
    }

    private void sendDirectoryListing(HttpExchange ex, File dir, String displayPath) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            sendError(ex, 403, "无法读取目录");
            return;
        }
        Arrays.sort(files, Comparator
                .comparing((File f) -> !f.isDirectory())
                .thenComparing(f -> f.getName().toLowerCase()));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        StringBuilder rows = new StringBuilder();
        Path root = rootDir.toPath().toAbsolutePath().normalize();
        Path cur = dir.toPath().toAbsolutePath().normalize();
        if (!cur.equals(root)) {
            rows.append("<tr><td><a href=\"../\">../</a></td><td></td><td></td></tr>");
        }
        for (File f : files) {
            String n = f.getName();
            String display = escape(n + (f.isDirectory() ? "/" : ""));
            String link = URLEncoder.encode(n, StandardCharsets.UTF_8).replace("+", "%20")
                    + (f.isDirectory() ? "/" : "");
            String size = f.isDirectory() ? "" : humanSize(f.length());
            String mtime = sdf.format(new Date(f.lastModified()));
            rows.append("<tr><td><a href=\"").append(link).append("\">")
                    .append(display).append("</a></td><td>")
                    .append(size).append("</td><td>")
                    .append(mtime).append("</td></tr>");
        }

        String body = "<!DOCTYPE html>\n<html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escape(displayPath) + "</title>"
                + "<style>"
                + "body{font-family:-apple-system,\"Segoe UI\",sans-serif;max-width:780px;margin:24px auto;padding:0 16px;}"
                + "h1{font-size:18px;word-break:break-all;}"
                + "table{width:100%;border-collapse:collapse;}"
                + "th,td{text-align:left;padding:8px 6px;border-bottom:1px solid #eee;font-size:14px;}"
                + "a{color:#0a58ca;text-decoration:none;} a:hover{text-decoration:underline;}"
                + "td:nth-child(2){white-space:nowrap;color:#666;}"
                + "td:nth-child(3){white-space:nowrap;color:#999;font-size:12px;}"
                + "</style></head><body>"
                + "<h1>" + escape(displayPath) + "</h1>"
                + "<table><thead><tr><th>名称</th><th>大小</th><th>修改时间</th></tr></thead>"
                + "<tbody>" + rows + "</tbody></table>"
                + "</body></html>";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        if (!"HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private void sendError(HttpExchange ex, int code, String msg) throws IOException {
        byte[] bytes = ("<h1>" + code + " " + escape(msg) + "</h1>").getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String humanSize(long n) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double v = n;
        int i = 0;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        if (i == 0) return n + " B";
        return String.format("%.1f %s", v, units[i]);
    }
}
