package de.dion.httpserver;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import de.dion.SimpleHttpServerMain;

public class WebsiteServer {

	public static final String version = "1.1";
	private int port;
	private boolean previewMedia;
	private String[] shareFolders = new String[0];
	private boolean generateVideoThumbnails;
	private HttpServer server;
	
	public WebsiteServer() {
		init();
	}
	
	private void init() {
		port = SimpleHttpServerMain.config.getIntValue("Port");
		FileHandler.BUFFER_SIZE = SimpleHttpServerMain.config.getIntValue("Download-Buffersize");
		previewMedia = SimpleHttpServerMain.config.getBooleanValue("Preview-Media");
		generateVideoThumbnails = SimpleHttpServerMain.config.getBooleanValue("Generate-VideoThumbnails");
		
		String folders = SimpleHttpServerMain.config.getValue("Share-Folders").trim();
		if(folders.endsWith(";")) {
			folders = folders.substring(0, folders.length() - 1);
		}
		if(!folders.isEmpty()) {
			shareFolders = folders.split(";");
			for (int i = 0; i < shareFolders.length; i++) {
				String path = shareFolders[i];
				path = path.replace("\\", "/");
				if(path.startsWith("/")) {
					path = path.substring(1);
				}
				shareFolders[i] = path;
			}
		}
	}
	
	public void start() {
		System.out.println("Starte HTTP Server auf Port " + port);
    	
    	try {
    		
    		server = HttpServer.create(new InetSocketAddress(port), 0);
    		
    		addFileHandlers();
    		server.setExecutor(null); // creates a default executor
    		server.createContext("/", new HttpHandler() {
    			
    			@Override
    			public void handle(HttpExchange exchange) throws IOException {
    				System.out.println("Ping von " + exchange.getLocalAddress().getAddress().toString());
    				
    	            StringBuilder sb = new StringBuilder();
    	            sb.append("<!DOCTYPE html>\n");
    	            sb.append("<html lang=\"de\">\n");
    	            sb.append("<head>\n");
    	            sb.append("  <meta charset=\"utf-8\">\n");
    	            sb.append("  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n");
    	            sb.append("  <title>Simple HTTP Server</title>\n");
    	            
    	            // Styling angelehnt an die File-Listing-Seite (dunkles, modernes UI)
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
    	            sb.append("        <div class=\"meta\">Version " + version + "</div>\n");
    	            sb.append("      </div>\n");
    	            sb.append("      <div class=\"meta\">Mounted at: <strong>" + escapeHtmlForSimplePage(getLocalAddressListing(exchange)) + "</strong></div>\n");
    	            sb.append("    </header>\n");
    	            
    	            // Grid with primary download area and shared folders
    	            sb.append("    <section class=\"grid\">\n");
    	            
    	            // Downloads card (keeps existing link to /dl)
    	            sb.append("      <article class=\"card\">\n");
    	            sb.append("        <h3>Downloads</h3>\n");
    	            sb.append("        <p>Zeigt Inhalte des internen Download-Ordners an.</p>\n");
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
    	            
    	            sb.append("    <footer>\n");
    	            sb.append("      <div>Server läuft auf Port: " + port + "</div>\n");
    	            sb.append("      <div style=\"margin-top:6px;color:var(--muted)\">Hinweis: Preview- und Thumbnail-Einstellungen werden aus der Konfiguration geladen.</div>\n");
    	            sb.append("    </footer>\n");
    	            sb.append("  </div>\n");
    	            sb.append("</body>\n");
    	            sb.append("</html>\n");
    	            
    				
    				String response = sb.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
    				
    			}
    		});
    		try {
    			System.out.println("");
	       		System.out.println("Erreichbar unter: http://localhost");
	            InetAddress localhost = InetAddress.getLocalHost();
	            System.out.println("oder unter: http://" + localhost.getHostAddress());
	
	        } catch (UnknownHostException e) {
	            e.printStackTrace();
	        }
    		
    		server.start();
    	} catch(BindException e) {
    		System.err.println("Es läuft bereits ein Server auf port 80!");
    	} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
    }
	
	private void addFileHandlers() throws IOException {
		server.createContext("/dl", new FileHandler("dl", previewMedia, generateVideoThumbnails));
		server.createContext("/.thumbs", new FileHandler(".thumbs", false, false));
		
		for(String path: shareFolders) {
			System.out.println("Externer Ordner \"" + path + "\" wird geshared");
			server.createContext("/" + path, new FileHandler(path, previewMedia, generateVideoThumbnails));
		}
		
		
	}
	
	// ---------- kleine Helfer (nur für das HTML) ----------
	// Diese Methoden verändern keine Logik der Klasse, sie dienen lediglich zur sauberen Ausgabe.
	private String getLocalAddressListing(HttpExchange exchange) {
		try {
			InetAddress localhost = InetAddress.getLocalHost();
			return "localhost or " + localhost.getHostAddress();
		} catch (UnknownHostException e) {
			return "localhost";
		}
	}
	
	// sehr einfache Escaping-Routine für die Startseite (keine Logikänderung)
	private String escapeHtmlForSimplePage(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
