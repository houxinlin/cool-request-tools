package dev.coolrequest.tool.wallhaven;

import com.google.gson.JsonObject;

public class Wallpaper {
    public String id;
    public String url;
    public String shortUrl;
    public String path;
    public String thumbSmall;
    public String thumbLarge;
    public String thumbOriginal;
    public String resolution;
    public String category;
    public String purity;
    public String fileType;
    public long fileSize;

    public static Wallpaper fromJson(JsonObject o) {
        Wallpaper w = new Wallpaper();
        w.id = optStr(o, "id");
        w.url = optStr(o, "url");
        w.shortUrl = optStr(o, "short_url");
        w.path = optStr(o, "path");
        w.resolution = optStr(o, "resolution");
        w.category = optStr(o, "category");
        w.purity = optStr(o, "purity");
        w.fileType = optStr(o, "file_type");
        if (o.has("file_size") && !o.get("file_size").isJsonNull()) w.fileSize = o.get("file_size").getAsLong();
        if (o.has("thumbs") && o.get("thumbs").isJsonObject()) {
            JsonObject t = o.getAsJsonObject("thumbs");
            w.thumbSmall = optStr(t, "small");
            w.thumbLarge = optStr(t, "large");
            w.thumbOriginal = optStr(t, "original");
        }
        return w;
    }

    private static String optStr(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    public String pickThumb() {
        if (thumbSmall != null) return thumbSmall;
        if (thumbOriginal != null) return thumbOriginal;
        return thumbLarge;
    }

    public String fileExtension() {
        if (fileType != null) {
            int slash = fileType.indexOf('/');
            if (slash >= 0) return fileType.substring(slash + 1);
        }
        if (path != null) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0) return path.substring(dot + 1);
        }
        return "jpg";
    }
}
