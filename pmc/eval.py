import os
import json
import math

def compressionRatio(original_file, compressed_file):
    original_size = os.path.getsize(original_file)
    compressed_size = os.path.getsize(compressed_file)
    compression_ratio = compressed_size / original_size if original_size else 0
    return compression_ratio

def mse(original_file, decompressed_file):
    with open(original_file, 'r') as f_orig, open(decompressed_file, 'r') as f_decomp:
        orig_lines = f_orig.readlines()
        decomp_lines = f_decomp.readlines()

    total_diff = 0
    count = 0
    for orig, decomp in zip(orig_lines, decomp_lines):
        orig_json = json.loads(orig)
        decomp_json = json.loads(decomp)

        orig_value = float(orig_json["value"])
        decomp_value = float(decomp_json["value"])

        total_diff += (orig_value - decomp_value) ** 2
        count += 1

    return total_diff / count if count else 0

def rmse(original_file, decompressed_file):
    return math.sqrt(mse(original_file, decompressed_file))

def mae(original_file, decompressed_file):
    with open(original_file, 'r') as f_orig, open(decompressed_file, 'r') as f_decomp:
        orig_lines = f_orig.readlines()
        decomp_lines = f_decomp.readlines()

    total_abs_diff = 0
    count = 0
    for orig, decomp in zip(orig_lines, decomp_lines):
        orig_json = json.loads(orig)
        decomp_json = json.loads(decomp)

        orig_value = float(orig_json["value"])
        decomp_value = float(decomp_json["value"])

        total_abs_diff += abs(orig_value - decomp_value)
        count += 1

    return total_abs_diff / count if count else 0

def max_error(original_file, decompressed_file):
    max_err = float('-inf')
    with open(original_file, 'r') as f_orig, open(decompressed_file, 'r') as f_decomp:
        for orig, decomp in zip(f_orig, f_decomp):
            orig_value = float(json.loads(orig)["value"])
            decomp_value = float(json.loads(decomp)["value"])
            err = abs(orig_value - decomp_value)
            max_err = max(max_err, err)
    return max_err if max_err != float('-inf') else 0

def min_error(original_file, decompressed_file):
    min_err = float('inf')
    with open(original_file, 'r') as f_orig, open(decompressed_file, 'r') as f_decomp:
        for orig, decomp in zip(f_orig, f_decomp):
            orig_value = float(json.loads(orig)["value"])
            decomp_value = float(json.loads(decomp)["value"])
            err = abs(orig_value - decomp_value)
            min_err = min(min_err, err)
    return min_err if min_err != float('inf') else 0
