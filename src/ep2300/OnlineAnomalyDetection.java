package ep2300;

import java.util.ArrayList;
import java.util.List;

/**
 * Detect anomalys online
 */
public class OnlineAnomalyDetection
{

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
     * Calculate the Davies-Bouldin index
     */
    public void calculateDBI()
    {
        monitor.collectData();
        rawMeans = monitor.getMeans();
        means = monitor.normalize();

        for (int iterations = 1; iterations <= 20; ++iterations) {
            KMeans<TimeStep> km = new RouterKMeans(means, 3);
            km.updateClusters(iterations);

            List<List<TimeStep>> clusters = km.getClusters();
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
                    if (i == j)
                        continue;
                    max = Math.max(max, (avgDist[i] + avgDist[j])
                            / distance(centroidX[i], centroidY[i],
                                    centroidX[j], centroidY[j]));
                }
                DB += max;
            }
            DB /= n;
            System.out.printf("%s: %f\n", iterations, DB);
        }
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
    
    public static void main(String[] argv) {
        if (argv.length != 4) {
            System.err
                    .println("usage: java OnlineAnomalyDetection <first router> <interval(ms)> <timespan> <clusters>");
            System.exit(2);
        }
        
        String firstRouter = argv[0];
        int interval = Integer.parseInt(argv[1]);
        int timespan = Integer.parseInt(argv[2]);
        int numClusters = Integer.parseInt(argv[3]);

        System.out.println("Discovering the topology...");
        Topology topo = new Topology(firstRouter);
        topo.waitUntilFinished();

        System.out.println("Monitoring...");
        LinkStatistics stats = new LinkStatistics(topo);

        ClusteringMonitor monitor = new ClusteringMonitor(stats, interval,
                timespan, numClusters);
        
        (new OnlineAnomalyDetection(monitor)).calculateDBI();
        
        UDPSnmpV3.close();
    }
}
