package de.dion.httpserver;

import java.io.File;
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
	public static int maximumFileNameLength;
	private HttpServer server;
	
	public WebsiteServer() {
		init();
	}
	
	private void init() {
		port = SimpleHttpServerMain.config.getIntValue("Port");
		maximumFileNameLength = SimpleHttpServerMain.config.getIntValue("Maximum-FileName-Length");
		previewMedia = SimpleHttpServerMain.config.getBooleanValue("Preview-Media");
		
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
    				System.out.println("Ping 1 " + exchange.getLocalAddress());
    				
    	            StringBuilder sb = new StringBuilder();
    	            sb.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
    	            sb.append("<html>\n");
    	            sb.append("<head>\n");
    	            sb.append("<title>Simple HTTP Server</title>\n");
    	            sb.append("</head>\n");
    	            sb.append("<body style =\"background-color:#303030;\">\n");
    	            
    	            sb.append("<h1 style=\"color:#ff9900\";>Willkommen auf dem Java Simple-HTTP-Server</h1>\n");
    	            sb.append("<h3 style=\"color:#00AAFF\";>Version " + version + "</h3>\n");
    	            sb.append("<br />");
    	            sb.append("<a href=\"/dl\" style=\"color:red;\">").append("<B>Downloads</B>").append("</a>");
    	            
    	            sb.append("<br />");
    	            for(String path: shareFolders) {
    	            	sb.append("<br />");
    	            	sb.append("<a href=\"/" + path + "\" style=\"color:red;\">").append("<B>" + path + "</B>").append("</a>");
    	            }
    	            
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
		server.createContext("/dl", new FileHandler("dl", previewMedia));
		
		for(String path: shareFolders) {
			server.createContext("/" + path, new FileHandler(path, previewMedia));
		}
		
		
	}
}


