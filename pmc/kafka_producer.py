import json
from kafka import KafkaProducer

# Kafka configuration
KAFKA_BROKER = 'node1:9092'  # Change if your Kafka is running elsewhere
TOPIC = 'my-topic'

# Initialize the Kafka producer
producer = KafkaProducer(
    bootstrap_servers=KAFKA_BROKER,
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# Load data from JSON file
with open('sensor_data.json', 'r') as file:
    data = json.load(file)

# Send each JSON object as a Kafka message
for record in data:
    producer.send(TOPIC, value=record['data'])
    print(f"Sent: {record['data']}")

# Flush and close the producer
producer.flush()
producer.close()
