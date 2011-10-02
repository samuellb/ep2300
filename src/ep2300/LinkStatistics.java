package ep2300;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpIpAddress;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;

/*
 * Maintains statistics about the sent data on all links in a network.
 */
public final class LinkStatistics implements SnmpClient
{   
    private Topology topology;
    
    private AtomicInteger outstandingRequests = new AtomicInteger(0);
    
    public LinkStatistics(Topology topology) {
        this.topology = topology;
    }
    
    /**
     * Probe an IP for updated statistics.
     * 
     * @param ip The IP to probe
     */
    private void probe(String ip)
    {
        SNMP.sendOID(ip, this, SNMP.outOctetsOID);
        outstandingRequests.incrementAndGet();
    }

    @Override
    public boolean authenticate(SnmpPDU pdu, String community)
    {
        return true; // Because we already are authenticated
    }

    /**
     * Add all nexthops and the router in the response to the list of routes.
     */
    @Override
    public boolean callback(SnmpSession session, SnmpPDU pdu, int requestID)
    {
        try {
            UDPProtocolOptions opt = (UDPProtocolOptions) session
                        .getProtocolOptions();
            String address = opt.getRemoteAddress().getCanonicalHostName();
            
            if (pdu.getErrstat() != 0) { // FIXME is this state reachable?
                System.out.println("A request has failed:");
                System.out.println(pdu.getError());
                return true; // No further processing is needed since the request
                // failed
            }
            else if (pdu.getObjectID(0).equals(SNMP.usmStatsNotInTimeWindows)) {
                // Try again
//                probe(address);
                return true;
            }
            else if (!SNMP.samePrefix(pdu.getObjectID(0), SNMP.outOctetsOID)) {
                System.out.println("Invalid response, probing again: "+pdu.getObjectID(0));
                try {
                    for (int i = 1; ; i++) {
                        SnmpOID oid = pdu.getObjectID(i);
                        if (oid == null) break;
                        System.out.println("\t"+oid);
                    }
                } catch (Exception e) { }
                //probe(router);
                
                return false;
            }
            else {
                Router router = topology.getRouterFromIP(address);
                long octets = 0;
                long packets = 0;
                
                try {
                    octets = ArrayResponse.sum(pdu, SNMP.outOctetsOID);
                    packets = ArrayResponse.sum(pdu, SNMP.outPacketsOID);
                } catch (SnmpException e) {
                    e.printStackTrace();
                }
//                System.out.println(address+" octets = "+octets+"  packets = "+packets);
                
                router.octets.add(octets);
                router.packets.add(packets);
                
                return true; // done processing PDU
            }
        }
        finally {
            if (outstandingRequests.decrementAndGet() <= 0) {
                synchronized(this) { notifyAll(); }
            }
        }
    }
    
    public void update()
    {
        for (Router address : topology.getTopology().values())
        {
            probe(address.getIP());
        }
    }
    
    /**
     * Waits until all requests have finished, or at most timeout
     * milliseconds. Returns the number of unfinished requests.
     */
    public synchronized int waitUntilFinished(long timeout)
    {
        long start = System.currentTimeMillis();
        
        while (outstandingRequests.get() > 0) {
            try {
                long delay = System.currentTimeMillis() - start;
                
                if (delay <= 0) break;
                
                wait(timeout - delay);
            }
            catch (InterruptedException e) { }
        }
        
        UDPSnmpV3.close();
        return outstandingRequests.get();
    }

    
    /**
     * Get the topology of the network
     * 
     * @return The network topology
     */
    public Topology getTopology()
    {
        return topology;
    }

    @Override
    public void debugPrint(String debugOutput)
    {
        System.err.println("debuging");
    }

    @Override
    public String toString()
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        for (Router router : topology.getTopology().values()) {
            out.printf("%s (%s):\n", router, router.getIP());
            Iterator<Long> octets = router.octets.iterator();
            Iterator<Long> packets = router.octets.iterator();
            while (octets.hasNext() && packets.hasNext()) {
                out.printf("\t%dB (%s packets)\n", octets.next(), packets.next());
            }
        }
        return baos.toString();
    }
    
    /**
     * For testing this class.
     */
    public static void main(String[] args)
    {
        if (args.length != 1) {
            System.err.println("usage: java LinkStatistics <first router>");
            System.exit(2);
        }
        
        String firstRouter = args[0];
        
        System.out.println("Discovering the topology...");
        Topology topo = new Topology(firstRouter);
        topo.waitUntilFinished();
        
        System.out.println("Monitoring...");
        LinkStatistics stats = new LinkStatistics(topo);
        
        while (true) {
            System.out.println("Updating...");
            stats.update();
            System.out.println(stats);
            int unfinished = stats.waitUntilFinished(1000);
            if (unfinished > 0) {
                System.out.println("unfinished requests: "+unfinished);
            }
            
            try { Thread.sleep(1000); }
            catch (InterruptedException e) { }
        }
    }

}
