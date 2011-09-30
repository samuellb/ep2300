package ep2300;

import java.util.HashSet;
import java.util.Set;

public class Router implements Comparable<Router>
{
    private String sysName;
    private String description;
    private String ip;
    public Set<String> nextHops;
    public Router(String sysName) {
        nextHops = new HashSet<String>();
        this.sysName = sysName;
    }
    public String getSysName()
    {
        return sysName;
    }
    public void setDescription(String description)
    {
        this.description = description;
    }
    public String getDescription()
    {
        return description;
    }
    public String getIp()
    {
        return ip;
    }
    @Override
    public int compareTo(Router obj)
    {
        return sysName.compareTo(obj.getSysName());
    }
}
