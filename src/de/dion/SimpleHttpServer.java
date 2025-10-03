package de.dion;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class SimpleHttpServer {

	private static final String version = "1.0";
	
    public static void main(String[] args) throws Exception {
    	int port = 80;
    	
    	if(args.length > 0) {
    		if(args[0].endsWith("help")) {
    			System.out.println("Mögliche Argumente: <Port>");
    			System.out.println("java -jar simplehttpserver.jar 80");
    			System.exit(0);
    		} else {
    			try {
    				port = Integer.parseInt(args[0]);
    			} catch (NumberFormatException e) {}
    		}
    	}
    	
    	System.out.println("SimpleHttpServer Version: " + version + " by Di0nysus1");
    	
    	File f = new File("dl");
    	if(!f.exists() || !f.isDirectory()) {
    		System.out.println("Bitte erstell ein unterverzeichnis namens \"dl\" und pack da die files rein");
    		System.exit(1);
    	} else {
    		System.out.println("Verzeichnis \"dl\" gefunden.");
    	}
    	System.out.println("Starte Server auf Port" + port);
    	
    	try {
    		
    		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    		server.createContext("/dl", new MyHandler());
    		server.setExecutor(null); // creates a default executor
    		server.createContext("/", new HttpHandler() {
    			
    			@Override
    			public void handle(HttpExchange exchange) throws IOException {
    				System.out.println("Ping 1");
    				
    	            StringBuilder sb = new StringBuilder();
    	            sb.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n");
    	            sb.append("<html>\n");
    	            sb.append("<head>\n");
    	            sb.append("<title>Test Seite</title>\n");
    	            sb.append("</head>\n");
    	            sb.append("<body style =\"background-color:#303030;\">\n");
    	            
    	            sb.append("<h1 style=\"color:#ff9900\";>Willkommen auf der Java Test Seite</h1>\n");
    	            sb.append("<h3 style=\"color:#00AAFF\";>Version 1.1</h3>\n");
    	            sb.append("<br />");
    	            sb.append("<a href=\"./dl\" style=\"color:red;\">").append("<B>Downloads</B>").append("</a>");
    	            
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
    	}
        
        
    }
}

