import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class SendMessage implements Runnable {

    private byte[] msg;
    private String multicastType;
    private Peer peer;

    public SendMessage(byte[] msg, String multicastType, Peer peer) {
        this.msg = msg;
        this.multicastType = multicastType;
        this.peer = peer;
    }

    String message;
    String[] args;

    @Override
    public void run() {
        String[] aux;
        AbstractMap.SimpleEntry<String, String> entry;

        switch (this.multicastType) {
            case "backup":
                this.peer.getBackupChannel().send(this.msg);
                message = new String(this.msg);
                args = message.split("\r\n\r\n");
                this.peer.verifyRD(args[0], this.msg);
                break;
            case "stored":
                message = new String(this.msg);
                aux = message.split("\r\n\r\n");
                args = aux[0].split(" ");
                entry = new AbstractMap.SimpleEntry<String, String>(args[3], args[4]);

                for (AbstractMap.SimpleEntry<String, String> e : this.peer.getStoresToSend()) {
                    if (e.getKey().equals(entry.getKey()) && e.getValue().equals(entry.getValue())) {
                        this.peer.getControlChannel().send(this.msg);
                        break;
                    }
                }
                break;
            case "delete":
                int attempts = 0;
                do {
                    this.peer.getControlChannel().send(this.msg);
                    attempts++;
                } while (attempts < 3);
                break;
            case "getchunk":
                this.peer.getControlChannel().send(this.msg);
                break;
            case "chunk":
                message = new String(this.msg);
                aux = message.split("\r\n\r\n");
                args = aux[0].split(" ");
                entry = new AbstractMap.SimpleEntry<String, String>(args[3], args[4]);

                for (AbstractMap.SimpleEntry<String, String> e : this.peer.getChunksToSend()) {
                    if (e.getKey().equals(entry.getKey()) && e.getValue().equals(entry.getValue())) {
                        if (!args[0].equals("1.0")) {
                            try {
                                this.peer.getRestoreChannel().send(aux[0].getBytes(StandardCharsets.US_ASCII));
                                InetAddress address = InetAddress.getByName("192.168.56.1");

                                Socket socket = new Socket(address, 62868+Integer.parseInt(args[4]));
                                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                                System.out.println("num sent: " + this.msg.length);
                                out.println(message);
                                
                                out.flush();
                            } catch (Exception exception) {
                                exception.printStackTrace();

                            }

                        } else {
                            this.peer.getRestoreChannel().send(this.msg);
                        }
                        break;
                    }
                }
                break;
            case "removed":
                this.peer.getControlChannel().send(this.msg);
                break;

            case "verifyBackup":
                message = new String(this.msg);
                aux = message.split("\r\n\r\n");
                args = aux[0].split(" ");
                entry = new AbstractMap.SimpleEntry<String, String>(args[1], args[2]);

                for (AbstractMap.SimpleEntry<String, String> e : this.peer.getChunksToBackup()) {
                    if (e.getKey().equals(entry.getKey()) && e.getValue().equals(entry.getValue())) {
                        String crlf = "\r\n";
                        String header = this.peer.getVersion() + " " + "PUTCHUNK" + " " + this.peer.getID() + " "
                                + args[1] + " " + args[2] + " 1" + crlf + crlf;

                        byte[] ASCIIheader = header.getBytes(StandardCharsets.US_ASCII);
                        byte[] data = aux[1].getBytes(StandardCharsets.US_ASCII);
                        byte[] backup = new byte[ASCIIheader.length + data.length];

                        System.arraycopy(ASCIIheader, 0, backup, 0, ASCIIheader.length);
                        System.arraycopy(data, 0, backup, ASCIIheader.length, data.length);

                        this.peer.getBackupChannel().send(backup);
                        break;
                    }
                }
                break;
            default:
                break;
        }
    }
}
