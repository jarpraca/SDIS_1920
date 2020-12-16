
public class StabilizeChord implements Runnable {

    private ChordNode chordNode;

    public StabilizeChord(ChordNode node) {
        this.chordNode = node;
    }

    @Override
    public void run() {
        // chordNode.stabilize();
        chordNode.fixFingers();
    }
}
