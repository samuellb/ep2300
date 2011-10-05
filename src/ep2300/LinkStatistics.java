package ep2300;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;

/**
 * Maintains statistics about the sent data on all links in a network.
 */
public final class LinkStatistics implements SnmpClient
{
    private Topology topology;

    private AtomicInteger outstandingRequests = new AtomicInteger(0);

    /**
     * Create a new LinkStatistics object with the defined topology
     * The topology must already be discovered
     * 
     * @param topology The topology of the network
     */
    public LinkStatistics(Topology topology)
    {
        this.topology = topology;
    }

    /**
     * Probe an IP for updated statistics.
     * 
     * @param ip The IP to probe
     */
    private void probe(String ip)
    {
        SNMP.sendOID(ip, this, SNMP.inOctetsOID);
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

            if (pdu.getErrstat() != 0) {
                System.out.println("A request has failed:");
                System.out.println(pdu.getError());
                return true; // No further processing is needed since the
                // request
                // failed
            }
            else if (pdu.getObjectID(0).equals(SNMP.usmStatsNotInTimeWindows)) {
                // Try again
                probe(address);
                return true;
            }
            else if (!SNMP.samePrefix(pdu.getObjectID(0), SNMP.inOctetsOID)) {
                System.out.println("Invalid response, probing again: "
                        + pdu.getObjectID(0));
                probe(address);

                return false;
            }
            else {
                Router router = topology.getRouterFromIP(address);
                long octets = 0;
                long packets = 0;

                octets = ArrayResponse.sum(pdu, SNMP.inOctetsOID);
                packets = ArrayResponse.sum(pdu, SNMP.inPacketsOID);

                // System.out.println(address+" octets = "+octets+"  packets = "+packets);

                router.octets.add(octets);
                router.packets.add(packets);

                return true; // done processing PDU
            }
        }
        finally {
            if (outstandingRequests.decrementAndGet() <= 0) {
                synchronized (this) {
                    notifyAll();
                }
            }
        }
    }

    /**
     * Probe all network nodes for their statistical data.
     */
    public void update()
    {
        for (Router address : topology.getTopology().values()) {
            probe(address.getIP());
        }
    }

    /**
     * Clear all statistics gathered
     */
    public void clear()
    {
        topology.clear();
        outstandingRequests.set(0);
    }

    /**
     * Waits until all requests have finished, or at most timeout
     * milliseconds. Returns the number of unfinished requests.
     * 
     * @param timeout The time to wait, in milliseconds
     * @return The number of outstanding requests when finished.
     */
    public synchronized int waitUntilFinished(long timeout)
    {
        long start = System.currentTimeMillis();

        while (outstandingRequests.get() > 0) {
            try {
                long delay = System.currentTimeMillis() - start;

                if (delay >= timeout) {
                    break;
                }

                wait(timeout - delay);
            }
            catch (InterruptedException e) {
                // Something happened, continue
            }
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
                out.printf("\t%dB (%s packets)\n", octets.next(), packets
                        .next());
            }
        }
        return baos.toString();
    }

    /**
     * For testing this class.
     * 
     * @param args Argument list from CLI
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
                System.out.println("unfinished requests: " + unfinished);
            }

            try {
                Thread.sleep(1000);
            }
            catch (InterruptedException e) {
                // Wait until notified, then continue.
            }
        }
    }

}
