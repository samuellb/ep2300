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

        for (int numCluster = 1; numCluster <= 5; ++numCluster) {
            KMeans<TimeStep> km = new RouterKMeans(means, numCluster);

            List<List<TimeStep>> clusters = km.getClusters();
            int n = clusters.size();
            int centroidX[] = new int[n];
            int centroidY[] = new int[n];

        }

    }
}
