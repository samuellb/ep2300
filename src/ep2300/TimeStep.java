package ep2300;

/**
 * Describes a timestep. A mapping betwen time step and octets
 * and packets.
 */
public class TimeStep
{
    /**
     * The step number
     */
    public final int step;
    /**
     * Number of octets in this timestep
     */
    public final double octets;
    /**
     * Number of packets in this timestep
     */
    public final double packets;

    /**
     * Create a new timestep
     * 
     * @param step The step number
     * @param octets Amount of octets
     * @param packets Amount of packets
     */
    public TimeStep(int step, double octets, double packets)
    {
        this.step = step;
        this.octets = octets;
        this.packets = packets;
    }

    @Override
    public String toString()
    {
        return step + "(" + (int) octets + "," + (int) packets + ")";
    }
}
