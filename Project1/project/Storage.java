import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.io.*;

public class Storage implements Serializable {

    private ConcurrentHashMap<String, File> initiatorFiles;
    private ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, InitChunk> initiatorChunks;
    private ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, Chunk> storedChunks;
    private int currentlyAvailable;
    private int maxCapacity;

    public Storage() {
        maxCapacity = 10000000;
        currentlyAvailable = 10000000;
        initiatorFiles = new ConcurrentHashMap<String, File>();
        storedChunks = new ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, Chunk>();
        initiatorChunks = new ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, InitChunk>();
    }

    public ArrayList<InitChunk> getInitiatorFileChunks(String fileID) {
        ArrayList<InitChunk> chunks = new ArrayList<InitChunk>();

        initiatorChunks.forEach((k, v) -> {
            if (v.getFileID().equals(fileID)) {
                chunks.add(v);
            }
        });
        return chunks;
    }

    public void incCapacity(int size) {
        maxCapacity += size;
    }

    public void decCapacity(int size) {
        // TO DO -> reclaim space
        maxCapacity -= size;
    }

    public int getCapacity() {
        return maxCapacity;
    }

    public int getCurrentlyAvailable() {
        return currentlyAvailable;
    }

    public boolean storeChunk(String chunkNum, Chunk chunk, int peerID) {
        if (chunk.getData().length <= currentlyAvailable) {
            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(chunk.getFileID(), chunkNum);
            if (this.storedChunks.get(entry) == null) {
                this.storedChunks.put(entry, chunk);
                synchronized (this) {
                    this.currentlyAvailable -= chunk.getData().length;
                }
            }
            this.storedChunks.get(entry).storeBackupChunk(peerID);
            return true;
        }
        return false;
    }

    public ConcurrentHashMap<String, File> getInitiatorFiles() {
        return this.initiatorFiles;
    }

    public ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, Chunk> getStoredChunks() {
        return this.storedChunks;
    }

    public Chunk getStoredChunk(String chunkNum, String fileID) {
        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(fileID, chunkNum);
        return this.storedChunks.get(entry);
    }

    public int getNumStoredChunks() {
        return this.storedChunks.size();
    }

    public InitChunk getInitiatedChunk(String chunkNum, String fileID) {
        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(fileID, chunkNum);
        return this.initiatorChunks.get(entry);
    }

    public boolean storeInitiatedFile(File file, String fileID) {
        if (file.length() <= this.currentlyAvailable) {
            initiatorFiles.put(fileID, file);
            return true;
        }
        return false;
    }

    public boolean storeInitiatedChunk(String chunkNum, InitChunk chunk, int peerID) {
        if (chunk.getData().length <= this.currentlyAvailable) {
            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(chunk.getFileID(), chunkNum);
            initiatorChunks.put(entry, chunk);
            this.currentlyAvailable -= chunk.getData().length;
            initiatorChunks.get(entry).storeInitiatedChunk(peerID);
            return true;
        }
        return false;
    }

    public boolean containsFile(String fileID) {
        return initiatorFiles.containsKey(fileID);
    }

    public boolean containsInitiatedChunk(String chunkNum, String fileID) {
        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(fileID, chunkNum);
        return initiatorChunks.containsKey(entry);
    }

    public boolean containsStoredChunk(String chunkNum, String fileID) {
        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(fileID, chunkNum);
        return storedChunks.containsKey(entry);
    }

    public synchronized void deleteStoredChunk(String fileID, String chunkNum, int peerID) {
        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(fileID, chunkNum);
        File currentFile = new File("peer" + peerID + "/backup/" + fileID + "/" + chunkNum);
        currentFile.delete();
        if (this.storedChunks.get(entry) != null) {
            this.currentlyAvailable += this.storedChunks.get(entry).getData().length;
            this.storedChunks.remove(entry);
        }
    }

