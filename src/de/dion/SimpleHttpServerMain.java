package de.dion;

import java.io.File;
import java.io.IOException;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import de.dion.config.Config;
import de.dion.config.ConfigEntry;
import de.dion.config.ConfigHelper;
import de.dion.config.exceptions.WrongFileTypeException;
import de.dion.httpserver.WebServer;

public class SimpleHttpServerMain {
	
	private static WebServer website;
	public static ConfigHelper config;

    public static void main(String[] args) {
    	System.out.println("SimpleHttpServer Version: " + WebServer.version + " by Di0nysus1");
    	
    	createDLDir();
    	doConfigStuff();
    	
    	boolean start = false;
    	for(String arg : args) {
    		if(arg.equalsIgnoreCase("-start")) {
    			start = true;
    		}
    	}
    	if(!start) {
    		System.out.println("Zum starten bitte mit Argument '-start' ausführen.");
    		JOptionPane.showMessageDialog(new JFrame(), "Zum starten bitte per CMD oder Batch file mit Argument '-start' ausführen.");
    		System.exit(0);
    	}
    	
    	website = new WebServer();
    	website.start();
    }
    
    private static void doConfigStuff() {
    	Config programConfig = new Config("Program Settings", new ConfigEntry[] {
    			new ConfigEntry("Port", 80, false, "HTTP Port for the Website"),
    			new ConfigEntry("Download-Buffersize", 2048, false, "Buffersize for transfering Data in KiB"),
    			new ConfigEntry("Preview-Media", true, false, "Should the Users be able to Play Videos and Stuff like that instead of only downloading?"),
    			new ConfigEntry("Show-VideoThumbnails", false, false, "Should Thumbnails be created for Videos on the listing Page?\nffmpeg required!"),
    			new ConfigEntry("Thumbnail-Scale", 640, false, "Default Resolution for the Thumbnails. Example: 640 = 640x360p\nHigher Resolutions take longer to generate."),
    			new ConfigEntry("Share-Folders", "", true, "Path to External folders to be shared on the Website. Subfolders are also shared!\nSeparete with ;")
    	});
    	
    	try {
			config = new ConfigHelper(programConfig, new File("config.conf"));
		} catch (WrongFileTypeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	config.read();
    }
    
    private static void createDLDir() {
    	File dlDIR = new File("dl");
    	if(!dlDIR.exists() || !dlDIR.isDirectory()) {
    		try {
				dlDIR.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
				System.err.println("\"dl\" Verzeichnis konnte nicht erstellt werden!");
				System.exit(1);
			}
    	}
    	if(dlDIR.exists() && dlDIR.isDirectory()) {
    		System.out.println("Verzeichnis \"dl\" gefunden.");
    	}
    }
}

