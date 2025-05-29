package compressor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a time series, which is a sequence of Univariate data points.
 * This class allows adding data points and iterating over them.
 * Analogous to the C++ 'TimeSeries' class.
 */
public class TimeSeries implements Iterable<Univariate> {
    private List<Univariate> dataPoints; // Internal list to store the data points

    /**
     * Constructs an empty TimeSeries.
     */
    public TimeSeries() {
        this.dataPoints = new ArrayList<>();
    }

    /**
     * Adds a Univariate data point to the end of the time series.
     * @param dataPoint The Univariate data point to add.
     */
    public void push(Univariate dataPoint) {
        this.dataPoints.add(dataPoint);
    }

    /**
     * Gets the total number of data points in the time series.
     * @return The size of the time series.
     */
    public int size() {
        return this.dataPoints.size();
    }

    /**
     * Checks if the time series is empty.
     * @return True if there are no data points, false otherwise.
     */
    public boolean isEmpty() {
        return this.dataPoints.isEmpty();
    }

    /**
     * A method to signify that processing of this time series is complete.
     * In C++, this was 'finalize'. In Java, 'finalize' has a specific meaning for garbage collection,
     * so a different name is chosen. This method can be used for any cleanup or summary logging.
     */
    public void finishProcessing() {
        // Example: System.out.println("TimeSeries processing finished. Total points: " + size());
        // No specific cleanup needed for this simple list-based implementation.
    }

    /**
     * Returns an iterator over the Univariate data points in the time series.
     * This allows the TimeSeries object to be used in enhanced for-loops.
     * @return An iterator.
     */
    @Override
    public Iterator<Univariate> iterator() {
        return dataPoints.iterator();
    }
}
