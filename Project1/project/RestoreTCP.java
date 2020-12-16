import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap;
import java.nio.charset.StandardCharsets;

public class RestoreTCP implements Runnable {

    private Peer peer;
    private int chunkId;

    public RestoreTCP(Peer peer, int chunkId) {
        this.peer = peer;
        this.chunkId = chunkId;
    }

    @Override
    public void run() {
        try {
            char[] buffer = new char[65000];
            ServerSocket server = new ServerSocket(62868+chunkId, 1, InetAddress.getLocalHost());
            Socket client = server.accept();

            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            int read = in.read(buffer, 0, buffer.length);
            char[] buf = new char[read];
            System.arraycopy(buffer, 0, buf, 0, read);
            System.out.println("num read: " + read);
            String string = new String(buf);
            
            String[] args = string.split("\r\n\r\n");
            String[] headerArgs = args[0].split(" ");

            AbstractMap.SimpleEntry<String, String> entry = new AbstractMap.SimpleEntry<String, String>(headerArgs[3],
                    headerArgs[4]);
            for (AbstractMap.SimpleEntry<String, String> e : this.peer.getWaitingRestores()) {
                if (e.getKey().equals(entry.getKey()) && e.getValue().equals(entry.getValue())) {
                    this.peer.getWaitingRestores().remove(entry);
                    InitChunk chunk = new InitChunk(
                            this.peer.getStorage().getInitiatedChunk(headerArgs[4], headerArgs[3]).getDesiredRd(),
                            Integer.parseInt(headerArgs[4]), headerArgs[3], args[1].getBytes(StandardCharsets.US_ASCII));
                    this.peer.getStorage().restoreChunk(chunk, this.peer.getID());
                    client.close();
                    server.close();
                    return;
                }
            }
            client.close();
            server.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}