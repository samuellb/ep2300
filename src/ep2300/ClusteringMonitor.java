package ep2300;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Monitor what clusters exists in the statistical data inside the network.
 */
public class ClusteringMonitor
{
    private final LinkStatistics stats;
    private final int interval;
    private final int numClusters;
    private final int numTimeSteps;
    private List<TimeStep> means = new ArrayList<TimeStep>();

    /**
     * Create a new ClusteringMonitor.
     * 
     * @param stats The statistics over the network
     * @param interval The interval between statistics gathering
     * @param numTimeSteps The number of datapoints to collect,
     *            numTimeSteps*interval is the total time of execution.
     * @param numClusters The number of clusters in the clustering.
     */
    public ClusteringMonitor(LinkStatistics stats, int interval,
            int numTimeSteps, int numClusters)
    {
        this.stats = stats;
        this.interval = interval;
        this.numClusters = numClusters;
        this.numTimeSteps = numTimeSteps;
    }

    /**
     * Loads data from a monitor output file.
     * 
     * @param filename The filename to load offline data from
     * @throws IOException If the file cannot be read
     */
    public void loadFromFile(String filename) throws IOException
    {
        int timestep = 1;
        for (List<String> words : PatternReader.getLines(filename)) {

            if (words.size() != 3) {
                continue;
            }

            String w0 = words.get(0);
            if (!w0.matches("[0-9]+:")) {
                continue;
                // long timestep = Integer.parseInt(w0.replace(":", ""));
            }

            // Average packet size
            long packetSize = Long.parseLong(words.get(1));

            // Average number of packets
            long packets = Long.parseLong(words.get(2));

            means.add(new TimeStep(timestep++, packetSize, packets));
        }
    }

    /**
     * Get a list of TimeSteps with data gathered so far
     * 
     * @return A list of TimeSteps
     */
    public List<TimeStep> getMeans()
    {
        return means;
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

    /**
     * Run the algorithm
     * 
     * There is three steps to this:
     * 1. Update the statistics
     * 2. Calculate means
     * 3. Calculate K-Means over data
     * 
     * Step one and two are looped until enough time has passed.
     */
    public final void run()
    {
        collectData();
        // Analyze the results
        KMeans<TimeStep> km = getKMeans();
        km.updateClusters(10);
        printKMeans(km);
    }

    /**
     * Collect statistical data and store it in means
     */
    public final void collectData()
    {
        means.clear();
        stats.clear();
        for (int t = 0; t < numTimeSteps; t++) {

            long startTime = System.currentTimeMillis();

            stats.update();
            int unfinished = stats.waitUntilFinished(interval);
            if (unfinished > 0) {
                System.out.println("unfinished requests: " + unfinished);
            }

            Collection<Router> routers = stats.getTopology().getTopology()
                    .values();

            double octetSum = 0, packetSum = 0;

            // Calculate mean values
            for (Router router : routers) {
                octetSum += diffLast(router.octets);
                packetSum += diffLast(router.packets);
            }

            double numRouters = routers.size();
            double packetMean = packetSum / numRouters;
            double octetMean = (octetSum / numRouters) / packetMean;

            if (t > 0) {
                System.out.println(t + ": " + (int) octetMean + " "
                        + (int) packetMean);
                means.add(new TimeStep(t, octetMean, packetMean));
            }

            long workDuration = System.currentTimeMillis() - startTime;

            long delay = interval - workDuration;
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                }
                catch (InterruptedException e) {
                    // If we are interrupted, just continue
                }
            }
        }

    }

    /**
     * Normalize the means
     * 
     * @return A normalize list of TimeSteps
     */
    public List<TimeStep> normalize()
    {
        // return this.means;/*

        double octetsMin = Double.MAX_VALUE;
        double packetsMin = Double.MAX_VALUE;
        double octetsMax = 0, packetsMax = 0;
        for (TimeStep t : means) {
            if (t.octets < octetsMin) {
                octetsMin = t.octets;
            }
            if (t.octets > octetsMax) {
                octetsMax = t.octets;
            }

            if (t.packets < packetsMin) {
                packetsMin = t.packets;
            }
            if (t.packets > packetsMax) {
                packetsMax = t.packets;
            }
        }
        List<TimeStep> means = new ArrayList<TimeStep>();

        // double min = Math.min(packetsMin, octetsMin);
        // double max = Math.max(packetsMax, octetsMax);

        // for (TimeStep t : this.means) {
        // means.add(new TimeStep(t.step, (t.octets - min) / (max - min),
        // (t.packets - min) / (max - min)));
        // }

        for (TimeStep t : this.means) {
            means.add(new TimeStep(t.step, (t.octets - octetsMin)
                    / (octetsMax - octetsMin), (t.packets - packetsMin)
                    / (packetsMax - packetsMin)));
        }
        return means;
        // */

    }

    /**
     * Calculate the clusters
     * 
     * @return The KMeans clustering
     */
    public KMeans<TimeStep> getKMeans()
    {
        // k-means clustering
        KMeans<TimeStep> km = new RouterKMeans(normalize(), numClusters);

        return km;
    }

    /**
     * Print the resulting K-Means clusters
     * 
     * @param km The KMeans clusters to print
     */
    public void printKMeans(KMeans<TimeStep> km)
    {
        System.out.println("-----------------------------------------");

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

    /**
     * Gather statistics over the network and calculate the clusters.
     * 
     * @param args CLI, Should contain, in order, the address to the first
     *            router, the interval, the timespan and the number of clusters.
     */
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
                timespan / interval, numClusters);
        monitor.run();

        UDPSnmpV3.close();
    }

}
