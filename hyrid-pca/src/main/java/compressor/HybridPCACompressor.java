package compressor;

import java.io.*; // Keep existing IO for potential old methods or new file writing
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;


public class HybridPCACompressor {

    // Helper structure to hold segment data before writing to JSON
    private static class CompressedSegment {
        String timestamp; // Start timestamp of the segment
        int length;
        float value;

        CompressedSegment(String timestamp, int length, float value) {
            this.timestamp = timestamp;
            this.length = length;
            this.value = value;
        }

        JSONObject toJson() {
            JSONObject obj = new JSONObject();
            obj.put("timestamp", this.timestamp);
            obj.put("length", this.length);
            obj.put("value", this.value);
            return obj;
        }
    }

    /**
     * Adapts the __yield logic to add a segment to a list for later JSON output.
     *
     * @param segmentsList The list to add the new segment to.
     * @param segmentStartTime The starting timestamp of this segment.
     * @param length The length of the segment.
     * @param value The representative value for this segment.
     */
    private static void yieldSegmentToList(List<CompressedSegment> segmentsList, String segmentStartTime, int length, float value) {
        if (length > 0) {
            segmentsList.add(new CompressedSegment(segmentStartTime, length, value));
        }
    }

    /**
     * Adapts PMC compression to output segments to a list for later JSON output.
     *
     * @param segmentsList The list to add new segments to.
     * @param window The Window object to compress.
     * @param bound The error bound.
     */
    private static void pmcCompressWindowToList(List<CompressedSegment> segmentsList, Window window, float bound) {
        if (window == null || window.data.isEmpty()) {
            return;
        }

        float minVal = Float.POSITIVE_INFINITY;
        float maxVal = Float.NEGATIVE_INFINITY;
        float segmentValue = 0;
        int segmentLength = 0;
        String currentSegmentStartTime = "";

        for (int i = 0; i < window.data.size(); i++) {
            Univariate dataPoint = window.data.get(i);
            if (segmentLength == 0) { // Starting a new segment
                currentSegmentStartTime = dataPoint.getTime();
            }

            // Tentatively update min/max for the current point
            float nextMin = Math.min(minVal, dataPoint.getValue());
            float nextMax = Math.max(maxVal, dataPoint.getValue());

            if (segmentLength > 0 && (nextMax - nextMin > 2 * bound)) {
                // Current point causes violation, so yield the previous segment
                yieldSegmentToList(segmentsList, currentSegmentStartTime, segmentLength, segmentValue);
                
                // Start new segment with the current dataPoint
                minVal = dataPoint.getValue();
                maxVal = dataPoint.getValue();
                segmentValue = dataPoint.getValue();
                segmentLength = 1;
                currentSegmentStartTime = dataPoint.getTime();
            } else {
                // Point fits, or it's the first point
                minVal = nextMin;
                maxVal = nextMax;
                segmentValue = (minVal + maxVal) / 2;
                segmentLength++;
            }
        }

        // Yield any remaining segment
        if (segmentLength > 0) {
            yieldSegmentToList(segmentsList, currentSegmentStartTime, segmentLength, segmentValue);
        }
    }

