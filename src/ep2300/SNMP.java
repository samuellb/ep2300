package ep2300;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;

/**
 * A simple class containing constans and generall functions regarding SNMP
 */
public class SNMP
{
    /**
     * OID for the system name
     */
    public static final SnmpOID sysName = new SnmpOID(".1.3.6.1.2.1.1.5");
    /**
     * OID for the next-hops to the interface
     */
    public static final SnmpOID ipRouteNextHop = new SnmpOID(
            ".1.3.6.1.2.1.4.21.1.7");
    /**
     * A description of the system
     */
    public static final SnmpOID sysDescr = new SnmpOID(".1.3.6.1.2.1.1.1");

    /**
     * The number of responses in each query.
     */
    public static final int numPerResponse = 30;

    /**
     * Send a list of OIDs to a router and register the defined client with that
     * SnmpSession
     * 
     * @param ip The IP of the SNMP target
     * @param client The client to receive the callback
     * @param oids The OIDs to send
     * @return The number of tries before success
     */
    public static int sendOID(String ip, SnmpClient client, SnmpOID... oids)
    {
        int requests = 0;
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
            int id = session.addSnmpClientWithID(client);
            SnmpPDU pdu = new SnmpPDU();
            pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
            pdu.setClientID(id);

            pdu.setMaxRepetitions(numPerResponse);

            for (SnmpOID oid : oids) {
                pdu.addNull(oid);
            }
            try {
                requests++;
                session.send(pdu);
            }
            catch (SnmpException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return requests;
    }
}
