package ep2300;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;


public class Test {
    
    public static void main(String[] args) throws Exception {
        
        SnmpSession session = UDPSnmpV3.createSession("192.168.1.10",
            "2G1332_student", "netmanagement");
        
        // .1.3.6.1.2. <-- this is implicit if the OID doesn't start with a .
        // .1.3.6.1.2.1.4.21.1.7 = ipRouteNextHop
        SnmpPDU pdu = new SnmpPDU();
        pdu.setUserName("2G1332_student".getBytes());
        pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
        pdu.setMaxRepetitions(30);
        pdu.setNonRepeaters(0);
        
        pdu.addNull(new SnmpOID(".1.3.6.1.2.1.4.21.1.7"));
        
        SnmpPDU response_pdu = session.syncSend(pdu);
        if (response_pdu == null) {
             System.out.println("The Request has timed out.");
        } else if (response_pdu.getErrstat() != 0) {
            System.out.println("The request failed.");
            System.out.println(response_pdu.getError());
        } else {
            System.out.println("got response of ipRouteNextHop!");
            System.out.println(response_pdu.printVarBinds());
        }
        
        System.out.println("done (code v 3)");

    }
    
}

