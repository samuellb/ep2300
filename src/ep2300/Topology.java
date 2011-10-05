package ep2300;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpIpAddress;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.SnmpString;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;

/**
 * Creates a Topology over the given network.
 * 
 * It does this by probing all routers and its neighbors (basically performing a
 * tree traversal). It needs to have the sysName included in the response and if
 * it is not it probes again.
 */
public class Topology implements SnmpClient
{
    /**
     * A mapping between router names and routers, preferably unique
     */
    private Map<String, Router> routers = new HashMap<String, Router>();

    /**
     * A mapping from an IP to a router, each router can have several IPs.
     */
    private Map<String, Router> IPToRouter = new HashMap<String, Router>();
    private Set<String> probed = new HashSet<String>();

    private AtomicInteger outstandingRequests = new AtomicInteger(0);

    /**
     * Default constructor. Only used by fromFile()
     */
    private Topology()
    {
        // Nothing to be done, as we don't do any probing
    }

    /**
     * Start the probing
     * 
     * @param firstRouter The router to start probing at
     */
    public Topology(String firstRouter)
    {
        probed.add(firstRouter);
        probe(firstRouter);
    }

    /**
     * Loads link statistics from a topology output file.
     * 
     * @param filename The filename to read the statistics from
     * @return The topology described in the file
     * @throws IOException If the file cannot be read
     */
    public static Topology fromFile(String filename) throws IOException
    {
        Topology topo = new Topology();
        Router router = null;

        for (List<String> words : PatternReader.getLines(filename)) {

            if (words.size() == 0) {
                continue;
            }

            String w0 = words.get(0);
            if (w0.matches("^\\w+:$")) {
                // Start of a new router
                router = new Router(w0.replace(":", ""));
                topo.routers.put(router.getSysName(), router);

                // Add interface IPs
                for (int i = 2; i < words.size() - 1; i++) {
                    String ip = words.get(i);
                    router.addIP(ip);
                    topo.IPToRouter.put(ip, router);
                }
            }
            else if (w0.matches("^[0-9]+[.:][0-9..]+")) {
                // Neighbor list
                router.nextHops.add(w0);
            }
        }

        return topo;
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
        SNMP.sendOID(ip, this, startingOID);
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
            if (pdu.getErrstat() != 0) {
                System.err.println("A request has failed:");
                System.err.println(pdu.getError());
                return true; // No further processing is needed since the
                // request
                // failed
            }
            else {
                UDPProtocolOptions opt = (UDPProtocolOptions) session
                        .getProtocolOptions();
                String routerIP = opt.getRemoteAddress().getCanonicalHostName();

                if (SNMP.samePrefix(pdu.getObjectID(0), SNMP.sysName)) {
                    // Check if this is a new router
                    Router router;
                    ArrayResponse<SnmpString> sysArray = new ArrayResponse<SnmpString>(
                            pdu, SNMP.sysName, SNMP.numPerResponse);
                    // Make sure we get a sysName in the response
                    if (sysArray.getElements().size() > 0) {
                        String routerName = sysArray.getElements().get(0)
                                .toString();
                        router = routers.get(routerName);
                        if (router == null) {
                            System.out.println("New router discovered: \t"
                                    + routerName);
                            router = new Router(routerName);
                            router.addIP(routerIP);
                            routers.put(routerName, router);
                        }
                        IPToRouter.put(routerIP, router);
                        router.addIP(routerIP);
                    }
                    else {
                        System.err
                                .println("We didnt get router sysName at the same "
                                        + "time as we got the first respons from that router!");
                        probe(routerIP);
                        return true;
                    }

                    // Go through the lists of next hops (=neighbors)
                    ArrayResponse<SnmpIpAddress> respArray = new ArrayResponse<SnmpIpAddress>(
                            pdu, SNMP.ipRouteNextHop, SNMP.numPerResponse);

                    for (SnmpIpAddress addr : respArray) {
                        String addrStr = addr.toString();
                        if (addrStr.equals(routerIP)) {
                            continue;
                        }

                        router.nextHops.add(addrStr);

                        if (probed.add(addrStr)) {
                            // Not yet probed
                            probe(addrStr);
                        }
                    }

                    if (!respArray.reachedEnd()) {
                        // The list is not complete, request more elements
                        probe(routerIP, SNMP.sysName, respArray
                                .getNextStartOID());
                    }
                    else {
                        // We're done
                        session.removeSnmpClient(this);
                    }

                    return true; // done processing PDU
                }
                else {
                    // The callback was not a ipRouteNextHop
                    System.err.println("Invalid response, probing again: "
                            + pdu.getObjectID(0));
                    probe(routerIP);
                    return false;
                }

            }
        }
        finally {
            // System.out.println(outstandingRequests);
            if (outstandingRequests.decrementAndGet() <= 0) {
                System.out.println("Discovery finished.\n\n");
                synchronized (this) {
                    notifyAll();
                }
            }
        }
    }

    /**
     * This method is run until the topology discovery is completed
     */
    public synchronized void waitUntilFinished()
    {
        while (outstandingRequests.get() > 0) {
            try {
                wait();
            }
            catch (InterruptedException e) {
                // Nothing to do but continue
            }
        }
        UDPSnmpV3.close();
    }

    /**
     * Clear all statistics associated with all routers
     */
    public void clear()
    {
        for (Router router : routers.values()) {
            router.octets.clear();
            router.packets.clear();
        }
    }

    @Override
    public void debugPrint(String debugOutput)
    {
        System.err.println("debuging");
    }

    /**
     * Get the router topology
     * 
     * @return The topology
     */
    public Map<String, Router> getTopology()
    {
        return routers;
    }

    /**
     * Return the Router associated with the specified address
     * 
     * @param address The IP of the router
     * @return The Router listening to the address
     */
    public Router getRouterFromIP(String address)
    {
        return IPToRouter.get(address);
    }

    @Override
    public String toString()
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(baos);
        List<String> routerList = new ArrayList<String>(routers.keySet());
        Collections.sort(routerList);
        for (String hostname : routerList) {
            Router router = routers.get(hostname);
            out.printf("%s: ( ", hostname);
            for (String ip : router.ips) {
                out.printf("%s ", ip);
            }
            out.printf(")\n");
            for (String nextHop : router.nextHops) {
                // Print neighbor
                if (IPToRouter.get(nextHop) == router) {
                    continue;
                }
                out.println("\t" + nextHop + " (" + IPToRouter.get(nextHop)
                        + ")");
            }
            out.println();
        }
        return baos.toString();
    }

    /**
     * Runs the topology discovery with the defined router as the starting point
     * of the discovery.
     * 
     * @param args CLI args
     * @throws IOException Thrown if the topology is read from file and the file
     *             cannot be read
     */
    public static void main(String[] args) throws IOException
    {

        Topology topo;
        if (args.length == 2 && args[0].equals("-f")) {
            // Read from file
            topo = Topology.fromFile(args[1]);
        }
        else if (args.length == 1) {
            // Explore the network
            System.out.println("Discovering the topology...");

            topo = new Topology(args[0]);
            topo.waitUntilFinished();
            UDPSnmpV3.close();
        }
        else {
            System.err.println("usage: java Topology <first router>");
            System.exit(2);
            return;
        }

        System.out.println("----------------------------------------");
        System.out.println("Discovered topology:\n");
        System.out.print(topo.toString());
    }

}
