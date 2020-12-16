import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MulticastBackup implements Runnable {

    private InetAddress group;
    private MulticastSocket socket;
    private int port;
    private Peer peer;

    public MulticastBackup(String address, int port, Peer peer) {
        try {
            this.group = InetAddress.getByName(address);
            this.port = port;
            this.peer = peer;
            this.socket = new MulticastSocket(port);
            this.socket.joinGroup(this.group);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void send(byte[] message) {
        try {
            DatagramPacket packet = new DatagramPacket(message, message.length, this.group, this.port);
            socket.send(packet);
        } 
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void run() {
        System.out.println("running backup channel...");
        try {
            // ByteArrayOutputStream b = new ByteArrayOutputStream();
            // byte[] buffer = new byte[];
            byte[] buffer = new byte[65000];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);
                byte[] buf = new byte[receivePacket.getLength()];
                System.arraycopy(receivePacket.getData(), 0, buf, 0, receivePacket.getLength());
                String received = new String(buf);
                this.peer.getExecutor().execute((Runnable) new ReceiveMessage(received, this.peer));
            }
        }

        catch (IOException e) {
            e.printStackTrace();
        }
    }
}