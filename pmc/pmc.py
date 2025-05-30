import os
import json
from datetime import datetime, timedelta
from eval import compressionRatio, mse, rmse, mae, max_error, min_error

INTERVAL = timedelta(seconds=6)
DATA_DIR = 'processed_data'

class pmc:
    def __init__(self, **kwargs):
        self.elements = {}
        for key, item in kwargs.items():
            self.elements[key] = pmcElement(key, item[0])

    def getNewData(self, data, timeStamp, midRange=True):
        for key, value in data.items():
            if midRange:
                self.elements[key].pmcMidRange(timeStamp, value)
            else:
                self.elements[key].pmcMean(timeStamp, value)

    def decompress(self):
        for key, element in self.elements.items():
            element.decompress(INTERVAL)



class pmcElement:
    def __init__(self, fieldName, threshold):
        self.fieldName = fieldName
        self.max = float('-inf')
        self.min = float('inf')
        self.prevMax = self.max
        self.preMin = self.min
        # self.preValue = 0
        self.sum = 0
        self.count = 0
        self.threshold = threshold
        self.timeStamp = 0
        
        os.makedirs(os.path.join(DATA_DIR , 'original'), exist_ok=True)
        os.makedirs(os.path.join(DATA_DIR , 'compress'), exist_ok=True)
        os.makedirs(os.path.join(DATA_DIR , 'decompress'), exist_ok=True)
        
        self.originalFile = os.path.join(DATA_DIR, 'original', f'{fieldName}OriginalData.jsonl')
        self.compressedFile = os.path.join(DATA_DIR, 'compress', f'{fieldName}CompressedData.jsonl')
        self.decompressedFile = os.path.join(DATA_DIR, 'decompress', f'{fieldName}DecompressedData.jsonl')
        
    
    def saveToJson(self, data, file):
        with open(file, 'a') as f:
            f.write(json.dumps(data) + "\n")
    
    def pmcMidRange(self, timeStamp, value, final = False):
        self.saveToJson({"timestamp": timeStamp, "value": value}, self.originalFile)
        if self.count == 0:
            self.timeStamp = timeStamp
        if final:
            result = {'timestamp': self.timeStamp, 'length': self.count,'value': (self.max + self.min) / 2}
            self.saveToJson(result, self.compressedFile)
            return
            
        self.max = max(self.max, value)
        self.min = min(self.min, value)
        if (self.max - self.min) > 2 * self.threshold:
            result = {'timestamp': self.timeStamp, 'length': self.count,'value': (self.preMax + self.preMin) / 2}
            self.saveToJson(result, self.compressedFile)
            self.max = value
            self.min = value
            self.count = 0
            self.timeStamp = timeStamp
            
        self.preMax = self.max
        self.preMin = self.min
        self.count += 1

    def pmcMean(self, timeStamp, value, final=False):
        self.saveToJson({"timestamp": timeStamp, "value": value}, self.originalFile)

        if self.count == 0:
            self.timeStamp = timeStamp
            self.sum += value
            self.count = 1
            self.max = max(self.max, value)
            self.min = min(self.min, value)
            return

        # If it's the final call, emit the last segment
        if final:
            mean_value = self.sum / self.count
            result = {
                'timestamp': self.timeStamp,
                'length': self.count,
                'value': mean_value
            }
            self.saveToJson(result, self.compressedFile)
            return

        projected_sum = self.sum + value
        projected_count = self.count + 1
        projected_mean = projected_sum / projected_count

        # Condition check for error-bound
        if max(value, self.max) - projected_mean > self.threshold or \
           projected_mean - min(value, self.min) > self.threshold:
            # Emit previous segment
            mean_value = self.sum / self.count
            result = {
                'timestamp': self.timeStamp,
                'length': self.count,
                'value': mean_value
            }
            self.saveToJson(result, self.compressedFile)

            # Start new segment with current value
            self.timeStamp = timeStamp
            self.sum = value
            self.count = 1
            self.max = value
            self.min = value
        else:
            self.sum += value
            self.count += 1
            self.max = max(self.max, value)
            self.min = min(self.min, value)

        



    def decompress(self, interval = INTERVAL):

        with open(self.compressedFile, "r") as f_in, open(self.decompressedFile, "w") as f_out:
            for line in f_in:
                segment = json.loads(line)
            
                
                start_time = datetime.strptime(segment['timestamp'], "%H:%M:%S.%f")
                length = int(segment['length'])
                value = segment['value']
            
                for i in range(length):
                    timestamp = start_time + i * interval
                    result = {
                        "timestamp": datetime.strftime(timestamp, "%H:%M:%S.%f"),
                        "value": value
                    }
                    f_out.write(json.dumps(result) + "\n")
                    
    def evaluate(self):
        
        compression_ratio = compressionRatio(self.originalFile, self.compressedFile)
        mse_value = mse(self.originalFile, self.decompressedFile)
        rmse_value = rmse(self.originalFile, self.decompressedFile)
        mae_value = mae(self.originalFile, self.decompressedFile)
        max_error_value = max_error(self.originalFile, self.decompressedFile)
        min_error_value = min_error(self.originalFile, self.decompressedFile)
        
        return {'compression_ratio': compression_ratio,
                'mse': mse_value,
                'rmse': rmse_value,
                'mae': mae_value,
                'max_error': max_error_value,
                'min_error': min_error_value}

