package ep2300;

import java.util.ArrayList;

import com.adventnet.snmp.snmp2.ProtocolOptions;
import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;
import com.adventnet.snmp.snmp2.usm.USMUserEntry;
import com.adventnet.snmp.snmp2.usm.USMUtils;

public class UDPSnmpV3 {
    
    private UDPSnmpV3() { } // static class
    
    private static SnmpAPI api = new SnmpAPI();
    
    private static ArrayList<SnmpSession> sessions = new ArrayList<SnmpSession>();
    
    // Perhaps not the best place for them
    private static final String username = "2G1332_student";
    private static final String password = "netmanagement";
    
    // How many times we should try to connect again
    private static final int numRetries = 2;
    
    public static SnmpSession createSession(String address) throws SnmpException {
        return createSession(address, username, password);
    }
    
    /**
     * Creates an SNMPv3 session with the given username and password for
     * authorization (encryption is not used).
     */
    public static SnmpSession createSession(String address, String username,
            String password) throws SnmpException {
        
        int attempt = 0;
        while (true) {
            try {
                return tryCreateSession(address, username, password);
            } catch (SnmpException e) {
                if (attempt++ > numRetries) throw e;
            }
        }
    }
    
    /**
     * Tries once to create an SNMPv3 session.
     */
    private static SnmpSession tryCreateSession(String address, String username,
            String password) throws SnmpException {
        
        ProtocolOptions protocolOptions = new UDPProtocolOptions(address);
        
        SnmpSession session = new SnmpSession(api);
        session.setVersion(SnmpAPI.SNMP_VERSION_3);
        session.setProtocolOptions(protocolOptions);
        session.setUserName(username.getBytes());
        session.open();
        
        boolean success = false;
        try {
            USMUtils.init_v3_parameters(username, null, USMUserEntry.MD5_AUTH,
                password, null, protocolOptions, session, true);
            
            sessions.add(session);
            
            success = true;
        } finally {
            if (!success) session.close();
        }
        
        return session;
    }
    
    public static void close() {
        // TODO Go through the sessions and close them (perhaps unnescesary?)
        api.close();
    }
}


