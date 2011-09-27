package ep2300;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;


public class Test {
    
    public static void main(String[] args) throws Exception {
        
        SnmpSession session = UDPSnmpV3.createSession("192.168.1.10",
            "2G1332_student", "netmanagement");
        
        SnmpPDU pdu = new SnmpPDU();
        pdu.setCommand(SnmpAPI.GET_REQ_MSG);
        pdu.addNull(new SnmpOID(".1.3.6.1.2.1.1.1.0"));
        SnmpPDU response_pdu = session.syncSend(pdu);
        if(response_pdu == null) {
             System.out.println("The Request has timed out.");
        } else {
            System.out.println("got response!");
            System.out.println(response_pdu.printVarBinds());
        }
        
        System.out.println("done");
    }
    
}

