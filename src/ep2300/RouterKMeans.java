package ep2300;

import java.util.List;

/**
 * The KMeans algorithm with specifics regarding Router statistics
 */
public class RouterKMeans extends KMeans<TimeStep>
{

    /**
     * Create a new RouterKMeans
     * 
     * @param samples The samples to use in the clustering
     * @param k The number of clusters
     */
    public RouterKMeans(List<TimeStep> samples, int k)
    {
        super(samples, k);
    }

    private double square(double a)
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
        double size = list.size();
        double octetMean = 0;
        double packetMean = 0;
        for (TimeStep elem : list) {
            octetMean += elem.octets;
            packetMean += elem.packets;
        }
        octetMean /= size;
        packetMean /= size;
        return new TimeStep(-1, octetMean, packetMean);
    }
}
