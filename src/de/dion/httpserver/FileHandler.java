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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
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
        sb.append("<style>body{background:#111827;color:#e6eef8;font-family:Segoe UI, Roboto, Arial;padding:18px;margin:0;}\n");
        sb.append(".wrap{max-width:1100px;margin:0 auto;}\n");
        sb.append(".top{display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;}\n");
        sb.append("a.btn{background:#0ea5a4;color:#022c2b;padding:8px 12px;border-radius:6px;text-decoration:none;margin-left:8px;display:inline-block;}\n");
        sb.append("h2{color:#ff9900;margin:0 0 8px 0;}\n");
        sb.append("video,audio{background:#000;border-radius:6px;display:block;width:100%;max-height:80vh;}\n");
        sb.append("iframe{border-radius:6px;border:1px solid #222;}\n");
        sb.append("</style>");
        sb.append("</head><body><div class=\"wrap\">\n");
        sb.append("<div class=\"top\"><div><h2>Preview</h2><div style=\"color:#9ca3af;font-size:0.95rem;margin-top:6px;\">Preview für: ").append(escapeHtml(relUrl)).append("</div></div>");
        sb.append("<div><a class=\"btn\" href=\"").append(relUrl).append("?download=1\">Download</a>");
        sb.append("</div></div>\n");

        if (mimeType.startsWith("video/")) {
            sb.append("<video controls preload=\"metadata\" id=\"mediaPlayer\">\n");
            sb.append("<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">\n");
            sb.append("Ihr Browser unterstützt das Video-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>\n");
            sb.append("</video>\n");
            sb.append("<script>const vp=document.getElementById('mediaPlayer');vp.volume=localStorage.getItem('userVolume')?parseFloat(localStorage.getItem('userVolume')):0.2;vp.addEventListener('volumechange',function(){localStorage.setItem('userVolume',this.volume);});</script>\n");
        } else if (mimeType.startsWith("audio/")) {
            sb.append("<audio controls preload=\"metadata\" id=\"mediaPlayer\">\n");
            sb.append("<source src=\"").append(rawUrl).append("\" type=\"").append(mimeType).append("\">\n");
            sb.append("Ihr Browser unterstützt das Audio-Tag nicht. <a href=\"").append(rawUrl).append("\">Download</a>\n");
            sb.append("</audio>\n");
            sb.append("<script>const ap=document.getElementById('mediaPlayer');ap.volume=localStorage.getItem('userVolume')?parseFloat(localStorage.getItem('userVolume')):0.2;ap.addEventListener('volumechange',function(){localStorage.setItem('userVolume',this.volume);});</script>\n");
        } else if (mimeType.startsWith("image/")) {
            sb.append("<div style=\"display:flex;justify-content:center;\"><img src=\"").append(rawUrl).append("\" alt=\"image\" style=\"max-width:100%;height:auto;border-radius:8px;\"></div>\n");
        } else if (mimeType.equals("application/pdf")) {
            sb.append("<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:80vh;border:none;\"></iframe>\n");
        } else if (mimeType.startsWith("text/")) {
            sb.append("<iframe src=\"").append(rawUrl).append("\" style=\"width:100%;height:80vh;border:none;background:#fff;color:#000;\"></iframe>\n");
        } else {
            sb.append("<p>Preview nicht verfügbar. <a href=\"").append(rawUrl).append("\">Datei öffnen</a></p>\n");
        }

        sb.append("</div></body></html>");
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
            System.out.println(ex.getMessage());
        }
    }

    private String generateDirectoryListing(String contextPath, File dir) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"de\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"utf-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        sb.append("  <title>Index of ").append(escapeHtml(getRelativePath(dir))).append("</title>\n");

        // CSS -- moderne, dunkle Listing-Page mit Buttons, Icons, Thumbs, Lightbox
        sb.append("  <style>\n");
        sb.append("    :root{--bg:#0b1320;--card:#0f1724;--muted:#9aa4b2;--accent:#ff9900;--link:#00aaff;--ok:#00ff88;}\n");
        sb.append("    body{background:var(--bg);color:#e6eef8;font-family:Segoe UI,Roboto,Arial,\"Helvetica Neue\",sans-serif;margin:0;padding:20px;}\n");
        sb.append("    .container{max-width:1100px;margin:0 auto;}\n");
        sb.append("    header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:14px;}\n");
        sb.append("    header h1{margin:0;font-size:1.4rem;color:var(--accent);}\n");
        sb.append("    .controls{display:flex;gap:8px;align-items:center;}\n");
        sb.append("    .search{padding:8px 12px;border-radius:8px;border:1px solid #1f2937;background:#0b1220;color:#e6eef8;min-width:180px;}\n");
        sb.append("    .breadcrumb{color:var(--muted);font-size:0.95rem;}\n");
        sb.append("    table{width:100%;border-collapse:collapse;background:var(--card);border-radius:8px;overflow:hidden;}\n");
        sb.append("    thead{background:#071124;color:var(--muted);}\n");
        sb.append("    th,td{padding:12px 14px;text-align:left;border-bottom:1px solid rgba(255,255,255,0.03);vertical-align:middle;}\n");
        sb.append("    th{font-size:0.9rem;cursor:pointer;user-select:none;}\n");
        sb.append("    tbody tr:hover{background:linear-gradient(90deg, rgba(255,255,255,0.02), transparent);}\n");
        sb.append("    .fname{display:flex;align-items:center;gap:12px;}\n");
        sb.append("    .icon{width:36px;height:36px;display:inline-flex;align-items:center;justify-content:center;border-radius:6px;background:#08111b;color:#9fb4c8;}\n");
        sb.append("    .thumb{width:56px;height:40px;object-fit:cover;border-radius:6px;border:1px solid rgba(255,255,255,0.04);}\n");
        sb.append("    .name a{color:#ffffff;text-decoration:none;font-weight:600;}\n");
        sb.append("    .muted{color:var(--muted);font-size:0.95rem;}\n");
        sb.append("    .actions a{display:inline-block;padding:6px 10px;border-radius:6px;text-decoration:none;font-weight:600;margin-right:6px;}\n");
        sb.append("    .btn-download{background:linear-gradient(180deg,#07243a,#053049);color:var(--link);}\n");
        sb.append("    .btn-view{background:linear-gradient(180deg,#0b3a21,#08361b);color:var(--ok);}\n");
        sb.append("    .size{text-align:right;}\n");
        sb.append("    .date{width:220px;}\n");
        sb.append("    @media (max-width:800px){thead{display:none;}table,tbody,td,tr{display:block;width:100%;}td{box-sizing:border-box;padding:10px;}td:before{content:attr(data-label);display:block;font-weight:700;margin-bottom:6px;color:var(--muted);} .size{text-align:left;} .date{width:auto;} }");
        sb.append("    /* Lightbox */\n");
        sb.append("    .lightbox{display:none;position:fixed;inset:0;background:rgba(0,0,0,0.85);align-items:center;justify-content:center;padding:20px;z-index:9999;}\n");
        sb.append("    .lightbox img{max-width:calc(100% - 40px);max-height:calc(100% - 40px);border-radius:8px;}\n");
        sb.append("    .lb-close{position:fixed;top:16px;right:20px;color:#fff;font-size:22px;cursor:pointer;}\n");
        sb.append("  </style>\n");

        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div class=\"container\">\n");

        // Header + search + breadcrumb
        sb.append("<header>\n");
        sb.append("  <div>\n");
        sb.append("    <h1>Index of ").append(escapeHtml(getRelativePath(dir))).append("</h1>\n");
        sb.append("    <div class=\"breadcrumb\">Mounted at: ").append(escapeHtml(baseDir.getAbsolutePath())).append("</div>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"controls\">\n");
        sb.append("    <input id=\"searchBox\" class=\"search\" placeholder=\"Filter Dateien (Name / Typ)...\">\n");
        sb.append("  </div>\n");
        sb.append("</header>\n");

        // Table header
        sb.append("<table id=\"fileTable\">\n");
        sb.append("  <thead><tr>\n");
        sb.append("    <th style=\"width:44px;padding-left:16px;\">&nbsp;</th>\n");
        sb.append("    <th data-col=\"name\">Name</th>\n");
        sb.append("    <th class=\"date\" data-col=\"date\">Last modified</th>\n");
        sb.append("    <th class=\"size\" data-col=\"size\">Size</th>\n");
        sb.append("    <th>Actions</th>\n");
        sb.append("  </tr></thead>\n");
        sb.append("  <tbody>\n");

        // Parent directory
        if (!dir.getCanonicalFile().equals(baseDir.getCanonicalFile())) {
            File parent = dir.getParentFile();
            String parentRel = getEncodedRelativePath(contextPath, parent);
            sb.append("    <tr data-name=\"..\" data-size=\"0\" data-date=\"0\">\n");
            sb.append("      <td><div class=\"icon\">&#x1F4C1;</div></td>\n");
            sb.append("      <td class=\"name\"><a href=\"").append(parentRel).append("/\" style=\"color:#cfe9ff;text-decoration:none;font-weight:700;\">Parent Directory</a></td>\n");
            sb.append("      <td class=\"date\">&nbsp;</td>\n");
            sb.append("      <td class=\"size\">&nbsp;</td>\n");
            sb.append("      <td class=\"actions\">&nbsp;</td>\n");
            sb.append("    </tr>\n");
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
                if (displayName.length() > WebsiteServer.maximumFileNameLength) {
                    displayName = displayName.substring(0, WebsiteServer.maximumFileNameLength);
                }
                String relUrl = getEncodedRelativePath(contextPath, f);
                if (f.isDirectory()) {
                    sb.append("    <tr data-name=\"").append(escapeHtml(displayName.toLowerCase())).append("\" data-size=\"0\" data-date=\"").append(f.lastModified()).append("\">\n");
                    sb.append("      <td><div class=\"icon\">&#128193;</div></td>\n");
                    sb.append("      <td class=\"name\"><a href=\"").append(relUrl).append("/\" style=\"color:#cfe9ff;text-decoration:none;font-weight:700;\">" ).append(escapeHtml(displayName)).append("/</a></td>\n");
                    sb.append("      <td class=\"date\">&nbsp;</td>\n");
                    sb.append("      <td class=\"size\">&nbsp;</td>\n");
                    sb.append("      <td class=\"actions\">&nbsp;</td>\n");
                    sb.append("    </tr>\n");
                } else if (f.isFile()) {
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

                    sb.append("    <tr data-name=\"").append(escapeHtml(displayName.toLowerCase())).append("\" data-size=\"").append(f.length()).append("\" data-date=\"").append(f.lastModified()).append("\">\n");

                    // icon or thumbnail
                    sb.append("      <td>");
                    if (previewMedia && mimeType.startsWith("image/")) {
                        sb.append("<img class=\"thumb\" src=\"").append(relUrl).append("?raw=1\" alt=\"").append(escapeHtml(displayName)).append("\">\n");
                    } else {
                        String icon = "\u1F5CE"; // generic file icon fallback
                        if (mimeType.startsWith("audio/")) icon = "\u1F3B5"; // musical note
                        if (mimeType.startsWith("video/")) icon = "\u25B6"; // play
                        if (mimeType.startsWith("text/")) icon = "\u270D"; // pencil
                        if (mimeType.contains("pdf")) icon = "\uD83D\uDCC4"; // page
                        sb.append("<div class=\"icon\">" ).append(icon).append("</div>\n");
                    }
                    sb.append("</td>\n");

                    // name (with preview link if enabled)
                    sb.append("      <td class=\"name\">\n");
                    if (previewMedia && isPreviewable(mimeType)) {
                        sb.append("        <div class=\"fname\"><div class=\"name\"><a href=\"").append(relUrl).append("?preview=1\">" ).append(escapeHtml(displayName)).append("</a></div></div>\n");
                    } else {
                        sb.append("        <div class=\"fname\"><div class=\"name\">" ).append(escapeHtml(displayName)).append("</div></div>\n");
                    }
                    sb.append("      </td>\n");

                    sb.append("      <td class=\"date\">" ).append(new Date(f.lastModified()).toString()).append("</td>\n");
                    sb.append("      <td class=\"size\">" ).append(dFormater.format(size)).append(" ").append(unit).append("</td>\n");

                    // actions
                    sb.append("      <td class=\"actions\">\n");
                    if (previewMedia && isPreviewable(mimeType)) {
                        sb.append("        <a class=\"btn-view\" href=\"").append(relUrl).append("?preview=1\">View</a>");
                    }
                    sb.append("        <a class=\"btn-download\" href=\"").append(relUrl).append("?download=1\">Download</a>\n");
                    sb.append("      </td>\n");

                    sb.append("    </tr>\n");
                }
            }
        }

        sb.append("  </tbody>\n");
        sb.append("</table>\n");

        // Lightbox container (for images)
        sb.append("<div id=\"lightbox\" class=\"lightbox\" onclick=\"closeLB()\">\n");
        sb.append("  <span class=\"lb-close\" onclick=\"closeLB()\">✕</span>\n");
        sb.append("  <img id=\"lb-img\" src=\"\" alt=\"\">\n");
        sb.append("</div>\n");

        // JS: search + simple sort + lightbox
        sb.append("<script>\n");
        sb.append("(function(){\n");
        sb.append("  const search=document.getElementById('searchBox');\n");
        sb.append("  const tbody=document.querySelector('#fileTable tbody');\n");
        sb.append("  search.addEventListener('input',function(){\n");
        sb.append("    const q=this.value.trim().toLowerCase();\n");
        sb.append("    Array.from(tbody.rows).forEach(r=>{\n");
        sb.append("      const name=r.getAttribute('data-name')||'';\n");
        sb.append("      if(!q || name.indexOf(q)!==-1) r.style.display=''; else r.style.display='none';\n");
        sb.append("    });\n");
        sb.append("  });\n");

        sb.append("  // sort by clicking headers (name,size,date)\n");
        sb.append("  document.querySelectorAll('th[data-col]').forEach(th=>{\n");
        sb.append("    th.addEventListener('click',()=>{\n");
        sb.append("      const col=th.getAttribute('data-col');\n");
        sb.append("      const rows=Array.from(tbody.rows).filter(r=>r.style.display !== 'none');\n");
        sb.append("      const dir = th._dir = -(th._dir || -1); // toggle\n");
        sb.append("      rows.sort((a,b)=>{\n");
        sb.append("        const va = a.getAttribute('data-'+col)||'';\n");
        sb.append("        const vb = b.getAttribute('data-'+col)||'';\n");
        sb.append("        if(col==='name') return va.localeCompare(vb)*dir;\n");
        sb.append("        if(col==='size' || col==='date') return (parseFloat(va)||0) - (parseFloat(vb)||0) > 0 ? dir : -dir;\n");
        sb.append("        return 0;\n");
        sb.append("      });\n");
        sb.append("      rows.forEach(r=>tbody.appendChild(r));\n");
        sb.append("    });\n");
        sb.append("  });\n");

        if (previewMedia) {
            sb.append("  // lightbox handling for image thumbs\n");
            sb.append("  window.openLB = function(src){\n");
            sb.append("    const lb=document.getElementById('lightbox');\n");
            sb.append("    const img=document.getElementById('lb-img');\n");
            sb.append("    img.src=src; lb.style.display='flex';\n");
            sb.append("  }\n");
            sb.append("  window.closeLB = function(){document.getElementById('lightbox').style.display='none';document.getElementById('lb-img').src='';}\n");

            // attach click listeners on thumbs after DOM ready
            sb.append("  document.addEventListener('DOMContentLoaded',function(){\n");
            sb.append("    Array.from(document.querySelectorAll('.thumb')).forEach(t=>{t.style.cursor='zoom-in';t.addEventListener('click',function(e){e.preventDefault();openLB(this.src);});});\n");
            sb.append("  });\n");
        }

        sb.append("})();\n");
        sb.append("</script>\n");

        sb.append("</div>\n");
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
