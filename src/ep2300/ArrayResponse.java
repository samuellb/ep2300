package ep2300;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import com.adventnet.snmp.snmp2.SnmpException;
import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpVar;
import com.adventnet.snmp.snmp2.SnmpVarBind;

public final class ArrayResponse<T extends SnmpVar> implements Iterable<T>
{
    
    private final List<T> elements;
    private final SnmpOID lastOID;
    private final boolean reachedEnd;
    
    /**
     * Parses a response of a getbulk operation on an array. The response
     * may contain nodes after the array, which are not added. This class
     * also detects if the end of the array was reached, or if there's
     * more elements that didn't fit in the response.
     *
     * For instance, with this request PDU...
     *
     *  SnmpPDU pdu = new SnmpPDU();
     *  pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
     *  pdu.setMaxRepetitions(30);
     *  pdu.addNull(new SnmpOID(".1.3.6.1.2.1.4.21.1.7"));
     *
     * ...you would use these parameters:
     *
     *  arrayPrefix = ".1.3.6.1.2.1.4.21.1.7"
     *  numAskedFor = 30
     */
    @SuppressWarnings("unchecked")
    public ArrayResponse(SnmpPDU pdu, SnmpOID arrayPrefix, int numAskedFor)
        throws SnmpException
    {
        boolean reachedEnd = false;
        SnmpOID lastOID = null;
        List<T> elements = new ArrayList<T>();
        
        Vector bindings = pdu.getVariableBindings();
        for (int i = 0; i < bindings.size(); ++i) {
            SnmpVarBind vb = (SnmpVarBind)bindings.get(i);
            SnmpOID oid = vb.getObjectID();
            
            if (SnmpOID.getLexicographicallyFirstOID(oid, arrayPrefix) == oid) {
                // This OID comes before the array, so skip it!
                continue;
            }
            
            if (!samePrefix(oid, arrayPrefix)) {
                // Reached something that's not in the array.
                // This also means that we reached the end.
                reachedEnd = true;
                break;
            }
            
            SnmpVar var = ((SnmpVarBind)bindings.get(i)).getVariable();
            elements.add((T)var);
            lastOID = oid;
        }
        
        // If we have less than the asked for elements, then we have also reached the end.
        if (bindings.size() < numAskedFor) {
            reachedEnd = true;
        }
        
        this.elements = elements;
        this.lastOID = lastOID;
        this.reachedEnd = reachedEnd;
    }
    
    /**
     * Checks whether oid starts with prefix. For example:
     *
     *   samePrefix("1.2.1", "1.2") == true
     *   samePrefix("1.3",   "1.2") == false
     *   samePrefix("1.2",   "1.2") == true
     *   
     *   TODO Should this be moved somewhere else? its used in Topology.
     */
    static boolean samePrefix(SnmpOID oid, SnmpOID prefix)
    {
        String os = oid.toString()+".";
        String ps = prefix.toString()+".";
        return os.startsWith(ps);
    }
    
    public boolean reachedEnd()
    {
        return reachedEnd;
    }
    
    /**
     * Returns the OID we should start the next request from
     */
    public SnmpOID getNextStartOID()
    {
        if (reachedEnd) return null; // no remaining elements
        
        // This is not a valid OID of an element, but it comes before the
        // next element, and after the last element in this response.
        return new SnmpOID(lastOID.toString()+".1");
    }
    
    public List<T> getElements()
    {
        return elements;
    }

    @Override
    public Iterator<T> iterator()
    {
        return elements.iterator();
    }
}


