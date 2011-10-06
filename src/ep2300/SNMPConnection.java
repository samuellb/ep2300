package ep2300;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.adventnet.snmp.snmp2.ProtocolOptions;
import com.adventnet.snmp.snmp2.SnmpAPI;
import com.adventnet.snmp.snmp2.SnmpClient;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpSession;
import com.adventnet.snmp.snmp2.UDPProtocolOptions;
import com.adventnet.snmp.snmp2.usm.USMUserEntry;
import com.adventnet.snmp.snmp2.usm.USMUtils;

/**
 * Handles the SnmpAPI and the connections to SNMP servers
 * It keeps track of all connections.
 */
public class SNMPConnection
{

    /**
     * Container mapping one SnmpSession to one clientID
     */
    public final static class Result
    {
        private final SnmpSession session;
        private final int clientId;

        private Result(SnmpSession session, int clientId)
        {
            this.session = session;
            this.clientId = clientId;
        }

        /**
         * Get the session contained within this result
         * 
         * @return The session
         */
        public SnmpSession getSession()
        {
            return session;
        }

        /**
         * Get the ID of this client
         * 
         * @return The client ID
         */
        public int getClientId()
        {
            return clientId;
        }
    }

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

    /**
     * Wrapper around createSession(String, SnmpClient, String, String) with the
     * default username and password registering the client for callbacks.
     * 
     * @see #createSession(String, SnmpClient, String, String)
     * 
     * @param address The address to connect to
     * @param client The client to register
     * @return The resulting Result object
     * @throws SnmpException If trying to open the same connection several
     *             times, should not happen.
     */
    public static Result createSession(String address, SnmpClient client)
            throws SnmpException
    {
        return createSession(address, client, username, password);
    }

    /**
     * Wrapper around createSession(String, SnmpClient, String, String) with the
     * default username and password. This method does not register for
     * callbacks.
     * 
     * @see #createSession(String, SnmpClient, String, String)
     * 
     * @param address The address to connect to
     * @return The resulting Result object
     * @throws SnmpException If trying to open the same connection several
     *             times, should not happen.
     */
    public static SnmpSession createSession(String address)
            throws SnmpException
    {
        return createSession(address, username, password);
    }

    /**
     * Create a new session to the specified address if it has not yet been
     * established, otherwise return the previously estabilshed connection.
     * 
     * @param address The address to connect to
     * @param client The client to register
     * @param username The username to authenticate with
     * @param password The password to authenticate with
     * @return The resulting Result object
     * @throws SnmpException If trying to open the same connection several
     *             times, should not happen.
     */
    @SuppressWarnings("unchecked")
    public static Result createSession(String address, SnmpClient client,
            String username, String password) throws SnmpException
    {

        SnmpSession session = createSession(address, username, password);

        // Check if the client has been registered already
        int id = -1;
        boolean registered = false;
        Hashtable clients = session.getSnmpClientsWithID();
        for (Object elem : clients.keySet()) {
            Integer mapId = (Integer) elem;
            if (clients.get(mapId) == client) {
                id = mapId;
                registered = true;
                break;
            }
        }

        if (!registered) {
            // register a new session
            id = session.addSnmpClientWithID(client);
        }

        return new Result(session, id);
    }

    /**
     * Creates an SNMPv3 session with the given username and password for
     * authorization (encryption is not used).
     * 
     * @param address The address to connect to
     * @param username The username to authenticate with
     * @param password The password to authenticate with
     * @return A SnmpSession to the address specified
     * @throws SnmpException If trying to open the same connection several
     *             times, should not happen.
     */
    public static SnmpSession createSession(String address, String username,
            String password) throws SnmpException
    {

        // Check if a session exists to this address
        synchronized (sessions) {
            if (sessions.containsKey(address)) {
                // Wait for it to be established
                while (true) {
                    SnmpSession existing = sessions.get(address);
                    if (existing != null) {
                        return existing;
                    }

                    try {
                        sessions.wait();
                    }
                    catch (InterruptedException e) {
                        // We ignore the error and try again later.
                    }
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
                SnmpSession session = tryCreateSession(address, username,
                        password);

                // Success
                successfulConnections.incrementAndGet();
                synchronized (sessions) {
                    sessions.put(address, session);
                    sessions.notifyAll();
                }
                return session;

            }
            catch (SnmpException e) {
                // Error. Abort if the retry limit is reached
                if (attempt++ > numRetries) {
                    System.out.println("gave up with: " + address);
                    synchronized (sessions) {
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
    private static SnmpSession tryCreateSession(String address,
            String username, String password) throws SnmpException
    {

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
        }
        finally {
            if (!success) {
                session.close();
            }
        }

        return session;
    }

    /**
     * Close all sessions and the API.
     */
    public static void close()
    {
        for (SnmpSession session : sessions.values()) {
            session.close();
        }
        sessions.clear();
        api.close();
    }

    /**
     * Get the number of connection attempts
     * 
     * @return The number of connection attempts.
     */
    public static int getAttemptedConnections()
    {
        return attemptedConnections.get();
    }

    /**
     * Get the number of successful connections
     * 
     * @return The number of successful connections
     */
    public static int getSuccessfulConnections()
    {
        return successfulConnections.get();
    }
}
