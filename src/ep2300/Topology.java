package ep2300;

import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;

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
    private SnmpOID discoverOID;

    public Topology()
    {
        discoverOID = new SnmpOID(".1.3.6.1.2.1.4.21.1.7"); // ipRouteNextHop

        try {
            UDPSnmpV3.createSession("192.168.1.10"); // Begin here

        }
        catch (SnmpException e) {
            System.err.println("Could not start initial session, aborting");
            System.exit(1); // TODO Do proper shutdown
        }
    }

    @Override
    public boolean authenticate(SnmpPDU pdu, String community)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean callback(SnmpSession session, SnmpPDU pdu, int requestID)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void debugPrint(String debugOutput)
    {
        // TODO Auto-generated method stub

    }

}
