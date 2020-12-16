import java.io.*;
import javax.net.ssl.*;
import java.security.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EngineTestApp {

    protected ByteBuffer myAppData;     //this peer's app data decrypted
    protected ByteBuffer myNetData;     //this peer's app data encrypted
    protected ByteBuffer peerAppData;   //other peer's app data decrypted
    protected ByteBuffer peerNetData;   //other peer's app data encrypted

    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    private SSLEngine engine;
    private SocketChannel socketChannel;

    private String remoteAddress;
    private int port;

    public EngineTestApp(String remoteAddress, int port) throws Exception {
        this.remoteAddress = remoteAddress;
        this.port = port;

        // Create and initialize the SSLContext with key material
        char[] passphrase = "123456".toCharArray();

        // First initialize the key and trust material
        KeyStore ksKeys = KeyStore.getInstance("JKS");
        ksKeys.load(new FileInputStream("server.keys"), passphrase);
        KeyStore ksTrust = KeyStore.getInstance("JKS");
        ksTrust.load(new FileInputStream("truststore"), passphrase);

        // KeyManagers decide which key material to use
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
        kmf.init(ksKeys, passphrase);

        // TrustManagers decide whether to allow connections
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(ksTrust);

        // Get an SSLContext for TLS Protocol without authentication
        SSLContext context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // Create the engine
        engine = context.createSSLEngine(remoteAddress, port);

        // Use the engine as server
        engine.setUseClientMode(true);

        SSLSession session = engine.getSession();
        myAppData = ByteBuffer.allocate(1024);
        myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        peerAppData = ByteBuffer.allocate(1024);
        peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
    }

    public void write(String message) throws Exception {
        write(socketChannel, engine, message);
    }

    protected void write(SocketChannel socketChannel, SSLEngine engine, String message) throws Exception {

        System.out.println("Writing to the server...");

        myAppData.clear();
        myAppData.put(message.getBytes());
        myAppData.flip();
        while (myAppData.hasRemaining()) {
            // The loop is used with messages larger than 16KB.
            myNetData.clear();
            SSLEngineResult result = engine.wrap(myAppData, myNetData);
            switch (result.getStatus()) {
                case OK:
                    myNetData.flip();
                    while (myNetData.hasRemaining()) {
                        socketChannel.write(myNetData);
                    }
                    System.out.println("Message sent to the server: " + message);
                    break;
                case BUFFER_OVERFLOW:
                    myNetData = enlargeBuffer(myNetData, engine.getSession().getPacketBufferSize());
                    break;
                case BUFFER_UNDERFLOW:
                    throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                case CLOSED:
                    closeConnection(socketChannel, engine);
                    return;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
            }
        }

    }

    public void read() throws Exception {
        read(socketChannel, engine);
    }

    protected void read(SocketChannel socketChannel, SSLEngine engine) throws Exception  {

        System.out.println("Reading from the server...");

        peerNetData.clear();
        int waitToReadMillis = 100;
        boolean exit = false;
        while (!exit) {
            Thread.sleep(waitToReadMillis);
            int bytesRead = socketChannel.read(peerNetData);
            if (bytesRead < 0) {
                endOfStream(socketChannel, engine);
                return;
            } else if (bytesRead > 0) {
                peerNetData.flip();
                while (peerNetData.hasRemaining()) {
                    peerAppData.clear();
                    SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
                    switch (result.getStatus()) {
                        case OK:
                            peerAppData.flip();
                            System.out.println("Server response: " + new String(peerAppData.array()));
                            exit = true;
                            break;
                        case BUFFER_OVERFLOW:
                            peerAppData = enlargeBuffer(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            peerNetData = handleBufferUnderflow(engine, peerNetData);
                            break;
                        case CLOSED:
                            closeConnection(socketChannel, engine);
                            return;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                }
            }
        }
    }


    protected boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) throws Exception {

        System.out.println("About to do hanshake...");

        SSLEngineResult result;
        SSLEngineResult.HandshakeStatus handshakeStatus;


        // Create byte buffers to use for holding application data
        int appBufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);
        myNetData.clear();
        peerNetData.clear();

        handshakeStatus = engine.getHandshakeStatus();

        // Process handshaking message
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED
                && handshakeStatus != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
            switch (handshakeStatus) {

                case NEED_UNWRAP:
                    // Receive handshaking data from peer
                    if (socketChannel.read(peerNetData) < 0) {
                        // The channel has reached end-of-stream

                        if(engine.isInboundDone() && engine.isOutboundDone()){
                            return false;
                        }
                        try{
                            engine.closeInbound();
                        } catch (SSLException e){
                            System.err.println("This engine was forced to close inbound without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
                        }
                        engine.closeOutbound();
                        // After closeOutbound the engine will be set to WRAP state
                        handshakeStatus = engine.getHandshakeStatus();
                    }
                    // Process incoming handshaking data
                    peerNetData.flip();
                    try{
                        result = engine.unwrap(peerNetData, peerAppData);
                        peerNetData.compact();
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException sslException) {
                        System.err.println("A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }

                    // Check status
                    switch (result.getStatus()) {
                        case OK:
                            break;
                        case BUFFER_OVERFLOW:
                            // Will occur when peerAppData's capacity is smaller than the data derived from peerNetData's unwrap.
                            peerAppData = enlargeBuffer(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            // Will occur either when no data was read from the peer or when the peerNetData buffer was too small to hold all peer's data.
                            peerNetData = handleBufferUnderflow(engine, peerNetData);
                            break;
                        case CLOSED:
                            if (engine.isOutboundDone()) {
                                return false;
                            } else {
                                engine.closeOutbound();
                                handshakeStatus = engine.getHandshakeStatus();
                                break;
                            }
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;

                case NEED_WRAP:

                    myNetData.clear();

                    // Generate handshaking data
                    try {
                        result = engine.wrap(myAppData, myNetData);
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException e){
                        System.err.println("A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }

                    // Check status
                    switch (result.getStatus()) {
                        case OK:
                            myNetData.flip();
                            while (myNetData.hasRemaining()) {
                                socketChannel.write(myNetData);
                            }
                            break;

                        case BUFFER_OVERFLOW:
                            myNetData = enlargeBuffer(peerAppData, engine.getSession().getApplicationBufferSize());
                            break;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException("Buffer underflow occurred after a wrap. Not supposed to happen.");
                        case CLOSED:
                            try {
                                myNetData.flip();
                                while (myNetData.hasRemaining()) {
                                    socketChannel.write(myNetData);
                                }
                                // At this point the handshake status will probably be NEED_UNWRAP so we make sure that peerNetData is clear to read.
                                peerNetData.clear();
                            } catch (Exception e) {
                                System.err.println("Failed to send server's CLOSE message due to socket channel's failure.");
                                handshakeStatus = engine.getHandshakeStatus();
                            }
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;

                case NEED_TASK:
                    Runnable task;
                    while((task = engine.getDelegatedTask()) != null) {
                        executor.execute(task);
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                case NOT_HANDSHAKING:
                    break;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + handshakeStatus);
            }


        }
        return true;
    }

    protected ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity > buffer.capacity()) {
            buffer = ByteBuffer.allocate(sessionProposedCapacity);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    protected ByteBuffer handleBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
        if (engine.getSession().getPacketBufferSize() < buffer.limit()) {
            return buffer;
        } else {
            ByteBuffer replaceBuffer = enlargeBuffer(buffer, engine.getSession().getPacketBufferSize());
            buffer.flip();
            replaceBuffer.put(buffer);
            return replaceBuffer;
        }
    }

    protected void closeConnection(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        engine.closeOutbound();
        doHandshake(socketChannel, engine);
        socketChannel.close();
    }

    protected void endOfStream(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        try {
            engine.closeInbound();
        } catch (Exception e) {
            System.err.println("This engine was forced to close inbound due to end of stream.");
        }
        closeConnection(socketChannel, engine);
    }


    public boolean connect() throws Exception {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(new InetSocketAddress(remoteAddress, port));

        System.out.println("Connecting to " + remoteAddress + ":" + port);
        while (!socketChannel.finishConnect()) {
            System.out.print(".");
            Thread.sleep(500);
        }
        System.out.println("Connection successful.");

        engine.beginHandshake();
        return doHandshake(socketChannel, engine);
    }

    public void shutdown() throws Exception {
        System.out.println("About to close connection with the server...");
        closeConnection(socketChannel, engine);
        executor.shutdown();
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5) {
            System.out.println("Protocol usage: java EngineTestApp <address> <port> <sub_protocol> [ <opnd_1> <opnd_2> ] ");
            return;
        }

        EngineTestApp testApp = new EngineTestApp(args[0], Integer.parseInt(args[1]));
        if (!testApp.connect()){
            System.err.println("Failed to handshake with peer.");
            return;
        }

        testApp.write("I'm client.");
        testApp.read();

    }
}