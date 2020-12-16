import java.net.*;
import javax.net.ssl.SSLSocket;
import java.io.*;
import javax.net.ssl.*;

public class TestApp {
    private String address;
    private Integer port;

    public TestApp(String[] args) {
        if (args[0].equals("localhost")) {
            try {
                InetAddress ip = InetAddress.getLocalHost();
                args[0] = ip.getHostAddress();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        this.address = args[0];
        this.port = Integer.parseInt(args[1]);
    }

    public String getAddress() {
        return address;
    }

    public Integer getPort() {
        return port;
    }

    public static void sendRequest(String message, String address, String port) {
        try {
            SSLSocketFactory ssf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket s = (SSLSocket) ssf.createSocket(address, Integer.parseInt(port));

            if (!s.isConnected()) {
                return;
            }

            DataOutputStream out = new DataOutputStream(s.getOutputStream());

            out.writeBytes(message);
            out.flush();
            out.close();
            s.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) throws Exception {
        if (args.length < 3 || args.length > 5) {
            System.out.println("Protocol usage: java TestApp <address> <port> <sub_protocol> [ <opnd_1> <opnd_2> ] ");
            System.exit(-1);
        }

        TestApp test = new TestApp(args);

        System.setProperty("javax.net.ssl.keyStore", "client.keys");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStore", "truststore");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
        String header;
        switch (args[2]) {
            case "BACKUP":
                if (args.length != 5) {
                    System.out.println("The BACKUP protocol requires exactly five parameters");
                    System.exit(-1);
                } else if (Integer.parseInt(args[4]) > 9 || Integer.parseInt(args[4]) < 1) {
                    System.out.println("The replication degree must be a digit, thus a number between 1 and 9.");
                    System.exit(-1);
                }

                header = "BACKUP " + args[3] + " " + args[4];
                sendRequest(header, args[0], args[1]);

                break;
            case "RESTORE":
                if (args.length != 4) {
                    System.out.println("The RESTORE protocol requires exactly four parameters");
                    System.exit(-1);
                }
                header = "RESTORE_PROTOCOL " + args[3];
                sendRequest(header, args[0], args[1]);
                break;
            case "DELETE":
                if (args.length != 4) {
                    System.out.println("The DELETE protocol requires exactly four parameters");
                    System.exit(-1);
                }
                header = "DELETE_PROTOCOL " + args[3];
                sendRequest(header, args[0], args[1]);
                break;

            case "RECLAIM":
                if (args.length != 4) {
                    System.out.println("The RECLAIM protocol requires exactly four parameters");
                    System.exit(-1);
                }
                header = "RECLAIM_PROTOCOL " + args[3];
                sendRequest(header, args[0], args[1]);
                break;
            default:
                System.out.println(
                        "The protocol specified is not supported, please try one of 'BACKUP', 'RESTORE' OR 'DELETE'");
                System.exit(-1);
        }
    }
}