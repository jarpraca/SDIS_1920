import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.util.Base64;

public class MessageListener implements Runnable {

    private ChordNode chordNode;
    private SSLServerSocket serverSocket;

    public MessageListener(ChordNode chordNode) {
        this.chordNode = chordNode;

        try {
            SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            InetAddress address = InetAddress.getByName(chordNode.getKey().getAddress());
            this.serverSocket = (SSLServerSocket) ssf.createServerSocket(chordNode.getKey().getPort(), 0, address);

            this.serverSocket.setNeedClientAuth(false);
            String[] protocols = new String[1];
            protocols[0] = "TLSv1";
            this.serverSocket.setEnabledProtocols(protocols);
            this.serverSocket.setEnabledCipherSuites(ssf.getSupportedCipherSuites());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                SSLSocket socket = (SSLSocket) this.serverSocket.accept();

                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String msgRead = input.readLine();
                String[] message = msgRead.split(" ");

                switch (message[0]) {
                    case "BACKUP":
                        this.chordNode.receiveBackup(message[1], message[2]);
                        break;
                    case "DELETE_PROTOCOL":
                        this.chordNode.protocolManager.delete(message[1]);
                        break;
                    case "RESTORE_PROTOCOL":
                        this.chordNode.protocolManager.restore(message[1]);
                        break;
                    case "RECLAIM_PROTOCOL":
                        this.chordNode.reclaim(message[1]);
                        break;
                    case "PUTCHUNK":
                        System.out.println("INSIDE PUTCHUNK");
                        DataInputStream in = new DataInputStream(socket.getInputStream());
                        System.out.println(message[5]);
                        int size = Integer.parseInt(message[5]);
                        byte[] buffer = new byte[size];
                        int n;

                        do {
                            // in.readFully(buffer);
                            n = in.read(buffer, 0, size);
                        } while (n > 0);

                        in.close();

                        this.chordNode.receivePutChunk(message[1], message[2], message[3], message[4], message[5],
                                buffer);
                        break;
                    case "STORED":
                        System.out.println(msgRead);
                        if (this.chordNode.getStorage().getInitiatedChunk(message[2], message[1]) != null)
                            this.chordNode.getStorage().getInitiatedChunk(message[2], message[1])
                                    .addStorer(new Finger(message[3], message[4]));
                        break;
                    case "DELETE":
                        this.chordNode.getStorage().deleteChunks(message[1], this.chordNode.getKey().getID());
                        break;
                    case "GETCHUNK":
                        this.chordNode.receiveGetChunk(message[1], message[2], message[3], message[4]);
                        break;
                    case "CHUNK":
                        DataInputStream in_buf = new DataInputStream(socket.getInputStream());

                        int chunk_size = Integer.parseInt(message[3]);
                        byte[] buf = new byte[chunk_size];
                        int num;

                        do {
                           // in_buf.readFully(buf);
                            num = in_buf.read(buf, 0, chunk_size);
                        } while (num > 0);

                        in_buf.close();

                        this.chordNode.receiveChunk(buf, message[1], message[2]);
                        break;

                    case "BACKUP_CHUNK":
                        System.out.println("INSIDE BACKUP_CHUNK");
                        DataInputStream inbuf = new DataInputStream(socket.getInputStream());

                        int chunkSize = Integer.parseInt(message[6]);
                        byte[] buff = new byte[chunkSize];
                        int number;

                        do {
                          //  inbuf.readFully(buff);
                            number = inbuf.read(buff, 0, chunkSize);
                        } while (number > 0);

                        inbuf.close();
                        this.chordNode.successorBackup(buff, message[1], message[2], message[3], message[4],
                                message[5]);
                        break;
                    case "FIND_SUCCESSOR":
                        if (message.length == 4) {
                            this.chordNode.findSuccessor(message);
                        } else {
                            this.chordNode.fingerSearch(message);
                        }
                        break;
                    case "TELL_SUCCESSOR":
                        Finger predecessor = new Finger(message[1], message[2]);
                        this.chordNode.setPredecessor(predecessor);
                        break;
                    case "SUCCESSOR_IS":
                        this.chordNode.setSuccessor(new Finger(message[1], message[2]));
                        break;
                    case "PREDECESSOR_IS":
                        this.chordNode.setPredecessor(new Finger(message[1], message[2]));
                        break;

                    case "FINGER_INDEX":
                        this.chordNode.setFingerTable(message);
                        break;
                    case "GET_PREDECESSOR":
                        String msg = "CONFIRM_PREDECESSOR " + this.chordNode.getPredecessor().getID();

                        this.chordNode.getExecutor()
                                .execute(new SendMessage(msg, message[1], Integer.parseInt(message[2]), chordNode));
                        break;
                    case "CONFIRM_PREDECESSOR":
                        // this.chordNode.updateNodes(message);
                        break;
                    case "MOVECHUNK":
                        DataInputStream mov = new DataInputStream(socket.getInputStream());

                        int cSize = Integer.parseInt(message[4]);
                        byte[] movBuff = new byte[cSize];
                        int movNumber;

                        do {
                          //  mov.readFully(movBuff);
                            movNumber = mov.read(movBuff, 0, cSize);
                        } while (movNumber > 0);

                        mov.close();

                        this.chordNode.moveChunk(message, movBuff);
                        break;
                    case "CHANGE_STORED":
                        this.chordNode.changeStored(message);
                        break;
                    case "DELETE_STORED":
                        this.chordNode.deleteStored(message);
                        break;
                    default:
                        break;
                }
            }
        } catch (Exception e) {

        }
    }
}