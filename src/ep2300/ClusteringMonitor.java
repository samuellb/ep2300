package ep2300;

public class ClusteringMonitor
{
    
    private final LinkStatistics stats;
    private final int interval;
    private final int timespan;
    private final int numClusters;
    private final int numTimeSteps;
    
    
    public ClusteringMonitor(LinkStatistics stats, int interval, int timespan, int numClusters)
    {
        this.stats = stats;
        this.interval = interval;
        this.timespan = timespan;
        this.numClusters = numClusters;
        this.numTimeSteps = timespan/interval;
    }
    
    public final void run()
    {
        for (int t = 0; t < numTimeSteps; t++) {
            
            long startTime = System.currentTimeMillis();
            
            stats.update();
            stats.waitUntilFinished();
            
            // TODO calculate mean value
            
            // TODO k-means clustering
            
            // TODO process results (output, or analyze in task 3)
            
            long workDuration = System.currentTimeMillis() - startTime;
            
            long delay = interval - workDuration;
            if (delay > 0) {
                try { Thread.sleep(delay); }
                catch (InterruptedException e) { }
            }
        }
    }
    
    public static void main(String[] args)
    {
        if (args.length != 4) {
            System.err.println("usage: java ClusteringMonitor <first router> <interval(ms)> <timespan> <clusters>");
            System.exit(2);
        }
        
        String firstRouter = args[0];
        int interval = Integer.parseInt(args[1]);
        int timespan = Integer.parseInt(args[2]);
        int numClusters = Integer.parseInt(args[3]);
        
        System.out.println("Discovering the topology...");
        Topology topo = new Topology(firstRouter);
        topo.waitUntilFinished();
        
        System.out.println("Monitoring...");
        LinkStatistics stats = new LinkStatistics(topo);
        
        ClusteringMonitor monitor = new ClusteringMonitor(stats, interval,
            timespan, numClusters);
        monitor.run();
                
        UDPSnmpV3.close();
    }
    
}


