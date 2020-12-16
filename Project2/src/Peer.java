import java.io.*;

public class Peer {

    public static void delete(File f) throws IOException {
        if(!f.exists())
            return;

        if (f.isDirectory()) {
          for (File c : f.listFiles())
            delete(c);
        }
        if (!f.delete())
          throw new FileNotFoundException("Failed to delete file: " + f);
    }

    public static void main(String args[]) {
        ChordNode chordNode;

        if (args.length == 2) {
            chordNode = new ChordNode(args[0], args[1]);
        } else {
            chordNode = new ChordNode(args[0], args[1], args[2], args[3]);
        }

        try {
            // File file = new File("chordNode_" + args[0] + "_" + args[1] + "/storage.ser");

            // if (file.exists()) {
            //     FileInputStream fis = new FileInputStream("chordNode_" + args[0] + "_" + args[1] + "/storage.ser");
            //     ObjectInputStream input = new ObjectInputStream(fis);
            //     chordNode.setStorage((Storage) input.readObject());
            //     input.close();
            //     fis.close();
            // }

        } catch (Exception e) {
            e.printStackTrace();

        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Saving state...");
                try {
                    // File storageFile = new File("chordNode_" + args[0] + "_" + args[1] + "/storage.ser");

                    // if (!storageFile.exists()) {
                    //     storageFile.getParentFile().mkdirs();
                    //     storageFile.createNewFile();
                    // }

                    // FileOutputStream outFile = new FileOutputStream(
                    //         "chordNode_" + args[0] + "_" + args[1] + "/storage.ser");
                    // ObjectOutputStream output = new ObjectOutputStream(outFile);
                    // output.writeObject(chordNode.getStorage());
                    // output.close();
                    // outFile.close();

                    chordNode.notifyLeaving();

                    // delete storage folder
                    File f = new File("chordNode_" + chordNode.getKey().getID());
                    delete(f);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        });
    }
}
