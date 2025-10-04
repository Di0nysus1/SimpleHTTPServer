package de.dion.httpserver;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpExchange;

import de.dion.httpserver.handlers.FileHandler;

public class DataServer {
	
	private final boolean filterFileNames;
	
    public DataServer(boolean filterFileNames) {
		this.filterFileNames = filterFileNames;
	}

	public void serveFileWithRange(HttpExchange exchange, File file, String mimeType, boolean inline) throws IOException {
        long fileLength = file.length();
        String range = exchange.getRequestHeaders().getFirst("Range");
        long start = 0;
        long end = fileLength - 1;
        boolean isPartial = false;

        if (range != null && range.startsWith("bytes=")) {
            String[] parts = range.substring("bytes=".length()).split("-", 2);
            try {
                if (parts.length > 0 && !parts[0].isEmpty()) start = Long.parseLong(parts[0].trim());
                if (parts.length > 1 && !parts[1].isEmpty()) end = Long.parseLong(parts[1].trim());
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
            exchange.getResponseHeaders().set("Content-Disposition", "inline; filename=\"" + getCleanFileName(exchange, file) + "\"");
        } else {
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + getCleanFileName(exchange, file) + "\"");
        }
        exchange.getResponseHeaders().set("Content-Type", mimeType);
        exchange.getResponseHeaders().set("Accept-Ranges", "bytes");

        if (isPartial) {
            exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            exchange.sendResponseHeaders(206, contentLength);
        } else {
            exchange.sendResponseHeaders(200, contentLength);
        }

        // Verwende FileChannel.transferTo für effizienteren Transfer in Chunks.

        try (FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
             WritableByteChannel outChannel = Channels.newChannel(exchange.getResponseBody())) {

            long position = start;
            long remaining = contentLength;
            while (remaining > 0) {
                long chunk = Math.min(remaining, FileHandler.BUFFER_SIZE);
                long transferred = fileChannel.transferTo(position, chunk, outChannel);
                
                if (transferred <= 0) {
                    // safety: vermeide infinite-loop, beende wenn nichts mehr transferiert wird
                    break;
                }
                position += transferred;
                remaining -= transferred;
            }
            

            // flush: beim Channels-API ist flush nicht direkt möglich; OutputStream wird beim close geschlossen.
            // Zumindest sicherstellen, dass wir die ResponseBody-Stream schließen (done durch try-with-resources).
        } catch (SocketException se) {
            // Häufige, erwartbare Client-Abbruch-Meldungen (seek/stop/close). Nur kurz loggen.
            System.out.println(se.getMessage());
        } catch (IOException ex) {
            // Anderes IO-Problem; loggen (kann weiterhin auftreten, z.B. wenn Netzwerkprobleme)
            System.out.println(ex.getMessage());
        } finally {
            try {
                exchange.getResponseBody().close();
            } catch (IOException ignored) {}
        }
    }
	
	private String getCleanFileName(HttpExchange exchange, File file) {
		String fileName = file.getName();
		
		//filterFileNames == true ODER Zugriff via NGROK
		if(this.filterFileNames || exchange.getRemoteAddress().getAddress().getHostAddress().equals("0:0:0:0:0:0:0:1")) {
        	fileName = Normalizer.normalize(fileName, Normalizer.Form.NFC);
        	fileName = fileName.replaceAll("[^\\p{L}\\p{N}\\s#\\.\\-\\_\\(\\)\\[\\]\\{\\}]", "").trim();
        }
		return fileName;
	}
	
	public void serveDirectoryAsZip(HttpExchange exchange, File dir, String zipFileName) throws IOException {
        // Header vorbereiten
        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + zipFileName + "\"");

        // Chunked transfer (Länge unbekannt) -> send 200 with 0
        exchange.sendResponseHeaders(200, 0);

        OutputStream rawOut = exchange.getResponseBody();
        BufferedOutputStream bos = new BufferedOutputStream(rawOut);
        ZipOutputStream zos = new ZipOutputStream(bos);

        try {
            // rekursiv alle Dateien hinzufügen
            addDirectoryToZip(zos, dir, "");
            // versuchen, ZIP sauber zu beenden
            try {
                zos.finish();
            } catch (IOException e) {
                // finish() kann ebenfalls fehlschlagen, z.B. wenn Client schon geschlossen hat
                if (isClientAbort(e)) {
                    System.out.println("Client hat die Verbindung beendet während ZIP fertiggestellt wurde.");
                    return;
                } else {
                    throw e;
                }
            }
        } catch (IOException e) {
            // Unterscheide zwischen Client-Abbruch und echten Fehlern
            if (isClientAbort(e)) {
                // sehr häufiger Fall: Nutzer hat Download abgebrochen -> keine lauten Stacktraces
                System.out.println("Client aborted ZIP download (connection closed).");
            } else {
                // Echte Fehler beim Lesen/Schreiben: ausführlicher loggen
                System.err.println("Fehler beim Erstellen des ZIP-Streams: " + e.toString());
                e.printStackTrace();
            }
        } finally {
            // Schließe Streams möglichst leise
            try { zos.close(); } catch (IOException ignored) {}
            try { exchange.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Fügt rekursiv Dateien in den ZipOutputStream ein.
     * Wenn während des Schreibens eine IOException auftritt, wird sie weitergeworfen.
     */
    private void addDirectoryToZip(ZipOutputStream zos, File dir, String parentPrefix) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;

        Arrays.sort(children);
        byte[] buffer = new byte[(int) FileHandler.BUFFER_SIZE];

        for (File child : children) {
            if (child.isHidden()) continue; // optional
            String entryName = parentPrefix.isEmpty() ? child.getName() : parentPrefix + "/" + child.getName();
            if (child.isDirectory()) {
                // add directory entry (optional)
                ZipEntry dirEntry = new ZipEntry(entryName + "/");
                dirEntry.setTime(child.lastModified());
                try {
                    zos.putNextEntry(dirEntry);
                    zos.closeEntry();
                } catch (IOException e) {
                    // weiterwerfen, damit outer handler reagieren kann (z.B. Client closed)
                    throw e;
                }
                addDirectoryToZip(zos, child, entryName);
            } else if (child.isFile()) {
                ZipEntry fileEntry = new ZipEntry(entryName);
                fileEntry.setTime(child.lastModified());
                zos.putNextEntry(fileEntry);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(child))) {
                    int len;
                    while ((len = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, len); // kann IOException werfen -> nach oben
                    }
                } catch (IOException e) {
                    // wichtig: beim Abbruch nicht weiter versuchen, sondern die Exception hochreichen
                    try { zos.closeEntry(); } catch (Exception ignored) {}
                    throw e;
                }
                zos.closeEntry();
            }
        }
    }

    /** Prüft, ob es sich um ein Client-Abbruch-Problem handelt (verschiedene Server-Implementierungen).
     *  Wir vergleichen Klassennamen, weil sun.net.httpserver.* nicht immer kompiliersicher importiert werden sollte. */
    private boolean isClientAbort(Throwable t) {
        while (t != null) {
            String cn = t.getClass().getName();
            if ("sun.net.httpserver.StreamClosedException".equals(cn) ||
                "org.apache.catalina.connector.ClientAbortException".equals(cn) ||
                "org.eclipse.jetty.io.EofException".equals(cn)) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
