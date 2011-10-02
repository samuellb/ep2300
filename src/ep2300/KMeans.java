package ep2300;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class KMeans<T>
{
    private Random rand = new Random();
    
    /**
     * The samples that should be clustered
     */
    private final List<T> samples;
    
    /**
     * Number of clusters to create.
     */
    private final int k;
    
    /**
     * Centroid values of each cluster.
     */
    private final int[] centroids;
    
    public KMeans(List<T> samples, int k)
    {
        if (samples.size() < k)
            throw new IllegalArgumentException("Number of clusters is smaller than number of samples.");
        
        this.samples = samples;
        this.k = k;
        centroids = new int[k];
        initialize();
    }
    
    /**
     * Converts an object to a numeric value. Must be implemented by
     * user of this class.
     */
    public abstract double getValue(T a);
    
    /**
     * Calculates the distance between two samples.
     */
    private double distance(T a, T b)
    {
        return distance(a, getValue(b));
    }
    
    /**
     * Calculates the distance between two samples.
     */
    private double distance(T a, double b)
    {
        return Math.abs(getValue(a) - b);
    }
    
    /**
     * 
     */
    private double getMean(List<T> list)
    {
        double mean = 0;
        for (T elem : list) {
            mean += getValue(elem);
        }
        return mean / (double)list.size();
    }
    
    /**
     * Assigns random elements as the centroid values
     */
    private void initialize()
    {
        for (int i = 0; i < k; i++) {
            // Assign a new distinct element
            int index;
            boolean exists;
            do {
                index = rand.nextInt(samples.size());
                
                exists = false;
                for (int j = 0; j < i; j++) {
                    if (centroids[j] == index) {
                        exists = true;
                        break;
                    }
                }
            } while (exists);
            centroids[i] = index;
        }
    }
    
    public List<List<T>> getClusters()
    {
        // Create a list for each cluster
        List<List<T>> clusters = new ArrayList<List<T>>(k);
        for (int ki = 0; ki < k; ki++) {
            clusters.add(new ArrayList<T>());
        }
        
        //for (int i = 0; i < samples.size(); i++) {
        for (T sample : samples) {
            
            // Find closest centroid
            int closest = 0;
            double diff = Double.MAX_VALUE;
            
            for (int ki = 0; ki < k; ki++) {
                double thisdiff = distance(sample, samples.get(centroids[ki]));
                if (thisdiff < diff) {
                    closest = ki;
                    diff = thisdiff;
                }
            }
            
            // Add it to the cluster of that centroid
            List<T> cluster = clusters.get(closest);
            cluster.add(sample);
        }
        
        return clusters;
    }
    
    public void updateClusters()
    {
        List<List<T>> clusters = getClusters();
        for (int ki = 0; ki < k; ki++) {
            // Calculate mean inside this cluster
            double mean = getMean(clusters.get(ki));
            
            // Find closest sample
            int closest = 0;
            double diff = Double.MAX_VALUE;
            
            for (int i = 0; i < samples.size(); i++) {
                double thisdiff = distance(samples.get(i), mean);
                if (thisdiff < diff) {
                    closest = i;
                    diff = thisdiff;
                }
            }
            
            // Update centroid
            centroids[ki] = closest;
        }
    }
    
    public static void main(String[] args)
    {
        List<Integer> samples = new ArrayList<Integer>();
        for (String arg : args) {
            samples.add(Integer.parseInt(arg));
        }
        
        KMeans<Integer> km = new KMeans<Integer>(samples, 3) {
            public double getValue(Integer sample) {
                return sample;
            }
        };
        
        for (int iter = 0; iter < 10; iter++) {
            System.out.println("------------------------------------------");
            System.out.println("iteration: " + iter);
            System.out.println();
            
            List<List<Integer>> clusters = km.getClusters();
            for (List<Integer> cluster : clusters) {
                System.out.println("cluster:");
                System.out.print("  ");
                for (Integer sample : cluster) {
                    System.out.print("  "+sample);
                }
                System.out.println();
            }
            
            System.out.println();
            km.updateClusters();
        }
    }
    
}


