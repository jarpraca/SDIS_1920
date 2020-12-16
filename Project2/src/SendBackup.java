import javax.net.ssl.SSLSocket;
import java.io.*;
import javax.net.ssl.*;
import java.net.*;

public class SendBackup implements Runnable {

    private String message;
    private String address;
    private Integer port;
    private ChordNode node;
    private byte[] chunk;

    public SendBackup(String msg, byte[] chunk, String address, Integer port, ChordNode node) {
        this.message = msg;
        this.address = address;
        this.port = port;
        this.node = node;
        this.chunk = chunk;
    }

    @Override
    public void run() {
        try {
            SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket s = (SSLSocket) ssf.createSocket(address, port);

            if (!s.isConnected()) {
                return;
            }

            // send request
            DataOutputStream out = new DataOutputStream(s.getOutputStream());
            out.writeBytes(message);

            out.flush();
            // out.close();
            
            out = new DataOutputStream(s.getOutputStream());
            out.write(chunk);

            out.close();
            s.close();

        } catch (ConnectException e) {
            node.fixFingers();
        }
        catch(Exception e) {
            e.printStackTrace();

        }
    }
}
