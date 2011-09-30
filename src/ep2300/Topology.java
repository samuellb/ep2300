package ep2300;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import com.adventnet.snmp.snmp2.SnmpString;
import com.adventnet.snmp.snmp2.SnmpVar;
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
    private static final int numPerResponse = 30;

    private Map<String, Router> neighbors = new HashMap<String, Router>();
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
        probe(ip, SNMP.sysName, SNMP.ipRouteNextHop);
        // probe(ip, SNMP.ipRouteNextHop);
    }

    /**
     * Probe an IP for routes starting at startingOID
     * 
     * @param ip The IP to probe
     * @param startingOID The OID to start at
     */
    private void probe(String ip, SnmpOID... startingOID)
    {
        outstandingRequests.addAndGet(SNMP.sendOID(ip, this, startingOID));
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
                return true; // No further processing is needed since the
                // request
                // failed
            }
            else {
                UDPProtocolOptions opt = (UDPProtocolOptions) session
                        .getProtocolOptions();
                String routerIP = opt.getRemoteAddress().getCanonicalHostName();

                if (ArrayResponse.samePrefix(pdu.getObjectID(0), SNMP.sysName)) {
                    // Check if this is a new router
                    Router router = neighbors.get(routerIP);
                    if (router == null) {
                        System.out.println("New router discovered: \t"
                                + routerIP);
                        router = new Router(routerIP);
                        neighbors.put(routerIP, router);
                    }

                    // Go through the lists of next hops (=neighbors)
                    try {
                        ArrayResponse<SnmpString> sysArray = new ArrayResponse<SnmpString>(
                                pdu, SNMP.sysName, SNMP.numPerResponse);
                        if (sysArray.getElements().size() > 0)
                            router.setSysName(sysArray.getElements().get(0).toString());
                        
                        ArrayResponse<SnmpIpAddress> respArray = new ArrayResponse<SnmpIpAddress>(
                                pdu, SNMP.ipRouteNextHop, numPerResponse);

                        for (SnmpIpAddress addr : respArray) {
                            String addrStr = addr.toString();
                            if (addrStr.equals(routerIP))
                                continue;

                            router.nextHops.add(addrStr);

                            if (probed.add(addrStr)) {
                                // Not yet probed
                                probe(addrStr);
                            }
                        }

                        if (!respArray.reachedEnd()) {
                            // The list is not complete, request more elements
                            probe(routerIP, respArray.getNextStartOID());
                        }
                        else {
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
                else {
                    // The callback was not a ipRouteNextHop
                    System.out.println("Invalid response, probing again: "
                            + pdu.getObjectID(0));
                    probe(routerIP);
                    return false;
                }

            }
        }
        finally {
//            System.out.println(outstandingRequests);
            if (outstandingRequests.decrementAndGet() <= 0) {
                System.out.println("Discovery finished.\n\n");
                synchronized (this) {
                    notifyAll();
                }
            }
        }
    }

    public synchronized void waitUntilFinished()
    {
        while (outstandingRequests.get() > 0) {
            try {
                wait();
            }
            catch (InterruptedException e) {
            }
        }
    }

    @Override
    public void debugPrint(String debugOutput)
    {
        System.err.println("debuging");
    }

    public Map<String, Router> getNeighborTable()
    {
        return neighbors;
    }

    @Override
    public String toString()
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        for (String routerIP : neighbors.keySet()) {
            Router router = neighbors.get(routerIP);
            out.printf("%s (%s):\n", routerIP, router.getSysName());
            for (String nextHop : router.nextHops) {
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
