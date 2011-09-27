package ep2300;

import com.adventnet.snmp.snmp2.ProtocolOptions;
import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpEngineEntry;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;
import com.adventnet.snmp.snmp2.usm.USMUserEntry;
import com.adventnet.snmp.snmp2.usm.USMUtils;


public class Test {
    
    public static void main(String[] args) throws Exception {
        
        SnmpAPI api = new SnmpAPI();
        ProtocolOptions protocolOptions = new UDPProtocolOptions("192.168.1.10");
        
        SnmpSession session = new SnmpSession(api);
        session.setVersion(SnmpAPI.SNMP_VERSION_3);
        session.setProtocolOptions(protocolOptions);
        //session.setUserName("2G1332_student".getBytes());
        session.open();
        
        USMUtils.init_v3_parameters("2G1332_student", null, USMUserEntry.MD5_AUTH,
            "netmanagement", null, protocolOptions, session, true);
        
        SnmpPDU pdu = new SnmpPDU();
//        pdu.setProtocolOptions(protocolOptions);
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

