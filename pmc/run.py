import time
import json
from kafka import KafkaConsumer
from datetime import datetime
from clean import deleteFolder
from pmc import pmc, pmcElement
from pathlib import Path

LOG_DIR = "logs"
DATA_DIR = "processed_data"


deleteFolder(True, folder_path=Path(DATA_DIR))

# Kafka configuration
KAFKA_BROKER = 'node1:9092'
TOPIC = 'my-topic'
GROUP_ID = 'my-consumer-group'

consumer = KafkaConsumer(
    TOPIC,
    bootstrap_servers=KAFKA_BROKER,
    auto_offset_reset='earliest',
    enable_auto_commit=True,
    group_id=GROUP_ID,
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

print("Listening for messages...")

fieldNames = ['temperature', 'humidity']
threshold = {'temperature': 0.2, 'humidity': 0.31}

params = {key: [threshold[key]] for key in fieldNames}
pmcInstance = pmc(**params)

timeout_secs = 5
last_msg_time = time.time()

while True:
    # poll() returns a dict of topic-partition to messages
    records = consumer.poll(timeout_ms=1000)  # wait up to 1 second for messages

    if records:
        for tp, messages in records.items():
            for message in messages:
                dt = datetime.fromtimestamp(message.timestamp / 1000.0)

                # Format to string
                formatted_time = dt.strftime('%H:%M:%S.%f')
                pmcInstance.getNewData(message.value, formatted_time, False)
                last_msg_time = time.time()  # reset the timer on each message
    else:
        if time.time() - last_msg_time >= timeout_secs:
            print(f"No messages received in the last {timeout_secs} seconds. Exiting.")
            break

# Post-processing
for element in pmcInstance.elements.values():
    element.pmcMean(0, 0, True)

try:
    pmcInstance.decompress()
except Exception as e:
    print(f"Decompression failed: {e}")
    exit(1)

for key, element in pmcInstance.elements.items():
    eval = element.evaluate()
    print(f"[{key}] Compression Ratio: {eval['compression_ratio']},\n MSE: {eval['mse']},\n RMSE: {eval['rmse']},\n MAE: {eval['mae']},\n Max Error: {eval['max_error']},\n Min Error: {eval['min_error']}")
