package de.dion.httpserver.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

/**
 * UploadHandler
 *
 * - GET liefert ein Upload-Formular (gestylt analog zur MainPage)
 * - POST (multipart/form-data) speichert die Dateien in uploadDir
 *
 * Hinweis: Diese Implementation parst multipart-Formularinhalte in-memory
 * (liest request body komplett). Für sehr große Dateien empfehle ich Apache Commons FileUpload (streaming).
 */
public class UploadHandler implements HttpHandler {

    private final File uploadDir;

    /**
     * @param uploadDirPath Verzeichnis, in das die Dateien geschrieben werden sollen (muss existieren)
     * @throws IOException wenn das Verzeichnis nicht existiert oder nicht erreichbar ist
     */
    public UploadHandler(String uploadDirPath) throws IOException {
        if (uploadDirPath == null || uploadDirPath.isEmpty()) {
            throw new IllegalArgumentException("uploadDirPath darf nicht leer sein");
        }
        File d = new File(uploadDirPath);
        d.mkdirs();
        if (!d.exists() || !d.isDirectory()) {
            throw new IOException("Upload directory does not exist or is not a directory: " + uploadDirPath);
        }
        this.uploadDir = d.getCanonicalFile();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            serveForm(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            handleUpload(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void serveForm(HttpExchange exchange) throws IOException {
        String contextPath = exchange.getHttpContext().getPath(); // z.B. "/upload"
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"de\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"utf-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        sb.append("  <title>Upload - Simple HTTP Server</title>\n");
        sb.append("  <style>\n");
        sb.append("    :root{--bg:#0b1320;--card:#0f1724;--muted:#9aa4b2;--accent:#ff9900;--link:#00aaff;--ok:#00ff88}\n");
        sb.append("    body{background:var(--bg);color:#e6eef8;font-family:Segoe UI,Roboto,Arial,Helvetica,sans-serif;margin:0;padding:24px}\n");
        sb.append("    .wrap{max-width:900px;margin:0 auto}\n");
        sb.append("    header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:18px}\n");
        sb.append("    h1{margin:0;font-size:1.5rem;color:var(--accent)}\n");
        sb.append("    .meta{color:var(--muted);font-size:0.95rem}\n");
        sb.append("    .card{background:var(--card);border-radius:10px;padding:18px;box-shadow:0 6px 18px rgba(2,6,23,0.6);display:flex;flex-direction:column;gap:12px}\n");
        sb.append("    label{display:block;color:var(--muted);font-size:0.95rem;margin-bottom:8px}\n");
        sb.append("    input[type=file]{background:transparent;color:var(--muted)}\n");
        sb.append("    .actions{margin-top:8px}\n");
        sb.append("    .btn{display:inline-block;padding:8px 12px;border-radius:8px;text-decoration:none;font-weight:700;border:none;cursor:pointer}\n");
        sb.append("    .btn-primary{background:linear-gradient(180deg,#07243a,#053049);color:var(--link)}\n");
        sb.append("    .btn-secondary{background:linear-gradient(180deg,#07220f,#05210b);color:var(--ok);margin-left:8px}\n");
        sb.append("    .note{color:var(--muted);font-size:0.9rem}\n");
        sb.append("    .progress-wrap{background:#06101a;border-radius:8px;padding:10px;margin-top:10px}\n");
        sb.append("    .progress{width:100%;height:16px;background:#05121a;border-radius:8px;overflow:hidden;border:1px solid rgba(255,255,255,0.04)}\n");
        sb.append("    .progress > .bar{height:100%;width:0%;background:linear-gradient(90deg,#ff9900,#00aaff)}\n");
        sb.append("    .progress-info{display:flex;justify-content:space-between;font-size:0.85rem;color:var(--muted);margin-top:6px}\n");
        sb.append("    .result{margin-top:12px}\n");
        sb.append("    @media (max-width:600px){header{flex-direction:column;align-items:flex-start}}\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <div class=\"wrap\">\n");
        sb.append("    <header>\n");
        sb.append("      <div>\n");
        sb.append("        <h1>Datei hochladen</h1>\n");
        sb.append("        <div class=\"meta\">Zielordner: <strong>").append(escapeHtml(uploadDir.getAbsolutePath())).append("</strong></div>\n");
        sb.append("      </div>\n");
        sb.append("      <div class=\"meta\">Mounted at: <strong>").append(escapeHtml(getLocalAddressListing())).append("</strong></div>\n");
        sb.append("    </header>\n");

        sb.append("    <section class=\"card\">\n");
        sb.append("      <form id=\"uploadForm\" method=\"post\" enctype=\"multipart/form-data\">\n");
        sb.append("        <label for=\"file\">Datei(en) auswählen</label>\n");
        sb.append("        <input id=\"file\" name=\"file\" type=\"file\" multiple>\n");
        sb.append("        <div class=\"note\">Wähle eine oder mehrere Dateien. Dateinamen werden nicht verändert.</div>\n");
        sb.append("        <div class=\"actions\">\n");
        sb.append("          <button id=\"btnUpload\" class=\"btn btn-primary\" type=\"submit\">Hochladen</button>\n");
        sb.append("          <button id=\"btnCancel\" class=\"btn btn-secondary\" type=\"button\" style=\"display:none\">Abbrechen</button>\n");
        sb.append("          <a class=\"btn\" href=\"/\" style=\"text-decoration:none;background:linear-gradient(180deg,#07220f,#05210b);padding:8px 12px;border-radius:8px;color:var(--ok);margin-left:8px\">Zurück zur Hauptseite</a>\n");
        sb.append("        </div>\n");

        // progress UI
        sb.append("        <div class=\"progress-wrap\" id=\"progressWrap\" style=\"display:none\">\n");
        sb.append("          <div class=\"progress\"><div class=\"bar\" id=\"progressBar\"></div></div>\n");
        sb.append("          <div class=\"progress-info\"><span id=\"progressText\">0%</span><span id=\"speedText\"></span></div>\n");
        sb.append("        </div>\n");

        sb.append("      </form>\n");
        sb.append("      <div class=\"result\" id=\"result\"></div>\n");
        sb.append("    </section>\n");

        sb.append("    <footer style=\"margin-top:18px;color:var(--muted);font-size:0.9rem\">Server lädt Dateien in: <strong>")
          .append(escapeHtml(uploadDir.getAbsolutePath())).append("</strong></footer>\n");

        sb.append("  </div>\n");
        sb.append("  <script>\n");
        sb.append("    (function(){\n");
        sb.append("      const form = document.getElementById('uploadForm');\n");
        sb.append("      const fileInput = document.getElementById('file');\n");
        sb.append("      const btnUpload = document.getElementById('btnUpload');\n");
        sb.append("      const btnCancel = document.getElementById('btnCancel');\n");
        sb.append("      const progressWrap = document.getElementById('progressWrap');\n");
        sb.append("      const progressBar = document.getElementById('progressBar');\n");
        sb.append("      const progressText = document.getElementById('progressText');\n");
        sb.append("      const speedText = document.getElementById('speedText');\n");
        sb.append("      const resultDiv = document.getElementById('result');\n");
        sb.append("      let xhr = null;\n");
        sb.append("      let startTime = 0;\n");
        sb.append("      form.addEventListener('submit', function(ev){\n");
        sb.append("        ev.preventDefault();\n");
        sb.append("        if (!fileInput.files || fileInput.files.length === 0) {\n");
        sb.append("          resultDiv.innerHTML = '<div style=\"color:var(--muted)\">Keine Datei ausgewählt.</div>';\n");
        sb.append("          return;\n");
        sb.append("        }\n");
        sb.append("        btnUpload.disabled = true;\n");
        sb.append("        resultDiv.innerHTML = '';\n");
        sb.append("        progressBar.style.width = '0%';\n");
        sb.append("        progressText.textContent = '0%';\n");
        sb.append("        speedText.textContent = '';\n");
        sb.append("        progressWrap.style.display = 'block';\n");
        sb.append("        btnCancel.style.display = 'inline-block';\n");
        sb.append("\n");
        sb.append("        const fd = new FormData();\n");
        sb.append("        // append all files under same field name 'file'\n");
        sb.append("        for (let i=0;i<fileInput.files.length;i++) {\n");
        sb.append("          fd.append('file', fileInput.files[i]);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        xhr = new XMLHttpRequest();\n");
        sb.append("        xhr.open('POST', window.location.pathname, true);\n");
        sb.append("\n");
        sb.append("        xhr.upload.onprogress = function(evt) {\n");
        sb.append("          if (!evt.lengthComputable) return;\n");
        sb.append("          const percent = Math.round((evt.loaded / evt.total) * 100);\n");
        sb.append("          progressBar.style.width = percent + '%';\n");
        sb.append("          progressText.textContent = percent + '% (' + formatBytes(evt.loaded) + ' / ' + formatBytes(evt.total) + ')';\n");
        sb.append("          const elapsed = (performance.now() - startTime) / 1000; // seconds\n");
        sb.append("          if (elapsed > 0) {\n");
        sb.append("            const speed = evt.loaded / elapsed; // bytes/sec\n");
        sb.append("            speedText.textContent = formatBytes(speed) + '/s';\n");
        sb.append("          }\n");
        sb.append("        };\n");
        sb.append("\n");
        sb.append("        xhr.onload = function() {\n");
        sb.append("          btnUpload.disabled = false;\n");
        sb.append("          btnCancel.style.display = 'none';\n");
        sb.append("          try {\n");
        sb.append("            // show raw server HTML response\n");
        sb.append("            resultDiv.innerHTML = xhr.responseText;\n");
        sb.append("          } catch (e) {\n");
        sb.append("            resultDiv.textContent = 'Upload abgeschlossen.';\n");
        sb.append("          }\n");
        sb.append("        };\n");
        sb.append("        xhr.onerror = function() {\n");
        sb.append("          btnUpload.disabled = false;\n");
        sb.append("          btnCancel.style.display = 'none';\n");
        sb.append("          resultDiv.innerHTML = '<div style=\"color:#ff6b6b\">Fehler beim Hochladen.</div>';\n");
        sb.append("        };\n");
        sb.append("        xhr.onabort = function() {\n");
        sb.append("          btnUpload.disabled = false;\n");
        sb.append("          btnCancel.style.display = 'none';\n");
        sb.append("          resultDiv.innerHTML = '<div style=\"color:var(--muted)\">Upload abgebrochen.</div>';\n");
        sb.append("        };\n");
        sb.append("\n");
        sb.append("        btnCancel.onclick = function(){ if (xhr) xhr.abort(); };\n");
        sb.append("        startTime = performance.now();\n");
        sb.append("        xhr.send(fd);\n");
        sb.append("      });\n");
        sb.append("\n");
        sb.append("      function formatBytes(bytes) {\n");
        sb.append("        if (bytes === 0) return '0 B';\n    ");
        sb.append("        const k = 1024;\n");
        sb.append("        const sizes = ['B','KB','MB','GB','TB'];\n");
        sb.append("        const i = Math.floor(Math.log(bytes) / Math.log(k));\n");
        sb.append("        return (bytes / Math.pow(k, i)).toFixed( (i>0)?2:0 ) + ' ' + sizes[i];\n");
        sb.append("      }\n");
        sb.append("    })();\n");
        sb.append("  </script>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void handleUpload(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            sendPlainText(exchange, 400, "Bad Request: content-type must be multipart/form-data");
            return;
        }

        String boundary = getBoundary(contentType);
        if (boundary == null) {
            sendPlainText(exchange, 400, "Bad Request: boundary not found in Content-Type");
            return;
        }

        // read full body (in-memory). For production / large files -> use streaming parser (Apache Commons FileUpload).
        byte[] body = readAllBytes(exchange.getRequestBody());

        // Split parts by boundary
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<Integer> partPositions = findAll(body, boundaryBytes);

        if (partPositions.size() < 2) {
            sendPlainText(exchange, 400, "Bad Request: no multipart parts found");
            return;
        }

        List<String> savedFiles = new ArrayList<>();
        // iterate parts (between boundaries)
        for (int i = 0; i < partPositions.size() - 1; i++) {
            int start = partPositions.get(i) + boundaryBytes.length;
            // skip possible CRLF
            if (start + 2 <= body.length && body[start] == '\r' && body[start + 1] == '\n') {
                start += 2;
            } else if (start + 1 <= body.length && (body[start] == '\n')) {
                start += 1;
            }
            int end = partPositions.get(i + 1); // position of next boundary
            // content for this part is body[start .. end-1] (may end with \r\n)
            if (end - start <= 0) continue;

            // find header end (double CRLF)
            int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, start, end);
            if (headerEnd == -1) {
                headerEnd = indexOf(body, new byte[]{'\n', '\n'}, start, end);
                if (headerEnd == -1) {
                    continue; // ignore malformed part
                } else {
                    headerEnd += 2;
                }
            } else {
                headerEnd += 4;
            }

            // parse headers
            String headersStr = new String(body, start, headerEnd - start, StandardCharsets.ISO_8859_1);
            Map<String, String> hdrs = parsePartHeaders(headersStr);

            String disposition = hdrs.getOrDefault("Content-Disposition", "");
            // extract filename if present
            String filename = extractFileNameFromContentDisposition(disposition);
            if (filename == null || filename.isEmpty()) {
                // no filename -> skip (could be a simple form field)
                continue;
            }

            // sanitize filename (strip path components)
            filename = Paths.get(filename).getFileName().toString();

            // compute data start & end (strip final CRLF before boundary)
            int dataStart = headerEnd;
            int dataEnd = end;
            // trim trailing CRLF before boundary
            if (dataEnd - 2 >= dataStart && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            } else if (dataEnd - 1 >= dataStart && (body[dataEnd - 1] == '\n')) {
                dataEnd -= 1;
            }

            // write bytes to file
            File outFile = new File(uploadDir, filename).getCanonicalFile();
            // ensure we don't escape the uploadDir
            if (!outFile.getPath().startsWith(uploadDir.getPath()) || !outFile.getCanonicalPath().startsWith(uploadDir.getPath())) {
                // suspicious filename -> skip
                continue;
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(body, dataStart, dataEnd - dataStart);
                fos.flush();
            } catch (IOException ex) {
                System.err.println("Could not write uploaded file: " + ex.getMessage());
                continue;
            }
            savedFiles.add(outFile.getName());
        }

        // Build response HTML
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Upload Ergebnis</title>");
        sb.append("\n<style>:root{--bg:#0b1320;--card:#0f1724;--muted:#9aa4b2;--accent:#ff9900;--link:#00aaff;--ok:#00ff88}");
        sb.append("\nbody{background:var(--bg);color:#e6eef8;font-family:Segoe UI,Roboto,Arial,Helvetica,sans-serif;margin:0;padding:24px}.wrap{max-width:900px;margin:0 auto}.card{background:var(--card);border-radius:10px;padding:18px;margin-top:18px}</style>");
        sb.append("\n</head><body><div class=\"wrap\">");
        sb.append("\n<h1 style=\"color:var(--accent)\">Upload Ergebnis</h1>");
        sb.append("\n<div class=\"card\">");
        if (savedFiles.isEmpty()) {
            sb.append("\n<p class=\"note\">Keine Dateien hochgeladen oder Fehler beim Speichern.</p>");
        } else {
            sb.append("\n<p>Folgende Datei(en) wurden erfolgreich gespeichert in <strong>").append(escapeHtml(uploadDir.getAbsolutePath())).append("</strong>:</p>");
            sb.append("\n<ul>");
            for (String n : savedFiles) {
                sb.append("\n<li>").append(escapeHtml(n)).append("</li>");
            }
            sb.append("\n</ul>");
        }
        sb.append("\n<div style=\"margin-top:12px\"><a class=\"btn\" href=\"/upload\" style=\"text-decoration:none;background:linear-gradient(180deg,#07220f,#05210b);padding:8px 12px;border-radius:8px;color:var(--ok)\">Zurück</a></div>");
        sb.append("\n</div></div></body></html>");

        byte[] out = sb.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    // -------------------- Hilfsfunktionen --------------------

    private void sendPlainText(HttpExchange exchange, int code, String txt) throws IOException {
        byte[] b = txt.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(code, b.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(b);
        }
    }

    private String getLocalAddressListing() {
        try {
            InetAddress localhost = InetAddress.getLocalHost();
            return "localhost or " + localhost.getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String getBoundary(String contentType) {
        // Content-Type: multipart/form-data; boundary=----WebKitFormBoundaryabc123
        String[] parts = contentType.split(";");
        for (String p : parts) {
            p = p.trim();
            if (p.startsWith("boundary=")) {
                String b = p.substring("boundary=".length());
                if (b.startsWith("\"") && b.endsWith("\"") && b.length() > 1) {
                    b = b.substring(1, b.length() - 1);
                }
                return b;
            }
        }
        return null;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        // Java 9+ has InputStream.readAllBytes(); implement fallback for older versions:
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            baos.write(buffer, 0, n);
        }
        return baos.toByteArray();
    }

    // Finds all occurrences of pattern in data and returns list of starting indices
    private static List<Integer> findAll(byte[] data, byte[] pattern) {
        List<Integer> res = new ArrayList<>();
        int idx = 0;
        while (idx < data.length) {
            int pos = indexOf(data, pattern, idx, data.length);
            if (pos == -1) break;
            res.add(pos);
            idx = pos + pattern.length;
        }
        return res;
    }

    // indexOf for byte arrays in range [from, to)
    private static int indexOf(byte[] data, byte[] pattern, int from, int to) {
        if (pattern.length == 0) return from;
        outer:
        for (int i = from; i <= to - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    // convenience: search for small pattern
    private static int indexOf(byte[] data, byte[] pattern, int from, int to, boolean unused) {
        return indexOf(data, pattern, from, to);
    }

    // parse headers block into map (headerName -> headerValue)
    private static Map<String, String> parsePartHeaders(String headersBlock) {
        Map<String, String> map = new HashMap<>();
        String[] lines = headersBlock.split("\r\n|\n");
        for (String l : lines) {
            int idx = l.indexOf(':');
            if (idx > 0) {
                String name = l.substring(0, idx).trim();
                String val = l.substring(idx + 1).trim();
                map.put(name, val);
            }
        }
        return map;
    }

    // Extract filename from Content-Disposition header, e.g. form-data; name="file"; filename="mein.mp3"
    private static String extractFileNameFromContentDisposition(String cd) {
        if (cd == null) return null;
        String[] parts = cd.split(";");
        for (String p : parts) {
            p = p.trim();
            if (p.startsWith("filename=")) {
                String v = p.substring("filename=".length());
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
                return v;
            }
        }
        return null;
    }
}
