import java.util.Base64;
import java.util.HashMap;
import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.Random;
import java.security.*;
import java.net.*;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;

public class ChordNode {

    private final Finger key;
    private HashMap<Integer, Finger> fingers;

    private Finger successor;
    private Finger predecessor;
    private ThreadPoolExecutor executor;
    private ScheduledExecutorService scheduledExecutor;

    private Storage storage;
    public ProtocolManager protocolManager;
    private HashMap<String, Integer> chunksRestored;

    public ChordNode(String address, String port) {
        if (address.equals("localhost")) {
            try {
                InetAddress ip = InetAddress.getLocalHost();
                address = ip.getHostAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        chunksRestored = new HashMap<String, Integer>();
        key = new Finger(address, port);
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(200);
        scheduledExecutor = Executors.newScheduledThreadPool(100);
        protocolManager = new ProtocolManager(this);
        fingers = new HashMap<Integer, Finger>(8);
        storage = new Storage();
        for (int i = 0; i < 8; i++)
            fingers.put(i, new Finger(address, port));

        System.setProperty("javax.net.ssl.keyStore", "server.keys");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStore", "truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");

        System.out.println("ID: " + key.getID());
        this.executor.execute((Runnable) new MessageListener(this));
        scheduledExecutor.scheduleAtFixedRate(new StabilizeChord(this), 10, 10, TimeUnit.SECONDS);

    }

    public ChordNode(String address, String port, String existingAddress, String existingPort) {

        if (address.equals("localhost")) {
            try {
                InetAddress ip = InetAddress.getLocalHost();
                address = ip.getHostAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (existingAddress.equals("localhost")) {
            try {
                InetAddress ip = InetAddress.getLocalHost();
                existingAddress = ip.getHostAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        chunksRestored = new HashMap<String, Integer>();
        key = new Finger(address, port);
        executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(200);
        protocolManager = new ProtocolManager(this);
        fingers = new HashMap<Integer, Finger>(8);
        scheduledExecutor = Executors.newScheduledThreadPool(100);
        storage = new Storage();

        System.setProperty("javax.net.ssl.keyStore", "server.keys");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStore", "truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");

        this.executor.execute((Runnable) new MessageListener(this));
        System.out.println("ID: " + key.getID());

        join(new Finger(existingAddress, existingPort));
        scheduledExecutor.scheduleAtFixedRate(new StabilizeChord(this), 10, 10, TimeUnit.SECONDS);
    }

    public void receiveBackup(String filePath, String rd) {
        protocolManager.backup(filePath, Integer.parseInt(rd));
    }

    public void receivePutChunk(String fileID, String chunkNum, String rd, String originPeerAddress,
            String originPeerPort, byte[] data) {
        System.out.println("receive put chunk");

        if (storage.getInitiatedChunk(chunkNum, fileID) != null)
            return;
        Chunk chunk = new Chunk(Integer.parseInt(chunkNum), data, fileID, originPeerAddress,
                Integer.parseInt(originPeerPort));

        if (storage.storeChunk(chunkNum, chunk, this.key.getID())) {
            Random rand = new Random();
            long delay = rand.nextInt(401);
            String crlf = "\r\n";
            String header = "STORED " + fileID + " " + chunkNum + " " + this.getAddress() + " " + this.getPort() + " "
                    + crlf;

            scheduledExecutor.schedule(
                    (Runnable) new SendMessage(header, originPeerAddress, Integer.parseInt(originPeerPort), this),
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    public void receiveGetChunk(String fileID, String chunkNum, String addressToSend, String portToSend) {

        Chunk chunk = this.storage.getStoredChunk(chunkNum, fileID);
        if (chunk != null) {
            byte[] data = chunk.getData();

            String header = "CHUNK " + fileID + " " + chunkNum + " " + data.length + "\n";

            executor.execute(
                    (Runnable) new SendBackup(header, data, addressToSend, Integer.parseInt(portToSend), this));

        }

    }

    public void receiveChunk(byte[] data, String fileID, String chunkNum) {
        if (chunksRestored.get(fileID) != null)
            chunksRestored.put(fileID, chunksRestored.get(fileID) + 1);
        else
            chunksRestored.put(fileID, 1);

        System.out.println("fileID: " + fileID + " num: " + chunkNum);

        FileData file = new FileData(this.storage.getInitiatorFiles().get(fileID).getPath());

        if (chunksRestored.get(fileID) >= file.getNumChunks()) {
            this.storage.restoreFile(fileID, file.getNumChunks(), this.key.getID());
            chunksRestored.put(fileID, 0);
        }
    }

    public void reclaim(String newCapacity) {
        ConcurrentHashMap<AbstractMap.SimpleEntry<String, String>, byte[]> removedChunks = this.storage
                .reclaimMemory(Integer.parseInt(newCapacity) * 1000, key.getID());

        removedChunks.forEach((k, v) -> {
            System.out.println("chunk removed");

            String chunk_header = "BACKUP_CHUNK " + 1 + " " + k.getKey() + " " + k.getValue() + " " + key.getAddress()
                    + " " + key.getPort() + " " + v.length + "\n";
            executor.execute((Runnable) new SendBackup(chunk_header, v, key.getAddress(), key.getPort(), this));
        });

    }

    public Finger getKey() {
        return key;
    }

    public ScheduledExecutorService getScheduledExecutor() {
        return scheduledExecutor;
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public String getAddress() {
        return this.key.getAddress();
    }

    public Integer getPort() {
        return this.key.getPort();
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Storage getStorage() {
        return storage;
    }

    public Finger getSuccessor() {
        return successor;
    }

    public void setSuccessor(Finger successor) {
        this.successor = successor;
        for (int i = 0; i < 8; i++) {
            fingers.put(i, successor);
        }

        this.executor.execute(
                (Runnable) new SendMessage("PREDECESSOR_IS " + this.key.getAddress() + " " + this.key.getPort(),
                        successor.getAddress(), successor.getPort(), this));
    }

    public Finger getPredecessor() {
        return predecessor;
    }

    public void setPredecessor(Finger predecessor) {
        this.predecessor = predecessor;
    }

    public HashMap<Integer, Finger> getFingers() {
        return fingers;
    }

    public void join(Finger finger) {
        predecessor = null;
        String message = "FIND_SUCCESSOR " + this.key.getID() + " " + this.key.getAddress() + " " + this.key.getPort();
        this.executor.execute((Runnable) new SendMessage(message, finger.getAddress(), finger.getPort(), this));
    }

    // public void updateNodes(String[] message) {
    // if (!((new BigInteger(message[1])).compareTo(this.key.getID()) == 0)) {
    // fixFingers();
    // }
    // }

    // public void stabilize() {

    // this.executor.execute(
    // (Runnable) new SendMessage("GET_PREDECESSOR " + this.key.getAddress() + " " +
    // this.key.getPort(),
    // successor.getAddress(), successor.getPort(), this));

    // }

    public BigInteger getFingerID(BigInteger key, int i) {
        return ((key.add(new BigInteger("2").pow(i))).mod(new BigInteger("2").pow(8)));
    }

    public void fixFingers() {
        for (int next = 0; next < 8; next++) {
            BigInteger id = getFingerID(this.key.getID(), next);
            String message = "FIND_SUCCESSOR " + id + " " + next + " " + this.key.getAddress() + " "
                    + this.key.getPort();
            this.executor
                    .execute((Runnable) new SendMessage(message, successor.getAddress(), successor.getPort(), this));
        }
    }

    public boolean isInBetween(BigInteger id, BigInteger predNode, BigInteger succNode) {
        if (predNode.compareTo(succNode) > 0) {
            if (id.compareTo(predNode) > 0 || id.compareTo(succNode) < 0) {
                return true;
            }

        } else if (id.compareTo(predNode) > 0 && id.compareTo(succNode) < 0) {
            return true;
        }

        return false;
    }

    public void fingerSearch(String[] message) {

        BigInteger id = new BigInteger(message[1]);
        int index = Integer.parseInt(message[2]);
        String ipAddress = message[3];
        int port = Integer.parseInt(message[4]);
        if (isInBetween(id, this.predecessor.getID(), this.key.getID()) || this.key.getID().compareTo(id) == 0) {
            this.executor.execute((Runnable) new SendMessage(
                    "FINGER_INDEX " + index + " " + this.key.getAddress() + " " + this.key.getPort(), ipAddress, port,
                    this));
        } else {
            this.executor.execute(
                    (Runnable) new SendMessage("FIND_SUCCESSOR " + id + " " + index + " " + ipAddress + " " + port,
                            successor.getAddress(), successor.getPort(), this));
        }
    }

    public void setFingerTable(String[] message) {
        int index = Integer.parseInt(message[1]);
        String ipAddress = message[2];
        int port = Integer.parseInt(message[3]);
        fingers.put(index, new Finger(ipAddress, port));
    }

    public void findSuccessor(String[] message) {

        BigInteger id = new BigInteger(message[1]);
        String ipAddress;
        int port;
        if (message.length == 4) {
            ipAddress = message[2];
            port = Integer.parseInt(message[3]);
        } else {
            ipAddress = message[3];
            port = Integer.parseInt(message[4]);
        }

        if (this.successor == null || this.key.getID().compareTo(this.successor.getID()) == 0) {
            this.executor.execute((Runnable) new SendMessage(
                    "SUCCESSOR_IS " + this.key.getAddress() + " " + this.key.getPort(), ipAddress, port, this));
            setSuccessor(new Finger(ipAddress, port));

        } else if (isInBetween(id, this.key.getID(), this.successor.getID())
                || id.compareTo(this.successor.getID()) == 0) {
            this.executor.execute((Runnable) new SendMessage(
                    "SUCCESSOR_IS " + this.successor.getAddress() + " " + this.successor.getPort(), ipAddress, port,
                    this));
            setSuccessor(new Finger(ipAddress, port));

        } else {
            Finger nextFinger = closestPrecedingNode(id);
            this.executor.execute((Runnable) new SendMessage("FIND_SUCCESSOR " + id + " " + ipAddress + " " + port,
                    nextFinger.getAddress(), nextFinger.getPort(), this));
        }
    }

    public Finger closestPrecedingNode(BigInteger id) {
        for (int i = fingers.size() - 1; i >= 0; i--) {
            if (isInBetween(fingers.get(i).getID(), this.key.getID(), id))
                return fingers.get(i);
        }
        return this.predecessor;
    }

    public void successorBackup(byte[] chunk, String rd, String fileID, String chunkNum, String originalAddress,
            String originalPort) {
        int i = 1;
        int j = 1;

        String header = "PUTCHUNK " + fileID + " " + chunkNum + " " + rd + " " + originalAddress + " " + originalPort
                + " " + chunk.length;

        executor.execute((Runnable) new SendBackup(header, chunk, successor.getAddress(), successor.getPort(), this));
        while (i <= Integer.parseInt(rd) && j < fingers.size()) {
            if (fingers.get(j).getID().compareTo(fingers.get(j - 1).getID()) != 0) {
                executor.execute((Runnable) new SendBackup(header, chunk, fingers.get(j).getAddress(),
                        fingers.get(j).getPort(), this));
                i++;
            }
            j++;
        }

        if (i < Integer.parseInt(rd)) {
            String address = successor.getAddress();
            Integer port = successor.getPort();
            Integer chunk_rd = Integer.parseInt(rd) - i;
            String chunk_header = "BACKUP_CHUNK " + chunk_rd + " " + fileID + " " + chunkNum + " " + originalAddress
                    + " " + originalPort + " " + chunk.length + "\n";
            executor.execute((Runnable) new SendBackup(chunk_header, chunk, address, port, this));
        }

    }

    public void moveChunk(String[] message, byte[] data) {
        BigInteger peerID = new BigInteger(message[1]);
        String fileID = message[2];
        String number = message[3];
        String originalPeerAddress = message[5];
        Integer originalPeerPort = Integer.parseInt(message[6]);

        Chunk chunk = new Chunk(Integer.parseInt(number), data, fileID, originalPeerAddress, originalPeerPort);

        if (storage.storeChunk(number, chunk, peerID)) {
            String oldAddress = message[7];
            Integer oldPort = Integer.parseInt(message[8]);
            String newAdress = this.key.getAddress();
            Integer newPort = this.key.getPort();
            String msg = "CHANGE_STORED " + fileID + " " + number + " " + oldAddress + " " + oldPort + " " + newAdress
                    + " " + newPort;

            protocolManager.sendRequest(msg, originalPeerAddress, originalPeerPort);
            return;
        }

        BigInteger successorID = new BigInteger(message[9]);

        if (successorID.compareTo(this.successor.getID()) == 0) {
            // send delete_store message
            String oldAddress = message[7];
            Integer oldPort = Integer.parseInt(message[8]);
            String m = "DELETE_STORED " + fileID + " " + number + " " + oldAddress + " " + oldPort;
            protocolManager.sendRequest(m, originalPeerAddress, originalPeerPort);
        } else {
            // send msg to successor
            String msgAppend = message[0];
            for (int i = 1; i < message.length; i++) {
                msgAppend += " " + message[i];
            }
            this.getExecutor().execute(
                    (Runnable) new SendBackup(msgAppend, data, successor.getAddress(), successor.getPort(), this));
        }
    }

    public void changeStored(String[] message) {
        String fileID = message[1];
        String chunkNum = message[2];
        String oldAddress = message[3];
        Integer oldPort = Integer.parseInt(message[4]);
        String newAddress = message[5];
        Integer newPort = Integer.parseInt(message[6]);
        InitChunk init = this.storage.getInitiatedChunk(chunkNum, fileID);
        init.removeStorer(new Finger(oldAddress, oldPort));
        init.addStorer(new Finger(newAddress, newPort));
        this.storage.putInitiatedChunk(init, fileID, chunkNum);
    }

    public void deleteStored(String[] message) {
        String fileID = message[1];
        String chunkNum = message[2];
        String address = message[3];
        Integer port = Integer.parseInt(message[4]);
        InitChunk init = this.storage.getInitiatedChunk(chunkNum, fileID);
        init.removeStorer(new Finger(address, port));
    }

    public void notifyLeaving() {

        // sends node's files to backup
        storage.getStoredChunks().forEach((k, v) -> {
            String message = "MOVECHUNK " + v.getID() + " " + v.getFileID() + " " + v.getNumber() + " "
                    + v.getData().length + " " + v.getOriginalPeerAddress() + " " + v.getOriginalPeerPort() + " "
                    + this.key.getAddress() + " " + this.key.getPort() + " " + this.successor.getID() + " " + "\r\n";
            this.getExecutor().execute(
                    (Runnable) new SendBackup(message, v.getData(), successor.getAddress(), successor.getPort(), this));
        });

        // try {
        //     Thread.sleep(3000);
        // } catch (Exception e) {

        // }
        
        // send new successor to predecessor
        String msg = "SUCCESSOR_IS " + this.successor.getAddress() + " " + this.successor.getPort();
        this.executor.execute((Runnable) new SendMessage(msg, predecessor.getAddress(), predecessor.getPort(), this));
        try {
            Thread.sleep(3000);
        } catch (Exception e) {

        }
    }

}
