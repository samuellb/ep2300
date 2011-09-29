package ep2300;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
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
 * TODO In the constructor, create an initial session and request the route
 * table from there.
 * 
 * For each respons (in callback im assuming) handle it, if its a new router,
 * ask for its routes, if its a new route, add it to a data structure describing
 * the topology.
 * 
 * The topology will probably be represented with something like map<String,
 * ArrayList<IPs>> where the string is the IP of the router, and the arraylist
 * contains all next-hops of that router.
 */
public class Topology implements SnmpClient
{
    private static final SnmpOID discoverOID = new SnmpOID(
            ".1.3.6.1.2.1.4.21.1.7"); // ipRouteNextHop
    private static final int numPerResponse = 30;

    private Map<String, Set<String>> neighbors = new HashMap<String, Set<String>>();
    private Set<String> probed = new HashSet<String>();
    
    private AtomicInteger outstandingRequests = new AtomicInteger(0);

    /**
     * Start the probing
     */
    public Topology(String firstRouter)
    {
        probed.add(firstRouter);
        probe(firstRouter);
    }
    
    @Deprecated
    public Topology()
    {
        this("192.168.1.10");
    }

    /**
     * Probe an IP for routes.
     * 
     * @param ip The IP to probe
     */
    private void probe(String ip)
    {
        // Start from discoverOID
        probe(ip, discoverOID);
    }

    /**
     * Probe an IP for routes starting at startingOID
     * 
     * @param ip The IP to probe
     * @param startingOID The OID to start at
     */
    private void probe(String ip, SnmpOID startingOID)
    {
        SnmpSession session = null;
        try {
            session = UDPSnmpV3.createSession(ip); // Begin here
        }
        catch (SnmpException e) {
            System.err.println("Could not start session to ip " + ip + ": "
                    + e.getMessage());
            // e.printStackTrace();
        }
        if (session != null) { // Should always happen
            int id = session.addSnmpClientWithID(this);
            SnmpPDU pdu = new SnmpPDU();
            pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
            pdu.setClientID(id);

            pdu.setMaxRepetitions(numPerResponse);

            pdu.addNull(discoverOID);
            try {
                outstandingRequests.incrementAndGet();
                session.send(pdu);
            }
            catch (SnmpException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
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
            if (pdu.getErrstat() != 0) { // FIXME is this state reachable?
                System.out.println("A request has failed:");
                System.out.println(pdu.getError());
                return true; // No further processing is needed since the request
                // failed
            }
            else {
                UDPProtocolOptions opt = (UDPProtocolOptions) session
                        .getProtocolOptions();
                String router = opt.getRemoteAddress().getCanonicalHostName();
                
                if (!ArrayResponse.samePrefix(pdu.getObjectID(0), discoverOID)) {
                    // The callback was not a ipRouteNextHop
                    System.out.println("Invalid response, probing again: "+pdu.getObjectID(0));
                    probe(router);
                    return false;
                }

                // Check if this is a new router
                Set<String> nextHops = neighbors.get(router);
                if (nextHops == null) {
                    System.out.println("New router discovered: \t" + router);
                    nextHops = new TreeSet<String>();
                    neighbors.put(router, nextHops);
                }

                // Go through the lists of next hops (=neighbors)
                try {
                    ArrayResponse<SnmpIpAddress> respArray = new ArrayResponse<SnmpIpAddress>(
                            pdu, discoverOID, numPerResponse);

                    for (SnmpIpAddress addr : respArray) {
                        String addrStr = addr.toString();
                        if (addrStr.equals(router)) continue;
                        
                        nextHops.add(addrStr);

                        if (probed.add(addrStr)) {
                            // Not yet probed
                            probe(addrStr);
                        }
                    }

                    if (!respArray.reachedEnd()) {
                        // The list is not complete, request more elements
                        probe(router, respArray.getNextStartOID());
                    } else {
                        // We're done
                        session.removeSnmpClient(this);
                    }
                }
                catch (SnmpException e) {
                    // throw new RuntimeException(e);
                    e.printStackTrace();
                }

                return true; // done processing PDU
            }
        }
        finally {
            if (outstandingRequests.decrementAndGet() <= 0) {
                System.out.println("Discovery finished.\n\n");
                synchronized(this) { notifyAll(); }
            }
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
    
    public Map<String, Set<String>> getNeighborTable()
    {
        return neighbors;
    }

    @Override
    public String toString()
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        for (String router : neighbors.keySet()) {
            Set<String> nextHops = neighbors.get(router);
            out.println(router + ":");
            for (String nextHop : nextHops) {
                out.println("\t" + nextHop);
            }
            out.println();
        }
        return baos.toString();
    }
    
    
    public static void main(String[] args)
    {
        if (args.length != 1) {
            System.err.println("usage: java Topology <first router>");
            System.exit(2);
        }
        
        System.out.println("Discovering the topology...");
        Topology topo = new Topology();
        
        topo.waitUntilFinished();
        
        System.out.println("----------------------------------------");
        System.out.println("Discovered topology:\n");
        System.out.print(topo.toString());
        
        UDPSnmpV3.close();
    }

}
