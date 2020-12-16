import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MulticastControl implements Runnable {

    private InetAddress group;
    private MulticastSocket socket;
    private int port;
    private Peer peer;

    public MulticastControl(String address, int port, Peer peer) {
        try {
            this.group = InetAddress.getByName(address);
            this.port = port;
            this.socket = new MulticastSocket(port);
            this.socket.joinGroup(this.group);
            this.peer = peer;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(byte[] message) {

        try {
            DatagramPacket packet = new DatagramPacket(message, message.length, this.group, this.port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void run() {
        System.out.println("running control channel...");
        try {
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