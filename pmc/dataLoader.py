import json 

def getDataSource(fileName):
    with open(fileName, 'r') as file:
        data = json.load(file)
    return data

