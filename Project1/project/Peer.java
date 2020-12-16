import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.*;
import java.io.*;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Peer implements RMInterface {

    private int id;
    private String version;
    private String accessPoint;
    private MulticastBackup backupChannel;
    private MulticastRestore restoreChannel;
    private MulticastControl controlChannel;
    private ScheduledThreadPoolExecutor scheduledExecutor;
    private ThreadPoolExecutor executor;
    private Storage storage;
    private ArrayList<AbstractMap.SimpleEntry<String, String>> chunksToSend;
    private ArrayList<AbstractMap.SimpleEntry<String, String>> chunksToBackup;
    private ArrayList<AbstractMap.SimpleEntry<String, String>> storesToSend;
    private ArrayList<AbstractMap.SimpleEntry<String, String>> waitingRestores;
    private HashMap<String, Integer> chunksRestored;

    public Peer(String[] args) {
        this.version = args[0];
        this.id = Integer.parseInt(args[1]);
        this.accessPoint = args[2];
        String[] MC = args[3].split(" ");
        String[] MDB = args[4].split(" ");
        String[] MDR = args[5].split(" ");

        this.controlChannel = new MulticastControl(MC[0], Integer.parseInt(MC[1]), this);

        this.backupChannel = new MulticastBackup(MDB[0], Integer.parseInt(MDB[1]), this);
        this.restoreChannel = new MulticastRestore(MDR[0], Integer.parseInt(MDR[1]), this);

        scheduledExecutor = new ScheduledThreadPoolExecutor(20);
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(200);
        storage = new Storage();
        chunksToSend = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        chunksToBackup = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        storesToSend = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        waitingRestores = new ArrayList<AbstractMap.SimpleEntry<String, String>>();
        chunksRestored = new HashMap<String, Integer>();
        try {
            RMInterface stub = (RMInterface) UnicastRemoteObject.exportObject(this, 0);
            Registry registry = LocateRegistry.getRegistry();

            try {
                registry.lookup(args[1]);
                registry.unbind(args[1]);
            } catch (Exception e) {
            }

            registry.bind(args[1], stub);

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public int getID() {
        return this.id;
    }

    public ThreadPoolExecutor getExecutor() {
        return this.executor;
    }

    public ScheduledThreadPoolExecutor getScheduledExecutor() {
        return this.scheduledExecutor;
    }

    public String getVersion() {
        return this.version;
    }

    public ArrayList<AbstractMap.SimpleEntry<String, String>> getChunksToSend() {
        return chunksToSend;
    }

    public ArrayList<AbstractMap.SimpleEntry<String, String>> getChunksToBackup() {
        return chunksToBackup;
    }

    public ArrayList<AbstractMap.SimpleEntry<String, String>> getStoresToSend() {
        return storesToSend;
    }

    public ArrayList<AbstractMap.SimpleEntry<String, String>> getWaitingRestores() {
        return waitingRestores;
    }

    public Storage getStorage() {
        return storage;
    }

    @Override
    public void backup(String filePath, int rd) throws IOException {
        FileData file = new FileData(filePath);
        storage.storeInitiatedFile(file.getFile(), file.getId());

        if (!this.version.equals("1.0")) {
            updateDeleteSer(file.getId());
        }

        for (Chunk c : file.getChunks()) {

            c.setDesiredRd(rd);

            String crlf = "\r\n";
            String header = this.version + " " + "PUTCHUNK" + " " + this.id + " " + file.getId() + " " + c.getNumber()
                    + " " + c.getDesiredRd() + crlf + crlf;

            byte[] ASCIIheader = header. getBytes(StandardCharsets.US_ASCII);
            byte[] data = c.getData();
            byte[] message = new byte[ASCIIheader.length + data.length];

            System.arraycopy(ASCIIheader, 0, message, 0, ASCIIheader.length);
            System.arraycopy(data, 0, message, ASCIIheader.length, data.length);

            InitChunk chunk = new InitChunk(c.getDesiredRd(), c.getNumber(), c.getFileID(), c.getData());

            if (storage.storeInitiatedChunk(Integer.toString(c.getNumber()), chunk, this.id))
                this.executor.execute((Runnable) new SendMessage(message, "backup", this));
            else {
                System.out.println("No space available to execute backup protocol");
            }
        }
    }

    public void verifyRD(String args, byte[] message) {
        String[] header = args.split(" ");
        int waitingTime = 1000;
        int attempts = 1;
        try {
            while (attempts < 5) {
                Thread.sleep(waitingTime);
                if (this.storage.getInitiatedChunk(header[4], header[3]).getRD() >= this.storage
                        .getInitiatedChunk(header[4], header[3]).getDesiredRd()) {
                    System.out.println("Backup successful with desired rd");
                    return;
                }

                waitingTime *= 2;
                attempts++;
                this.backupChannel.send(message);
                this.backupChannel.send(message);
            }

            System.out.println("Couldn't backup with desired replication degree. The actual rd is: "
                    + this.storage.getInitiatedChunk(header[4], header[3]).getRD());

        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void receivePutChunk(String msg, String id, String chunkNum, String fileID, String version) {
        if (this.id == Integer.parseInt(id) || this.storage.containsInitiatedChunk(chunkNum, fileID)) {
            return;
        }

        byte[] data = msg. getBytes(StandardCharsets.US_ASCII);

        Chunk chunk = new Chunk(Integer.parseInt(chunkNum), data, fileID);
        storage.storeChunk(chunkNum, chunk, this.id);

        AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(fileID, chunkNum);
        chunksToBackup.remove(entry);

        Random rand = new Random();
        long delay = rand.nextInt(401);
        String crlf = "\r\n";
        String header = version + " " + "STORED" + " " + this.id + " " + fileID + " " + chunkNum + crlf + crlf;
        byte[] message = header. getBytes(StandardCharsets.US_ASCII);

        storesToSend.add(entry);
        scheduledExecutor.schedule((Runnable) new SendMessage(message, "stored", this), delay, TimeUnit.MILLISECONDS);
    }

    public void receiveStored(String fileID, String chunkNum, int peerID, String version) {
        if (storage.containsInitiatedChunk(chunkNum, fileID) && peerID != this.id) {
            this.storage.getInitiatedChunk(chunkNum, fileID).addStorer(peerID);
            return;
        }
        if (this.id == peerID) {
            return;
        }

        if (!version.equals("1.0")) {
            ArrayList<Integer> rd = this.storage.getStoredChunkRd(fileID, chunkNum);
            if (rd.size() == 2 && rd.get(0) >= rd.get(1)) {
                AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(fileID,
                        chunkNum);
                
                synchronized (this) {
                    for (AbstractMap.SimpleEntry<String, String> e : storesToSend) {
                        if (entry != null && e != null && e.getKey().equals(entry.getKey()) && e.getValue().equals(entry.getValue())) {
                            storesToSend.remove(entry);
                            this.storage.deleteStoredChunk(fileID, chunkNum, this.id);
                            break;
                        }
                    }
                }
            }
        }

    }

    @Override
    public void delete(String filePath) {
        FileData file = new FileData(filePath);
        if (storage.containsFile(file.getId())) {
            String crlf = "\r\n";
            String header = this.version + " " + "DELETE" + " " + this.id + " " + file.getId() + crlf + crlf;
            byte[] message = header. getBytes(StandardCharsets.US_ASCII);

            if (!this.version.equals("1.0"))
                updateEnhancement(header);
            this.executor.execute((Runnable) new SendMessage(message, "delete", this));
        } else {
            System.out.println("The specified file wasn't previously backed up from this peer");
        }
    }

    public void updateEnhancement(String message) {
        try {
            File file = new File("deleteEnhancement.ser");
            if (!file.exists())
                file.createNewFile();
            FileOutputStream outFile = new FileOutputStream("deleteEnhancement.ser", true);
            ObjectOutputStream output = new ObjectOutputStream(outFile);
            output.writeObject(message);
            output.close();
            outFile.close();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    public void deleteEnhancement(String content) {
        String[] files = content.split("\r\n\r\n");

        for (int i = 0; i < files.length; i++) {
            String[] args = files[i].split(" ");
            this.storage.deleteChunks(args[3], this.id);
        }
    }

    public synchronized void updateDeleteSer(String fileID) {
        try {
            File file = new File("deleteEnhancement.ser");
            if (!file.exists())
                return;

            FileInputStream fis = new FileInputStream("deleteEnhancement.ser");
            ObjectInputStream input = new ObjectInputStream(fis);
            String content = (String) input.readObject();

            input.close();
            fis.close();

            file.delete();
            file.createNewFile();

            FileOutputStream outFile = new FileOutputStream("deleteEnhancement.ser");
            ObjectOutputStream output = new ObjectOutputStream(outFile);

            String[] chunks = content.split("\r\n\r\n");
            System.out.println(chunks.length);
            for (int i = 0; i < chunks.length; i++) {
                String[] headerArgs = chunks[i].split(" ");
                if (!headerArgs[3].equals(fileID)) {
                    synchronized (this) {
                        output.writeObject(chunks[i]);
                    }
                }
            }
            System.out.println("hereee");
            output.close();
            outFile.close();
            System.out.println("hereee2");
        } catch (Exception e) {
            System.out.println("in update delete ser: " + e.toString());
            e.printStackTrace();
        }

    }

    public void receiveDelete(String fileID) {
        this.storage.deleteChunks(fileID, this.id);
    }

    @Override
    public void restore(String filePath) {

        String crlf = "\r\n";
        FileData file = new FileData(filePath);
        int numChunks = file.getChunks().size();
        int chunkId = 1;

        while (chunkId <= numChunks) {
            String header = this.version + " " + "GETCHUNK" + " " + this.id + " " + file.getId() + " " + chunkId + crlf
                    + crlf;
            byte[] message = header. getBytes(StandardCharsets.US_ASCII);

            if (!this.version.equals("1.0")) {
                executor.execute((Runnable) new RestoreTCP(this, chunkId));
            }

            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(file.getId(), Integer.toString(chunkId));
            waitingRestores.add(entry);

            this.executor.execute((Runnable) new SendMessage(message, "getchunk", this));
            header = null;
            message = null;
            chunkId++;
        }

    }

    public void receiveGetChunk(String fileID, String chunkNum, String peerID) {
        if (this.id == Integer.parseInt(peerID)) {
            return;
        }
        String crlf = "\r\n";

        Chunk chunk = this.storage.getStoredChunk(chunkNum, fileID);
        if (chunk != null) {
            byte[] data = chunk.getData();

            String header = this.version + " " + "CHUNK" + " " + this.id + " " + fileID + " " + chunkNum + crlf + crlf;
            byte[] ASCIIheader = header. getBytes(StandardCharsets.US_ASCII);
            byte[] message = new byte[ASCIIheader.length + data.length];

            System.arraycopy(ASCIIheader, 0, message, 0, ASCIIheader.length);
            System.arraycopy(data, 0, message, ASCIIheader.length, data.length);

            Random rand = new Random();
            long delay = rand.nextInt(401);
            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(fileID,
                    chunkNum);
            chunksToSend.add(entry);
            scheduledExecutor.schedule((Runnable) new SendMessage(message, "chunk", this), delay,
                    TimeUnit.MILLISECONDS);
        }

    }

    public void receiveChunk(byte[] data, String fileID, String chunkNum, String peerID, String version) {
        if (this.storage.containsFile(fileID)) {
            if(chunksRestored.get(fileID) != null)
                chunksRestored.put(fileID, chunksRestored.get(fileID) + 1 );
            else
                chunksRestored.put(fileID, 1);
            
            InitChunk chunk = new InitChunk(this.storage.getInitiatedChunk(chunkNum, fileID).getDesiredRd(),
                    Integer.parseInt(chunkNum), fileID, data);
            
            FileData file = new FileData(this.storage.getInitiatorFiles().get(fileID).getPath());
            System.out.println(("num chunks: " + file.getNumChunks()));
            if(chunksRestored.get(fileID) >= file.getNumChunks()){
                this.storage.restoreFile(fileID,file.getNumChunks(), this.id);
                chunksRestored.put(fileID,  0);
            }

            if (version.equals("1.0"))
                this.storage.restoreChunk(chunk, this.id);
        } else {
            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(fileID,
                    chunkNum);
            chunksToSend.remove(entry);
        }
    }

    @Override
    public void reclaim(String newCapacity) {
        ArrayList<AbstractMap.SimpleEntry<String, String>> removedFiles = this.storage
                .reclaimMemory(Integer.parseInt(newCapacity) * 1000, this.id);
        for (AbstractMap.SimpleEntry<String, String> entry : removedFiles) {
            String crlf = "\r\n";
            String header = this.version + " " + "REMOVED" + " " + this.id + " " + entry.getKey() + " "
                    + entry.getValue() + crlf + crlf;

            this.executor.execute((Runnable) new SendMessage(header.getBytes(StandardCharsets.US_ASCII), "removed", this));
        }
    }

    public void receiveRemoved(String fileID, String chunkNum, String peerID) {
        if (this.id == Integer.parseInt(peerID)) {
            return;
        }

        if (this.storage.containsStoredChunk(chunkNum, fileID)) {
            byte[] data = this.storage.getStoredChunk(chunkNum, fileID).getData();

            String crlf = "\r\n";
            String header = this.id + " " + fileID + " " + chunkNum + crlf + crlf;
            byte[] ASCIIheader = header. getBytes(StandardCharsets.US_ASCII);
            byte[] message = new byte[ASCIIheader.length + data.length];

            System.arraycopy(ASCIIheader, 0, message, 0, ASCIIheader.length);
            System.arraycopy(data, 0, message, ASCIIheader.length, data.length);

            Random rand = new Random();
            long delay = rand.nextInt(401);
            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(fileID,
                    chunkNum);
            chunksToBackup.add(entry);
            scheduledExecutor.schedule((Runnable) new SendMessage(message, "verifyBackup", this), delay,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void state() {
        System.out.println("");
        System.out.println("~~~~~~ STATE ~~~~~~");
        System.out.println("");
        System.out.println("~ Info");
        System.out.println("\tPeer " + this.id);
        System.out.println("");
        System.out.println("~ Storage");
        System.out.println("\tCapacity: " + (double) this.storage.getCapacity() / 1000 + " KB");
        System.out.println("\tIn use: "
                + (double) (this.storage.getCapacity() - this.storage.getCurrentlyAvailable()) / 1000 + " KB");
        System.out.println("\tAvailable: " + (double) this.storage.getCurrentlyAvailable() / 1000 + " KB");
        System.out.println("");

        System.out.println("~ Initiated Files (" + this.storage.getInitiatorFiles().size() + ")");
        for (Map.Entry<String, File> entry : this.storage.getInitiatorFiles().entrySet()) {
            System.out.println("\t* File");
            System.out.println("\t\tID: " + entry.getKey());
            System.out.println("\t\tName: " + entry.getValue().getName());
            System.out.println("\t\tPath: " + entry.getValue().getPath());
            System.out.println("\t\tSize: " + (double) entry.getValue().length() / 1000 + " KB");
            System.out.println(
                    "\t\tDesired Replication Degree: " + this.storage.getStoredChunkRd(entry.getKey(), "1").get(1));

            System.out.println("\t\tChunks (" + this.storage.getInitiatorFileChunks(entry.getKey()).size() + ")");
            for (InitChunk chunk : this.storage.getInitiatorFileChunks(entry.getKey())) {
                System.out.println("");
                System.out.println("\t\t\tID: " + chunk.getNumber());
                System.out.println("\t\t\tPerceived Replication Degree: " + chunk.getCurrentRd());
                System.out.println("\t\t\t_______________________________");
            }
            System.out.println("");
        }

        if (this.storage.getInitiatorFiles().size() == 0)
            System.out.println("");

        System.out.println("~ Stored Chunks (" + this.storage.getStoredChunks().size() + ")");
        for (Map.Entry<AbstractMap.SimpleEntry<String, String>, Chunk> entry : this.storage.getStoredChunks()
                .entrySet()) {
            System.out.println("\t* Chunk");
            System.out.println("\t\tID: " + entry.getKey().getValue());
            System.out.println("\t\tFile ID: " + entry.getKey().getKey());
            System.out.println("\t\tPerceived Replication Degree: "
                    + this.storage.getStoredChunkRd(entry.getKey().getKey(), entry.getKey().getValue()).get(0));
            System.out.println("");
        }
        System.out.println("");
    }

    public static void main(String args[]) {
        if (args.length != 6) {
            System.out.println("Protocol usage: java Peer <version> <peer_id> <access_point> <MC> <MDB> <MDR>");
            System.exit(-1);
        }

    for(int i=0; i<args.length;i++)
        System.out.println(args[i]);

        Peer peer = new Peer(args);

        try {
            File file = new File("peer" + args[1] + "/storage.ser");

            if (file.exists()) {
                FileInputStream fis = new FileInputStream("peer" + args[1] + "/storage.ser");
                ObjectInputStream input = new ObjectInputStream(fis);
                peer.storage = (Storage) input.readObject();
                input.close();
                fis.close();

                if (!peer.getVersion().equals("1.0")) {
                    System.out.println("enhancement delete");
                    file = new File("deleteEnhancement.ser");
                    if (file.exists()) {
                        fis = new FileInputStream("deleteEnhancement.ser");
                        input = new ObjectInputStream(fis);

                        String content = (String) input.readObject();
                        peer.deleteEnhancement(content);
                        input.close();
                        fis.close();
                    }
                }

            }

        } catch (Exception e) {
            e.printStackTrace();

        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Saving state...");
                try {
                    File storageFile = new File("peer" + args[1] + "/storage.ser");

                    if (!storageFile.exists()) {
                        storageFile.getParentFile().mkdirs();
                        storageFile.createNewFile();
                    }

                    FileOutputStream outFile = new FileOutputStream("peer" + args[1] + "/storage.ser");
                    ObjectOutputStream output = new ObjectOutputStream(outFile);
                    output.writeObject(peer.storage);
                    output.close();
                    outFile.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        peer.executor.execute(peer.backupChannel);
        peer.executor.execute(peer.controlChannel);
        peer.executor.execute(peer.restoreChannel);
    }

    public MulticastBackup getBackupChannel() {
        return this.backupChannel;
    }

    public MulticastControl getControlChannel() {
        return this.controlChannel;
    }

    public MulticastRestore getRestoreChannel() {
        return this.restoreChannel;
    }
}