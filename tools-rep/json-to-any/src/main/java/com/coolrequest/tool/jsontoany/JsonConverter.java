package com.coolrequest.tool.jsontoany;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * Core conversion logic: JSON → SQL(INSERT/UPDATE) / CSV / Markdown / HTML / XML / LaTeX / MediaWiki / YAML
 */
public class JsonConverter {

    public enum Format {
        SQL, CSV, MARKDOWN, HTML, XML, LATEX, MEDIAWIKI, YAML
    }

    public enum SqlMode {
        INSERT, UPDATE, DELETE
    }

    // ── public entry points ──────────────────────────────────────────────

    /** For non-SQL formats */
    public String convert(String jsonText, Format format) throws Exception {
        return convert(jsonText, format, SqlMode.INSERT, Collections.emptyList());
    }

    /** Full entry */
    public String convert(String jsonText, Format format, SqlMode sqlMode, List<String> whereFields) throws Exception {
        jsonText = jsonText.trim();
        JSONArray arr;
        if (jsonText.startsWith("[")) {
            arr = new JSONArray(jsonText);
        } else if (jsonText.startsWith("{")) {
            arr = new JSONArray();
            arr.put(new JSONObject(jsonText));
        } else {
            throw new IllegalArgumentException("Input must be a JSON object or array");
        }
        if (arr.length() == 0) return "(empty array)";

        List<String> keys = extractKeys(arr);
        switch (format) {
            case SQL:       return toSQL(arr, keys, sqlMode, whereFields);
            case CSV:       return toCSV(arr, keys);
            case MARKDOWN:  return toMarkdown(arr, keys);
            case HTML:      return toHTML(arr, keys);
            case XML:       return toXML(arr, keys);
            case LATEX:     return toLaTeX(arr, keys);
            case MEDIAWIKI: return toMediaWiki(arr, keys);
            case YAML:      return toYAML(arr, keys);
            default:        throw new IllegalArgumentException("Unknown format: " + format);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private List<String> extractKeys(JSONArray arr) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            try {
                for (String k : arr.getJSONObject(i).keySet()) keys.add(k);
            } catch (JSONException ignored) {}
        }
        return new ArrayList<>(keys);
    }

    private String cellValue(JSONObject obj, String key) {
        if (!obj.has(key) || obj.isNull(key)) return "";
        return obj.get(key).toString();
    }

