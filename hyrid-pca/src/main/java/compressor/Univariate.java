package compressor;

/**
 * Represents a single univariate data point, typically a time-value pair.
 * This class is analogous to the C++ Univariate structure/class.
 */
public class Univariate {
    private String time;  // Corresponds to C++ time_t, best mapped to long in Java
    private float value; // The actual data value

    /**
     * Constructs a new Univariate data point.
     * @param time The timestamp of the data point.
     * @param value The value of the data point.
     */
    public Univariate(String time, float value) {
        this.time = time;
        this.value = value;
    }

    /**
     * Gets the timestamp of the data point.
     * @return The time.
     */
    public String getTime() {
        return time;
    }

    /**
     * Gets the value of the data point.
     * @return The value.
     */
    public float getValue() {
        return value;
    }

    /**
     * Provides a string representation of the Univariate object.
     * @return A string in the format "Univariate{time=..., value=...}".
     */
    @Override
    public String toString() {
        return "Univariate{time=" + time + ", value=" + value + "}";
    }
}
