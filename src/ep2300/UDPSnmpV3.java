package ep2300;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
    
    private static Map<String, SnmpSession> sessions = new HashMap<String, SnmpSession>();
    
    // Perhaps not the best place for them
    private static final String username = "2G1332_student";
    private static final String password = "netmanagement";
    
    // How many times we should try to connect again
    private static final int numRetries = 2;
    
    // Statistics
    private static AtomicInteger successfulConnections = new AtomicInteger();
    private static AtomicInteger attemptedConnections = new AtomicInteger();
    
    public static SnmpSession createSession(String address) throws SnmpException {
        return createSession(address, username, password);
    }
    
    /**
     * Creates an SNMPv3 session with the given username and password for
     * authorization (encryption is not used).
     */
    public static SnmpSession createSession(String address, String username,
            String password) throws SnmpException {
        
        // Check if a session exists to this address
        synchronized (sessions) {
            if (sessions.containsKey(address)) {
                // Wait for it to be established
                while (true) {
                    SnmpSession existing = sessions.get(address);
                    if (existing != null) return existing;
                    
                    try { sessions.wait(); }
                    catch (InterruptedException e) { }
                }
                // not reached
            }
            else {
                // Otherwise, continue and mark this address as "in progress"
                sessions.put(address, null);
            }
        }
        
        int attempt = 0;
        while (true) {
            try {
                attemptedConnections.incrementAndGet();
                SnmpSession session = tryCreateSession(address, username, password);
                
                // Success
                successfulConnections.incrementAndGet();
                synchronized(sessions) {
                    sessions.put(address, session);
                    sessions.notifyAll();
                }
                return session;
                
            } catch (SnmpException e) {
                // Error. Abort if the retry limit is reached
                if (attempt++ > numRetries) {
                    System.out.println("gave up with: "+address);
                    synchronized(sessions) {
                        sessions.remove(address);
                        sessions.notifyAll();
                    }
                    throw e;
                }
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
        session.setTimeout(750);
        session.open();
        
        boolean success = false;
        try {
            USMUtils.init_v3_parameters(username, null, USMUserEntry.MD5_AUTH,
                password, null, protocolOptions, session, true);
            
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
    
    public static int getAttemptedConnections() {
        return attemptedConnections.get();
    }
    
    public static int getSuccessfulConnections() {
        return successfulConnections.get();
    }
}


