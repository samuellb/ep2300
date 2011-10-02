package ep2300;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
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
    // XXX maybe it should have getters/setters...
    public Map<String,Router> routers = new HashMap<String,Router>();
    
    private AtomicInteger outstandingRequests = new AtomicInteger(0);
    
    public LinkStatistics()
    {
        /*Map<String, Set<String>> neighbors = topology.getNeighborTable();
        
        for (String addr : neighbors.keySet()) {
            links.add(new Interface(addr));
        }*/
        
        Router r1 = new Router("first", "192.168.1.10");
        routers.put(r1.getIP(), r1);
        Router r2 = new Router("other", "192.168.4.10");
        routers.put(r2.getIP(), r2);
    }
    
    public LinkStatistics(Topology topology) {
        // TODO
        this();
    }
    
    /**
     * Probe an IP for updated statistics.
     * 
     * @param ip The IP to probe
     */
    private void probe(String ip)
    {
        outstandingRequests.addAndGet(SNMP.sendOID(ip, this, SNMP.outOctetsOID));
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
            System.out.println("callback!");
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
                probe(address);
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
                Router router = routers.get(address);
                long octets = 0;
                long packets = 0;
                
                try {
                    octets = ArrayResponse.sum(pdu, SNMP.outOctetsOID);
                    packets = ArrayResponse.sum(pdu, SNMP.outPacketsOID);
                } catch (SnmpException e) {
                    e.printStackTrace();
                }
                System.out.println(address+" octets = "+octets+"  packets = "+packets);
                
                router.octets.add(octets);
                router.packets.add(packets);
                
                return true; // done processing PDU
            }
        }
        finally {
            if (outstandingRequests.decrementAndGet() <= 0) {
                System.out.println("Updated.\n");
                synchronized(this) { notifyAll(); }
            }
        }
    }
    
    public void update()
    {
        for (String address : routers.keySet())
        {
            probe(address);
        }
    }
    
    public synchronized void waitUntilFinished()
    {
        while (outstandingRequests.get() > 0) {
            try {
                wait();
            }
            catch (InterruptedException e) { }
        }
    }

    @Override
    public void debugPrint(String debugOutput)
    {
        System.err.println("debuging");
    }

    @Override
    public String toString()
    {
        // TODO
        throw new UnsupportedOperationException("Not implemented");
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
            stats.waitUntilFinished();
            
            try { Thread.sleep(1000); }
            catch (InterruptedException e) { }
        }
    }

}
