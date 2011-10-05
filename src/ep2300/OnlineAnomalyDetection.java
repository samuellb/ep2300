package ep2300;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detect anomalys online
 */
public class OnlineAnomalyDetection
{
    private ClusteringMonitor monitor;
    private List<TimeStep> means;

    /**
     * Create a new anomaly detector
     * 
     * @param monitor The ClusteringMonitor used for data gathering.
     */
    public OnlineAnomalyDetection(ClusteringMonitor monitor)
    {
        this.monitor = monitor;
    }

    /**
     * Get the best KMeans clustering by using Davies-Bouldin indexes.
     * 
     * @return The best clustering found
     */
    public KMeans<TimeStep> getBestKM()
    {
        means = monitor.normalize();

        double minDB = Double.MAX_VALUE;
        KMeans<TimeStep> bestKM = null;

        for (int iterations = 1; iterations <= 1000; ++iterations) {
            KMeans<TimeStep> km = new RouterKMeans(means, 3);
            km.updateClusters(1000);

            km.removeEmptyClusters();

            List<List<TimeStep>> clusters = km.getClusters();

            int n = clusters.size();

            double avgDist[] = new double[n];
            for (int i = 0; i < n; ++i) {
                avgDist[i] = 0;
                for (TimeStep t : clusters.get(i)) {
                    avgDist[i] += distance(t, km.getCentroid(i));
                }
                avgDist[i] /= clusters.get(i).size();
            }

            double DB = 0;
            for (int i = 0; i < n; ++i) {
                double max = 0;
                for (int j = 0; j < n; ++j) {
                    if (i == j) {
                        continue;
                    }
                    max = Math.max(max, (avgDist[i] + avgDist[j])
                            / distance(km.getCentroid(i), km.getCentroid(j)));
                }
                DB += max;
            }
            DB /= n;
            // System.out.printf("%s: %f\n", iterations, DB);
            if (DB < minDB) {
                bestKM = km;
                minDB = DB;
            }
        }
        return bestKM;
    }

    private double distance(TimeStep a, TimeStep b)
    {
        return distance(a.octets, a.packets, b.octets, b.packets);

    }

    private double distance(double octets, double packets, double x2, double y2)
    {
        return Math.sqrt((octets - x2) * (octets - x2) + (y2 - packets)
                * (y2 - packets));
    }

    /**
     * Run the algorithm, this will collect data and detect anomalies
     */
    public void run()
    {
        while (true) {
            monitor.collectData();
            detect();
        }
    }

