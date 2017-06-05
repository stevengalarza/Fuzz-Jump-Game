package com.fuzzjump.game.net;

import com.google.protobuf.GeneratedMessage;
import com.kerpowgames.server.common.packets.Packet;
import com.kerpowgames.server.common.packets.PacketHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Created by Steveadoo on 12/29/2015.
 */
public class Connection {

    private final int selectorTimeout;
    private final ByteBuffer readBuffer;

    private ConnectionListener listener;
    private Selector selector;
    private SocketChannel socket;

    private String ip;
    private int port;
    private InetSocketAddress address;

    private PacketHandler packetHandler;
    private Queue<GeneratedMessage> writeQueue = new LinkedList<>();

    private boolean connected = false;
    private Thread thread;

    public Connection(PacketHandler packetHandler, ConnectionListener listener, int selectorTimeout, int readBufferSize) {
        this.packetHandler = packetHandler;
        this.selectorTimeout = selectorTimeout;
        this.listener = listener;
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
    }

    public void send(GeneratedMessage message) {
        synchronized (writeQueue) {
            writeQueue.offer(message);
        }
    }

    public void connect(String ip, int port) {
        this.ip = ip;
        this.port = port;
        if (thread != null) {
            try {
                disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        thread = new Thread(new Runnable() {

            @Override
            public void run() {
                connect();
            }

        });
        thread.start();
    }

    public void disconnect() throws IOException {
        if (thread == null || selector == null || socket == null)
            return;
        thread.interrupt();
        selector.close();
        socket.close();
        thread = null;
        selector = null;
        socket = null;
    }

    private void connect() {
        try {
            address = new InetSocketAddress(ip, port);
            initSocket();
            connected = false;
            while (!Thread.interrupted()) {

                //pretty sure this does nothing
                if (connected && !socket.isConnected())
                    break;

                //do this here rather than in send. key.interestOps will block if the selector is happening
                //so we can end up blocking the calling thread.
                checkWrite();

                selector.select(selectorTimeout);

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    handleKey(key);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        if (listener != null) {
            listener.disconnected();
        }
    }

    private void initSocket() throws IOException {
        selector = Selector.open();
        socket = SocketChannel.open();
        socket.socket().setTcpNoDelay(true);
        socket.socket().setSendBufferSize(30);
        socket.socket().setReceiveBufferSize(30);
        socket.socket().setKeepAlive(true);
        socket.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_CONNECT);
        System.out.println("Connecting to " + address.getHostName() + ":" + address.getPort());
        socket.connect(address);
    }

    private void checkWrite() {
        synchronized (writeQueue) {
            if (writeQueue.peek() != null) {
                SelectionKey rwKey = socket.keyFor(selector);
                rwKey.interestOps(rwKey.interestOps() | SelectionKey.OP_WRITE);
            }
        }
    }

    private void handleKey(SelectionKey key) throws IOException {
        if (!key.isValid())
            return;
        if (key.isConnectable()) {
            if (connect(key)) {
                connected = true;
            }
        }
        if (key.isReadable()) {
            read(key);
        }
        if (key.isWritable()) {
            if (write(key)) {
                SelectionKey rwKey = socket.keyFor(selector);
                rwKey.interestOps(rwKey.interestOps() & (~SelectionKey.OP_WRITE));
            }
        }
    }

    private void read(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        readBuffer.clear();
        int length;
        try {
            length = channel.read(readBuffer);
        } catch (IOException e) {
            e.printStackTrace();
            key.cancel();
            channel.close();
            return;
        }
        if (length == -1) {
            channel.close();
            key.cancel();
            return;
        } else if (length == 0) {
            return;
        }
        readBuffer.flip();
        parse(key, readBuffer);
    }

    private void parse(SelectionKey key, ByteBuffer buffer) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        //Most of the time we will only receive one packet in a message, but if we do get multiple,
        //switch to a list and pass all of them to the listener at once
        Packet singlePacket = null;
        List<Packet> packets = null;
        while (buffer.hasRemaining()) {
            int opcode = buffer.get() & 0xFF;
            int len = buffer.get() & 0xFF;
            while (buffer.remaining() < len) {
                ByteBuffer tempBuffer = ByteBuffer.allocate(len - buffer.remaining());
                if (channel.read(tempBuffer) == -1) {
                    channel.close();
                    key.cancel();
                    return;
                }
                tempBuffer.flip();
                ByteBuffer newBuffer = ByteBuffer.allocate(len);
                newBuffer.put(buffer);
                newBuffer.put(tempBuffer);
                newBuffer.flip();
                buffer = newBuffer;
            }
            byte[] data = new byte[len];
            buffer.get(data);
            Packet packet = new Packet(opcode, data);
            //if there is more data in the buffer, more than one packet was received
            if (buffer.hasRemaining() && packets == null) {
                packets = new ArrayList<>();
            }
            if (packets != null) {
                packets.add(packet);
            } else {
                singlePacket = packet;
            }
        }
        if (listener != null) {
            if (singlePacket != null) {
                listener.receivedMessage(singlePacket);
            } else {
                listener.receivedMessages(packets);
            }
        }
    }

    private boolean write(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        GeneratedMessage message = null;
        ByteBuffer buffer = null;
        Packet packet = null;
        synchronized (writeQueue) {
            while ((message = writeQueue.peek()) != null) {
                packet = packetHandler.getPacket(message);
                buffer = ByteBuffer.allocateDirect(2 + packet.length);
                buffer.put((byte) packet.opcode);
                buffer.put((byte) packet.length);
                if (packet.length > 0)
                    buffer.put(packet.data);
                buffer.flip();
                if (channel.write(buffer) == 0) {
                    //return false so it will wait until sendbuffer is cleared
                    return false;
                }
                buffer.clear();
                writeQueue.remove();
            }
        }
        return true;
    }

    private boolean connect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        if (channel.isConnectionPending()) {
            if (!channel.finishConnect()) {
                return false;
            }
        }
        if (!channel.isConnected())
            return false;
        channel.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_READ);
        if (listener != null) {
            listener.connected();
        }
        return true;
    }

    public PacketHandler getPacketHandler() {
        return packetHandler;
    }

    public void removeListener() {
        listener = null;
    }

    public interface ConnectionListener {

        void connected();

        void disconnected();

        void receivedMessage(Packet packet);

        void receivedMessages(List<Packet> packets);

    }
}
