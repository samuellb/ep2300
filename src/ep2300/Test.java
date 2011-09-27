package ep2300;

import java.util.Vector;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpIpAddress;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.SnmpVar;
import com.adventnet.snmp.snmp2.SnmpVarBind;


public class Test {
    
    private static SnmpOID ipRouteNextHop = new SnmpOID(".1.3.6.1.2.1.4.21.1.7");
    private static int numPerResponse = 30;
    
    public static void main(String[] args) throws Exception {
        
        SnmpSession session = UDPSnmpV3.createSession("192.168.1.10",
            "2G1332_student", "netmanagement");
        
        // .1.3.6.1.2. <-- this is implicit if the OID doesn't start with a .
        // .1.3.6.1.2.1.4.21.1.7 = ipRouteNextHop
        SnmpPDU pdu = new SnmpPDU();
        pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
        pdu.setMaxRepetitions(numPerResponse);
        pdu.setNonRepeaters(0);
        
        pdu.addNull(ipRouteNextHop);
        
        SnmpPDU response_pdu = session.syncSend(pdu);
        if (response_pdu == null) {
             System.out.println("The Request has timed out.");
        } else if (response_pdu.getErrstat() != 0) {
            System.out.println("The request failed.");
            System.out.println(response_pdu.getError());
        } else {
            System.out.println("got response of ipRouteNextHop!");
            
            ArrayResponse<SnmpIpAddress> nextHops =
                new ArrayResponse<SnmpIpAddress>(response_pdu, ipRouteNextHop, numPerResponse);
            
            for (SnmpIpAddress addr : nextHops.getElements()) {
                System.out.println(addr);
            }
            System.out.println("reached end? "+nextHops.reachedEnd()+
                "   next oid="+nextHops.getNextStartOID());
//            System.out.println(response_pdu.printVarBinds());
        }
        
        System.out.println("done (code v 4)");
        UDPSnmpV3.close();
    }
    
}

