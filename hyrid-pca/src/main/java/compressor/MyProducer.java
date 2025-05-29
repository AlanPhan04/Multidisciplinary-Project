package compressor; // Or com.iot if that's your actual package for MyProducer

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class MyProducer {

    private static final String BOOTSTRAP_SERVERS = "192.168.182.128:9092";
    // TOPIC_NAME will now be dynamic, read from the JSON file's "topic" field.
    // private static final String TOPIC_NAME = "dht11"; // This line is removed/commented
    private static final String JSON_FILE_PATH = "Data/sensor_data.json";

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        // Using FQCN strings for serializers, similar to IotProducer
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer"); // Sending JSON as String

        // Optional: Add other producer configurations for tuning, like in IotProducer or for async best practices
        // props.put(ProducerConfig.ACKS_CONFIG, "all");
        // props.put(ProducerConfig.RETRIES_CONFIG, 3);
        // props.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        // props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB

        final AtomicInteger messagesSentSuccessfully = new AtomicInteger(0);
        final AtomicInteger messagesFailedToSend = new AtomicInteger(0);
        int totalMessagesAttempted;

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String content = new String(Files.readAllBytes(Paths.get(JSON_FILE_PATH)));
            JSONArray jsonArray = new JSONArray(content);
            totalMessagesAttempted = jsonArray.length();

            System.out.println("Starting to asynchronously produce " + totalMessagesAttempted + " messages from '" + JSON_FILE_PATH + "'...");

            for (int i = 0; i < totalMessagesAttempted; i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String messageValue = jsonObject.toString();

                String kafkaTopicForThisMessage;
                try {
                    kafkaTopicForThisMessage = jsonObject.getString("topic");
                    if (kafkaTopicForThisMessage == null || kafkaTopicForThisMessage.trim().isEmpty()) {
                        System.err.println("Skipping record at index " + i + ": 'topic' field is empty or null. Record: " + jsonObject.toString(2));
                        messagesFailedToSend.incrementAndGet(); // Count as failed if we can't determine topic
                        continue;
                    }
                } catch (JSONException e) {
                    System.err.println("Skipping record at index " + i + ": 'topic' field is missing or not a string. Record: " + jsonObject.toString(2));
                    messagesFailedToSend.incrementAndGet(); // Count as failed
                    continue;
                }

                kafkaTopicForThisMessage = "dht11";
                
                String messageKey = jsonObject.optString("timestamp", "key-" + i + "-" + System.nanoTime());
                ProducerRecord<String, String> record = new ProducerRecord<>(kafkaTopicForThisMessage, messageKey, messageValue);

                final int messageNumber = i + 1; // For logging within the callback
                final String currentTopic = kafkaTopicForThisMessage; // Capture for lambda

                producer.send(record, new Callback() {
                    @Override
                    public void onCompletion(RecordMetadata metadata, Exception exception) {
                        if (exception == null) {
                            messagesSentSuccessfully.incrementAndGet();
                            // System.out.println("Successfully sent message " + messageNumber + "/" + totalMessagesAttempted +
                            // " with key '" + messageKey + "' to topic=" + metadata.topic() +
                            // ", partition=" + metadata.partition() + ", offset=" + metadata.offset());
                        } else {
                            messagesFailedToSend.incrementAndGet();
                            System.err.println("Error sending message " + messageNumber + "/" + totalMessagesAttempted +
                                               " with key '" + messageKey + "' to topic '" + currentTopic + "': " + exception.getMessage());
                        }
                    }
                });
            }

            System.out.println("All " + totalMessagesAttempted + " messages have been submitted for sending.");
            System.out.println("Flushing producer to ensure all messages are attempted...");
            producer.flush(); // Crucial for asynchronous sends

        } catch (IOException e) {
            System.err.println("Error reading JSON file '" + JSON_FILE_PATH + "': " + e.getMessage());
            e.printStackTrace();
        } catch (JSONException e) {
            System.err.println("Error parsing top-level JSON content from file '" + JSON_FILE_PATH + "': " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("--------------------------------------------------");
            System.out.println("Production Summary:");
            //System.out.println("Total messages attempted: " + totalMessagesAttempted);
            System.out.println("Messages reported as sent successfully by callbacks: " + messagesSentSuccessfully.get());
            System.out.println("Messages reported as failed/skipped by callbacks or pre-send checks: " + messagesFailedToSend.get());
            System.out.println("--------------------------------------------------");
        }
    }
}