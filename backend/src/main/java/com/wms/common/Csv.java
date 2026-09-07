package com.wms.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Minimal CSV (UTF-8 with BOM, Excel friendly) writer / reader. */
public final class Csv {
    private Csv() {
    }

    public static <T> ResponseEntity<byte[]> download(String fileName, String[] headers, List<T> rows, Function<T, Object[]> mapper) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendRow(sb, headers);
        for (T row : rows) {
            appendRow(sb, mapper.apply(row));
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (IOException e) {
            encoded = "export.csv";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static void appendRow(StringBuilder sb, Object[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cells[i] == null ? "" : String.valueOf(cells[i]);
            if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
                v = '"' + v.replace("\"", "\"\"") + '"';
            }
            sb.append(v);
        }
        sb.append("\r\n");
    }

    /** Parses CSV rows (handles quotes, strips BOM); first row is header. */
    public static List<String[]> read(InputStream in) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) {
                    first = false;
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                }
                if (line.trim().isEmpty()) {
                    continue;
                }
                rows.add(split(line));
            }
        }
        return rows;
    }

    private static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }
}