    /**
     * Detects anomalies in collected data.
     */
    public void detect()
    {
        KMeans<TimeStep> km = getBestKM();
        int numClusters = km.getClusters().size();
        double centroidValue[] = new double[numClusters];
        double size[] = new double[numClusters];
        int minCentVal = 0;
        int maxCentVal = 0;
        int minSize = 0;
        int maxSize = 0;
        for (int i = 0; i < numClusters; ++i) {
            List<TimeStep> cluster = km.getClusters().get(i);

            centroidValue[i] = distance(0, 0, km.getCentroid(i).octets, km
                    .getCentroid(i).packets);
            // Find biggest centroid value
            if (centroidValue[i] > centroidValue[maxCentVal]) {
                maxCentVal = i;
            }
            if (centroidValue[i] < centroidValue[minCentVal]) {
                minCentVal = i;
            }

            double dist = 0;

            // Get the size of the clusters depending on the maximum
            // distance
            // from their centroid
            for (TimeStep t : cluster) {
                double curDist = distance(t, km.getCentroid(i));
                dist = Math.max(curDist, dist);
            }
            size[i] = dist;

            // Get the smallest cluster depending on number of elements in
            // cluster
            // size[i] = km.getClusters().get(i).size();

            if (size[i] > size[maxSize]) {
                maxSize = i;
            }
            if (size[i] < size[minSize]) {
                minSize = i;
            }

            System.out.printf("contiguity of cluster %d: %f\n", i,
                    getContiguity(cluster));
        }

        for (int i = 0; i < size.length; ++i) {
            System.out.printf("size[%d] = %f\n", i, size[i]);
        }

        for (int i = 0; i < centroidValue.length; ++i) {
            System.out.printf("centroidValue[%d] = %f\n", i, centroidValue[i]);
        }

        System.out.println("maxCentVal: " + maxCentVal);
        System.out.println("minSize: " + minSize);
        System.out.println("minCentVal: " + minCentVal);
        System.out.println("maxSize: " + maxSize);

        // Calculate average packet size of the centroids
        double avgOctets = 0;
        double avgPackets = 0;
        int k = 0;
        for (int i = 0; i < numClusters; ++i) {
            for (TimeStep t : km.getClusters().get(i)) {
                avgOctets += t.octets;
                avgPackets += t.packets;
                k++;
            }
        }
        avgOctets /= k;
        avgPackets /= k;

        System.out.println("avgOctets: " + avgOctets);
        System.out.println(".. " + km.getCentroid(maxSize).octets);
        System.out.println("avgPackets: " + avgPackets);
        System.out.println(".. " + km.getCentroid(maxSize).packets);

        for (int i = 0; i < numClusters; ++i) {
            if (km.getCentroid(i).octets < 0.01 * avgOctets
                    && km.getCentroid(i).packets > avgPackets * 100) {
                System.out.println("DDOS: " + i);
            }
        }

        // DDoS
        if (maxCentVal == minSize
                && km.getCentroid(minSize).octets <= 0.2 * avgOctets) {
            List<TimeStep> cluster = km.getClusters().get(minSize);

            double contiguity = getContiguity(cluster);
            if (contiguity <= 1.4) {
                System.out.println(minSize
                        + " is a contiguous DOS cluster (contiguity="
                        + contiguity + ")");
            }
            else {
                System.out.println(minSize + " is not contiguous, "
                        + "but otherwise looks like a dos cluster (contiguity="
                        + contiguity + ")");
            }
        }

        if (minCentVal == maxSize) {
            List<TimeStep> cluster = km.getClusters().get(maxSize);

            double contiguity = getContiguity(cluster);
            if (contiguity <= 1.4) {
                System.out.println(maxSize
                        + " is a contiguous Portscan cluster (contiguity="
                        + contiguity + ")");
            }
            else {
                System.out
                        .println(maxSize
                                + " is not contiguous, "
                                + "but otherwise looks like a portscan cluster (contiguity="
                                + contiguity + ")");
            }
        }
        monitor.printKMeans(km);
    }

    /**
     * Determines how contiguous a cluster is. This is defined as the
     * average squared time span between samples in the cluster.
     * 
     * @param cluster The cluster to determine contiguity on
     * @return The contiguity off the cluster
     */
    public double getContiguity(List<TimeStep> cluster)
    {
        if (cluster.size() <= 1) {
            return 1;
        }

        List<TimeStep> sorted = new ArrayList<TimeStep>(cluster);
        Collections.sort(sorted);

        double distSum = 0;
        for (int i = 0; i < cluster.size() - 1; i++) {
            int diff = sorted.get(i + 1).step - sorted.get(i).step;
            distSum += diff * diff;
        }

        return distSum / (cluster.size() - 1);
    }

    /**
     * Start online anomaly detection
     * 
     * @param argv CLI arguments containing the first router and the number of
     *            states to collect between anomaly detection
     * @throws IOException When the file for offline detection cannot be read.
     */
    public static void main(String[] argv) throws IOException
    {
        Topology topo;
        int numStates;
        boolean offline = false;
        String monitorFilename = null;

        if (argv.length == 3 && argv[0].equals("-f")) {
            // Run on algorithm offline, on saved files
            String topologyFilename = argv[1];
            monitorFilename = argv[2];
            numStates = 0; // not applicable

            topo = Topology.fromFile(topologyFilename);
            offline = true;
        }
        else if (argv.length == 2) {
            // Run online detection
            String firstRouter = argv[0];
            numStates = Integer.parseInt(argv[1]);

            System.out.println("Discovering the topology...");
            topo = new Topology(firstRouter);
            topo.waitUntilFinished();

            System.out.println("Monitoring...");
        }
        else {
            System.err
                    .println("usage: java OnlineAnomalyDetection <first router> <numStates>");
            System.exit(2);
            return;
        }

        LinkStatistics stats = new LinkStatistics(topo);

        ClusteringMonitor monitor = new ClusteringMonitor(stats, 1, numStates,
                3);

        OnlineAnomalyDetection OAD = new OnlineAnomalyDetection(monitor);

        if (offline) {
            monitor.loadFromFile(monitorFilename);
            OAD.detect();
        }
        else {
            OAD.run();
            UDPSnmpV3.close();
        }

    }
}
