package de.dion.httpserver.handlers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;


public class OpenConfig implements HttpHandler {

	public void handle(HttpExchange exchange) throws IOException {
        // nur lokale Requests d�rfen diesen Endpunkt ausl�sen
        if (!isLocalRequest(exchange)) {
            String resp = "Forbidden";
            exchange.sendResponseHeaders(403, resp.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp.getBytes(StandardCharsets.UTF_8));
            }
            return;
        }
        
        File cfg = new File("config.conf").getAbsoluteFile();
        String message;  // Wir brauchen die message jetzt nicht mehr f�r den Response, aber du kannst sie f�r Logging behalten
        try {
            // zuerst versuchen Notepad++ zu starten
            ProcessBuilder pb = new ProcessBuilder("C:\\Program Files\\Notepad++\\notepad++.exe", "-lyaml", cfg.getAbsolutePath());
            pb.start();
        } catch (Exception e1) {
            try {
                // Fallback auf Windows Notepad
                ProcessBuilder pb2 = new ProcessBuilder("notepad", cfg.getAbsolutePath());
                pb2.start();
            } catch (Exception e2) {
            	e2.printStackTrace();
            }
        }
        
        // Umleitung zur Hauptseite "/"
        exchange.getResponseHeaders().add("Location", "/");
        exchange.sendResponseHeaders(302, -1);  // 302 f�r Redirect, -1 bedeutet kein Body
        // Kein Body mehr n�tig, da umgeleitet wird
    }
	
	static boolean isLocalRequest(HttpExchange exchange) {
		try {
			InetAddress remote = exchange.getRemoteAddress().getAddress();
			return remote.getHostAddress().equals("127.0.0.1");
		} catch (Exception e) {
			return false;
		}
	}

}
