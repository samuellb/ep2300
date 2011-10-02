package ep2300;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ClusteringMonitor
{
    
    private static class TimeStep
    {
        public final int step;
        public final long octets;
        public final long packets;
        
        public TimeStep(int step, long octets, long packets)
        {
            this.step = step;
            this.octets = octets;
            this.packets = packets;
        }
        
        @Override
        public String toString()
        {
            return step+"(" + octets + "," + packets + ")";
        }
    }
    
    private final LinkStatistics stats;
    private final int interval;
    private final int timespan;
    private final int numClusters;
    private final int numTimeSteps;
    private List<TimeStep> means = new ArrayList<TimeStep>();
    
    
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
            Collection<Router> routers = stats.getTopology().getTopology().values();
            
            long octetSum = 0, packetSum = 0;
            
            // Calculate mean values
            for (Router router : routers) {
                Iterator<Long> it;
                it = router.octets.iterator();
                while (it.hasNext()) {
                    octetSum += it.next();
                }
                it = router.packets.iterator();
                while (it.hasNext()) {
                    packetSum += it.next();
                }
            }
            
            int numRouters = routers.size();
            means.add(new TimeStep(t, octetSum/numRouters, packetSum/numRouters));
            
            long workDuration = System.currentTimeMillis() - startTime;
            
            long delay = interval - workDuration;
            if (delay > 0) {
                try { Thread.sleep(delay); }
                catch (InterruptedException e) { }
            }
        }
        
        // Analyze the results
        KMeans<TimeStep> km = calculateKMeans();
        printKMeans(km);
    }
    
    private KMeans<TimeStep> calculateKMeans()
    {
        // k-means clustering
        KMeans<TimeStep> km = new KMeans<TimeStep>(means, numClusters)
        {
            private long square(long a) {
                return a*a;
            }
            
            @Override
            public double distance(TimeStep a, TimeStep b) {
                return Math.sqrt(
                    square(Math.abs(a.octets - b.octets)) +
                    square(Math.abs(a.packets - b.packets)));
            }
            
            @Override
            public TimeStep getMean(List<TimeStep> list) {
                int size = list.size();
                long octetMean = 0;
                long packetMean = 0;
                for (TimeStep elem : list) {
                    octetMean += elem.octets;
                    packetMean += elem.packets;
                }
                return new TimeStep(-1, octetMean/size, packetMean/size);
            }
        };
        
        km.updateClusters(10);
        
        return km;
    }
    
    private void printKMeans(KMeans<TimeStep> km)
    {
            // TODO process results (output, or analyze in task 3)
            System.out.println("-----------------------------------------");
            km.printClusters(); // maybe we should analyze which cluster is which
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


