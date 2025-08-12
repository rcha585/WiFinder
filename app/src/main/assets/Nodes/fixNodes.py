import json
import os
"""
This script takes nodesManualInput.JSON and converts and modifies it for use in Navigation,
It formats the Nodes and Edges and adds weighting to the edges.
"""
def calculate_distance(node1, node2):
    p1 = {'x': node1['x'], 'y': node1['y']}
    p2 = {'x': node2['x'], 'y': node2['y']}
    """
    Calculate Euclidean distance between two points
    Each point should have 'x' and 'y' coordinates
    """
    return ((p1['x'] - p2['x'])**2 + (p1['y'] - p2['y'])**2)**0.5

# Get the directory of the current script
current_dir = os.path.dirname(os.path.abspath(__file__))
proj_root = os.path.join(current_dir, "..", "..")
dest_dir = os.path.join(proj_root, "res", "raw")

read_file_path = os.path.join(current_dir, 'nodesMI.json')

write_file_path = os.path.join(dest_dir, 'nodes.json')
data = {}
# Read and parse the JSON file
try:
    with open(read_file_path, 'r') as file:
        data = json.load(file)
        print("JSON data loaded successfully:")
except FileNotFoundError:
    print(f"Error: The file '{read_file_path}' was not found.")
except json.JSONDecodeError:
    print(f"Error: Failed to decode JSON from the file '{read_file_path}'.")

nodes = data.get('nodes')
## print(nodes)
newEdges = []

for edge in data['edges']:
    
    if 'from' in edge and 'to' in edge:
        from_node = edge['from']
        to_node = edge['to']
        
        # Check if 'from' and 'to' are not empty strings
        if from_node == "" or to_node == "":
            print(f"Edge with empty 'from' or 'to': {edge}")
            continue
        else:
            
            for node in nodes: 
                if node['id'] == from_node: 
                    fromNode = node
                    break
            for node in nodes: 
                if node['id'] == to_node: 
                    toNode = node
                    break
            
            edge['weight'] = calculate_distance(fromNode, toNode)
            reversedEdge = {
                'from': to_node,
                'to': from_node,
                'weight': edge['weight']
            }
            newEdges.append(edge)
            newEdges.append(reversedEdge)

for node in nodes:
    node['edges'] = []
    for edge in newEdges:
        if node['id'] == edge['from']:
            sendEdge = {
                'to': edge['to'],
                'weight': edge['weight']
            }
            node['edges'].append(sendEdge)

# Write the modified data back to a new JSON file
try:
    with open(write_file_path, 'w') as file:
        json.dump(nodes, file, indent=4)
    print(f"Modified JSON data written to '{write_file_path}' successfully.")
except IOError:
    print(f"Error: Failed to write to file '{write_file_path}'.")
except Exception as e:
    print(f"An unexpected error occurred: {e}")