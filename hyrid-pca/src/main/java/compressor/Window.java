package compressor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a window of time series data, containing a list of Univariate points.
 * It also tracks the minimum and maximum values within this window.
 * Analogous to the C++ 'Window' struct.
 */
public class Window {
    float min;                     // Minimum value in this window
    float max;                     // Maximum value in this window
    List<Univariate> data;         // List of data points in this window

    /**
     * Constructs an empty Window, initializing min to positive infinity and max to negative infinity.
     */
    public Window() {
        this.min = Float.POSITIVE_INFINITY;
        this.max = Float.NEGATIVE_INFINITY;
        this.data = new ArrayList<>();
    }

    /**
     * Gets the number of data points currently in this window.
     * @return The size of the window.
     */
    public int size() {
        return this.data.size();
    }

    /**
     * Appends a Univariate data point to this window and updates the window's min and max values.
     * @param p The Univariate data point to append.
     */
    public void append(Univariate p) {
        this.data.add(p);
        this.min = Math.min(this.min, p.getValue());
        this.max = Math.max(this.max, p.getValue());
    }
}
