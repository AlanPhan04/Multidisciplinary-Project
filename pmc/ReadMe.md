# Piecewise Mean Compression (PMC)

This project implements a **Piecewise Mean Compression (PMC)** algorithm for time series data, focusing on reducing storage while preserving accuracy. It includes:

- Midrange-based compression logic
- JSON output formatting
- Decompression for reconstruction
- Evaluation metrics: Compression Ratio, MSE, RMSE, MAE, Max/Min Error

---

## Concept

**PMC** compresses univariate time series by segmenting the data and approximating each segment with a representative value (e.g., midrange). Segments are emitted when a defined **threshold** is exceeded (adaptive segmentation).

---

## Project Structure

```
├── pmc.py # Compression engine (PMC, PMCElement classes)
├── eval.py # Evaluation metrics (compression ratio, errors)
├── sensor_data.json # Example input data (timestamp, value)
├── pmc.py # Compression engine (PMC, PMCElement classes)
├── process_data/ # Processed data outputs
│   ├── compress/ # Output of compressed data
│   └──decompress/ # Output of decompressed data
├── kafka_producer.py # Sends data to Kafka
├── dataLoader.py # Loads input data
├── clean.py # Utility to delete intermediate files
├── train.py # Trains model on small dataset for hyperparameter tuning
├── run.py # Runs compression algorithm with tuned hyperparameters
├── extract.py # Extracts a sample of the main dataset
├── requirements.txt # List of required Python packages
└── README.md # Project documentation
```

---

## Usage

python run.py

python kafka_producer.py

## Threshold Selection Strategy

To determine the most appropriate threshold (error bound) for compression, we use an empirical grid search approach:

Define a range of candidate threshold values from 0.001 to 0.9.

Compress and decompress the time series data sample (20% of the real dataset) using each threshold.

For a predefined accuracy, find threshold with the best compression ratio for each candidate.

This process helps tailor the compression behavior to the statistical properties of the dataset and the desired trade-off between fidelity and storage.
