package dev.coolrequest.tool.wallhaven;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WallhavenClient {

    private static final Logger LOG = Logger.getInstance(WallhavenClient.class);
    private static final String BASE = "https://wallhaven.cc/api/v1";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Gson gson = new Gson();

    public static class SearchQuery {
        public String q = "";
        public String categories = "111";
        public String purity = "100";
        public String sorting = "date_added";
        public String order = "desc";
        public String atleast = "";
        public String ratios = "";
        public int page = 1;
        public String apiKey = "";
    }

    public static class SearchResult {
        public final List<Wallpaper> data;
        public final int currentPage;
        public final int lastPage;

        public SearchResult(List<Wallpaper> data, int currentPage, int lastPage) {
            this.data = data;
            this.currentPage = currentPage;
            this.lastPage = lastPage;
        }
    }

    public SearchResult search(SearchQuery q) throws IOException, InterruptedException {
        Map<String, String> params = new LinkedHashMap<>();
        if (q.q != null && !q.q.isBlank()) params.put("q", q.q);
        params.put("categories", q.categories);
        params.put("purity", q.purity);
        params.put("sorting", q.sorting);
        params.put("order", q.order);
        if (q.atleast != null && !q.atleast.isBlank()) params.put("atleast", q.atleast);
        if (q.ratios != null && !q.ratios.isBlank()) params.put("ratios", q.ratios);
        params.put("page", String.valueOf(Math.max(1, q.page)));
        if (q.apiKey != null && !q.apiKey.isBlank()) params.put("apikey", q.apiKey);

        StringBuilder url = new StringBuilder(BASE).append("/search?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) url.append('&');
            url.append(e.getKey()).append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "CoolRequest-Wallhaven/1.0")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Wallhaven HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonObject root = gson.fromJson(resp.body(), JsonObject.class);
        List<Wallpaper> list = new ArrayList<>();
        if (root.has("data") && root.get("data").isJsonArray()) {
            root.getAsJsonArray("data").forEach(el -> list.add(Wallpaper.fromJson(el.getAsJsonObject())));
        }
        int cur = 1, last = 1;
        if (root.has("meta") && root.get("meta").isJsonObject()) {
            JsonObject m = root.getAsJsonObject("meta");
            if (m.has("current_page") && !m.get("current_page").isJsonNull()) cur = m.get("current_page").getAsInt();
            if (m.has("last_page") && !m.get("last_page").isJsonNull()) last = m.get("last_page").getAsInt();
        }
        return new SearchResult(list, cur, last);
    }

    public byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "CoolRequest-Wallhaven/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }
}