    private boolean isNumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        try { Double.parseDouble(s); return true; } catch (NumberFormatException e) { return false; }
    }

    private String sqlLiteral(String val) {
        if (isNumeric(val)) return val;
        return "'" + val.replace("'", "''") + "'";
    }

    // ── SQL ──────────────────────────────────────────────────────────────

    private String toSQL(JSONArray arr, List<String> keys, SqlMode mode, List<String> whereFields) {
        if (mode == SqlMode.INSERT) return toSqlInsert(arr, keys);
        if (mode == SqlMode.UPDATE) return toSqlUpdate(arr, keys, whereFields);
        return toSqlDelete(arr, keys, whereFields);
    }

    private String toSqlInsert(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        String cols = String.join(", ", keys);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("INSERT INTO data (").append(cols).append(") VALUES (");
            for (int j = 0; j < keys.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(sqlLiteral(cellValue(obj, keys.get(j))));
            }
            sb.append(");\n");
        }
        return sb.toString();
    }

    private String toSqlUpdate(JSONArray arr, List<String> keys, List<String> whereFields) {
        StringBuilder sb = new StringBuilder();
        List<String> setCols = new ArrayList<>(keys);
        if (!whereFields.isEmpty()) setCols.removeAll(whereFields);

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("UPDATE data SET ");
            boolean first = true;
            for (String k : setCols) {
                if (!first) sb.append(", ");
                sb.append(k).append(" = ").append(sqlLiteral(cellValue(obj, k)));
                first = false;
            }
            if (!whereFields.isEmpty()) {
                sb.append(" WHERE ");
                boolean wf = true;
                for (String k : whereFields) {
                    if (!wf) sb.append(" AND ");
                    sb.append(k).append(" = ").append(sqlLiteral(cellValue(obj, k)));
                    wf = false;
                }
            }
            sb.append(";\n");
        }
        return sb.toString();
    }

    private String toSqlDelete(JSONArray arr, List<String> keys, List<String> whereFields) {
        StringBuilder sb = new StringBuilder();
        // If no WHERE fields chosen, use all keys as condition to avoid blind DELETE
        List<String> condCols = whereFields.isEmpty() ? keys : whereFields;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("DELETE FROM data WHERE ");
            boolean first = true;
            for (String k : condCols) {
                if (!first) sb.append(" AND ");
                sb.append(k).append(" = ").append(sqlLiteral(cellValue(obj, k)));
                first = false;
            }
            sb.append(";\n");
        }
        return sb.toString();
    }

    // ── CSV ──────────────────────────────────────────────────────────────

    private String toCSV(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvRow(keys));
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            List<String> row = new ArrayList<>();
            for (String k : keys) row.add(cellValue(obj, k));
            sb.append(csvRow(row));
        }
        return sb.toString();
    }

    private String csvRow(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(escapeCsv(cells.get(i)));
        }
        return sb.append("\n").toString();
    }

    private String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ── Markdown ─────────────────────────────────────────────────────────

    private String toMarkdown(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", keys)).append(" |\n");
        sb.append("| ").append(String.join(" | ", Collections.nCopies(keys.size(), "---"))).append(" |\n");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("| ");
            for (int j = 0; j < keys.size(); j++) {
                if (j > 0) sb.append(" | ");
                sb.append(cellValue(obj, keys.get(j)).replace("|", "\\|"));
            }
            sb.append(" |\n");
        }
        return sb.toString();
    }

    // ── HTML ─────────────────────────────────────────────────────────────

    private String toHTML(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table border=\"1\" cellpadding=\"4\" cellspacing=\"0\">\n  <thead>\n    <tr>\n");
        for (String k : keys) sb.append("      <th>").append(escapeHtml(k)).append("</th>\n");
        sb.append("    </tr>\n  </thead>\n  <tbody>\n");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("    <tr>\n");
            for (String k : keys) sb.append("      <td>").append(escapeHtml(cellValue(obj, k))).append("</td>\n");
            sb.append("    </tr>\n");
        }
        return sb.append("  </tbody>\n</table>").toString();
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ── XML ──────────────────────────────────────────────────────────────

    private String toXML(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<dataset>\n");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("  <row>\n");
            for (String k : keys) {
                String tag = safeXmlTag(k);
                sb.append("    <").append(tag).append(">")
                  .append(escapeXml(cellValue(obj, k)))
                  .append("</").append(tag).append(">\n");
            }
            sb.append("  </row>\n");
        }
        return sb.append("</dataset>").toString();
    }

    private String safeXmlTag(String s) {
        s = s.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
        if (s.isEmpty() || Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ── LaTeX ─────────────────────────────────────────────────────────────

    private String toLaTeX(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        String cols = String.join(" | ", Collections.nCopies(keys.size(), "l"));
        sb.append("\\begin{tabular}{| ").append(cols).append(" |}\n\\hline\n");
        List<String> header = new ArrayList<>();
        for (String k : keys) header.add(escapeLatex(k));
        sb.append(String.join(" & ", header)).append(" \\\\\n\\hline\n");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            List<String> row = new ArrayList<>();
            for (String k : keys) row.add(escapeLatex(cellValue(obj, k)));
            sb.append(String.join(" & ", row)).append(" \\\\\n");
        }
        return sb.append("\\hline\n\\end{tabular}").toString();
    }

    private String escapeLatex(String s) {
        return s.replace("\\", "\\textbackslash{}")
                .replace("&", "\\&").replace("%", "\\%").replace("$", "\\$")
                .replace("#", "\\#").replace("_", "\\_").replace("{", "\\{")
                .replace("}", "\\}").replace("~", "\\textasciitilde{}")
                .replace("^", "\\textasciicircum{}");
    }

    // ── MediaWiki ────────────────────────────────────────────────────────

    private String toMediaWiki(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("{| class=\"wikitable\"\n|-\n");
        for (String k : keys) sb.append("! ").append(k).append("\n");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("|-\n");
            for (String k : keys) sb.append("| ").append(cellValue(obj, k)).append("\n");
        }
        return sb.append("|}").toString();
    }

    // ── YAML ─────────────────────────────────────────────────────────────

    private String toYAML(JSONArray arr, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj;
            try { obj = arr.getJSONObject(i); } catch (JSONException e) { continue; }
            sb.append("-\n");
            for (String k : keys) sb.append("  ").append(k).append(": ").append(yamlValue(cellValue(obj, k))).append("\n");
        }
        return sb.toString();
    }

    private String yamlValue(String val) {
        if (val.isEmpty()) return "\"\"";
        if (isNumeric(val) || val.equals("true") || val.equals("false") || val.equals("null")) return val;
        if (val.contains(":") || val.contains("#") || val.contains("\n") || val.startsWith(" ")) {
            return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return val;
    }
}
