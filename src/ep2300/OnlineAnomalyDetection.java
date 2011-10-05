package ep2300;

import java.util.ArrayList;
import java.util.List;

/**
 * Detect anomalys online
 */
public class OnlineAnomalyDetection
{
    private class Result
    {
        KMeans<TimeStep> km;
        double centroidX[];
        double centroidY[];

        public Result(KMeans<TimeStep> km, double[] centroidX,
                double[] centroidY)
        {
            this.km = km;
            this.centroidX = centroidX;
            this.centroidY = centroidY;
        }
    }

    private ClusteringMonitor monitor;
    private List<Integer> DBI = new ArrayList<Integer>();
    private List<TimeStep> means;
    private List<TimeStep> rawMeans;

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
    public Result getBestKM()
    {
        monitor.collectData();
        rawMeans = monitor.getMeans();
        means = monitor.normalize();

        double minDB = Double.MAX_VALUE;
        Result bestKM = null;

        for (int iterations = 1; iterations <= 20; ++iterations) {
            KMeans<TimeStep> km = new RouterKMeans(means, 3);
            km.updateClusters(100);

            List<List<TimeStep>> clusters = km.getClusters();
            for (int i = 0; i < clusters.size(); ++i) {
                if (clusters.get(i).size() == 0) {
                    clusters.remove(i--);
                }
            }

            int n = clusters.size();
            double centroidX[] = new double[n];
            double centroidY[] = new double[n];

            // Calculate centroids of clusters
            for (int i = 0; i < n; ++i) {
                centroidX[i] = 0;
                centroidY[i] = 0;
                List<TimeStep> cluster = clusters.get(i);
                for (int j = 0; j < cluster.size(); ++j) {
                    centroidX[i] += cluster.get(j).octets;
                    centroidY[i] += cluster.get(j).packets;
                }
                centroidX[i] /= clusters.size();
                centroidY[i] /= clusters.size();
            }

            double avgDist[] = new double[n];
            for (int i = 0; i < n; ++i) {
                avgDist[i] = 0;
                for (TimeStep t : clusters.get(i)) {
                    avgDist[i] += distance(t, centroidX[i], centroidY[i]);
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
                            / distance(centroidX[i], centroidY[i],
                                    centroidX[j], centroidY[j]));
                }
                DB += max;
            }
            DB /= n;
            // System.out.printf("%s: %f\n", iterations, DB);
            if (DB < minDB) {
                bestKM = new Result(km, centroidX, centroidY);
            }
        }
        return bestKM;
    }

    private double distance(TimeStep a, double octets, double packets)
    {
        return distance(a.octets, a.packets, octets, packets);

    }

    private double distance(double octets, double packets, double x2, double y2)
    {
        return Math.sqrt((octets - x2) * (octets - x2) + (y2 - packets)
                * (y2 - packets));
    }

    public void run()
    {
        Result res = getBestKM();
        double centroidValue[] = new double[res.centroidX.length];
        double size[] = new double[res.centroidX.length];
        int minCentVal = 0;
        int maxCentVal = 0;
        int minSize = 0;
        int maxSize = 0;
        for (int i = 0; i < res.centroidX.length; ++i) {
            centroidValue[i] = distance(0, 0, res.centroidX[i],
                    res.centroidY[i]);
            // Find biggest centroid value
            if (centroidValue[i] > centroidValue[maxCentVal])
                maxCentVal = i;
            if (centroidValue[i] < centroidValue[minCentVal])
                minCentVal = i;

            double dist = 0;

            // Get the size of the clusters depending on the maximum distance
            // from their centroid
            for (TimeStep t : res.km.getClusters().get(i)) {
                double curDist = distance(t, res.centroidX[i], res.centroidY[i]);
                dist = Math.max(curDist, dist);
            }
            size[i] = dist;

            // Get the smallest cluster depending on number of elements in cluster
//            if (res.km.getClusters().get(i).size() < res.km.getClusters().get(
//                    minSize).size())
//                minSize = i;
//            if (res.km.getClusters().get(i).size() > res.km.getClusters().get(
//                    maxSize).size())
//                maxSize = i;

            if (size[i] > size[maxSize])
                maxSize = i;
            if (size[i] < size[minSize])
                minSize = i;
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

        // DDoS
        if (maxCentVal == minSize) {
            System.out.println(minSize + " is DDOS cluster!!!");
        }
        if (minCentVal == maxSize) {
            System.out.println(maxSize + " is Portscan cluster!!!");
        }
        monitor.printKMeans(res.km);
    }

    public static void main(String[] argv)
    {
        if (argv.length != 2) {
            System.err
                    .println("usage: java OnlineAnomalyDetection <first router> <numStates>");
            System.exit(2);
        }

        String firstRouter = argv[0];
        int numStates = Integer.parseInt(argv[1]);

        System.out.println("Discovering the topology...");
        Topology topo = new Topology(firstRouter);
        topo.waitUntilFinished();

        System.out.println("Monitoring...");
        LinkStatistics stats = new LinkStatistics(topo);

        ClusteringMonitor monitor = new ClusteringMonitor(stats, 1, numStates,
                3);

        OnlineAnomalyDetection OAD = new OnlineAnomalyDetection(monitor);
        OAD.run();

        // monitor.printKMeans((new
        // OnlineAnomalyDetection(monitor)).getBestKM().km);

        UDPSnmpV3.close();
    }
}