    public synchronized void deleteChunks(String fileID, int peerID) {
        this.storedChunks.forEach((k, v) -> {
            if (v.getFileID().equals(fileID)) {
                this.storedChunks.remove(k, v);
                File currentFile = new File("peer" + peerID + "/backup/" + fileID + "/" + k.getValue());
                currentFile.delete();
                this.currentlyAvailable += v.getData().length;
            }
        });
        File directory = new File("peer" + peerID + "/backup/" + fileID);
        directory.delete();
    }

    public void restoreChunk(InitChunk chunk, int peerID) {
        this.initiatorChunks.forEach((k, v) -> {
            if (v.getFileID().equals(chunk.getFileID()) && v.getNumber() == chunk.getNumber()) {
                // File currentFile = new File("peer" + peerID + "/backup/" + chunk.getFileID()
                // + "/" + k.getValue());
                // if (currentFile.exists())
                // currentFile.delete();
                chunk.storeInitiatedChunk(peerID);
                AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(chunk.getFileID(),
                        Integer.toString(chunk.getNumber()));
                initiatorChunks.remove(entry);
                initiatorChunks.put(entry, chunk);
                return;
            }
        });
    }

    public void restoreFile(String fileID, int numChunks, int peerID) {
        try {

            File file = new File(initiatorFiles.get(fileID).getPath());

            PrintWriter writer = new PrintWriter(file);
            writer.print("");
            writer.close();

            System.out.println("file size1: " + file.length());
            FileOutputStream outFile = new FileOutputStream(file, true);

            for (int chunkID = 1; chunkID <= numChunks; chunkID++) {
                AbstractMap.SimpleEntry<String, String> key = new AbstractMap.SimpleEntry<String, String>(fileID,
                        Integer.toString(chunkID));
                byte[] content = initiatorChunks.get(key).getData();
                System.out.println("file size: " + file.length());
                outFile.write(content);
            }
            outFile.close();
        } catch (

        Exception e) {
            e.printStackTrace();
        }

    }

