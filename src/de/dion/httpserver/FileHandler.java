package de.dion.httpserver;


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
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class FileHandler implements HttpHandler {
 private final File baseDir;
 private final boolean previewMedia;
 private static final int BUFFER_SIZE = 8192;

 /**
  * @param basePath     Pfad zum Verzeichnis, das serviert werden soll (kann absolut sein, z.B. "Z:\\admin1\\Music")
  * @param previewMedia Wenn true: UI zeigt "View" + ?preview=1 wird ausgewertet. Wenn false: keine Preview-Funktionalität.
  * @throws IOException wenn basePath nicht existiert oder nicht erreichbar ist
  */
 public FileHandler(String basePath, boolean previewMedia) throws IOException {
     File bd = new File(basePath);
     if (!bd.exists() || !bd.isDirectory()) {
         throw new IOException("Base path does not exist or is not a directory: " + basePath);
     }
     this.baseDir = bd.getCanonicalFile();
     this.previewMedia = previewMedia;
 }

 @Override
 public void handle(HttpExchange exchange) throws IOException {
     // Kontext-Pfad dynamisch aus dem HttpContext entnehmen (z.B. "/dl" oder "/music")
     String contextPath = exchange.getHttpContext().getPath(); // z.B. "/dl"
     URI requestUri = exchange.getRequestURI();
     String rawPath = requestUri.getPath(); // kompletter Pfad, z.B. "/dl/sub/file.mp4"
     String query = requestUri.getQuery();  // Query-String, null möglich

     // Sicherheits-Check: Pfad muss mit dem Context-Pfad beginnen
     if (!rawPath.startsWith(contextPath)) {
         send404(exchange);
         return;
     }

     // decodiere den Pfad hinter dem Kontext (segmentweise)
     String relativeEncoded = rawPath.substring(contextPath.length()); // z.B. "/sub/file.mp4" oder ""
     String relativeDecoded = URLDecoder.decode(relativeEncoded, "UTF-8");
     File requested = new File(baseDir, relativeDecoded).getCanonicalFile();

     // Schutz gegen Verzeichnis-Traversal: requested muss innerhalb baseDir liegen
     if (!requested.getPath().startsWith(baseDir.getPath())) {
         send404(exchange);
         return;
     }

     if (requested.isDirectory()) {
         String response = generateDirectoryListing(contextPath, requested);
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

         Map<String, String> params = parseQuery(query);
         boolean isPreviewRequest = previewMedia && params.containsKey("preview");
         boolean isRawRequest = params.containsKey("raw") && "1".equals(params.get("raw"));
         boolean isDownloadRequest = params.containsKey("download");

         // Preview page requested (only when previewMedia == true)
         if (isPreviewRequest && isPreviewable(mimeType)) {
             String relUrl = getEncodedRelativePath(contextPath, requested);
             String previewHtml = makePreviewPage(relUrl, mimeType);
             exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
             byte[] bytes = previewHtml.getBytes("UTF-8");
             exchange.sendResponseHeaders(200, bytes.length);
             try (OutputStream os = exchange.getResponseBody()) {
                 os.write(bytes);
             }
             return;
         }

         // Raw request from player/viewer (or fallback) -> serve inline with Range support
         if (isRawRequest || (isPreviewRequest && isPreviewable(mimeType))) {
             serveFileWithRange(exchange, requested, mimeType, true);
             return;
         }

         // Explicit download or default -> serve as attachment
         if (isDownloadRequest || !isPreviewRequest) {
             serveFileWithRange(exchange, requested, mimeType, false);
             return;
         }

         send404(exchange);
         return;
     } else {
         send404(exchange);
         return;
     }
 }

 // -------------------- Hilfsfunktionen --------------------

 private void send404(HttpExchange exchange) throws IOException {
     String response = "404 Not Found";
     exchange.sendResponseHeaders(404, response.length());
     try (OutputStream os = exchange.getResponseBody()) {
         os.write(response.getBytes());
     }
 }

 private boolean isPreviewable(String mimeType) {
     return mimeType.startsWith("video/")
             || mimeType.startsWith("audio/")
             || mimeType.startsWith("image/")
             || mimeType.equals("application/pdf")
             || mimeType.startsWith("text/");
 }

 private String makePreviewPage(String relUrl, String mimeType) {
     // relUrl ist bereits ein vollständig encodeter Pfad inkl. contextPath, z.B. "/dl/sub/My%20Song.mp3"
     String rawUrl = relUrl + (relUrl.contains("?") ? "&" : "?") + "raw=1";
     StringBuilder sb = new StringBuilder();
     sb.append("<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>Preview</title>");
     sb.append("<style>body{background:#303030;color:#fff;font-family:Arial;padding:16px;} .player{max-width:100%;}</style>");
     sb.append("</head><body>\n");
     sb.append("<h2 style=\"color:#ff9900\">Preview</h2>\n");

     if (mimeType.startsWith("video/")) {
    	    sb.append("<video class=\"player\" controls preload=\"metadata\" style=\"max-width:100%\" id=\"mediaPlayer\">")
    	      .append("<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">")
    	      .append("Ihr Browser unterstützt das Video-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>")
    	      .append("</video>\n")
    	      .append("<script>")
    	      .append("document.getElementById('mediaPlayer').volume = localStorage.getItem('userVolume') ? parseFloat(localStorage.getItem('userVolume')) : 0.2;")
    	      .append("document.getElementById('mediaPlayer').addEventListener('volumechange', function() {")
    	      .append("localStorage.setItem('userVolume', this.volume);")
    	      .append("});")
    	      .append("</script>\n");
    	} else if (mimeType.startsWith("audio/")) {
    	    sb.append("<audio controls preload=\"metadata\" id=\"mediaPlayer\">")
    	      .append("<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">")
    	      .append("Ihr Browser unterstützt das Audio-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>")
    	      .append("</audio>\n")
    	      .append("<script>")
    	      .append("document.getElementById('mediaPlayer').volume = localStorage.getItem('userVolume') ? parseFloat(localStorage.getItem('userVolume')) : 0.2;")
    	      .append("document.getElementById('mediaPlayer').addEventListener('volumechange', function() {")
    	      .append("localStorage.setItem('userVolume', this.volume);")
    	      .append("});")
    	      .append("</script>\n");
     } else if (mimeType.startsWith("image/")) {
         sb.append("<img src=\"").append(rawUrl).append("\" alt=\"image\" style=\"max-width:100%;height:auto;display:block;margin-top:8px;\">");
     } else if (mimeType.equals("application/pdf")) {
         sb.append("<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:90vh;border:none;\"></iframe>");
     } else if (mimeType.startsWith("text/")) {
         sb.append("<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:90vh;border:none;background:#fff;color:#000;\"></iframe>");
     } else {
         sb.append("<p>Preview nicht verfügbar. <a href=\"").append(rawUrl).append("\">Datei öffnen</a></p>");
     }

     sb.append("<p><a href=\"").append(relUrl).append("?download=1\" style=\"color:#00aaff;\">Download</a></p>");
     sb.append("</body></html>");
     return sb.toString();
 }

 private void serveFileWithRange(HttpExchange exchange, File file, String mimeType, boolean inline) throws IOException {
     long fileLength = file.length();
     String range = exchange.getRequestHeaders().getFirst("Range");
     long start = 0;
     long end = fileLength - 1;
     boolean isPartial = false;

     if (range != null && range.startsWith("bytes=")) {
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
                 exchange.sendResponseHeaders(416, -1);
                 return;
             }
             isPartial = true;
         } catch (NumberFormatException e) {
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
         while (toWrite > 0 && (read = raf.read(buffer, 0, (int) Math.min(buffer.length, toWrite))) != -1) {
             os.write(buffer, 0, read);
             toWrite -= read;
         }
         os.flush();
     } catch (IOException ex) {
         // connection closed by client oder ähnliches -> nur loggen
         System.out.println("Error while sending file: " + ex.getMessage());
     }
 }

 private String generateDirectoryListing(String contextPath, File dir) throws IOException {
     StringBuilder sb = new StringBuilder();
     sb.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
     sb.append("<html>\n");
     sb.append("<head>\n");
     sb.append("<meta charset=\"utf-8\">");
     sb.append("<title>Index of ").append(escapeHtml(getRelativePath(dir))).append("</title>\n");
     sb.append("</head>\n");
     sb.append("<body style =\"background-color:#303030;color:#fff;font-family:Arial;\">\n");
     sb.append("<h1 style=\"color:#ff9900\">Index of ").append(escapeHtml(getRelativePath(dir))).append("</h1>\n");
     sb.append("<table>\n");
     sb.append("<tr><th>Name</th><th>Last modified</th><th>Size</th><th>Actions</th></tr>\n");
     sb.append("<tr><th colspan=\"4\"><hr></th></tr>\n");

     // Parent directory (nur anzeigen, wenn nicht root)
     if (!dir.getCanonicalFile().equals(baseDir.getCanonicalFile())) {
         File parent = dir.getParentFile();
         String parentRel = getEncodedRelativePath(contextPath, parent);
         sb.append("<tr><td><a href=\"").append(parentRel).append("/\">Parent Directory</a></td><td>&nbsp;</td><td>&nbsp;</td><td>&nbsp;</td></tr>\n");
     }

     File[] files = dir.listFiles();
     if (files != null) {
         Arrays.sort(files);
         for (File f : files) {
             String name = f.getName();
             String relUrl = getEncodedRelativePath(contextPath, f);
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
                 sb.append("<td>");
                 sb.append("<a href=\"").append(relUrl).append("?download=1\" style=\"color:#00aaff;margin-right:10px;\">Download</a>");
                 if (previewMedia && isPreviewable(mimeType)) {
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

 // Gibt Pfad relativ zur baseDir zurück (z.B. "/sub/dir" oder "" wenn baseDir)
 private String getRelativePath(File f) throws IOException {
     String base = baseDir.getCanonicalPath();
     String path = f.getCanonicalPath();
     if (path.equals(base)) return "";
     String rel = path.substring(base.length());
     rel = rel.replace(File.separatorChar, '/');
     if (!rel.startsWith("/")) rel = "/" + rel;
     return rel;
 }

 // Encodiert jede Segment separat und hängt contextPath (z.B. "/dl") voran
 private String getEncodedRelativePath(String contextPath, File f) throws UnsupportedEncodingException, IOException {
     String rel = getRelativePath(f); // z.B. "/sub/file name.mp4"
     String[] segs = rel.split("/");
     StringBuilder sb = new StringBuilder();
     sb.append(contextPath); // z.B. "/dl"
     for (String s : segs) {
         if (s == null || s.isEmpty()) continue;
         sb.append("/");
         sb.append(URLEncoder.encode(s, "UTF-8").replace("+", "%20"));
     }
     return sb.toString();
 }

 private String escapeHtml(String s) {
     if (s == null) return "";
     return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
 }

 private Map<String, String> parseQuery(String query) {
     Map<String, String> map = new HashMap<>();
     if (query == null || query.isEmpty()) return map;
     String[] pairs = query.split("&");
     for (String p : pairs) {
         int idx = p.indexOf('=');
         if (idx >= 0) {
             String k = p.substring(0, idx);
             String v = p.substring(idx + 1);
             map.put(k, v);
         } else {
             map.put(p, "");
         }
     }
     return map;
 }
}
