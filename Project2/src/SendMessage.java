import javax.net.ssl.SSLSocket;
import java.io.*;
import javax.net.ssl.*;
import java.net.*;

public class SendMessage implements Runnable {

    private String message;
    private String address;
    private Integer port;
    private ChordNode node;

    public SendMessage(String msg, String address, Integer port, ChordNode node) {
        this.message = msg;
        this.address = address;
        this.port = port;
        this.node = node;
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
