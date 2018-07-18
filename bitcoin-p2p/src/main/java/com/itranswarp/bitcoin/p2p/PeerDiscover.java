package com.itranswarp.bitcoin.p2p;

import com.itranswarp.bitcoin.BitcoinException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Discover full nodes by DNS query:
 * https://bitcoin.org/en/developer-guide#peer-discovery
 *
 * @author liaoxuefeng
 */
class PeerDiscover {

    private static final Log log = LogFactory.getLog(PeerDiscover.class);

    // https://en.bitcoin.it/wiki/Satoshi_Client_Node_Discovery#DNS_Addresses
    private static final String[] DNS_SEEDS = {"bitseed.xf2.org", "dnsseed.bluematt.me", "seed.bitcoin.sipa.be", "dnsseed.bitcoin.dashjr.org", "seed.bitcoinstats.com"};

    /**
     * Lookup bitcoin peers by DNS seed.
     *
     * @return InetAddress[] contains 1~N peers.
     * @throws BitcoinException If lookup failed.
     */
    static String[] lookup() {
        log.info("Lookup peers from DNS seed...");
        String[] ips = Arrays.stream(DNS_SEEDS).parallel().map((host) -> {
            try {
                return InetAddress.getAllByName(host);
            } catch (UnknownHostException e) {
                log.warn("Cannot look up host: " + host);
                return new InetAddress[0];
            }
        }).flatMap(Arrays::stream).filter(addr -> addr instanceof Inet4Address).map(InetAddress::getHostAddress).toArray(String[]::new);
        if (ips.length == 0) {
            throw new BitcoinException("Cannot lookup pears from all DNS seeds.");
        }
        log.info(ips.length + " peers found.");
        return ips;
    }
}
