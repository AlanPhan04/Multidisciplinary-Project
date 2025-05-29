package compressor;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class MainKafka {

    // Kafka specific constants
    private static final String DEFAULT_BOOTSTRAP_SERVERS = "192.168.182.128:9092"; // Make configurable
    private static final String DEFAULT_KAFKA_GROUP_ID_PREFIX = "hybrid-pca-live-cg-";
    private static final long KAFKA_POLL_TIMEOUT_MS = 1000;

    // File constants from Main.java (for output structure)
    private static final String TEMPERATURE_ORIGINAL_JSON = "temperatureOriginalData.json";
    private static final String HUMIDITY_ORIGINAL_JSON = "humidityOriginalData.json";
    private static final String TEMPERATURE_COMPRESSED_JSON = "temperatureCompressedData.json";
    private static final String HUMIDITY_COMPRESSED_JSON = "humidityCompressedData.json";
    private static final String TEMPERATURE_DECOMPRESSED_JSON = "temperatureDecompressedData.json";
    private static final String HUMIDITY_DECOMPRESSED_JSON = "humidityDecompressedData.json";

    // Constants for fields
    private static final String FIELD_TEMPERATURE = "temperature";
    private static final String FIELD_HUMIDITY = "humidity";
    // For metrics, the original data for comparison will now be the continuously updated OriginalData.json files
    // The size comparison for compression ratio will also use these.


    private static long parseTimestampToMillis(String timeStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
            LocalTime localTime = LocalTime.parse(timeStr, formatter);
            return localTime.toNanoOfDay() / 1_000_000L;
        } catch (Exception e) {
            System.err.println("Error parsing timestamp: \"" + timeStr + "\". Defaulting to 0 for this record. Error: " + e.getMessage());
            return 0L; // Or handle more gracefully, e.g., by skipping the record
        }
    }
    
    /**
     * Reads an existing simple JSON array file (array of {"timestamp": long, "value": float})
     * or returns an empty JSONArray if the file doesn't exist or is invalid.
     */
    private static JSONArray readOrCreateJsonArray(String filePath) throws IOException {
        File file = new File(filePath);
        if (file.exists() && file.length() > 0) {
            try {
                String content = new String(Files.readAllBytes(Paths.get(filePath)));
                return new JSONArray(content);
            } catch (JSONException e) {
                System.err.println("Warning: File " + filePath + " is not a valid JSON array. Starting fresh. Error: " + e.getMessage());
                return new JSONArray(); // Return empty if malformed
            } catch (NoSuchFileException e) {
                 System.out.println("Info: File " + filePath + " not found. Will be created.");
                return new JSONArray();
            }
        }
        return new JSONArray();
    }

    /**
     * Appends new data to a JSON file (which is an array of {"timestamp": long, "value": float}).
     * This version reads the whole file, adds to the array, and writes back.
     * Not efficient for very large files or very high frequency, but simpler.
     */
    private static void appendDataToJsonFile(String filePath, List<JSONObject> newDataPoints) throws IOException, JSONException {
        JSONArray existingData = readOrCreateJsonArray(filePath);
        for (JSONObject point : newDataPoints) {
            existingData.put(point);
        }
        try (PrintWriter out = new PrintWriter(new FileWriter(filePath))) {
            out.print(existingData.toString(2)); // Pretty print
        }
    }


    // --- Metric calculation methods (can be copied from your Main.java) ---
    // For brevity, I'm omitting the full metric methods here, but they would be:
    // static class MaxDifferenceDetail { ... }
    // private static List<Univariate> loadUnivariateListFromSimpleJson(String simpleJsonPath) ...
    // private static List<Float> extractValues(List<Univariate> dp) ...
    // private static double calculateMSE(List<Float> o, List<Float> a) ...
    // etc.
    // The calculateCompressionRatio would compare e.g. temperatureOriginalData.json vs temperatureCompressedData.json


    private static void printUsage() {
        System.err.println("Usage (MainKafka.java):");
        System.err.println("  java -cp <classpath> compressor.MainKafka consume <kafkaTopic> <bootstrapServers> <wSize> <nWindow> <errorBound> [kafkaGroupIdSuffix]");
        System.err.println("     Continuously consumes from <kafkaTopic> and triggers compression on new data.");
        System.err.println("     [kafkaGroupIdSuffix]: Optional. Appended to '" + DEFAULT_KAFKA_GROUP_ID_PREFIX + "'.");
        System.err.println("  --- The following actions operate on files generated by the 'consume' action ---");
        System.err.println("  java -cp <classpath> compressor.MainKafka decompress <field> <interval>");
        System.err.println("     <field>: '" + FIELD_TEMPERATURE + "' or '" + FIELD_HUMIDITY + "'");
        System.err.println("  java -cp <classpath> compressor.MainKafka metrics <field_evaluated>");
        System.err.println("     <field_evaluated>: '" + FIELD_TEMPERATURE + "' or '" + FIELD_HUMIDITY + "'");
        System.err.println("\n<classpath> should include org.json and kafka-clients libraries.");
    }

    public static void main(String[] args) {
        if (args.length < 1) { System.err.println("Error: No action specified."); printUsage(); return; }
        String action = args[0].toLowerCase();

        if ("consume".equals(action)) {
            if (args.length < 7) {
                System.err.println("Error: Insufficient arguments for 'consume' action.");
                System.err.println("Usage: consume <kafkaTopic> <bootstrapServers> <wSize> <nWindow> <errorBound> [kafkaGroupIdSuffix]");
                printUsage(); return;
            }
            String kafkaTopic = args[1];
            String bootstrapServers = args[2];
            int wSize = Integer.parseInt(args[3]);
            int nWindow = Integer.parseInt(args[4]);
            float errorBound = Float.parseFloat(args[5]);
            String kafkaGroupIdSuffix = (args.length > 6) ? args[6] : "default";
            String groupId = DEFAULT_KAFKA_GROUP_ID_PREFIX + kafkaGroupIdSuffix;

            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // Process new messages
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true"); // Or manage commits manually
            props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");


            final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
            consumer.subscribe(Collections.singletonList(kafkaTopic));
            System.out.println("Kafka Consumer started for topic '" + kafkaTopic + "' with group ID '" + groupId + "'. Waiting for messages...");
            System.out.println("Press Ctrl+C to stop.");

            // Add shutdown hook to close consumer gracefully
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutdown hook called. Waking up consumer and closing...");
                consumer.wakeup();
            }));

            try {
                while (true) { // Continuous loop
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(KAFKA_POLL_TIMEOUT_MS));
                    if (records.isEmpty()) {
                        continue; // Go back to polling
                    }

                    System.out.println("Received " + records.count() + " new messages from Kafka.");
                    List<JSONObject> newTempData = new ArrayList<>();
                    List<JSONObject> newHumidData = new ArrayList<>();

                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            String jsonPayload = record.value();
                            JSONObject jsonObject = new JSONObject(jsonPayload);
                            String timeStr = jsonObject.getString("timestamp");
                            // We need the long timestamp for the Univariate objects later for HybridPCACompressor
                            // but the intermediate JSON files (temperatureOriginalData.json) store the string
                            // as per your Main.java's preprocessAndSplitOriginalJson.
                            // Let's align with that. If needed, can convert to long here.
                            // For simplicity of appending, we will store the string timestamp.
                            // The `loadUnivariateListFromSimpleJson` in HybridPCACompressor will need to parse it.
                            // OR, better, `preprocessAndSplitOriginalJson` should store long. Let's assume it does.

                            long timeMillis = parseTimestampToMillis(timeStr); // Convert to long for internal use

                            JSONObject dataObject = jsonObject.getJSONObject("data");

                            if (dataObject.has(FIELD_TEMPERATURE)) {
                                float tempValue = ((Number) dataObject.get(FIELD_TEMPERATURE)).floatValue();
                                JSONObject tempRecord = new JSONObject();
                                tempRecord.put("timestamp", timeMillis); // Store as long
                                tempRecord.put("value", tempValue);
                                newTempData.add(tempRecord);
                            }

                            if (dataObject.has(FIELD_HUMIDITY)) {
                                float humidValue = ((Number) dataObject.get(FIELD_HUMIDITY)).floatValue();
                                JSONObject humidRecord = new JSONObject();
                                humidRecord.put("timestamp", timeMillis); // Store as long
                                humidRecord.put("value", humidValue);
                                newHumidData.add(humidRecord);
                            }
                        } catch (JSONException | ClassCastException e) {
                            System.err.println("Skipping malformed Kafka message (offset " + record.offset() + "): " + record.value() + ". Error: " + e.getMessage());
                        }
                    }

                    boolean dataAppended = false;
                    if (!newTempData.isEmpty()) {
                        appendDataToJsonFile(TEMPERATURE_ORIGINAL_JSON, newTempData);
                        System.out.println("Appended " + newTempData.size() + " records to " + TEMPERATURE_ORIGINAL_JSON);
                        dataAppended = true;
                    }
                    if (!newHumidData.isEmpty()) {
                        appendDataToJsonFile(HUMIDITY_ORIGINAL_JSON, newHumidData);
                        System.out.println("Appended " + newHumidData.size() + " records to " + HUMIDITY_ORIGINAL_JSON);
                        dataAppended = true;
                    }

                    if (dataAppended) {
                        System.out.println("Triggering compression for updated original data files...");
                        if (!newTempData.isEmpty()) {
                             System.out.println("\nCompressing Temperature Data...");
                             HybridPCACompressor.compressSourceJsonToJsonOutput(TEMPERATURE_ORIGINAL_JSON, TEMPERATURE_COMPRESSED_JSON, wSize, nWindow, errorBound);
                        }
                         if (!newHumidData.isEmpty()) {
                             System.out.println("\nCompressing Humidity Data...");
                             HybridPCACompressor.compressSourceJsonToJsonOutput(HUMIDITY_ORIGINAL_JSON, HUMIDITY_COMPRESSED_JSON, wSize, nWindow, errorBound);
                         }
                        System.out.println("Compression cycle complete after receiving new messages.");
                    }
                     // Manually commit offsets if enable.auto.commit is false
                    // consumer.commitAsync(); 
                }
            } catch (WakeupException e) {
                // Ignore wakeup exception, it's for shutting down
                System.out.println("Kafka consumer woken up, shutting down.");
            } catch (Exception e) { // Catch other exceptions like IOException, JSONException from append/compress
                System.err.println("Error in Kafka consumption/processing loop: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("Closing Kafka consumer.");
                consumer.close();
            }

        } else if ("decompress".equals(action) || "metrics".equals(action)) {
            // Defer to a static method that encapsulates the logic from original Main.java
            // to keep this main method focused on the 'consume' action.
            // This requires copying or refactoring those actions into static methods
            // or simply instructing the user to use the original Main.java for these.
            // For this example, we'll just print a message.
            System.out.println("Action '" + action + "' should be run using the original Main.java class with file-based inputs/outputs.");
            System.out.println("This MainKafka.java is primarily for the continuous 'consume' and compress workflow.");
            printUsage(); // Show usage for all commands for reference
        } else {
            System.err.println("Error: Unknown action '" + action + "'.");
            printUsage();
        }
    }
}