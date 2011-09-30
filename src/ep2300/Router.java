package ep2300;

import java.util.HashSet;
import java.util.Set;

public class Router
{
    private String sysName;
    private String description;
    private String ip;
    public Set<String> nextHops;
    public Router(String ip) {
        nextHops = new HashSet<String>();
        this.ip = ip;
    }
    
    public void setSysName(String hostName)
    {
        this.sysName = hostName;
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
}
