package ep2300;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;

public class SNMP
{
    public static final SnmpOID sysName = new SnmpOID(".1.3.6.1.2.1.1.5");
    public static final int numPerResponse = 30;
    public static final SnmpOID ipRouteNextHop = new SnmpOID(
            ".1.3.6.1.2.1.4.21.1.7");
    public static final SnmpOID sysDescr = new SnmpOID(".1.3.6.1.2.1.1.1");

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
