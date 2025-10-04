package de.dion.httpserver.handlers;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import de.dion.httpserver.WebServer;

public class MainPage implements HttpHandler {

	private final int port;
	private boolean previewMedia;
	private String[] shareFolders = new String[0];
	private boolean showVideoThumbnails;

	public MainPage(int port, boolean previewMedia, boolean showVideoThumbnails, String[] shareFolders) {
		this.port = port;
		this.previewMedia = previewMedia;
		this.showVideoThumbnails = showVideoThumbnails;
		this.shareFolders = shareFolders;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
//		System.out.println("Ping von " + exchange.getLocalAddress().getAddress().toString());
		
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"de\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"utf-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
        sb.append("  <title>Simple HTTP Server</title>\n");
        
        sb.append("  <style>\n");
        sb.append("    :root{--bg:#0b1320;--card:#0f1724;--muted:#9aa4b2;--accent:#ff9900;--link:#00aaff;--ok:#00ff88}\n");
        sb.append("    body{background:var(--bg);color:#e6eef8;font-family:Segoe UI,Roboto,Arial,Helvetica,sans-serif;margin:0;padding:24px}\n");
        sb.append("    .wrap{max-width:1100px;margin:0 auto}\n");
        sb.append("    header{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:18px}\n");
        sb.append("    h1{margin:0;font-size:1.5rem;color:var(--accent)}\n");
        sb.append("    .meta{color:var(--muted);font-size:0.95rem}\n");
        sb.append("    .grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:14px;margin-top:18px}\n");
        sb.append("    .card{background:var(--card);border-radius:10px;padding:14px;box-shadow:0 6px 18px rgba(2,6,23,0.6);display:flex;flex-direction:column;gap:8px}\n");
        sb.append("    .card h3{margin:0;color:#cfe9ff;font-size:1rem}\n");
        sb.append("    .card p{margin:0;color:var(--muted);font-size:0.9rem}\n");
        sb.append("    .actions{margin-top:8px}\n");
        sb.append("    .btn{display:inline-block;padding:8px 12px;border-radius:8px;text-decoration:none;font-weight:700}\n");
        sb.append("    .btn-primary{background:linear-gradient(180deg,#07243a,#053049);color:var(--link)}\n");
        sb.append("    .btn-secondary{background:linear-gradient(180deg,#07220f,#05210b);color:var(--ok);margin-left:8px}\n");
        sb.append("    footer{margin-top:20px;color:var(--muted);font-size:0.85rem}\n");
        sb.append("    @media (max-width:600px){header{flex-direction:column;align-items:flex-start} .grid{grid-template-columns:1fr}}\n");
        sb.append("  </style>\n");
        
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("  <div class=\"wrap\">\n");
        sb.append("    <header>\n");
        sb.append("      <div>\n");
        sb.append("        <h1>Java Simple-HTTP-Server</h1>\n");
        sb.append("        <div class=\"meta\">Version " + WebServer.version + "</div>\n");
        sb.append("      </div>\n");
        sb.append("      <div class=\"meta\">Mounted at: <strong>" + escapeHtmlForSimplePage(getLocalAddressListing(exchange)) + "</strong></div>\n");
        sb.append("    </header>\n");
        
        // Grid with primary download area and shared folders
        sb.append("    <section class=\"grid\">\n");
        
        // Downloads card (keeps existing link to /dl)
        sb.append("      <article class=\"card\">\n");
        sb.append("        <h3>Downloads</h3>\n");
        sb.append("        <p>Zeigt Inhalte des internen Download-Ordners an.</p>\n            ");
        sb.append("        <div class=\"actions\">\n");
        sb.append("          <a class=\"btn btn-primary\" href=\"/dl\">Öffnen</a>\n");
        sb.append("        </div>\n");
        sb.append("      </article>\n");
        
        // External share folders (preserve exact links/paths as before)
        for(String path: shareFolders) {
        	sb.append("      <article class=\"card\">\n");
        	sb.append("        <h3>").append(escapeHtmlForSimplePage(path)).append("</h3>\n");
        	sb.append("        <p>Gegebenenfalls externes Verzeichnis — öffne um Dateien anzusehen.</p>\n");
        	sb.append("        <div class=\"actions\">\n");
        	sb.append("          <a class=\"btn btn-primary\" href=\"/" + path + "\">Öffnen</a>\n");
        	sb.append("        </div>\n");
        	sb.append("      </article>\n");
        }
        
        sb.append("    </section>\n");
        
        // Footer with dynamic settings info
        sb.append("    <footer>\n");
        sb.append("      <div>Server läuft auf Port: " + port + "</div>\n");
        if (previewMedia) {
        	sb.append("      <div style=\"margin-top:6px;color:var(--muted)\">Vorschau (Preview) ist <strong>aktiviert</strong> — Videos und Audio können im Browser abgespielt werden.</div>\n");
        } else {
        	sb.append("      <div style=\"margin-top:6px;color:var(--muted)\">Vorschau (Preview) ist <strong>deaktiviert</strong> — Dateien werden nur zum Herunterladen angeboten.</div>\n");
        }
        if (showVideoThumbnails) {
        	sb.append("      <div style=\"margin-top:6px;color:var(--muted)\">Video-Thumbnail-Generierung ist <strong>aktiviert</strong>. Beim ersten Laden kann die Seite verzögert erscheinen, während Thumbnails erzeugt werden.</div>\n");
        } else {
        	sb.append("      <div style=\"margin-top:6px;color:var(--muted)\">Video-Thumbnail-Generierung ist <strong>deaktiviert</strong>.</div>\n");
        }
        
        // show Config-Öffnen button only if request comes from local machine (loopback)
        if (OpenConfig.isLocalRequest(exchange)) {
        	sb.append("      <div style=\"margin-top:8px\"><a class=\"btn btn-secondary\" href=\"/open-config\">Config Öffnen</a></div>\n");
        }
        
        sb.append("    </footer>\n");
        sb.append("  </div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");
        
		String response = sb.toString();
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        // send correct byte-length (wichtig für UTF-8 / Umlaute)
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
        	os.write(bytes);
        }
		
	}
	
	private String getLocalAddressListing(HttpExchange exchange) {
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			return "localhost or " + localhost.getHostAddress();
		} catch (UnknownHostException e) {
			return "localhost";
		}
	}
	
	private String escapeHtmlForSimplePage(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