    public ArrayList<AbstractMap.SimpleEntry<String, String>> reclaimMemory(int newCapacity, int peerID) {
        if (newCapacity == 0) {
            for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storedChunks.entrySet()) {
                ArrayList<Integer> rd = getStoredChunkRd(entry.getKey().getKey(), entry.getKey().getValue());

                this.storedChunks.remove(entry.getKey());
                this.currentlyAvailable += entry.getValue().getData().length;
                File currentFile = new File(
                        "peer" + peerID + "/backup/" + entry.getKey().getKey() + "/" + entry.getKey().getValue());
                if (currentFile.exists())
                    currentFile.delete();

                InitChunk chunk = new InitChunk(rd.get(1), Integer.parseInt(entry.getKey().getValue()),
                        entry.getKey().getKey(), entry.getValue().getData());
                chunk.setRd(rd.get(0) - 1);
                try {
                    File chunkFile = new File(entry.getKey().getKey() + entry.getKey().getValue() + "/chunk.ser");

                    if (chunkFile.exists()) {
                        chunkFile.delete();
                    }

                    chunkFile.createNewFile();

                    FileOutputStream outFile = new FileOutputStream(
                            entry.getKey().getKey() + entry.getKey().getValue() + "/chunk.ser");
                    ObjectOutputStream out = new ObjectOutputStream(outFile);
                    out.writeObject(chunk);
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            for (Map.Entry<AbstractMap.SimpleEntry<String, String>, InitChunk> entry : this.initiatorChunks
                    .entrySet()) {

                this.initiatorChunks.remove(entry.getKey());
                this.currentlyAvailable += entry.getValue().getData().length;
                File currentFile = new File(
                        "peer" + peerID + "/initiated/" + entry.getKey().getKey() + "/" + entry.getKey().getValue());
                if (currentFile.exists())
                    currentFile.delete();
            }
        }

        loop: while (maxCapacity > newCapacity) {
            if (newCapacity >= (maxCapacity - this.currentlyAvailable)) {
                currentlyAvailable = newCapacity - (maxCapacity - currentlyAvailable);
                maxCapacity = newCapacity;
                break;
            } else {
                for (Map.Entry<AbstractMap.SimpleEntry<String, String>, InitChunk> entry : this.initiatorChunks
                        .entrySet()) {
                    this.initiatorChunks.remove(entry.getKey());
                    this.currentlyAvailable += entry.getValue().getData().length;
                    File currentFile = new File("peer" + peerID + "/initiated/" + entry.getKey().getKey() + "/"
                            + entry.getKey().getValue());
                    if (currentFile.exists())
                        currentFile.delete();
                    break;
                }
            }
        }

        outerloop: while (maxCapacity > newCapacity) {
            if (newCapacity >= (maxCapacity - this.currentlyAvailable)) {
                currentlyAvailable = newCapacity - (maxCapacity - currentlyAvailable);
                maxCapacity = newCapacity;
            } else {
                for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storedChunks.entrySet()) {
                    ArrayList<Integer> rd = getStoredChunkRd(entry.getKey().getKey(), entry.getKey().getValue());

                    if (rd.get(0) > rd.get(1)) {
                        this.storedChunks.remove(entry.getKey());
                        this.currentlyAvailable += entry.getValue().getData().length;
                        File currentFile = new File("peer" + peerID + "/backup/" + entry.getKey().getKey() + "/"
                                + entry.getKey().getValue());
                        if (currentFile.exists())
                            currentFile.delete();

                        InitChunk chunk = new InitChunk(rd.get(1), Integer.parseInt(entry.getKey().getValue()),
                                entry.getKey().getKey(), entry.getValue().getData());
                        chunk.setRd(rd.get(0) - 1);
                        try {
                            File chunkFile = new File(
                                    entry.getKey().getKey() + entry.getKey().getValue() + "/chunk.ser");

                            if (chunkFile.exists()) {
                                chunkFile.delete();
                            }

                            chunkFile.createNewFile();

                            FileOutputStream outFile = new FileOutputStream(
                                    entry.getKey().getKey() + entry.getKey().getValue() + "/chunk.ser");
                            ObjectOutputStream out = new ObjectOutputStream(outFile);
                            out.writeObject(chunk);
                            out.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        continue outerloop;
                    }
                }
                break;
            }
        }

        ArrayList<AbstractMap.SimpleEntry<String, String>> removedFiles = new ArrayList<AbstractMap.SimpleEntry<String, String>>();

        do {
            int v = this.maxCapacity - this.currentlyAvailable;
            if (newCapacity >= v) {
                currentlyAvailable = newCapacity - (maxCapacity - currentlyAvailable);
                this.maxCapacity = newCapacity;
                break;
            }
            for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storedChunks.entrySet()) {
                this.storedChunks.remove(entry.getKey());
                this.currentlyAvailable += entry.getValue().getData().length;
                AbstractMap.SimpleEntry<String, String> remFile = new AbstractMap.SimpleEntry<>(entry.getKey().getKey(),
                        entry.getKey().getValue());
                removedFiles.add(remFile);
                File currentFile = new File(
                        "peer" + peerID + "/backup/" + entry.getKey().getKey() + "/" + entry.getKey().getValue());
                if (currentFile.exists())
                    currentFile.delete();
                break;
            }
        } while (this.maxCapacity > newCapacity);

        return removedFiles;
    }

    public ArrayList<Integer> getStoredChunkRd(String fileID, String chunkNum) {
        try {
            String filename = (fileID + chunkNum + "/chunk.ser");
            File file = new File(filename);
            if (!file.exists())
                return new ArrayList<Integer>();
            InitChunk initchunk;
            synchronized (this) {

                FileInputStream chunk = new FileInputStream(filename);
                ObjectInputStream in = new ObjectInputStream(chunk);
                initchunk = (InitChunk) in.readObject();
                in.close();
            }
            ArrayList<Integer> rd = new ArrayList<Integer>();
            rd.add(initchunk.getCurrentRd());
            rd.add(initchunk.getDesiredRd());
            return rd;

        } catch (Exception e) {
            // e.printStackTrace();
            return new ArrayList<Integer>();
        }
    }
}