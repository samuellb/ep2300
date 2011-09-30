package ep2300;

import java.util.HashSet;
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

    /**
     * Create a new router
     * @param sysName The name of the router
     */
    public Router(String sysName)
    {
        nextHops = new HashSet<String>();
        this.sysName = sysName;
    }

    /**
     * Get the router sysName
     * @return The name of the router
     */
    public String getSysName()
    {
        return sysName;
    }

    /**
     * Set the description of the router
     * @param description The router description
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * Get the description of the router
     * @return The description of the router
     */
    public String getDescription()
    {
        return description;
    }

    @Override
    public int compareTo(Router obj)
    {
        return sysName.compareTo(obj.getSysName());
    }
}
