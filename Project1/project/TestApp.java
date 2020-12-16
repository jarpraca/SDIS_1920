import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.net.*;

public class TestApp {

    public static void main(String args[]) throws Exception {
        if (args.length < 2 || args.length > 4) {
            System.out.println("Protocol usage: java TestApp <peer_ap> <sub_protocol> [ <opnd_1> <opnd_2> ] ");
            System.exit(-1);
        }

        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec("rmiregistry");
        System.setProperty("java.net.preferIPv4Stack", "true");

        String[] accessPoint = args[0].split(" ");

        Registry registry = LocateRegistry.getRegistry(accessPoint[0]);
        RMInterface stub = (RMInterface) registry.lookup(accessPoint[1]);

        switch (args[1]) {
            case "BACKUP":
                if (args.length != 4) {
                    System.out.println("The BACKUP protocol requires exactly four parameters");
                    System.exit(-1);
                } else if (Integer.parseInt(args[3]) > 9 || Integer.parseInt(args[3]) < 1) {
                    System.out.println("The replication degree must be a digit, thus a number between 1 and 9.");
                    System.exit(-1);
                }
                stub.backup(args[2], Integer.parseInt(args[3]));
                break;
            case "RESTORE":
                if (args.length != 3) {
                    System.out.println("The RESTORE protocol requires exactly 3 parameters");
                    System.exit(-1);
                }
                stub.restore(args[2]);
                break;
            case "DELETE":
                if (args.length != 3) {
                    System.out.println("The DELETE protocol requires exactly 3 parameters");
                    System.exit(-1);
                }
                stub.delete(args[2]);
                break;
            case "RECLAIM":
                if (args.length != 3) {
                    System.out.println("The RECLAIM protocol requires exactly 3 parameters");
                    System.exit(-1);
                }
                stub.reclaim(args[2]);
                break;
            case "STATE":
                if (args.length != 2) {
                    System.out.println("The STATE protocol requires exactly 2 parameters");
                    System.exit(-1);
                }
                stub.state();
                break;
            default:
                System.out.println(
                        "The protocol specified is not supported, please try one of 'BACKUP', 'RESTORE', 'DELETE', 'RECLAIM' OR 'STATE'");
                System.exit(-1);
        }
    }
}