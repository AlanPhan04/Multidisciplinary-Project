package compressor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class Main {

    private static final String DEFAULT_SOURCE_JSON_INPUT_FILE = "Data/sensor_data.json";
    
    private static final String TEMPERATURE_ORIGINAL_JSON = "temperatureOriginalData.json";
    private static final String HUMIDITY_ORIGINAL_JSON = "humidityOriginalData.json";
    private static final String TEMPERATURE_COMPRESSED_JSON = "temperatureCompressedData.json";
    private static final String HUMIDITY_COMPRESSED_JSON = "humidityCompressedData.json";
    private static final String TEMPERATURE_DECOMPRESSED_JSON = "temperatureDecompressedData.json";
    private static final String HUMIDITY_DECOMPRESSED_JSON = "humidityDecompressedData.json";

    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_HUMIDITY = "humidity";

    private static long parseTimestampToMillis(String timeStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            LocalTime localTime = LocalTime.parse(timeStr, formatter);
            return localTime.toNanoOfDay() / 1_000_000L;
        } catch (Exception e) {
            System.err.println("Error parsing timestamp: \"" + timeStr + "\". Defaulting to 0. Error: " + e.getMessage());
            return 0L;
        }
    }

    /**
     * Preprocesses the main source JSON file, splitting it into separate
     * JSON files for temperature and humidity, each with a simpler format.
     * Format: [{"timestamp": long, "value": float}, ...]
     */
    private static void preprocessAndSplitOriginalJson() throws IOException, JSONException {
        File inputFile = new File(DEFAULT_SOURCE_JSON_INPUT_FILE);
        if (!inputFile.exists()) {
            throw new IOException("Source input file not found: " + DEFAULT_SOURCE_JSON_INPUT_FILE);
        }
        if (inputFile.getParentFile() == null || !inputFile.getParentFile().exists() || !inputFile.getParentFile().isDirectory()) {
             throw new IOException("Data directory not found or invalid for: " + DEFAULT_SOURCE_JSON_INPUT_FILE);
        }

        String content = new String(Files.readAllBytes(Paths.get(DEFAULT_SOURCE_JSON_INPUT_FILE)));
        JSONArray sourceJsonArray = new JSONArray(content);

        JSONArray tempDataArray = new JSONArray();
        JSONArray humidDataArray = new JSONArray();

        System.out.println("Preprocessing " + DEFAULT_SOURCE_JSON_INPUT_FILE + "...");

        for (int i = 0; i < sourceJsonArray.length(); i++) {
            try {
                JSONObject entry = sourceJsonArray.getJSONObject(i);
                String timeStr = entry.getString("timestamp");
                JSONObject dataObj = entry.getJSONObject("data");

                if (dataObj.has(FIELD_TEMPERATURE)) {
                    float tempValue = ((Number) dataObj.get(FIELD_TEMPERATURE)).floatValue();
                    JSONObject tempRecord = new JSONObject();
                    tempRecord.put("timestamp", timeStr);
                    tempRecord.put("value", tempValue);
                    tempDataArray.put(tempRecord);
                }

                if (dataObj.has(FIELD_HUMIDITY)) {
                    float humidValue = ((Number) dataObj.get(FIELD_HUMIDITY)).floatValue();
                    JSONObject humidRecord = new JSONObject();
                    humidRecord.put("timestamp", timeStr);
                    humidRecord.put("value", humidValue);
                    humidDataArray.put(humidRecord);
                }
            } catch (JSONException | ClassCastException e) {
                System.err.println("Skipping record during preprocessing due to error: " + e.getMessage());
            }
        }

        try (PrintWriter outTemp = new PrintWriter(new FileWriter(TEMPERATURE_ORIGINAL_JSON))) {
            outTemp.print(tempDataArray.toString(2)); // Pretty print
        }
        System.out.println("Created " + TEMPERATURE_ORIGINAL_JSON + " with " + tempDataArray.length() + " records.");

        try (PrintWriter outHumid = new PrintWriter(new FileWriter(HUMIDITY_ORIGINAL_JSON))) {
            outHumid.print(humidDataArray.toString(2)); // Pretty print
        }
        System.out.println("Created " + HUMIDITY_ORIGINAL_JSON + " with " + humidDataArray.length() + " records.");
    }


    /**
     * Loads a list of Univariate objects from a "simple" JSON file
     * (array of {"timestamp": long, "value": float}).
     * Ensures the list is sorted by time.
     */
    public static List<Univariate> loadUnivariateListFromSimpleJson(String simpleJsonPath) throws IOException, JSONException {
        List<Univariate> dataPoints = new ArrayList<>();
        File inputFile = new File(simpleJsonPath);
        if (!inputFile.exists()) {
            throw new IOException("Simple JSON input file not found: " + simpleJsonPath);
        }

        String content = new String(Files.readAllBytes(Paths.get(simpleJsonPath)));
        JSONArray jsonArray = new JSONArray(content);

        for (int i = 0; i < jsonArray.length(); i++) {
            try {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String timeStr = jsonObject.get("timestamp").toString();
                float value = ((Number) jsonObject.get("value")).floatValue();
                dataPoints.add(new Univariate(timeStr, value));
            } catch (JSONException | ClassCastException e) {
                System.err.println("Skipping malformed record in " + simpleJsonPath + " at index " + i + ": " + e.getMessage());
            }
        }
        return dataPoints;
    }


    // --- Metric calculation methods (calculateMSE, RMSE, MAE, MaxDiff, MinDiff) remain the same ---
    // --- Their input will come from loadUnivariateListFromSimpleJson ---
    static class MaxDifferenceDetail {
        double maxDifference; int index = -1; float originalValue; float approximatedValue;
        public MaxDifferenceDetail(double m, int i, float o, float a) {maxDifference=m; index=i; originalValue=o; approximatedValue=a;}
        @Override public String toString() {
            if (index == -1) return String.format(Locale.US, "N/A");
            return String.format(Locale.US, "%.6f at index %d (Original: %.6f, Approx: %.6f)", maxDifference, index, originalValue, approximatedValue);
        }
    }
    private static List<Float> extractValues(List<Univariate> dp) { List<Float> v=new ArrayList<>(dp.size()); dp.forEach(p->v.add(p.getValue())); return v;}
    private static double calculateMSE(List<Float> o, List<Float> a) { if(o.isEmpty())return 0; double s=0; for(int i=0;i<o.size();i++){double d=o.get(i)-a.get(i);s+=d*d;} return s/o.size();}
    private static double calculateRMSE(List<Float> o, List<Float> a) { return Math.sqrt(calculateMSE(o,a));}
    private static double calculateMAE(List<Float> o, List<Float> a) { if(o.isEmpty())return 0; double s=0; for(int i=0;i<o.size();i++)s+=Math.abs(o.get(i)-a.get(i)); return s/o.size();}
    private static Main.MaxDifferenceDetail calculateMaxDiff(List<Float> o,List<Float> a){if(o.isEmpty())return new Main.MaxDifferenceDetail(0,-1,0,0);double m=-1;int mi=-1;float ov=0,av=0;for(int i=0;i<o.size();i++){double d=Math.abs(o.get(i)-a.get(i));if(d>m){m=d;mi=i;ov=o.get(i);av=a.get(i);}}if(mi==-1&&!o.isEmpty())return new Main.MaxDifferenceDetail(0,0,o.get(0),a.get(0));return new Main.MaxDifferenceDetail(m,mi,ov,av);}
    private static double calculateMinDiff(List<Float> o, List<Float> a){if(o.isEmpty())return 0;double m=Double.POSITIVE_INFINITY;for(int i=0;i<o.size();i++){double d=Math.abs(o.get(i)-a.get(i));if(d<m)m=d;}return m==Double.POSITIVE_INFINITY?0:m;}
    
    /**
     * Calculates compression ratio between a specific field's original simple JSON
     * and its corresponding compressed JSON.
     */
    private static double calculateFieldCompressionRatio(String originalSimpleJsonPath, String compressedJsonPath) {
        File originalFile = new File(originalSimpleJsonPath);
        File compressedFile = new File(compressedJsonPath);

        if (!originalFile.exists() || !compressedFile.exists()) {
            System.err.println("Error: Original simple JSON ("+originalSimpleJsonPath+") or compressed JSON ("+compressedJsonPath+") not found for ratio.");
            return 0.0;
        }
        long originalSize = originalFile.length();
        long compressedSize = compressedFile.length();
         System.out.println("Size of " + originalSimpleJsonPath + ": " + originalSize + " bytes");
         System.out.println("Size of " + compressedJsonPath + ": " + compressedSize + " bytes");
        if (compressedSize == 0) {
            System.err.println("Warning: Compressed file size for " + compressedJsonPath + " is 0.");
            return 0.0; // Or Double.POSITIVE_INFINITY if originalSize > 0
        }
        return (double) originalSize / compressedSize;
    }


    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java -cp <classpath> compressor.Main compress <wSize> <nWindow> <errorBound>");
        System.err.println("     (Source: " + DEFAULT_SOURCE_JSON_INPUT_FILE + ")");
        System.err.println("     (Outputs: " + TEMPERATURE_ORIGINAL_JSON + ", " + HUMIDITY_ORIGINAL_JSON + ")");
        System.err.println("     (         " + TEMPERATURE_COMPRESSED_JSON + ", " + HUMIDITY_COMPRESSED_JSON + ")");
        System.err.println("  java -cp <classpath> compressor.Main decompress <field> <interval>");
        System.err.println("     <field>: '" + FIELD_TEMPERATURE + "' or '" + FIELD_HUMIDITY + "'");
        System.err.println("     (Inputs: " + FIELD_TEMPERATURE + "CompressedData.json or " + FIELD_HUMIDITY + "CompressedData.json)");
        System.err.println("     (Outputs: " + FIELD_TEMPERATURE + "DecompressedData.json or " + FIELD_HUMIDITY + "DecompressedData.json)");
        System.err.println("  java -cp <classpath> compressor.Main metrics <field_evaluated>");
        System.err.println("     <field_evaluated>: '" + FIELD_TEMPERATURE + "' or '" + FIELD_HUMIDITY + "'");
        System.err.println("     (Compares fieldOriginalData.json with fieldDecompressedData.json)");
        System.err.println("     (Ratio compares fieldOriginalData.json with fieldCompressedData.json)");
        System.err.println("\n<classpath> should include the org.json library (e.g., .;json.jar or .:json.jar)");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) { System.err.println("Error: No action specified."); printUsage(); return; }
        String action = args[0].toLowerCase();

        try {
            switch (action) {
                case "preprocess": // New standalone action for clarity if needed, or part of compress
                     System.out.println("Preprocessing source JSON file...");
                     preprocessAndSplitOriginalJson();
                     System.out.println("Preprocessing complete.");
                     break;

                case "compress":
                    if (args.length < 4) { System.err.println("Error: Insufficient arguments for 'compress'. Usage: compress <wSize> <nWindow> <errorBound>"); printUsage(); return; }
                    int wSize = Integer.parseInt(args[1]);
                    int nWindow = Integer.parseInt(args[2]);
                    float errorBound = Float.parseFloat(args[3]);
                    if (wSize <= 0 || nWindow <= 0 || errorBound < 0) { System.err.println("Error: wSize/nWindow positive, errorBound non-negative."); return; }

                    System.out.println("Step 1: Preprocessing source JSON file...");
                    preprocessAndSplitOriginalJson();
                    System.out.println("Preprocessing complete.");

                    System.out.println("\nStep 2: Compressing Temperature Data...");
                    HybridPCACompressor.compressSourceJsonToJsonOutput(TEMPERATURE_ORIGINAL_JSON, TEMPERATURE_COMPRESSED_JSON, wSize, nWindow, errorBound);
                    
                    System.out.println("\nStep 3: Compressing Humidity Data...");
                    HybridPCACompressor.compressSourceJsonToJsonOutput(HUMIDITY_ORIGINAL_JSON, HUMIDITY_COMPRESSED_JSON, wSize, nWindow, errorBound);
                    System.out.println("\nCompression process finished for all fields.");
                    break;

                case "decompress":
                    if (args.length < 2) { System.err.println("Error: Insufficient arguments for 'decompress'. Usage: decompress <field>"); printUsage(); return; }
                    String fieldToDecompress = args[1].toLowerCase();

                    String inputCompressedJson, outputDecompressedJson;
                    if (FIELD_TEMPERATURE.equals(fieldToDecompress)) {
                        inputCompressedJson = TEMPERATURE_COMPRESSED_JSON;
                        outputDecompressedJson = TEMPERATURE_DECOMPRESSED_JSON;
                    } else if (FIELD_HUMIDITY.equals(fieldToDecompress)) {
                        inputCompressedJson = HUMIDITY_COMPRESSED_JSON;
                        outputDecompressedJson = HUMIDITY_DECOMPRESSED_JSON;
                    } else {
                        System.err.println("Error: Invalid field for decompression. Use '" + FIELD_TEMPERATURE + "' or '" + FIELD_HUMIDITY + "'.");
                        printUsage(); return;
                    }
                    System.out.println("\nDecompressing " + fieldToDecompress + " data from " + inputCompressedJson + "...");
                    HybridPCACompressor.decompressJsonInputToJsonOutput(inputCompressedJson, outputDecompressedJson);
                    System.out.println("Decompression finished. Output: " + outputDecompressedJson);
                    break;

                case "metrics":
                    if (args.length < 2) { System.err.println("Error: Insufficient arguments for 'metrics'. Usage: metrics <field_to_evaluate>"); printUsage(); return; }
                    String fieldToEvaluate = args[1].toLowerCase();
                    
                    String originalFieldJson, decompressedFieldJson, compressedFieldJson;

                    if (FIELD_TEMPERATURE.equals(fieldToEvaluate)) {
                        originalFieldJson = TEMPERATURE_ORIGINAL_JSON;
                        decompressedFieldJson = TEMPERATURE_DECOMPRESSED_JSON;
                        compressedFieldJson = TEMPERATURE_COMPRESSED_JSON;
                    } else if (FIELD_HUMIDITY.equals(fieldToEvaluate)) {
                        originalFieldJson = HUMIDITY_ORIGINAL_JSON;
                        decompressedFieldJson = HUMIDITY_DECOMPRESSED_JSON;
                        compressedFieldJson = HUMIDITY_COMPRESSED_JSON;
                    } else {
                        System.err.println("Error: Invalid field_to_evaluate for metrics. Use '" + FIELD_TEMPERATURE + "' or '" + FIELD_HUMIDITY + "'.");
                        printUsage(); return;
                    }

                    System.out.println("\nCalculating metrics for field: " + fieldToEvaluate);
                    System.out.println("Original data file: " + originalFieldJson);
                    System.out.println("Decompressed data file: " + decompressedFieldJson);
                    System.out.println("Compressed data file: " + compressedFieldJson);

                    List<Univariate> originalPoints = loadUnivariateListFromSimpleJson(originalFieldJson);
                    List<Univariate> approximatedPoints = loadUnivariateListFromSimpleJson(decompressedFieldJson); // Load from simple JSON

                    List<Float> originalValues = extractValues(originalPoints);
                    List<Float> approximatedValues = extractValues(approximatedPoints);

                    int countMin = Math.min(originalValues.size(), approximatedValues.size());
                    if (countMin == 0) {
                        System.err.println("Error: No data points found in original or approximated files for metrics calculation for field '"+fieldToEvaluate+"'.");
                        return;
                    }
                    if (originalValues.size() != approximatedValues.size()) {
                        System.err.printf("Warning: Original data (%d points for field '%s') and approximated data (%d points) have different lengths. Using first %d points for metrics.%n",
                                          originalValues.size(), fieldToEvaluate, approximatedValues.size(), countMin);
                    }

                    List<Float> oTrim=originalValues.subList(0,countMin); List<Float> aTrim=approximatedValues.subList(0,countMin);
                    
                    System.out.printf(Locale.US,"Compress Ratio (%s vs %s): %.6f%n", originalFieldJson, compressedFieldJson, calculateFieldCompressionRatio(originalFieldJson, compressedFieldJson));
                    System.out.printf(Locale.US,"MSE: %.6f%n",calculateMSE(oTrim,aTrim)); 
                    System.out.printf(Locale.US,"RMSE: %.6f%n",calculateRMSE(oTrim,aTrim));
                    System.out.printf(Locale.US,"MAE: %.6f%n",calculateMAE(oTrim,aTrim)); 
                    System.out.println("Max_E: "+calculateMaxDiff(oTrim,aTrim));
                    System.out.printf(Locale.US,"Min_E: %.6f%n",calculateMinDiff(oTrim,aTrim));
                    break;

                default: System.err.println("Error: Unknown action '" + action + "'."); printUsage(); break;
            }
        } catch (IOException | JSONException | NumberFormatException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}