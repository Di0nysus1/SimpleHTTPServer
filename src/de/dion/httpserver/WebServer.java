package de.dion.httpserver;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import de.dion.SimpleHttpServerMain;
import de.dion.httpserver.handlers.FileHandler;
import de.dion.httpserver.handlers.MainPage;
import de.dion.httpserver.handlers.OpenConfig;
import de.dion.httpserver.handlers.UploadHandler;

public class WebServer {

	public static final String version = "2.0";
	private int port;
	private boolean previewMedia;
	private String[] shareFolders = new String[0];
	private boolean showVideoThumbnails;
	private boolean allowUploads;
	private String uploadDir;
	private HttpServer server;
	
	public WebServer() {
		init();
	}
	
	private void init() {
		port = SimpleHttpServerMain.config.getIntValue("Port");
		FileHandler.BUFFER_SIZE = SimpleHttpServerMain.config.getIntValue("Download-Buffersize");
		FileHandler.BUFFER_SIZE *= 1024; //umrechnung KiB in Bytes
		previewMedia = SimpleHttpServerMain.config.getBooleanValue("Preview-Media");
		showVideoThumbnails = SimpleHttpServerMain.config.getBooleanValue("Show-VideoThumbnails");
		allowUploads = SimpleHttpServerMain.config.getBooleanValue("Allow-Uploads");
		uploadDir = SimpleHttpServerMain.config.getValue("Upload-Dir");
		
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
    		//So viele Threads für Multuthreading wie Cpu Threads erzeugen
    		server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    		
    		//Alle Sub-Pages erstellen
    		addFileHandlers();
    		server.createContext("/open-config", new OpenConfig());
    		server.createContext("/", new MainPage(port, previewMedia, showVideoThumbnails, shareFolders, allowUploads, uploadDir));
    		if(allowUploads) {
    			server.createContext("/upload", new UploadHandler(uploadDir));
    		}
    		
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
    		System.err.println("Server konnte nicht gestartet werden!");
			e1.printStackTrace();
		}
    }
	
	private void addFileHandlers() throws IOException {
		int thumbnailScale = SimpleHttpServerMain.config.getIntValue("Thumbnail-Scale");
		
		server.createContext("/dl", new FileHandler("dl", previewMedia, showVideoThumbnails, thumbnailScale));
		
		for(String path: shareFolders) {
			System.out.println("Externer Ordner \"" + path + "\" wird geshared");
			server.createContext("/" + path, new FileHandler(path, previewMedia, showVideoThumbnails, thumbnailScale));
		}
		
		if(showVideoThumbnails) {
			new File(".thumbs").mkdirs();
			server.createContext("/.thumbs", new FileHandler(".thumbs", false, false, 0));
		}
	}
	

	
}