    /**
     * New compression method: Reads from a simple "OriginalData" JSON file,
     * performs Hybrid-PCA, and writes compressed segments to a "CompressedData" JSON file.
     *
     * @param inputOriginalDataJsonPath Path to the simple JSON file (array of {"timestamp":long, "value":float}).
     * @param outputCompressedDataJsonPath Path for the output compressed JSON file.
     * @param wSize Window size.
     * @param nWindow Buffer size (number of windows).
     * @param bound Error bound.
     * @throws IOException If an I/O error occurs.
     * @throws JSONException If a JSON error occurs.
     */
    public static void compressSourceJsonToJsonOutput(String inputOriginalDataJsonPath, String outputCompressedDataJsonPath,
                                                 int wSize, int nWindow, float bound) throws IOException, JSONException {
        
        List<Univariate> univariateList = Main.loadUnivariateListFromSimpleJson(inputOriginalDataJsonPath); // Using Main's loader
        if (univariateList.isEmpty()) {
            System.err.println("No data loaded from " + inputOriginalDataJsonPath + ". Skipping compression.");
            // Create an empty JSON array as output
            try (PrintWriter out = new PrintWriter(new FileWriter(outputCompressedDataJsonPath))) {
                out.print(new JSONArray().toString(2));
            }
            return;
        }

        // Convert List<Univariate> to TimeSeries for the existing compression logic structure
        TimeSeries timeseries = new TimeSeries();
        for (Univariate u : univariateList) {
            timeseries.push(u);
        }
        
        List<CompressedSegment> outputSegments = new ArrayList<>();
        long operationStartTime = System.nanoTime();

        Buffer buffer = new Buffer();
        Window currentWindow = new Window();
        List<Univariate> pointsInCurrentWindowForTimestamp = new ArrayList<>(); // To track start time of segments

        Iterator<Univariate> it = timeseries.iterator();
        if (!it.hasNext()) { // Should be caught by univariateList.isEmpty() already
             try (PrintWriter out = new PrintWriter(new FileWriter(outputCompressedDataJsonPath))) {
                out.print(new JSONArray().toString(2)); // Empty output
            }
            return;
        }

        Univariate firstDataPoint = it.next();
        // Unlike binary format, we don't write a single global base time.
        // Each segment in JSON will have its own start timestamp.
        currentWindow.append(firstDataPoint);
        pointsInCurrentWindowForTimestamp.add(firstDataPoint);


        while (it.hasNext()) {
            Univariate data = it.next();
            currentWindow.append(data);
            pointsInCurrentWindowForTimestamp.add(data);

            if (currentWindow.size() == wSize) {
                String segmentStartTime = pointsInCurrentWindowForTimestamp.get(0).getTime(); // Timestamp of the first point in this window block

                if (buffer.isAppendable(currentWindow, bound)) {
                    buffer.append(currentWindow);
                    // We also need to track the start time of the data that went into the buffer
                    // For simplicity, when buffer yields, its start time is the start time of its first window.
                    // This requires Buffer to potentially store this or for us to retrieve it.
                    // Let's assume pointsInCurrentWindowForTimestamp gives context for currentWindow,
                    // and buffer would need similar context or we reconstruct.

                    if (buffer.size() == nWindow) {
                        // Determine start time of the buffer's data.
                        // This is tricky if buffer only stores Window objects without their original points list.
                        // For now, let's get the start time from the first point of the first window in the buffer.
                        // This requires Window to hold its original points or have a start time.
                        // Our Window class does hold `List<Univariate> data`.
                        String bufferStartTime = buffer.windows.get(0).data.get(0).getTime();
                        yieldSegmentToList(outputSegments, bufferStartTime, wSize * nWindow, (buffer.max + buffer.min) / 2);
                        buffer.clear();
                    }
                } else { 
                    if (buffer.size() > 0) {
                        String bufferStartTime = buffer.windows.get(0).data.get(0).getTime();
                        yieldSegmentToList(outputSegments, bufferStartTime, buffer.size() * wSize, (buffer.max + buffer.min) / 2);
                        buffer.clear();
                    }

                    if ((currentWindow.max - currentWindow.min) > 2 * bound) {
                        pmcCompressWindowToList(outputSegments, currentWindow, bound);
                    } else {
                        buffer.append(currentWindow); // This window starts a new buffer
                    }
                }
                currentWindow = new Window();
                pointsInCurrentWindowForTimestamp.clear();
            }
        }

        // End-of-stream processing
        if (buffer.size() > 0) {
            String bufferStartTime = buffer.windows.get(0).data.get(0).getTime();
            yieldSegmentToList(outputSegments, bufferStartTime, buffer.size() * wSize, (buffer.max + buffer.min) / 2);
            buffer.clear();
        }
        if (currentWindow.size() > 0) {
            if ((currentWindow.max - currentWindow.min) > 2 * bound) {
                pmcCompressWindowToList(outputSegments, currentWindow, bound);
            } else {
                String segmentStartTime = pointsInCurrentWindowForTimestamp.get(0).getTime();
                yieldSegmentToList(outputSegments, segmentStartTime, currentWindow.size(), (currentWindow.max + currentWindow.min) / 2);
            }
        }

        // Write outputSegments to JSON file
        JSONArray resultJsonArray = new JSONArray();
        for (CompressedSegment seg : outputSegments) {
            resultJsonArray.put(seg.toJson());
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(outputCompressedDataJsonPath))) {
            out.print(resultJsonArray.toString(2)); // Pretty print
        }

