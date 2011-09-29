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
 * Maintains statistics about the sent data on all links in a network.
 */
public final class LinkStatistics implements SnmpClient
{
    private static final SnmpOID outOctetsOID =
        new SnmpOID(".1.3.6.1.2.1.2.2.1.16");
    private static final SnmpOID outPacketsOID =
        new SnmpOID(".1.3.6.1.2.1.2.2.1.17");
    
    public static final class Link
    {
        public final Interface from;
        public final Interface to;
        
        Link(Interface from, Interface to)
        {
            this.from = from;
            this.to = to;
        }
        
        Link(String from, String to)
        {
            this(new Interface(from), new Interface(to));
        }
        
        public boolean equals(Object _other)
        {
            if (_other == null || !(_other instanceof Link)) return false;
            
            Link other = (Link)_other;
            return from.equals(other.from) && to.equals(other.to);
        }
    }
    
    public static final class Interface
    {
        public final String address;
        public long outOctets;
        public long outPackets;
        
        Interface(String address)
        {
            this.address = address;
        }
        
        public boolean equals(Object _other)
        {
            if (_other == null || !(_other instanceof Interface)) return false;
            
            Interface other = (Interface)_other;
            return address.equals(other.address);
        }
    }

    private Set<Link> links = new HashSet<Link>();
    
    private AtomicInteger outstandingRequests = new AtomicInteger(0);

    public LinkStatistics(Topology topology)
    {
        Map<String, Set<String>> neighbors = topology.getNeighborTable();
        
        for (String from : neighbors.keySet()) {
            for (String to : neighbors.get(from)) {
                links.add(new Link(from, to));
            }
        }
    }
    
    /**
     * Probe an IP for updated statistics.
     * 
     * @param ip The IP to probe
     */
    private void probe(String ip)
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
            //pdu.setCommand(SnmpAPI.GET_REQ_MSG);
            pdu.setClientID(id);
            
            // (nr=2, mr=0) should also work... but neither works.
            pdu.setNonRepeaters(0);
            //pdu.setMaxRepetitions(1);
            
            pdu.setMaxRepetitions(30); // should be a constant

            pdu.addNull(outOctetsOID); // we also need the interface number
//            pdu.addNull(outPacketsOID); -- comes directly after outOctetsOID
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
        System.out.println("callback!");
        UDPProtocolOptions opt = (UDPProtocolOptions) session
                    .getProtocolOptions();
        String router = opt.getRemoteAddress().getCanonicalHostName();
        
        if (pdu.getErrstat() != 0) { // FIXME is this state reachable?
            System.out.println("A request has failed:");
            System.out.println(pdu.getError());
            return true; // No further processing is needed since the request
            // failed
        }
        else if (pdu.getObjectID(0).toString().equals(".1.3.6.1.6.3.15.1.1.2.0")) {
            // Try again
            System.out.println("trying again...");
            probe(router);
            return true;
        }
        else if (!ArrayResponse.samePrefix(pdu.getObjectID(0), outOctetsOID)) {
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
            // TODO we should update the link statistics here
            System.out.println("hello? implement me!");
            System.out.println(pdu.printVarBinds());
            
            return true; // done processing PDU
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
        stats.probe(firstRouter);
        
        // sleep forever...
        while (true) {
            try { Thread.sleep(0); }
            catch (InterruptedException e) { }
        }
        
        //UDPSnmpV3.close();
    }

}
