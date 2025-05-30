import json
import random

# Load the original JSON file
with open('sensor_data.json', 'r') as f:
    data = json.load(f)

# If the file is a list of items (e.g., list of records):
if isinstance(data, list):
    sample_size = int(len(data) * 0.2)
    sampled_data = data[:sample_size]  # Take the first 20% of the data

    # Write to new file
    with open('sensor_data_train.json', 'w') as f_out:
        json.dump(sampled_data, f_out, indent=2)
else:
    print("The JSON file is not a list. Please check its structure.")
