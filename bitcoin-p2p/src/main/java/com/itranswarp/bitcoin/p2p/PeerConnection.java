package com.itranswarp.bitcoin.p2p;

import com.itranswarp.bitcoin.constant.BitcoinConstants;
import com.itranswarp.bitcoin.io.BitcoinInput;
import com.itranswarp.bitcoin.p2p.message.Message;
import com.itranswarp.bitcoin.p2p.message.VersionMessage;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Holds connection to a peer.
 *
 * @author liaoxuefeng
 */
public class PeerConnection extends Thread implements MessageSender {

    private final Log log = LogFactory.getLog(getClass());

    private final String nodeIp;
    private final PeerListener listener;
    private final BlockingQueue<Message> sendingQueue;

    private volatile boolean running = false;
    private volatile long timeout = 0;

    PeerConnection(String nodeIp, PeerListener listener) {
        this.nodeIp = nodeIp;
        this.listener = listener;
        this.sendingQueue = new ArrayBlockingQueue<>(100);
        this.setDaemon(true);
    }

    @Override
    public void run() {
        this.running = true;
        // connect:
        try (Socket sock = new Socket()) {
            sock.connect(new InetSocketAddress(nodeIp, BitcoinConstants.PORT), 10000);
            sock.setSoTimeout(10000);
            try (InputStream input = sock.getInputStream()) {
                try (OutputStream output = sock.getOutputStream()) {
                    listener.connected(this.nodeIp);
                    setTimeout(60_000);
                    // add version message to send automatically:
                    this.sendMessage(new VersionMessage(0, sock.getInetAddress()));
                    // loop:
                    while (this.running) {
                        if (isTimeout()) {
                            log.info("Timeout!");
                            break;
                        }
                        // try get message to send:
                        Message msg = sendingQueue.poll(1, TimeUnit.SECONDS);
                        if (this.running && msg != null) {
                            // send message:
                            log.info("socket msg out => " + msg.toString());
                            output.write(msg.toByteArray());
                        }
                        // try receive message:
                        if (this.running && (input.available() > 0)) {
                            BitcoinInput in = new BitcoinInput(input);
                            Message parsedMsg = Message.Builder.parseMessage(in);
                            log.info("socket msg in <= " + parsedMsg);
                            this.listener.onMessage(this, parsedMsg);
                        }
                    }
                }
            }
            listener.disconnected(this.nodeIp, null);
        } catch (SocketTimeoutException | ConnectException e) {
            log.warn("Connect exception: " + e.getMessage(), e);
            listener.disconnected(this.nodeIp, e);
        } catch (IOException e) {
            log.warn("IOException: " + e.getMessage(), e);
            listener.disconnected(this.nodeIp, e);
        } catch (InterruptedException e) {
            log.warn("Peer connection thread interrupted.");
            listener.disconnected(this.nodeIp, null);
        } catch (Exception e) {
            log.warn("Peer connection exception.", e);
            listener.disconnected(this.nodeIp, null);
        } finally {
            this.running = false;
        }
    }

    public void close() {
        this.running = false;
        try {
            this.join(1000);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    @Override
    public void sendMessage(Message message) {
        this.sendingQueue.add(message);
    }

    @Override
    public void setTimeout(long timeoutInMillis) {
        this.timeout = System.currentTimeMillis() + timeoutInMillis;
    }

    private boolean isTimeout() {
        return System.currentTimeMillis() > this.timeout;
    }
}
