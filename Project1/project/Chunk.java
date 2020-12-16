import java.util.ArrayList;
import java.io.*;

public class Chunk implements Serializable {

    private int number;
    private int desiredRd;
    private int currentRd = 0;
    private byte[] data;
    private ArrayList<Integer> peerStoring;
    private String fileID;

    public Chunk(int number, byte[] data, String fileID){

        this.number = number;
        this.data = data;
        this.peerStoring = new ArrayList<>();
        this.fileID = fileID;
    }

    public byte[] getData(){
        return data;
    }

    public int getNumber(){
        return this.number;
    }

    public int getDesiredRd(){

        return this.desiredRd;
    }

    public int getRD(){
        return this.currentRd;
    }

    public String getFileID() {
        return this.fileID;
    }
    public void setNumber(int number) {
        this.number = number;
    }

    public void setDesiredRd(int desiredRd) {
        this.desiredRd = desiredRd;
    }

    public void setRd(int rd) {
        this.currentRd = rd;
    }

    public void addStorer(int peerID) {
        if(peerStoring.contains(peerID))
            return;
        peerStoring.add(peerID);
        this.currentRd++;
    }

    public void storeBackupChunk(int peerID) {
        File chunk = new File("peer" + peerID + "/backup/" + this.fileID + "/" + Integer.toString(this.number));
        chunk.getParentFile().mkdirs();

        try {
            FileOutputStream out = new FileOutputStream(chunk);
            out.write(this.data);
            out.close();
        }

        catch(Exception e) {
            e.printStackTrace();

        }
    }
}