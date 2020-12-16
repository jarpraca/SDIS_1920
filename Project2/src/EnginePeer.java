import javax.net.ssl.SSLEngine;
import java.io.*;
import javax.net.ssl.*;
import java.nio.channels.spi.SelectorProvider;
import java.security.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.net.*;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class EnginePeer {
    protected int id;

    protected ByteBuffer myAppData;     //this peer's app data decrypted
    protected ByteBuffer myNetData;     //this peer's app data encrypted
    protected ByteBuffer peerAppData;   //other peer's app data decrypted
    protected ByteBuffer peerNetData;   //other peer's app data encrypted

    protected ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(300);

    private SSLContext context;
    private Selector selector;
    private SSLSession session;
    private static boolean active;

    public EnginePeer(int id, String hostAddress, int port) throws Exception {
        this.id = id;

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
        context = SSLContext.getInstance("TLSv1.2");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // Create the engine
        SSLEngine engine = context.createSSLEngine(hostAddress, port);

        // Use the engine as server
        engine.setUseClientMode(false);

        // Require client authentication
        engine.setNeedClientAuth(true);

        selector = SelectorProvider.provider().openSelector();
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(hostAddress, port));
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        session = engine.getSession();
        myAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
        peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
        peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());

        active = true;
    }


    protected boolean doHandshake(SSLEngine engine, SocketChannel socketChannel) throws Exception {

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

    protected void closeConnection(SSLEngine engine, SocketChannel socketChannel) throws Exception {
        engine.closeOutbound();
        doHandshake(engine, socketChannel);
        socketChannel.close();
        active = false;
    }

    protected void endOfStream(SSLEngine engine, SocketChannel socketChannel) throws Exception {
        try {
            engine.closeInbound();
        } catch (Exception e) {
            System.err.println("This engine was forced to close inbound due to end of stream.");
        }
        closeConnection(engine, socketChannel);
    }

    protected static boolean isActive(){
        return active;
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 3 && args.length != 5) {
            System.out.println("Error.");
            System.out.println("Usage: java EnginePeer <id> <hostAddress> <port> [<prevAddress> <pervPort>]");
            return;
        }

        EnginePeer p = new EnginePeer(Integer.parseInt(args[0]), args[1], Integer.parseInt(args[2]));
        p.start();

    }

    private void start() throws Exception {
        while(isActive()){
            selector.select();
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
            while(selectedKeys.hasNext()){
                SelectionKey currentKey = selectedKeys.next();
                System.out.println("current key: " + currentKey);
                selectedKeys.remove();
                if(currentKey.isAcceptable()){
                    accept(currentKey);
                }
                if(currentKey.isReadable()){
                    executor.execute(new EnginePeerReadThread(this, (SocketChannel) currentKey.channel(), (SSLEngine) currentKey.attachment()));
                }
            }
        }
    }

    private void accept(SelectionKey key) throws Exception {
        SocketChannel socketChannel = ((ServerSocketChannel) key.channel()).accept();
        socketChannel.configureBlocking(false);

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.beginHandshake();

        if (doHandshake(engine, socketChannel)) {
            socketChannel.register(selector, SelectionKey.OP_READ, engine);
        } else {
            socketChannel.close();
            System.err.println("Connection closed due to handshake failure.");
        }
    }

    public ByteBuffer getMyAppData(){
        return myAppData;
    }

    public ByteBuffer getMyNetData(){
        return myNetData;
    }

    public ByteBuffer getPeerAppData(){
        return peerAppData;
    }

    public ByteBuffer getPeerNetData(){
        return peerNetData;
    }

    public void setMyAppData(ByteBuffer myAppData){
        this.myAppData = myAppData;
    }

    public void setMyNetData(ByteBuffer myNetData){
        this.myNetData = myNetData;
    }

    public void setPeerAppData(ByteBuffer peerAppData){
        this.peerAppData = peerAppData;
    }

    public void setPeerNetData(ByteBuffer peerNetData){
        this.peerNetData = peerNetData;
    }

    public int getId() {
        return id;
    }
}