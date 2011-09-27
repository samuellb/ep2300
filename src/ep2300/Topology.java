package ep2300;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpIpAddress;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.SnmpVar;
import com.adventnet.snmp.snmp2.SnmpVarBind;
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
    private static final SnmpOID discoverOID = new SnmpOID(".1.3.6.1.2.1.4.21.1.7"); // ipRouteNextHop
    private static final int numPerResponse = 30;
    
    Map<String, Set<SnmpIpAddress>> neighbors = new HashMap<String, Set<SnmpIpAddress>>();

    public Topology()
    {
        probe("192.168.1.10");
        System.out.println("Topology constructor done");
    }
    
    private void probe(String ip)
    {
        probe(ip, discoverOID);
    }

    private void probe(String ip, SnmpOID startingOID)
    {
        SnmpSession session = null;
        try {
            session = UDPSnmpV3.createSession(ip); // Begin here
        }
        catch (SnmpException e) {
            System.err.println("Could not start session to ip " + ip);
            e.printStackTrace();
        }
        if (session != null) { // Should always happen
            int id = session.addSnmpClientWithID(this);
            SnmpPDU pdu = new SnmpPDU();
            pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
            pdu.setClientID(id);
            
            // TODO below nescesary? should be handled by someone qualified
            pdu.setMaxRepetitions(numPerResponse);

            pdu.addNull(discoverOID);
            try {
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

    @Override
    public boolean callback(SnmpSession session, SnmpPDU pdu, int requestID)
    {

        if (pdu.getErrstat() != 0) { // FIXME is this state reachable?
            System.out.println("A request has failed:");
            System.out.println(pdu.getError());
            return true; // No further processing is needed
        }
        else {
            UDPProtocolOptions opt = (UDPProtocolOptions) session
                    .getProtocolOptions();
            String router = opt.getRemoteAddress().getCanonicalHostName();
            
            // Check if this is a new router
            Set<SnmpIpAddress> nextHops = neighbors.get(router);
            if (nextHops == null) {
                System.out.println("New router discovered: \t" + router);
                nextHops = new HashSet<SnmpIpAddress>();
                neighbors.put(router, nextHops);
            }
            
            // Go through the lists of next hops (=neighbors)
            try {
                ArrayResponse<SnmpIpAddress> respArray =
                    new ArrayResponse<SnmpIpAddress>(pdu, discoverOID, numPerResponse);
                
                for (SnmpIpAddress addr : respArray) {
                    
                    nextHops.add(addr);
                    
                    if (neighbors.get(addr.toString()) == null) {
                        // Not yet probed
                        probe(addr.toString());
                    }
                }
                
                if (!respArray.reachedEnd()) {
                    // The list is not complete, request more elements
                    probe(router, respArray.getNextStartOID());
                }
            } catch (SnmpException e) {
                //throw new RuntimeException(e);
                e.printStackTrace();
            }
            
            return true; // done processing PDU
        }
    }

    @Override
    public void debugPrint(String debugOutput)
    {
        System.err.println("debuging");
    }

}
