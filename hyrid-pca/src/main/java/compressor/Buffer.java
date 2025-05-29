package compressor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a buffer that holds multiple 'Window' objects.
 * It tracks the overall minimum and maximum values across all windows it contains.
 * Analogous to the C++ 'Buffer' struct.
 */
public class Buffer {
    float min;                  // Overall minimum value in this buffer
    float max;                  // Overall maximum value in this buffer
    List<Window> windows;       // List of windows in this buffer

    /**
     * Constructs an empty Buffer, initializing min to positive infinity and max to negative infinity.
     */
    public Buffer() {
        this.min = Float.POSITIVE_INFINITY;
        this.max = Float.NEGATIVE_INFINITY;
        this.windows = new ArrayList<>();
    }

    /**
     * Gets the number of windows currently in this buffer.
     * @return The size of the buffer.
     */
    public int size() {
        return this.windows.size();
    }

    /**
     * Removes the last window from the buffer.
     * Note: If used, min/max might need recalculation if they depended on the popped window.
     * The C++ version's pop_back was not directly used in the compress logic path.
     */
    public void pop() {
        if (!this.windows.isEmpty()) {
            this.windows.remove(this.windows.size() - 1);
            // Recalculating min/max would be needed here if this method is used actively
            // and min/max values are critical after popping.
            // For this specific HybridPCA port, this method isn't crucial for the compression flow.
        }
    }

    /**
     * Appends a Window to this buffer and updates the buffer's overall min and max values.
     * @param window The Window to append.
     */
    public void append(Window window) {
        this.windows.add(window);
        this.min = Math.min(this.min, window.min);
        this.max = Math.max(this.max, window.max);
    }

    /**
     * Checks if a new window can be appended to this buffer without violating the error bound.
     * @param window The Window to potentially append.
     * @param bound The error bound (e.g., maximum allowed difference between n_max and n_min).
     * @return True if the window is appendable, false otherwise.
     */
    public boolean isAppendable(Window window, float bound) {
        float nMin = Math.min(this.min, window.min); // Potential new minimum if window is added
        float nMax = Math.max(this.max, window.max); // Potential new maximum if window is added
        // System.out.println("min: " + nMin + " max: " + nMax);
        return (nMax - nMin) <= 2 * bound;
    }

    /**
     * Clears all windows from this buffer and resets min and max values.
     * In Java, the Window objects themselves will be garbage collected if no other references exist.
     */
    public void clear() {
        this.min = Float.POSITIVE_INFINITY;
        this.max = Float.NEGATIVE_INFINITY;
        this.windows.clear();
    }
}
