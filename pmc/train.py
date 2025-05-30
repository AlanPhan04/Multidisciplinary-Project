from dataLoader import getDataSource 
from clean import deleteFolder
from pmc import pmc, pmcElement
import logging
import os
from tqdm import tqdm
from pathlib import Path

LOG_DIR = "logs"
DATA_DIR = "processed_data"

deleteFolder(True, folder_path=Path(LOG_DIR))
deleteFolder(True, folder_path=Path(DATA_DIR))

os.makedirs(LOG_DIR, exist_ok=True)

# Load data
data = getDataSource('sensor_data_train.json')
fieldNames = list(data[0]['data'].keys())

# Create a logger for each field
loggers = {}
for key in fieldNames:
    logger = logging.getLogger(key)
    logger.setLevel(logging.INFO)
    handler = logging.FileHandler(os.path.join(LOG_DIR, f"{key}_results.log"))
    handler.setFormatter(logging.Formatter("%(message)s"))
    logger.addHandler(handler)
    loggers[key] = logger

bestAccuracy = {key: [10,10,10] for key in fieldNames}
accuracyThreshold = {'temperature': 0.46, 'humidity': 0.1}

# Try different thresholds
for i in tqdm(range(1, 900), desc="Optimizing Thresholds"):
    threshold = i * 0.001

    params = {}
    for key in fieldNames:
        params[key] = [threshold]

    pmcInstance = pmc(**params)

    for dataPoint in data:
        pmcInstance.getNewData(dataPoint['data'], dataPoint['timestamp'], False)
    
    # Save the last part of the data
    for element in pmcInstance.elements.values():
        element.pmcMean(0, 0, True)  
    
    pmcInstance.decompress()

    for key, element in pmcInstance.elements.items():
        eval = element.evaluate()
        compressionRatio, mse = eval['compression_ratio'], eval['mse']
        if mse < accuracyThreshold[key] and compressionRatio < bestAccuracy[key][1]:
            bestAccuracy[key] = [threshold, compressionRatio, mse]
            loggers[key].info(
                f"Threshold: {threshold}, Compression Ratio: {compressionRatio}, MSE: {mse}"
            )

    deleteFolder(False)

# Print best accuracy summary
for key in fieldNames:
    print(f"[{key}] Best accuracy found: Threshold: {bestAccuracy[key][0]}, Compression Ratio: {bestAccuracy[key][1]}, MSE: {bestAccuracy[key][2]}")
    

