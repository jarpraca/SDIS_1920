import javax.net.ssl.SSLSocket;
import java.io.*;
import java.util.ArrayList;

import javax.net.ssl.*;

public class ProtocolManager {
    private ChordNode chordNode;

    public ProtocolManager(ChordNode node) {
        this.chordNode = node;
    }

    public void backup(String filePath, int rd) {

        if (chordNode.getKey().getID().compareTo(chordNode.getSuccessor().getID()) == 0) {
            System.out.println("There are no other peers connected at the time, cannot backup file");
            return;
        }

        FileData file = new FileData(filePath);

        chordNode.getStorage().storeInitiatedFile(file.getFile(), file.getId());

        for (Chunk c : file.getChunks()) {

            c.setDesiredRd(rd);

            String header = "PUTCHUNK " + file.getId() + " " + c.getNumber() + " " + c.getDesiredRd() + " "
                    + this.chordNode.getAddress() + " " + this.chordNode.getPort() + " " + c.getData().length;

            InitChunk chunk = new InitChunk(c.getDesiredRd(), c.getNumber(), c.getFileID(), c.getData());
            chordNode.getStorage().storeInitiatedChunk(String.valueOf(c.getNumber()), chunk,
                    chordNode.getKey().getID());
            int i = 1;
            int j = 1;
            this.chordNode.getExecutor().execute((Runnable) new SendBackup(header, c.getData(),
                    chordNode.getSuccessor().getAddress(), chordNode.getSuccessor().getPort(), chordNode));
            while (i <= c.getDesiredRd() && j < chordNode.getFingers().size()) {
                if (chordNode.getFingers().get(j).getID().compareTo(chordNode.getFingers().get(j - 1).getID()) != 0) {
                    this.chordNode.getExecutor()
                            .execute((Runnable) new SendBackup(header, c.getData(),
                                    chordNode.getFingers().get(j).getAddress(), chordNode.getFingers().get(j).getPort(),
                                    chordNode));
                    i++;
                }
                j++;
            }

            ///// nova parte; caso nao seja atingido o rd
            if (i < c.getDesiredRd()) {
                String address = chordNode.getSuccessor().getAddress();
                Integer port = chordNode.getSuccessor().getPort();
                Integer chunk_rd = rd - i;
                String chunk_header = "BACKUP_CHUNK " + chunk_rd + " " + c.getFileID() + " " + c.getNumber() + " " + this.chordNode.getAddress() + " " + this.chordNode.getPort() + " " +  c.getData().length + "\n";
                this.chordNode.getExecutor()
                        .execute((Runnable) new SendBackup(chunk_header, c.getData(), address, port, chordNode));
            }

        }
    }

    public void delete(String filePath) {
        FileData file = new FileData(filePath);
        if (this.chordNode.getStorage().containsFile(file.getId())) {
            String crlf = "\r\n";
            for (int i = 1; i <= file.getNumChunks(); i++) {
                ArrayList<Finger> storers = this.chordNode.getStorage()
                        .getInitiatedChunk(String.valueOf(i), file.getId()).getStorers();
                for (int j = 0; j < storers.size(); j++) {
                    String header = "DELETE" + " " + file.getId() + " " + String.valueOf(i) + " " + crlf;
                    this.chordNode.getExecutor().execute((Runnable) new SendMessage(header, storers.get(j).getAddress(),
                            storers.get(j).getPort(), chordNode));
                }
            }
        } else {
            System.out.println("The specified file wasn't previously backed up from this peer");
        }
    }

    public void sendRequest(String message, String address, Integer port) {

        try {
            SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket s = (SSLSocket) ssf.createSocket(address, port);

            if (!s.isConnected()) {
                return;
            }

            // send request
            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            out.writeBytes(message);

            out.flush();
            out.close();
            s.close();
        } catch (Exception e) {
        }
    }

    public void restore(String filePath) {
        String crlf = "\r\n";
        FileData file = new FileData(filePath);
        int numChunks = file.getChunks().size();
        int chunkId = 1;

        // AbstractMap.SimpleEntry<String, String> entry = new
        // AbstractMap.SimpleEntry<String, String>(file.getId(),
        // Integer.toString(chunkId));
        // waitingRestores.add(entry);

        for (int i = 1; i <= file.getNumChunks(); i++) {

            ArrayList<Finger> storers = this.chordNode.getStorage().getInitiatedChunk(String.valueOf(i), file.getId())
                    .getStorers();
            for (int j = 0; j < storers.size(); j++) {
                String header = "GETCHUNK" + " " + file.getId() + " " + chunkId + " " + chordNode.getKey().getAddress()
                        + " " + chordNode.getKey().getPort() + " " + crlf;
                this.chordNode.getExecutor().execute((Runnable) new SendMessage(header, storers.get(j).getAddress(),
                        storers.get(j).getPort(), chordNode));
            }
        }
    }

}