        long operationEndTime = System.nanoTime();
        double avgTimeNsPerPoint = (timeseries.size() > 0) ?
                                   (double)(operationEndTime - operationStartTime) / timeseries.size() : 0;
        System.out.printf(Locale.US, "Compress to JSON - Time per data point (ns): %.2f for %s%n", avgTimeNsPerPoint, inputOriginalDataJsonPath);
    }


    /**
     * New decompression method: Reads from a "CompressedData" JSON file
     * and writes decompressed data to a simple "DecompressedData" JSON file.
     *
     * @param inputCompressedDataJsonPath Path to the compressed JSON file.
     * @param outputDecompressedJsonPath Path for the output decompressed simple JSON file.
     * @param interval The original sampling interval.
     * @throws IOException If an I/O error occurs.
     * @throws JSONException If a JSON error occurs.
     */
    public static void decompressJsonInputToJsonOutput(String inputCompressedDataJsonPath, String outputDecompressedJsonPath) throws IOException, JSONException {
        File inputFile = new File(inputCompressedDataJsonPath);
        if (!inputFile.exists()) {
            throw new IOException("Compressed JSON input file not found: " + inputCompressedDataJsonPath);
        }

        String content = new String(Files.readAllBytes(Paths.get(inputCompressedDataJsonPath)));
        JSONArray compressedArray = new JSONArray(content);
        JSONArray decompressedArray = new JSONArray();

        System.out.println("Decompressing from " + inputCompressedDataJsonPath + "...");
        long totalPointsDecompressed = 0;

        for (int i = 0; i < compressedArray.length(); i++) {
            JSONObject segment = compressedArray.getJSONObject(i);
            String segmentStartTime = segment.get("timestamp").toString();
            int length = segment.getInt("length");
            float value = ((Number) segment.get("value")).floatValue();

            for (int j = 0; j < length; j++) {
                JSONObject point = new JSONObject();
                point.put("timestamp", segmentStartTime);
                point.put("value", value);
                decompressedArray.put(point);
                totalPointsDecompressed++;
            }
        }

        try (PrintWriter out = new PrintWriter(new FileWriter(outputDecompressedJsonPath))) {
            out.print(decompressedArray.toString(2)); // Pretty print
        }
        System.out.println("Decompression to JSON finished. Output: " + outputDecompressedJsonPath + ". Total points: " + totalPointsDecompressed);
    }


    // --- Old binary decompress method (can be kept for compatibility or removed) ---
    private static void decompressSegment(PrintWriter csvWriter, int interval, long baseTime, int length, float value) {
        for (int i = 0; i < length; i++) {
            csvWriter.println((baseTime + (long)i * interval) + "," + String.format(java.util.Locale.US, "%.6f", value));
        }
    }
    public static void decompress(String inputFilePath, String outputFilePath, int interval) throws IOException {
        // This is the old method outputting to CSV from binary HPC
        // Kept for reference, but new workflow uses JSON-to-JSON decompression
        long totalSegmentsProcessed = 0;
        long totalProcessingTimeNs = 0;
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(inputFilePath)));
             PrintWriter csvWriter = new PrintWriter(new BufferedWriter(new FileWriter(outputFilePath)))) {
            long baseTime = dis.readLong(); 
            while (dis.available() > 0) {
                long segStartTime = System.nanoTime();
                short rawLength = dis.readShort(); int length = Short.toUnsignedInt(rawLength); float value = dis.readFloat();
                if (length == 0 && dis.available() == 0) { System.err.println("Warning: Zero-length segment at end."); break; }
                if (length == 0 && dis.available() > 0) { System.err.println("Warning: Zero-length segment mid-stream."); continue; }
                decompressSegment(csvWriter, interval, baseTime, length, value);
                baseTime += (long)length * interval;
                totalProcessingTimeNs += (System.nanoTime() - segStartTime); totalSegmentsProcessed++;
            }
        }
        double avgTimeNs = (totalSegmentsProcessed > 0) ? (double)totalProcessingTimeNs / totalSegmentsProcessed : 0;
        System.out.printf(Locale.US, "Old Decompress to CSV - Time per segment (ns): %.2f%n", avgTimeNs);
    }
}