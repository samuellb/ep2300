package ep2300;

import java.util.ArrayList;
import java.util.Collection;
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
            return step + "(" + octets + "," + packets + ")";
        }
    }

    private final LinkStatistics stats;
    private final int interval;
    private final int timespan;
    private final int numClusters;
    private final int numTimeSteps;
    private List<TimeStep> means = new ArrayList<TimeStep>();

    public ClusteringMonitor(LinkStatistics stats, int interval, int timespan,
            int numClusters)
    {
        this.stats = stats;
        this.interval = interval;
        this.timespan = timespan;
        this.numClusters = numClusters;
        numTimeSteps = timespan / interval;
    }

    /**
     * Returns the difference of the last two elements in a list.
     */
    private long diffLast(List<Long> list)
    {
        int size = list.size();
        if (size == 0) {
            return 0;
        }
        else if (size == 1) {
            return list.get(0);
        }
        else {
            return list.get(size - 1) - list.get(size - 2);
        }
    }

    public final void run()
    {
        for (int t = 0; t < numTimeSteps; t++) {

            long startTime = System.currentTimeMillis();

            stats.update();
            int unfinished = stats.waitUntilFinished(interval);
            if (unfinished > 0) {
                System.out.println("unfinished requests: " + unfinished);
            }

            Collection<Router> routers = stats.getTopology().getTopology()
                    .values();

            long octetSum = 0, packetSum = 0;

            // Calculate mean values
            for (Router router : routers) {
                octetSum += diffLast(router.octets);
                packetSum += diffLast(router.packets);
            }

            int numRouters = routers.size();
            long packetMean = packetSum / numRouters;
            long octetMean = (octetSum / numRouters) / packetMean;

            if (t > 0) {
                System.out.println(t + ": " + octetMean + " " + packetMean);
                means.add(new TimeStep(t, octetMean, packetMean));
            }

            long workDuration = System.currentTimeMillis() - startTime;

            long delay = interval - workDuration;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                }
                catch (InterruptedException e) {
                }
            }
        }

        // Analyze the results
        KMeans<TimeStep> km = calculateKMeans();
        printKMeans(km);
    }

    private KMeans<TimeStep> calculateKMeans()
    {
        long octetsMin = Long.MAX_VALUE;
        long packetsMin = Long.MAX_VALUE;
        long octetsMax = 0, packetsMax = 0;
        for (TimeStep t : means) {
            if (t.octets < octetsMin) {
                octetsMin = t.octets;
            }
            else if (t.octets > octetsMax) {
                octetsMax = t.octets;
            }

            if (t.packets < packetsMin) {
                packetsMin = t.packets;
            }
            else if (t.packets > packetsMax) {
                packetsMax = t.packets;
            }
        }

        List<TimeStep> means = new ArrayList<TimeStep>();
        for (TimeStep t : this.means) {
            means
                    .add(new TimeStep(
                            t.step,
                            (long) (100 * (t.octets - octetsMin) / (double) (octetsMax - octetsMin)),
                            (long) (100 * (t.packets - packetsMin) / (double) (packetsMax - packetsMin))));
        }

        // k-means clustering
        KMeans<TimeStep> km = new KMeans<TimeStep>(means, numClusters) {
            private long square(long a)
            {
                return a * a;
            }

            @Override
            public double distance(TimeStep a, TimeStep b)
            {
                return Math.sqrt(square(Math.abs(a.octets - b.octets))
                        + square(Math.abs(a.packets - b.packets)));
            }

            @Override
            public TimeStep getMean(List<TimeStep> list)
            {
                int size = list.size();
                long octetMean = 0;
                long packetMean = 0;
                for (TimeStep elem : list) {
                    octetMean += elem.octets;
                    packetMean += elem.packets;
                }
                return new TimeStep(-1, octetMean / size, packetMean / size);
            }
        };

        km.updateClusters(10);

        return km;
    }

    private void printKMeans(KMeans<TimeStep> km)
    {
        // TODO process results (output, or analyze in task 3)
        System.out.println("-----------------------------------------");
        // km.printClusters(); // maybe we should analyze which cluster is which

        List<List<TimeStep>> clusters = km.getClusters();
        for (List<TimeStep> cluster : clusters) {
            System.out.println("cluster:");
            System.out.print("  ");
            for (TimeStep sample : cluster) {
                System.out.print("  " + means.get(sample.step - 1));
            }
            System.out.println();
        }
    }

    public static void main(String[] args)
    {
        if (args.length != 4) {
            System.err
                    .println("usage: java ClusteringMonitor <first router> <interval(ms)> <timespan> <clusters>");
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
