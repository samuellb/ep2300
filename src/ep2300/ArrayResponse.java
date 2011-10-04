package ep2300;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import com.adventnet.snmp.snmp2.SnmpOID;
import com.adventnet.snmp.snmp2.SnmpPDU;
import com.adventnet.snmp.snmp2.SnmpUnsignedInt;
import com.adventnet.snmp.snmp2.SnmpVar;
import com.adventnet.snmp.snmp2.SnmpVarBind;

/**
 * Parse and handle a SnmpVar in order to only get the relevant results.
 * 
 * @param <T> The type of the SnmpVar
 */
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
     * SnmpPDU pdu = new SnmpPDU();
     * pdu.setCommand(SnmpAPI.GETBULK_REQ_MSG);
     * pdu.setMaxRepetitions(30);
     * pdu.addNull(new SnmpOID(".1.3.6.1.2.1.4.21.1.7"));
     * 
     * ...you would use these parameters:
     * 
     * arrayPrefix = ".1.3.6.1.2.1.4.21.1.7"
     * numAskedFor = 30
     * 
     * @param pdu The SnmpPDU to parse
     * @param arrayPrefix The prefix of the OID, this is what is considered
     *            relevant data
     * @param numAskedFor The number of rows to ask for in each request.
     */
    @SuppressWarnings("unchecked")
    public ArrayResponse(SnmpPDU pdu, SnmpOID arrayPrefix, int numAskedFor)
    {
        boolean reachedEnd = false;
        SnmpOID lastOID = null;
        List<T> elements = new ArrayList<T>();

        Vector bindings = pdu.getVariableBindings();
        for (int i = 0; i < bindings.size(); ++i) {
            SnmpVarBind vb = (SnmpVarBind) bindings.get(i);
            SnmpOID oid = vb.getObjectID();

            if (SnmpOID.getLexicographicallyFirstOID(oid, arrayPrefix) == oid) {
                // This OID comes before the array, so skip it!
                continue;
            }

            if (!SNMP.samePrefix(oid, arrayPrefix)) {
                // Reached something that's not in the array.
                // This also means that we reached the end.
                reachedEnd = true;
                break;
            }

            SnmpVar var = ((SnmpVarBind) bindings.get(i)).getVariable();
            elements.add((T) var);
            lastOID = oid;
        }

        // If we have less than the asked for elements, then we have also
        // reached the end.
        if (bindings.size() < numAskedFor) {
            reachedEnd = true;
        }

        this.elements = elements;
        this.lastOID = lastOID;
        this.reachedEnd = reachedEnd;
    }

    /**
     * Check if all the data have been retrieved
     * 
     * @return If we are at the end of the data or not
     */
    public boolean reachedEnd()
    {
        return reachedEnd;
    }

    /**
     * Returns the OID we should start the next request from
     * 
     * @return The start OID.
     */
    public SnmpOID getNextStartOID()
    {
        if (reachedEnd) {
            return null; // no remaining elements
        }

        // This is not a valid OID of an element, but it comes before the
        // next element, and after the last element in this response.
        return new SnmpOID(lastOID.toString() + ".1");
    }

    /**
     * Get all relevant elements
     * 
     * @return A list of elements
     */
    public List<T> getElements()
    {
        return elements;
    }

    @Override
    public Iterator<T> iterator()
    {
        return elements.iterator();
    }

    /**
     * Calculates the sum of a response consisting of unsigned integers.
     * 
     * @param pdu The PDU to summarize over
     * @param arrayPrefix The relevant data
     * @return The sum
     */
    public static long sum(SnmpPDU pdu, SnmpOID arrayPrefix)
    {
        long sum = 0;
        for (SnmpUnsignedInt value : new ArrayResponse<SnmpUnsignedInt>(pdu,
                arrayPrefix, 0)) {
            sum += value.longValue();
        }
        return sum;
    }
}
