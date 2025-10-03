package de.dion;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class MyHandler implements HttpHandler {
    private static final String BASE_DIRECTORY = "dl"; // Basisverzeichnis für die Dateilisten
    private static final int BUFFER_SIZE = 8192;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        System.out.println("Ping 2");
        File baseDir = new File(BASE_DIRECTORY).getCanonicalFile();

        URI requestUri = exchange.getRequestURI();
        String rawPath = requestUri.getPath(); // z.B. /dl/unter/Datei.mp4
        String query = requestUri.getQuery(); // z.B. preview=1 oder download=1 oder raw=1

        // wir erwarten Pfade die mit /dl beginnen
        if (!rawPath.startsWith("/dl")) {
            send404(exchange);
            return;
        }

        // decodiere den Pfad hinter /dl
        String relativeEncoded = rawPath.substring("/dl".length()); // enthält führenden '/'
        String relativeDecoded = URLDecoder.decode(relativeEncoded, "UTF-8"); // sicher decodieren
        File requested = new File(baseDir, relativeDecoded).getCanonicalFile();

        // Schutz gegen Verzeichnis-Traversal
        if (!requested.getPath().startsWith(baseDir.getPath())) {
            send404(exchange);
            return;
        }

        if (requested.isDirectory()) {
            String response = generateDirectoryListing(baseDir, requested);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            byte[] bytes = response.getBytes("UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            return;
        } else if (requested.isFile()) {
            String mimeType = Files.probeContentType(requested.toPath());
            if (mimeType == null) mimeType = "application/octet-stream";

            boolean preview = query != null && query.contains("preview");
            boolean download = query != null && query.contains("download");
            boolean raw = query != null && query.contains("raw=1");

            // Wenn preview angefordert -> liefere HTML-Wrapper (falls geeignet)
            if (preview && isPreviewable(mimeType)) {
                String previewHtml = makePreviewPage(requestUri.getPath(), mimeType);
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                byte[] bytes = previewHtml.getBytes("UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
                return;
            }

            // Wenn raw (von Player) oder (preview==true but non-wrapper case) -> serve file inline w/ Range support
            if (raw || (preview && isPreviewable(mimeType))) {
                serveFileWithRange(exchange, requested, mimeType, /*inline=*/ true);
                return;
            }

            // Wenn explizit download oder default -> attachment
            if (download || !preview) {
                serveFileWithRange(exchange, requested, mimeType, /*inline=*/ false);
                return;
            }

            // fallback
            send404(exchange);
            return;
        } else {
            send404(exchange);
        }
    }

    private void send404(HttpExchange exchange) throws IOException {
        String response = "404 Not Found";
        exchange.sendResponseHeaders(404, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }

    private boolean isPreviewable(String mimeType) {
        // erweiterbar
        return mimeType.startsWith("video/")
                || mimeType.startsWith("audio/")
                || mimeType.startsWith("image/")
                || mimeType.equals("application/pdf")
                || mimeType.startsWith("text/");
    }

    private String makePreviewPage(String requestPath, String mimeType) throws UnsupportedEncodingException {
        // requestPath ist z.B. /dl/Name%20mit%20Leerzeichen.mp4 (oder ohne Encodierung, je nach environment)
        // Wir bauen den raw-URL: requestPath + "?raw=1"
        String rawUrl = requestPath + (requestPath.contains("?") ? "&" : "?") + "raw=1";
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>Preview</title>");
        sb.append("<style>body{background:#303030;color:#fff;font-family:Arial;padding:16px;} .player{max-width:100%;}</style>");
        sb.append("</head><body>\n");
        sb.append("<h2 style=\"color:#ff9900\">Preview: ").append(escapeHtml(requestPath)).append("</h2>\n");

        if (mimeType.startsWith("video/")) {
            sb.append("<video class=\"player\" controls preload=\"metadata\" style=\"max-width:100%\">")
              .append("<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">")
              .append("Ihr Browser unterstützt das Video-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>")
              .append("</video>\n");
        } else if (mimeType.startsWith("audio/")) {
            sb.append("<audio controls preload=\"metadata\">")
              .append("<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">")
              .append("Ihr Browser unterstützt das Audio-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>")
              .append("</audio>\n");
        } else if (mimeType.startsWith("image/")) {
            sb.append("<img src=\"").append(rawUrl).append("\" alt=\"image\" style=\"max-width:100%;height:auto;display:block;margin-top:8px;\">");
        } else if (mimeType.equals("application/pdf")) {
            sb.append("<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:90vh;border:none;\"></iframe>");
        } else if (mimeType.startsWith("text/")) {
            sb.append("<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:90vh;border:none;background:#fff;color:#000;\"></iframe>");
        } else {
            sb.append("<p>Preview nicht verfügbar. <a href=\"").append(rawUrl).append("\">Datei öffnen</a></p>");
        }

        sb.append("<p><a href=\"").append(requestPath).append("?download=1\" style=\"color:#00aaff;\">Download</a></p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void serveFileWithRange(HttpExchange exchange, File file, String mimeType, boolean inline) throws IOException {
        long fileLength = file.length();
        String range = exchange.getRequestHeaders().getFirst("Range");
        long start = 0;
        long end = fileLength - 1;
        boolean isPartial = false;

        if (range != null && range.startsWith("bytes=")) {
            // nur einfaches "bytes=start-end" handling
            String[] parts = range.substring("bytes=".length()).split("-", 2);
            try {
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0].trim());
                }
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1].trim());
                }
                if (start < 0) start = 0;
                if (end > fileLength - 1) end = fileLength - 1;
                if (start > end) {
                    exchange.getResponseHeaders().set("Content-Range", "bytes */" + fileLength);
                    exchange.sendResponseHeaders(416, -1); // Requested Range Not Satisfiable
                    return;
                }
                isPartial = true;
            } catch (NumberFormatException e) {
                // ignorieren -> sende komplette Datei
                start = 0;
                end = fileLength - 1;
                isPartial = false;
            }
        }

        long contentLength = end - start + 1;

        if (inline) {
            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + file.getName() + "\"");
        } else {
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
        }
        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

        if (isPartial) {
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            exchange.sendResponseHeaders(206, contentLength);
        } else {
            exchange.sendResponseHeaders(200, fileLength);
        }

        try (OutputStream os = exchange.getResponseBody();
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(start);
            byte[] buffer = new byte[BUFFER_SIZE];
            long toWrite = contentLength;
            int read;
            while (toWrite > 0 && (read = raf.read(buffer, 0, (int)Math.min(buffer.length, toWrite))) != -1) {
                os.write(buffer, 0, read);
                toWrite -= read;
            }
            os.flush();
        } catch (IOException ex) {
            // connection broken etc. -> nur loggen
            System.err.println("Error while sending file: " + ex.getMessage());
        }
    }

    private String generateDirectoryListing(File baseDir, File dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<title>Index of /dl").append(escapeHtml(getRelativePath(baseDir, dir))).append("</title>\n");
        sb.append("</head>\n");
        sb.append("<body style =\"background-color:#303030;color:#fff;font-family:Arial;\">\n");
        sb.append("<h1 style=\"color:#ff9900\">Index of /dl").append(escapeHtml(getRelativePath(baseDir, dir))).append("</h1>\n");
        sb.append("<table>\n");
        sb.append("<tr><th>Name</th><th>Last modified</th><th>Size</th><th>Actions</th></tr>\n");
        sb.append("<tr><th colspan=\"4\"><hr></th></tr>\n");

        // Parent directory
        if (!dir.getCanonicalFile().equals(baseDir.getCanonicalFile())) {
            File parent = dir.getParentFile();
            String parentRel = getEncodedRelativePath(baseDir, parent);
            sb.append("<tr><td><a href=\"").append(parentRel).append("\">Parent Directory</a></td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>\n");
        }

        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                String name = f.getName();
                String relUrl = getEncodedRelativePath(baseDir, f); // z.B. /dl/sub/Datei.mp4 (encoded per segment)
                if (f.isDirectory()) {
                    sb.append("<tr><td>").append("<a href=\"").append(relUrl).append("/\">").append(escapeHtml(name)).append("/</a></td>");
                    sb.append("<td>").append("&nbsp;").append("</td><td>").append("&nbsp;</td><td>").append("&nbsp;</td></tr>\n");
                } else if (f.isFile()) {
                    String mimeType = Files.probeContentType(f.toPath());
                    if (mimeType == null) mimeType = "application/octet-stream";
                    sb.append("<tr>");
                    sb.append("<td>").append(escapeHtml(name)).append("</td>");
                    sb.append("<td>").append(new Date(f.lastModified()).toString()).append("</td>");
                    sb.append("<td>").append(f.length()).append("</td>");
                    // Actions: Download + optional Preview
                    sb.append("<td>");
                    sb.append("<a href=\"").append(relUrl).append("?download=1\" style=\"color:#00aaff;margin-right:10px;\">Download</a>");
                    if (isPreviewable(mimeType)) {
                        sb.append("<a href=\"").append(relUrl).append("?preview=1\" style=\"color:#00ff88;\">View</a>");
                    }
                    sb.append("</td>");
                    sb.append("</tr>\n");
                }
            }
        }

        sb.append("</table>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }

    // Helfer: gibt Pfad relativ zur baseDir zurück, z.B. "/ /sub/..." oder "" für base
    private String getRelativePath(File baseDir, File f) throws IOException {
        String base = baseDir.getCanonicalPath();
        String path = f.getCanonicalPath();
        if (path.equals(base)) return "";
        String rel = path.substring(base.length());
        rel = rel.replace(File.separatorChar, '/');
        if (!rel.startsWith("/")) rel = "/" + rel;
        return rel;
    }

    // Encodiert jede Segment separat (damit / erhalten bleibt)
    private String getEncodedRelativePath(File baseDir, File f) throws UnsupportedEncodingException, IOException {
        String rel = getRelativePath(baseDir, f);
        // split by '/', encode each non-empty segment
        String[] segs = rel.split("/");
        StringBuilder sb = new StringBuilder();
        sb.append("/dl"); // immer mit /dl beginnen
        for (String s : segs) {
            if (s == null || s.isEmpty()) continue;
            sb.append("/");
            sb.append(URLEncoder.encode(s, "UTF-8").replace("+", "%20"));
        }
        // falls Datei war das ok. Falls directory caller add trailing slash in listing
        return sb.toString();
    }
}
