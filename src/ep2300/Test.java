package ep2300;

import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;


class Test {
    
    static void main(String[] args) throws Exception {
        SnmpAPI api = new SnmpAPI();
        SnmpSession session = new SnmpSession(api);
        session.open();

        SnmpPDU pdu = new SnmpPDU();
        pdu.setProtocolOptions(new UDPProtocolOptions("192.168.1.10"));
        pdu.setCommand(SnmpAPI.GET_REQ_MSG);
        pdu.addNull(new SnmpOID(".1.3.6.1.2.1.1.1.0"));
        SnmpPDU response_pdu = session.syncSend(pdu);
        if(response_pdu == null) {
             System.out.println("The Request has timed out.");
        } else {
             System.out.println(response_pdu.printVarBinds());
        }
    }
    
}

