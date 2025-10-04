package de.dion.httpserver.handlers;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import de.dion.httpserver.ThumbnailManager;

public class FileHandler implements HttpHandler {
	
    private final File baseDir;
    private final boolean previewMedia;
    private final boolean showVideoThumbnails;
    private static ThumbnailManager thumpnailManager = null;
    public static int BUFFER_SIZE;

    /**
     * @param basePath     Pfad zum Verzeichnis, das serviert werden soll (kann absolut sein, z.B. "Z:\admin1\Music")
     * @param previewMedia Wenn true: UI zeigt "View" + ?preview=1 wird ausgewertet. Wenn false: keine Preview-Funktionalität.
     * @throws IOException wenn basePath nicht existiert oder nicht erreichbar ist
     */
    public FileHandler(String basePath, boolean previewMedia, boolean showVideoThumbnails, int thumbnailScale) throws IOException {
        File bd = new File(basePath);
        if (!bd.exists() || !bd.isDirectory()) {
            throw new IOException("Base path does not exist or is not a directory: " + basePath);
        }
        this.baseDir = bd.getCanonicalFile();
        this.previewMedia = previewMedia;
        this.showVideoThumbnails = showVideoThumbnails;
        
        if(thumpnailManager == null) {
        	thumpnailManager = new ThumbnailManager(thumbnailScale);
        }
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
        if (!requested.getPath().startsWith(baseDir.getPath()) || !requested.getCanonicalPath().startsWith(baseDir.getPath())) {
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

    private String makePreviewPage(String relUrl, String mimeType) throws UnsupportedEncodingException {
        // relUrl ist bereits ein vollständig encodeter Pfad inkl. contextPath, z.B. "/dl/sub/My%20Song.mp3"
        String rawUrl = relUrl + (relUrl.contains("?") ? "&" : "?") + "raw=1";
        StringBuilder sb = new StringBuilder();
        sb.append("\n<!doctype html><html><head><meta charset=\"utf-8\"><title>Preview</title>");
        sb.append("\n<style>body{background:#111827;color:#e6eef8;font-family:Segoe UI, Roboto, Arial;padding:18px;margin:0;}");
        sb.append("\n.wrap{max-width:1100px;margin:0 auto;}");
        sb.append("\n.top{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;}");
        sb.append("\na.btn{background:#0ea5a4;color:#022c2b;padding:8px 12px;border-radius:6px;text-decoration:none;margin-left:8px;display:inline-block;}");
        sb.append("\nh2{color:#ff9900;margin:0 0 8px 0;}");
        sb.append("\nvideo,audio{background:#000;border-radius:6px;display:block;width:100%;max-height:80vh;}");
        sb.append("\niframe{border-radius:6px;border:1px solid #222;}");
        sb.append("\n</style>");
        sb.append("\n</head><body><div class=\"wrap\">");
        sb.append("\n<div class=\"top\"><div><h2>Preview</h2><div style=\"color:#9ca3af;font-size:0.95rem;margin-top:6px;\">Preview für: ")
        .append(URLDecoder.decode(escapeHtml(relUrl), "UTF-8"))
        .append("</div></div>");
        sb.append("\n<div><a class=\"btn\" href=\"").append(relUrl).append("?download=1\">Download</a>");
        sb.append("\n</div></div>");

        if (mimeType.startsWith("video/")) {
            sb.append("\n<video controls preload=\"metadata\" id=\"mediaPlayer\">");
            sb.append("\n<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">");
            sb.append("\nIhr Browser unterstützt das Video-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>");
            sb.append("\n</video>");
            sb.append("\n<script>const vp=document.getElementById('mediaPlayer');vp.volume=localStorage.getItem('userVolume')?parseFloat(localStorage.getItem('userVolume')):0.2;vp.addEventListener('volumechange',function(){localStorage.setItem('userVolume',this.volume);});</script>");
        } else if (mimeType.startsWith("audio/")) {
        	sb.append("\n<audio controls preload=\"metadata\" id=\"mediaPlayer\" autoplay>");
            sb.append("\n<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">");
            sb.append("\nIhr Browser unterstützt das Audio-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>");
            sb.append("\n</audio>");
            sb.append("\n<script>const ap=document.getElementById('mediaPlayer');ap.volume=localStorage.getItem('userVolume')?parseFloat(localStorage.getItem('userVolume')):0.2;ap.addEventListener('volumechange',function(){localStorage.setItem('userVolume',this.volume);});</script>");
        } else if (mimeType.startsWith("image/")) {
            sb.append("\n<div style=\"display:flex;justify-content:center;\"><img src=\"").append(rawUrl).append("\" alt=\"image\" style=\"max-width:100%;height:auto;border-radius:8px;\"></div>");
        } else if (mimeType.equals("application/pdf")) {
            sb.append("\n<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:80vh;border:none;\"></iframe>");
        } else if (mimeType.startsWith("text/")) {
            sb.append("\n<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:80vh;border:none;background:#fff;color:#000;\"></iframe>");
        } else {
            sb.append("\n<p>Preview nicht verfügbar. <a href=\"").append(rawUrl).append("\">Datei öffnen</a></p>");
        }

        sb.append("\n</div></body></html>");
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
            System.out.println(2);
            os.flush();
        } catch (IOException ex) {
            // connection closed by client oder ähnliches -> nur loggen
            System.out.println(ex.getMessage());
        }
    }

    private String generateDirectoryListing(String contextPath, File dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("\n<!DOCTYPE html>");
        sb.append("\n<html lang=\"de\">");
        sb.append("\n<head>");
        sb.append("\n  <meta charset=\"utf-8\">");
        sb.append("\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        sb.append("\n  <title>Index of ").append(escapeHtml(getRelativePath(dir))).append("</title>");

        // CSS -- moderne, dunkle Listing-Page mit Buttons, Icons, Thumbs, Lightbox
        sb.append("\n  <style>");
        sb.append("\n    :root{--bg:#0b1320;--card:#0f1724;--muted:#9aa4b2;--accent:#ff9900;--link:#00aaff;--ok:#00ff88;}");
        sb.append("\n    body{background:var(--bg);color:#e6eef8;font-family:Segoe UI,Roboto,Arial,\"Helvetica Neue\",sans-serif;margin:0;padding:20px;}");
        sb.append("\n    .container{max-width:1100px;margin:0 auto;}");
        sb.append("\n    header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px;}");
        sb.append("\n    header h1{margin:0;font-size:1.4rem;color:var(--accent);}");
        sb.append("\n    .controls{display:flex;gap:8px;align-items:center;}");
        sb.append("\n    .search{padding:8px 12px;border-radius:8px;border:1px solid #1f2937;background:#0b1220;color:#e6eef8;min-width:180px;}");
        sb.append("\n    .breadcrumb{color:var(--muted);font-size:0.95rem;}");
        sb.append("\n    table{width:100%;border-collapse:collapse;background:var(--card);border-radius:8px;overflow:hidden;}");
        sb.append("\n    thead{background:#071124;color:var(--muted);}");
        sb.append("\n    th,td{padding:12px 14px;text-align:left;border-bottom:1px solid rgba(255,255,255,0.03);vertical-align:middle;}");
        sb.append("\n    th{font-size:0.9rem;cursor:pointer;user-select:none;}");
        sb.append("\n    tbody tr:hover{background:linear-gradient(90deg, rgba(255,255,255,0.02), transparent);}");
        sb.append("\n    .fname{display:flex;align-items:center;gap:12px;}");
        sb.append("\n    .icon{width:36px;height:36px;display:inline-flex;align-items:center;justify-content:center;border-radius:6px;background:#08111b;color:#9fb4c8;}");
        sb.append("\n    .thumb{width:86px;height:56px;object-fit:cover;border-radius:6px;border:1px solid rgba(255,255,255,0.04);box-shadow:0 2px 6px rgba(0,0,0,0.5);}");
        sb.append("\n    .name a{color:#ffffff;text-decoration:none;font-weight:600;}");
        sb.append("\n    .muted{color:var(--muted);font-size:0.95rem;}");
        sb.append("\n    .actions a{display:inline-block;padding:6px 10px;border-radius:6px;text-decoration:none;font-weight:600;margin-right:6px;}");
        sb.append("\n    .btn-download{background:linear-gradient(180deg,#07243a,#053049);color:var(--link);}");
        sb.append("\n    .btn-view{background:linear-gradient(180deg,#0b3a21,#08361b);color:var(--ok);}");
        sb.append("\n    .size{text-align:right;}");
        sb.append("\n    .date{width:220px;}");
        sb.append("\n    @media (max-width:800px){thead{display:none;}table,tbody,td,tr{display:block;width:100%;}td{box-sizing:border-box;padding:10px;}td:before{content:attr(data-label);display:block;font-weight:700;margin-bottom:6px;color:var(--muted);} .size{text-align:left;} .date{width:auto;} }");
        sb.append("\n    /* Lightbox */");
        sb.append("\n    .lightbox{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.85);align-items:center;justify-content:center;padding:20px;z-index:9999;}");
        sb.append("\n    .lightbox img{max-width:calc(100% - 40px);max-height:calc(100% - 40px);border-radius:8px;}");
        sb.append("\n    .lb-close{position:fixed;top:16px;right:20px;color:#fff;font-size:22px;cursor:pointer;}");
        sb.append("\n  </style>");

        sb.append("\n</head>");
        sb.append("\n<body>");
        sb.append("\n<div class=\"container\">");

        // Header + search + breadcrumb
        sb.append("\n<header>");
        sb.append("\n  <div>");
        sb.append("\n    <h1>Index of ").append(escapeHtml(getRelativePath(dir))).append("</h1>");
        sb.append("\n    <div class=\"breadcrumb\">Mounted at: ").append(escapeHtml(baseDir.getAbsolutePath())).append("</div>");
        sb.append("\n  </div>");
        sb.append("\n  <div class=\"controls\">");
        sb.append("\n    <input id=\"searchBox\" class=\"search\" placeholder=\"Filter Dateien (Name / Typ)...\">");
        sb.append("\n  </div>");
        sb.append("\n</header>");

        // Table header
        sb.append("\n<table id=\"fileTable\">");
        sb.append("\n  <thead><tr>");
        sb.append("\n    <th style=\"width:44px;padding-left:16px;\">&nbsp;</th>");
        sb.append("\n    <th data-col=\"name\">Name</th>");
        sb.append("\n    <th class=\"date\" data-col=\"date\">Last modified</th>");
        sb.append("\n    <th class=\"size\" data-col=\"size\">Size</th>");
        sb.append("\n    <th>Actions</th>");
        sb.append("\n  </tr></thead>");
        sb.append("\n  <tbody>");

        // Parent directory
        if (!dir.getCanonicalFile().equals(baseDir.getCanonicalFile())) {
            File parent = dir.getParentFile();
            String parentRel = getEncodedRelativePath(contextPath, parent);
            sb.append("\n    <tr data-name=\"..\" data-size=\"0\" data-date=\"0\">");
            sb.append("\n      <td><div class=\"icon\">&#x1F4C1;</div></td>");
            sb.append("\n      <td class=\"name\"><a href=\"").append(parentRel).append("/\" style=\"color:#cfe9ff;text-decoration:none;font-weight:700;\">Parent Directory</a></td>");
            sb.append("\n      <td class=\"date\">&nbsp;</td>");
            sb.append("\n      <td class=\"size\">&nbsp;</td>");
            sb.append("\n      <td class=\"actions\">&nbsp;</td>");
            sb.append("\n    </tr>");
        }

        DecimalFormat dFormater = new DecimalFormat("###,##0.0");
        dFormater.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.GERMAN));

        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            
            for (File f : files) {
                if (f.isHidden()) {
                    continue;
                }
                
                String displayName = f.getName();
                String relUrl = getEncodedRelativePath(contextPath, f);
                if (f.isDirectory()) {
                    sb.append("\n    <tr data-name=\"").append(escapeHtml(displayName.toLowerCase())).append("\" data-size=\"0\" data-date=\"").append(f.lastModified()).append("\">");
                    sb.append("\n      <td><div class=\"icon\">&#128193;</div></td>");
                    sb.append("\n      <td class=\"name\"><a href=\"").append(relUrl).append("/\" style=\"color:#cfe9ff;text-decoration:none;font-weight:700;\">" ).append(escapeHtml(displayName)).append("/</a></td>");
                    sb.append("\n      <td class=\"date\">&nbsp;</td>");
                    sb.append("\n      <td class=\"size\">&nbsp;</td>");
                    sb.append("\n      <td class=\"actions\">&nbsp;</td>");
                    sb.append("\n    </tr>");
                }
            }
            
            for (File f : files) {
                if (f.isHidden()) {
                    continue;
                }

                String displayName = f.getName();
                String relUrl = getEncodedRelativePath(contextPath, f);
                if (f.isFile()) {
                    String mimeType = Files.probeContentType(f.toPath());
                    if (mimeType == null) mimeType = "application/octet-stream";

                    // calculate size nicely
                    String unit = "KiB";
                    double size = f.length();
                    size = size / 1024.0;
                    if (size >= 1024) {
                        size = size / 1024.0;
                        unit = "MiB";
                    }
                    if (size >= 1024) {
                        size = size / 1024.0;
                        unit = "GiB";
                    }

                    sb.append("\n    <tr data-name=\"").append(escapeHtml(displayName.toLowerCase())).append("\" data-size=\"").append(f.length()).append("\" data-date=\"").append(f.lastModified()).append("\">");

                    // icon or thumbnail (for images + videos)
                    sb.append("\n      <td>");
                    try {
                        File thumbFile = null;
                        if (previewMedia && mimeType.startsWith("video/") && showVideoThumbnails) {
                            thumbFile = thumpnailManager.getOrCreateVideoThumbnail(f);
                        }

                        if (previewMedia && mimeType.startsWith("image/")) {
                            sb.append("\n<img class=\"thumb\" src=\"").append(relUrl).append("?raw=1\" alt=\"").append(escapeHtml(displayName)).append("\">");
                        } else if (thumbFile != null && thumbFile.exists()) {
                            String thumbRel = "/.thumbs/" + thumbFile.getName();
                            sb.append("\n<img class=\"thumb\" src=\"").append(thumbRel).append("?raw=1\" alt=\"").append(escapeHtml(displayName)).append("\">");
                        } else {
                        	String icon = "📂"; // generic file icon fallback
                            if (mimeType.startsWith("audio/")) icon = "\u266B"; // musical note
                            if (mimeType.startsWith("video/")) icon = "\u25B6"; // play
                            if (mimeType.startsWith("text/")) icon = "\uD83D\uDCC4";
                            if (mimeType.startsWith("application")) icon = "\u2699";
                            if (mimeType.contains("pdf")) icon = "\uD83D\uDCC4";
                            sb.append("\n<div class=\"icon\">" ).append(icon).append("</div>");
                        }
                    } catch (Exception e) {
                        sb.append("\n<div class=\"icon\">&#128196;</div>");
                    }
                    sb.append("\n</td>");

                    // name (with preview link if enabled)
                    sb.append("\n      <td class=\"name\">");
                    if (previewMedia && isPreviewable(mimeType)) {
                        sb.append("\n        <div class=\"fname\"><div class=\"name\"><a href=\"").append(relUrl).append("?preview=1\">" ).append(escapeHtml(displayName)).append("</a></div></div>");
                    } else {
                        sb.append("\n        <div class=\"fname\"><div class=\"name\">" ).append(escapeHtml(displayName)).append("</div></div>");
                    }
                    sb.append("\n      </td>");

                    sb.append("\n      <td class=\"date\">" ).append(new Date(f.lastModified()).toString()).append("</td>");
                    sb.append("\n      <td class=\"size\">" ).append(dFormater.format(size)).append(" ").append(unit).append("</td>");

                    // actions
                    sb.append("\n      <td class=\"actions\">");
                    if (previewMedia && isPreviewable(mimeType)) {
                        sb.append("\n        <a class=\"btn-view\" href=\"").append(relUrl).append("?preview=1\">View</a>");
                    }
                    sb.append("\n        <a class=\"btn-download\" href=\"").append(relUrl).append("?download=1\">Download</a>");
                    sb.append("\n      </td>");

                    sb.append("\n    </tr>");
                }
            }
        }

        sb.append("\n  </tbody>");
        sb.append("\n</table>");

        // Lightbox container (for images)
        sb.append("\n<div id=\"lightbox\" class=\"lightbox\" onclick=\"closeLB()\">");
        sb.append("\n  <span class=\"lb-close\" onclick=\"closeLB()\">✕</span>");
        sb.append("\n  <img id=\"lb-img\" src=\"\" alt=\"\">");
        sb.append("\n</div>");

        // JS: search + simple sort + lightbox
        sb.append("\n<script>");
        sb.append("\n(function(){");
        sb.append("\n  const search=document.getElementById('searchBox');");
        sb.append("\n  const tbody=document.querySelector('#fileTable tbody');");
        sb.append("\n  search.addEventListener('input',function(){");
        sb.append("\n    const q=this.value.trim().toLowerCase();");
        sb.append("\n    Array.from(tbody.rows).forEach(r=>{");
        sb.append("\n      const name=r.getAttribute('data-name')||'';");
        sb.append("\n      if(!q || name.indexOf(q)!==-1) r.style.display=''; else r.style.display='none';");
        sb.append("\n    });");
        sb.append("\n  });");

        sb.append("\n  // sort by clicking headers (name,size,date)");
        sb.append("\n  document.querySelectorAll('th[data-col]').forEach(th=>{");
        sb.append("\n    th.addEventListener('click',()=>{");
        sb.append("\n      const col=th.getAttribute('data-col');");
        sb.append("\n      const rows=Array.from(tbody.rows).filter(r=>r.style.display !== 'none');");
        sb.append("\n      const dir = th._dir = -(th._dir || -1); // toggle");
        sb.append("\n      rows.sort((a,b)=>{");
        sb.append("\n        const va = a.getAttribute('data-'+col)||'';");
        sb.append("\n        const vb = b.getAttribute('data-'+col)||'';");
        sb.append("\n        if(col==='name') return va.localeCompare(vb)*dir;");
        sb.append("\n        if(col==='size' || col==='date') return (parseFloat(va)||0) - (parseFloat(vb)||0) > 0 ? dir : -dir;");
        sb.append("\n        return 0;");
        sb.append("\n      });");
        sb.append("\n      rows.forEach(r=>tbody.appendChild(r));");
        sb.append("\n    });");
        sb.append("\n  });");

        if (previewMedia) {
            sb.append("\n  // lightbox handling for image thumbs");
            sb.append("\n  window.openLB = function(src){");
            sb.append("\n    const lb=document.getElementById('lightbox');");
        sb.append("\n    const img=document.getElementById('lb-img');");
            sb.append("\n    img.src=src; lb.style.display='flex';");
            sb.append("\n  }");
            sb.append("\n  window.closeLB = function(){document.getElementById('lightbox').style.display='none';document.getElementById('lb-img').src='';}");

            // attach click listeners on thumbs after DOM ready
            sb.append("\n  document.addEventListener('DOMContentLoaded',function(){");
            sb.append("\n    Array.from(document.querySelectorAll('.thumb')).forEach(t=>{t.style.cursor='zoom-in';t.addEventListener('click',function(e){e.preventDefault();openLB(this.src);});});");
            sb.append("\n  });");
        }

        sb.append("\n})();");
        sb.append("\n</script>");

        sb.append("\n</div>");
        sb.append("\n</body>");
        sb.append("\n</html>");

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
            sb.append("\n/");
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
