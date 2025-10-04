package de.dion.httpserver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class ThumbnailManager {
	
	private final File thumbDir;
	private final int thumbnailScale;
	
	public ThumbnailManager(int thumbnailScale) {
		this.thumbnailScale = thumbnailScale;
		this.thumbDir = new File(".thumbs");
	}

    /**
     * Versucht, ein Thumbnail für ein Video zu erzeugen (ffmpeg wird dafür verwendet). Die Thumbs werden
     * im Ordner ".thumbs" als <sha1>.jpg gespeichert.
     * Wenn ffmpeg nicht verfügbar ist oder die Generierung fehlschlägt, wird null zurückgegeben und
     * das Listing zeigt stattdessen das Icon an.
     */
    public File getOrCreateVideoThumbnail(File video) throws IOException {
        try {
            String name = sha1Hex(video.getCanonicalPath()) + ".jpg";
            File out = new File(thumbDir, name);
            
            if (out.exists() && out.length() > 0) {
            	return out;
            }
            out.getParentFile().mkdirs();
            
            boolean ok = generateThumbnailWithFfmpeg(video, out);
            if (ok) {
            	return out;
            }
        } catch (Exception e) {
            System.err.println("Thumbnail generation failed: " + e.getMessage());
        }
        return null;
    }

    private String sha1Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private boolean generateThumbnailWithFfmpeg(File video, File thumb) throws IOException, InterruptedException {
    	System.out.println("generiere Thumbnail für \"" + video.getName() + "\"");
    	
        // Command: ffmpeg -y -ss 00:00:10 -i <video> -frames:v 1 -q:v 4 -vf scale=640:-1 <thumb>
    	ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-ss", "00:00:10",
                "-i", video.getAbsolutePath(),
                "-frames:v", "1",
                "-q:v", "4",
                "-vf", "scale=" + thumbnailScale + ":-1",
                thumb.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        // read output to avoid blocking
        try (InputStream is = p.getInputStream()) {
            byte[] buf = new byte[1024];
            while (is.read(buf) != -1) {
                // discard, but ensures stream buffer doesn't fill
            }
        } catch (IOException ignore) {}

        boolean finished = p.waitFor(8, TimeUnit.SECONDS);
        if (!finished) {
            p.destroy();
            return false;
        }
        int exit = p.exitValue();
        return exit == 0 && thumb.exists() && thumb.length() > 0;
    }
}
