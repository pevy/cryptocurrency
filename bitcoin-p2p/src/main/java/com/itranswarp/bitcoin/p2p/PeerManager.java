package com.itranswarp.bitcoin.p2p;

import com.itranswarp.bitcoin.util.JsonUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PeerManager {

    final int MAX_SIZE = 500;

    private final Log log = LogFactory.getLog(getClass());
    private final File cached;

    // peers
    private final List<Peer> peers = new ArrayList<>();

    public PeerManager() {
        this(null);
    }

    public PeerManager(File cachedFile) {
        this.cached = cachedFile;
        Peer[] cachedPeers = loadPeers();
        addPeers(cachedPeers);
        if (peers.size() < 5) {
            // lookup from DNS:
            Thread t = new Thread(() -> {
                try {
                    String[] ips = PeerDiscover.lookup();
                    addPeers(ips);
                } catch (Exception e) {
                    log.warn("Could not discover peers.", e);
                }
            });
            t.setDaemon(true);
            t.start();
        }
    }

    private synchronized int peerCount() {
        return peers.size();
    }

    /**
     * Return a peer ip to connect.
     *
     * @return Ip or null if no peer available.
     */
    synchronized String getPeer() {
        log.info("Try get an unused peer from " + this.peers.size() + " peers...");
        this.peers.sort((p1, p2) -> p1.score > p2.score ? -1 : 1);
        for (Peer p : this.peers) {
            if (!p.using) {
                p.using = true;
                return p.ip;
            }
        }
        return null;
    }

    /**
     * Release a peer.
     *
     * @param ip    The ip address.
     * @param score The score of peer.
     */
    synchronized void releasePeer(String ip, int score) {
        Peer target = null;
        for (Peer p : this.peers) {
            if (p.ip.equals(ip)) {
                target = p;
                break;
            }
        }
        if (target != null) {
            target.using = false;
            target.score += score;
            if (target.score < 0) {
                this.peers.remove(target);
            }
        }
        storePeers();
    }

    private synchronized void addPeers(String[] ips) {
        Peer[] ps = new Peer[ips.length];
        for (int i = 0; i < ps.length; i++) {
            ps[i] = new Peer(ips[i]);
        }
        addPeers(ps);
    }

    private synchronized void addPeers(Peer[] ps) {
        log.info("Add discovered " + ps.length + " peers...");
        for (Peer p : ps) {
            if (!this.peers.contains(p)) {
                this.peers.add(p);
            }
        }
        log.info("Total peers: " + this.peers.size());
        storePeers();
    }

    public synchronized void close() {
        storePeers();
    }

    Peer[] loadPeers() {
        if (this.cached != null) {
            try (InputStream input = new BufferedInputStream(new FileInputStream(this.cached))) {
                return JsonUtils.fromJson(Peer[].class, input);
            } catch (Exception e) {
                log.warn("Load cached peers from cached file failed: " + this.cached.getAbsolutePath());
            }
        }
        return new Peer[0];
    }

    void storePeers() {
        if (this.cached != null) {
            try (Writer writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(this.cached), "UTF-8"))) {
                Peer[] peerArray = this.peers.toArray(new Peer[0]);
                writer.write(JsonUtils.toJson(peerArray));
            } catch (Exception e) {
                log.warn("Write peers to cached file failed: " + this.cached.getAbsolutePath(), e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PeerManager manager = new PeerManager();
        for (int i = 0; i < 60; i++) {
            Thread.sleep(1000);
            System.out.print('.');
            if (manager.peerCount() > 0) {
                break;
            }
        }
        System.out.println("\n" + manager.peerCount() + " peers discovered.");
    }
}
