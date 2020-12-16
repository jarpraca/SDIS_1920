import java.io.IOException;
import java.net.DatagramPacket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

public class ReceiveMessage implements Runnable {

    private String message;
    private Peer peer;

    public ReceiveMessage(String message, Peer peer) {
        this.message = message;
        this.peer = peer;
    }

    public void run() {
        String[] args = this.message.split("\r\n\r\n");
        String[] headerArgs = args[0].split(" ");

        if (headerArgs.length > 1 && headerArgs[1].equals("PUTCHUNK")) {
            this.peer.receivePutChunk(args[1], headerArgs[2], headerArgs[4], headerArgs[3], headerArgs[0]);
        }
        else if (headerArgs.length > 1 && headerArgs[1].equals("STORED")) {
            this.peer.receiveStored(headerArgs[3], headerArgs[4], Integer.parseInt(headerArgs[2]), headerArgs[0]);
        }
        else if(headerArgs.length > 1 && headerArgs[1].equals("DELETE")) {
            this.peer.receiveDelete(headerArgs[3]);            
        }
        else if(headerArgs.length > 1 && headerArgs[1].equals("GETCHUNK")) {
            this.peer.receiveGetChunk(headerArgs[3], headerArgs[4], headerArgs[2]);
        }
        else if(headerArgs.length > 1 && headerArgs[1].equals("CHUNK")) {
            if(headerArgs[0].equals("1.0"))
                this.peer.receiveChunk(args[1].getBytes(StandardCharsets.US_ASCII), headerArgs[3], headerArgs[4] ,headerArgs[2], headerArgs[0]);
            else
                this.peer.receiveChunk(null, headerArgs[3], headerArgs[4] ,headerArgs[2], headerArgs[0]);

        }
        else if(headerArgs.length > 1 && headerArgs[1].equals("REMOVED")) {
            this.peer.receiveRemoved(headerArgs[3], headerArgs[4] ,headerArgs[2]);
        }

        headerArgs = null;
    }
}