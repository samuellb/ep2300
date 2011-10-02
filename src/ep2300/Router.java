package ep2300;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A simple router description
 */
public class Router implements Comparable<Router>
{
    private String sysName;
    private String description;

    /**
     * The interfaces connected to this router
     */
    public Set<String> nextHops;

    public List<Long> octets = new LinkedList<Long>();
    public List<Long> packets = new LinkedList<Long>();
    
    /**
     * Containing all the IPs to this router.
     */
    public Set<String> ips = new HashSet<String>();

    /**
     * Create a new router
     * 
     * @param sysName The name of the router
     * @param ip One IP to connect to the router at
     */
    public Router(String sysName)
    {
        nextHops = new HashSet<String>();
        this.sysName = sysName;
    }

    /**
     * Get the router sysName
     * 
     * @return The name of the router
     */
    public String getSysName()
    {
        return sysName;
    }

    /**
     * Set the description of the router
     * 
     * @param description The router description
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * Get the description of the router
     * 
     * @return The description of the router
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Get one IP to the router
     * 
     * @return One IP of the router
     */
    public String getIP()
    {
        return ips.iterator().next();
    }

    /**
     * Add an IP to this router
     * @param ip The IP to add
     */
    public void addIP(String ip) {
        ips.add(ip);
    }

    @Override
    public String toString()
    {
        return sysName;
    }

    @Override
    public int compareTo(Router obj)
    {
        return sysName.compareTo(obj.getSysName());
    }

    @Override
    public boolean equals(Object _other)
    {
        if (_other == null || !(_other instanceof Router)) {
            return false;
        }

        Router other = (Router) _other;
        return sysName.equals(other.sysName);
    }
}
