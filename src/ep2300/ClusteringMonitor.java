package ep2300;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClusteringMonitor
{
    
    private static class RouterMean
    {
        public final Router router;
        public final long octets;
        public final long packets;
        
        public RouterMean(Router router, long octets, long packets)
        {
            this.router = router;
            this.octets = octets;
            this.packets = packets;
        }
        
        @Override
        public String toString()
        {
            return router.getSysName() + "(" + octets + "," + packets + ")";
        }
    }
    
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
            
            long octetMean, packetMean;
            
            // Calculate mean values
            List<RouterMean> means = new ArrayList<RouterMean>();
            for (Router router : stats.getTopology().getTopology().values()) {
                Iterator<Long> it;
                long sum;
                it = router.octets.iterator();
                sum = 0;
                while (it.hasNext()) {
                    sum += it.next();
                }
                octetMean = sum / router.octets.size();
                it = router.packets.iterator();
                sum = 0;
                while (it.hasNext()) {
                    sum += it.next();
                }
                packetMean = sum / router.packets.size();
                
                means.add(new RouterMean(router, octetMean, packetMean));
            }
            
            // k-means clustering
            KMeans km = new KMeans<RouterMean>(means, 3)
            {
                private long square(long a) {
                    return a*a;
                }
                
                @Override
                public double distance(RouterMean a, RouterMean b) {
                    return Math.sqrt(
                        square(Math.abs(a.octets - b.octets)) +
                        square(Math.abs(a.packets - b.packets)));
                }
                
                @Override
                public RouterMean getMean(List<RouterMean> list) {
                    int size = list.size();
                    long octetMean = 0;
                    long packetMean = 0;
                    for (RouterMean elem : list) {
                        octetMean += elem.octets;
                        packetMean += elem.packets;
                    }
                    return new RouterMean(null, octetMean/size, packetMean/size);
                }
            };
            
            km.updateClusters(10);
            
            // TODO process results (output, or analyze in task 3)
            System.out.println("-----------------------------------------");
            km.printClusters(); // we should analyze which cluster is which
            
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


