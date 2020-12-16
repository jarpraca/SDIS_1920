import java.util.concurrent.ConcurrentHashMap;
import java.util.*;
import java.io.*;
import java.math.BigInteger;

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
        maxCapacity -= size;
    }

    public int getCapacity() {
        return maxCapacity;
    }

    public int getCurrentlyAvailable() {
        return currentlyAvailable;
    }

    public boolean storeChunk(String chunkNum, Chunk chunk, BigInteger peerID) {
        if (chunk.getData().length <= currentlyAvailable) {
            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(chunk.getFileID(), chunkNum);
            if (this.storedChunks.get(entry) == null) {
                this.storedChunks.put(entry, chunk);
                synchronized (this) {
                    this.currentlyAvailable -= chunk.getData().length;
                }
                this.storedChunks.get(entry).storeBackupChunk(peerID);
                return true;
            }
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

    public void putInitiatedChunk(InitChunk init, String fileID, String chunkNum) {
        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<>(fileID, chunkNum);
        this.initiatorChunks.put(entry, init);
    }

    public boolean storeInitiatedFile(File file, String fileID) {
        if (file.length() <= this.currentlyAvailable) {
            initiatorFiles.put(fileID, file);
            return true;
        }
        return false;
    }

    public boolean storeInitiatedChunk(String chunkNum, InitChunk chunk, BigInteger peerID) {
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
        File currentFile = new File("chordNode_" + peerID + "/backup/" + fileID + "/" + chunkNum);
        currentFile.delete();
        if (this.storedChunks.get(entry) != null) {
            this.currentlyAvailable += this.storedChunks.get(entry).getData().length;
            this.storedChunks.remove(entry);
        }
    }

    public synchronized void deleteChunks(String fileID, BigInteger peerID) {
        this.storedChunks.forEach((k, v) -> {
            if (v.getFileID().equals(fileID)) {
                this.storedChunks.remove(k, v);
                File currentFile = new File("chordNode_" + peerID + "/backup/" + fileID + "/" + k.getValue());
                currentFile.delete();
                this.currentlyAvailable += v.getData().length;
            }
        });
        File directory = new File("chordNode_" + peerID + "/backup/" + fileID);
        directory.delete();
    }

    public void restoreChunk(InitChunk chunk, BigInteger peerID) {
        this.initiatorChunks.forEach((k, v) -> {
            if (v.getFileID().equals(chunk.getFileID()) && v.getNumber() == chunk.getNumber()) {
                // File currentFile = new File("chordNode_" + peerID + "/backup/" +
                // chunk.getFileID()
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

    public void restoreFile(String fileID, int numChunks, BigInteger peerID) {
        try {

            File file = new File("restored/" + initiatorFiles.get(fileID).getPath());
            file.getParentFile().mkdirs();

            PrintWriter writer = new PrintWriter(file);
            writer.print("");
            writer.close();

            FileOutputStream outFile = new FileOutputStream(file, true);

            for (int chunkID = 1; chunkID <= numChunks; chunkID++) {
                AbstractMap.SimpleEntry<String, String> key = new AbstractMap.SimpleEntry<String, String>(fileID,
                        Integer.toString(chunkID));
                byte[] content = initiatorChunks.get(key).getData();
                outFile.write(content);
            }
            outFile.close();
        } catch (

        Exception e) {
            e.printStackTrace();
        }

    }

    public ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, byte[]> reclaimMemory(int newCapacity,
            BigInteger peerID) {
        ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, byte[]> removedChunks = new ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, byte[]>();
     
        if (newCapacity == 0) {
            for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storedChunks.entrySet()) {
                removedChunks.put(entry.getKey(), entry.getValue().getData());
                // ArrayList<Integer> rd = getStoredChunkRd(entry.getKey().getKey(),
                // entry.getKey().getValue());

                this.storedChunks.remove(entry.getKey());
                this.currentlyAvailable += entry.getValue().getData().length;
                File currentFile = new File(
                        "chordNode_" + peerID + "/backup/" + entry.getKey().getKey() + "/" + entry.getKey().getValue());
                if (currentFile.exists())
                    currentFile.delete();
            }

            for (Map.Entry<AbstractMap.SimpleEntry<String, String>, InitChunk> entry : this.initiatorChunks
                    .entrySet()) {

                this.initiatorChunks.remove(entry.getKey());
                this.currentlyAvailable += entry.getValue().getData().length;
                File currentFile = new File("chordNode_" + peerID + "/initiated/" + entry.getKey().getKey() + "/"
                        + entry.getKey().getValue());
                if (currentFile.exists())
                    currentFile.delete();
            }
            return removedChunks;
        }

        // while (maxCapacity > newCapacity && initiatorChunks.size() > 0) {
        //     System.out.println("new_ : " + newCapacity + " max: " + maxCapacity);

        //     if (newCapacity >= (maxCapacity - this.currentlyAvailable)) {
        //         currentlyAvailable = newCapacity - (maxCapacity - currentlyAvailable);
        //         maxCapacity = newCapacity;
        //         break;
        //     } else {
        //         for (Map.Entry<AbstractMap.SimpleEntry<String, String>, InitChunk> entry : this.initiatorChunks
        //                 .entrySet()) {
        //             this.initiatorChunks.remove(entry.getKey());
        //             this.currentlyAvailable += entry.getValue().getData().length;
        //             File currentFile = new File("peer" + peerID + "/initiated/" + entry.getKey().getKey() + "/"
        //                     + entry.getKey().getValue());
        //             if (currentFile.exists())
        //                 currentFile.delete();
        //             break;
        //         }
        //     }
        // }

        outerloop: while (maxCapacity > newCapacity) {
            if (newCapacity >= (maxCapacity - this.currentlyAvailable)) {
                currentlyAvailable = newCapacity - (maxCapacity - currentlyAvailable);
                maxCapacity = newCapacity;
            } else {
                for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storedChunks.entrySet()) {
                    removedChunks.put(entry.getKey(), entry.getValue().getData());
                    this.storedChunks.remove(entry.getKey());
                    this.currentlyAvailable += entry.getValue().getData().length;
                    File currentFile = new File("chordNode_" + peerID + "/backup/" + entry.getKey().getKey() + "/"
                            + entry.getKey().getValue());
                    if (currentFile.exists())
                        currentFile.delete();

                    continue outerloop;
                }
                break;
            }
        }

        do {
            int v = this.maxCapacity - this.currentlyAvailable;
            if (newCapacity >= v) {
                currentlyAvailable = newCapacity - (maxCapacity - currentlyAvailable);
                this.maxCapacity = newCapacity;
                break;
            }
            for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storedChunks.entrySet()) {
                removedChunks.put(entry.getKey(), entry.getValue().getData());
                this.storedChunks.remove(entry.getKey());
                this.currentlyAvailable += entry.getValue().getData().length;
                AbstractMap.SimpleEntry<String, String> remFile = new AbstractMap.SimpleEntry<>(entry.getKey().getKey(),
                        entry.getKey().getValue());

                File currentFile = new File(
                        "chordNode_" + peerID + "/backup/" + entry.getKey().getKey() + "/" + entry.getKey().getValue());
                if (currentFile.exists())
                    currentFile.delete();
                break;
            }
        } while (this.maxCapacity > newCapacity);

        return removedChunks;
    }
}