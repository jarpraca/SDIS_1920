import java.io.Serializable;
import java.util.ArrayList;
import java.io.*;

public class InitChunk implements Serializable {

    private int desiredRd;
    private int currentRd;
    private ArrayList<Integer> peerStoring;
    private int number;
    private byte[] data;
    private String fileID;

    public InitChunk(int desiredRd, int number, String fileID, byte[] data) {
        this.desiredRd = desiredRd;
        this.currentRd = 0;
        this.number = number;
        this.fileID = fileID;
        this.data = data;
        this.peerStoring = new ArrayList<>();
    }
    
    public int getNumber(){
        return this.number;
    }

    public String getFileID() {
        return this.fileID;
    }

    public int getRD(){
        return this.currentRd;
    }

    public int getDesiredRd(){
        return this.desiredRd;
    }

    public byte[] getData(){
        return data;
    }

    public int getCurrentRd() {
        return currentRd;
    }
    
    public void setRd(int rd) {
        this.currentRd = rd;
    }

    public void addStorer(int peerID) {
        if(peerStoring.contains(peerID))
            return;
        peerStoring.add(peerID);
        this.currentRd++;
        updateObject();
    }


    
    public void storeInitiatedChunk(int peerID) {
        File chunk = new File("peer"+ peerID + "/initiated/" + this.fileID + "/" + this.number);
        chunk.getParentFile().mkdirs();

        try {
            if (!chunk.exists()) {
                chunk.createNewFile();
             }
            FileOutputStream out = new FileOutputStream(chunk);
            out.write(this.data, 0, this.data.length);
            out.close();
            updateObject();
        }

        catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void updateObject() {
        try {
            File chunkFile = new File(fileID + number + "/chunk.ser");
            
            if (!chunkFile.exists()) {
                chunkFile.getParentFile().mkdirs();
                chunkFile.createNewFile();
            }

            FileOutputStream chunk = new FileOutputStream(fileID + number + "/chunk.ser");
            ObjectOutputStream out = new ObjectOutputStream(chunk);
            synchronized (this) {
                out.writeObject(this);
            }
            out.close();
            chunk.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}