import java.io.*;
import java.util.ArrayList;
import java.security.MessageDigest;

public class FileData {

    private File file;
    private String id;
    private String path;
    private ArrayList<Chunk> chunks;

    public FileData(String path) {
        this.path = path;
        this.file = new File(path);
        String preHashId = path + this.file.getName() + this.file.lastModified();
        id = sha256(preHashId);
        try {
            splitIntoChunks();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getId() {

        return id;
    }

    public File getFile() {
        return file;
    }

    public ArrayList<Chunk> getChunks() {

        return chunks;
    }

    public Integer getNumChunks() {
        return chunks.size();
    }

    public void splitIntoChunks() throws IOException {

        int chunkId = 1;
        int chunkSize = 64000;
        byte[] buffer = new byte[chunkSize];
        FileInputStream fis = new FileInputStream(this.file.getName());
        BufferedInputStream bis = new BufferedInputStream(fis);
        this.chunks = new ArrayList<Chunk>();
        int numRead;
        Chunk chunk;
        
        do {
            numRead = bis.read(buffer);
            if (numRead != 64000) {
                byte[] buf = new byte[numRead];
                System.arraycopy(buffer, 0, buf, 0, numRead);
                chunk = new Chunk(chunkId, buf, this.id);
            } else {
                chunk = new Chunk(chunkId, buffer, this.id);
            }

            this.chunks.add(chunk);
            buffer = new byte[chunkSize];
            chunkId++;

        } while (numRead == 64000);

    }

    public static String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < hash.length; i++) {
                String hex = Integer.toHexString(0xff & hash[i]);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}