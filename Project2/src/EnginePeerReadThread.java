import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import java.nio.channels.SocketChannel;

public class EnginePeerReadThread implements Runnable {

    private final EnginePeer peer;
    private final SSLEngine engine;
    private final SocketChannel channel;

    public EnginePeerReadThread(EnginePeer peer, SocketChannel channel, SSLEngine engine) {
        this.peer = peer;
        this.channel = channel;
        this.engine = engine;
    }

    protected void read() throws Exception{
        System.out.println("Peer " + peer.getId() + " start read.");

        peer.getPeerNetData().clear();
        int numBytes = channel.read(peer.getPeerNetData());
        if(numBytes < 0){
            System.err.println("End of stream.");
            peer.endOfStream(engine, channel);
        }
        else if(numBytes > 0){
            peer.getPeerNetData().flip();
            while (peer.getPeerNetData().hasRemaining()){
                peer.getPeerNetData().clear();
                SSLEngineResult engineResult = engine.unwrap(peer.getPeerNetData(), peer.getPeerAppData());
                switch (engineResult.getStatus()){
                    case OK:
                        System.out.println("ok");
                        peer.getPeerAppData().flip();
                        System.out.println("Incoming message: |" + new String(peer.getPeerAppData().array()) + "|");

                        //TODO: o que fazer com a mensagem
                        write("I'm peer " + peer.getId() + ".");

                        break;
                    case BUFFER_OVERFLOW:
                        System.out.println("overflow");
                        peer.setPeerAppData(peer.enlargeBuffer(peer.getPeerAppData(), engine.getSession().getApplicationBufferSize()));
                        break;
                    case BUFFER_UNDERFLOW:
                        System.out.println("underflow");
                        peer.setPeerNetData(peer.handleBufferUnderflow(engine, peer.getPeerNetData()));
                        break;
                    case CLOSED:
                        System.out.println("closed");
                        System.out.println("Client wants to close connection...");
                        peer.closeConnection(engine, channel);
                        return;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + engineResult.getStatus());
                }
            }

           // write("I'm peer " + peer.getId() + ".");
        }
    }

    protected void write(String message) throws Exception{
        System.out.println("Peer " + peer.getId() + " start write.");

        peer.getMyAppData().clear();
        peer.getMyAppData().put(message.getBytes());
        peer.getMyAppData().flip();

        System.out.println("message size: " + message.length());

        while (peer.getMyAppData().hasRemaining()) {
            // The loop is used with messages larger than 16KB.
            peer.getMyNetData().clear();
            SSLEngineResult result = engine.wrap(peer.getMyAppData(), peer.getMyNetData());
            switch (result.getStatus()) {
                case OK:
                    System.out.println("Write status OK");
                    peer.getMyNetData().flip();
                    while (peer.getMyNetData().hasRemaining()) {
                        channel.write(peer.getMyNetData());
                    }
                    System.out.println("Message sent to the client: " + message);
                    break;
                case BUFFER_OVERFLOW:
                    System.out.println("buffer capacity: " + peer.getMyNetData().capacity() + " ; buffer size: " + engine.getSession().getPacketBufferSize());
                    peer.setMyNetData(peer.enlargeBuffer(peer.getMyNetData(), engine.getSession().getPacketBufferSize()));
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occurred after a wrap. I don't think we should ever get here.");
                case CLOSED:
                    peer.closeConnection(engine, channel);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }

    }

    @Override
    public void run() {
        try {
            read();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
