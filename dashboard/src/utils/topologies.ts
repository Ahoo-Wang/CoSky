import { Node, Edge } from '@xyflow/react';

export interface ReactFlowTopology {
    nodes: Node[];
    edges: Edge[];
}

const HORIZONTAL_SPACING = 250;
const VERTICAL_SPACING = 150;
const START_X = 100;
const START_Y = 50;

export function toReactFlowTopology(topology: Record<string, string[]>): ReactFlowTopology {
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    
    // Collect all unique nodes
    const allNodes = new Set<string>();
    const incomingEdges = new Map<string, Set<string>>();
    const outgoingEdges = new Map<string, Set<string>>();
    
    // Build graph structure
    Object.keys(topology).forEach(nodeName => {
        allNodes.add(nodeName);
        if (!outgoingEdges.has(nodeName)) {
            outgoingEdges.set(nodeName, new Set());
        }
        
        topology[nodeName].forEach(targetName => {
            allNodes.add(targetName);
            outgoingEdges.get(nodeName)!.add(targetName);
            
            if (!incomingEdges.has(targetName)) {
                incomingEdges.set(targetName, new Set());
            }
            incomingEdges.get(targetName)!.add(nodeName);
            
            if (!outgoingEdges.has(targetName)) {
                outgoingEdges.set(targetName, new Set());
            }
        });
    });
    
    // Calculate node levels using BFS
    const nodeLevels = new Map<string, number>();
    const visited = new Set<string>();
    
    // Find root nodes (nodes with no incoming edges)
    const rootNodes: string[] = [];
    allNodes.forEach(nodeName => {
        if (!incomingEdges.has(nodeName) || incomingEdges.get(nodeName)!.size === 0) {
            rootNodes.push(nodeName);
        }
    });
    
    // BFS to assign levels
    const queue: Array<{ node: string; level: number }> = [];
    rootNodes.forEach(node => {
        queue.push({ node, level: 0 });
        nodeLevels.set(node, 0);
    });
    
    while (queue.length > 0) {
        const { node, level } = queue.shift()!;
        
        if (visited.has(node)) {
            continue;
        }
        visited.add(node);
        
        const targets = outgoingEdges.get(node);
        if (targets) {
            targets.forEach(target => {
                const currentLevel = nodeLevels.get(target);
                const newLevel = level + 1;
                
                // Assign the maximum level to handle multiple paths
                if (currentLevel === undefined || newLevel > currentLevel) {
                    nodeLevels.set(target, newLevel);
                }
                
                if (!visited.has(target)) {
                    queue.push({ node: target, level: newLevel });
                }
            });
        }
    }
    
    // Handle nodes that are not reachable from root nodes (circular dependencies)
    allNodes.forEach(nodeName => {
        if (!nodeLevels.has(nodeName)) {
            nodeLevels.set(nodeName, 0);
        }
    });
    
    // Group nodes by level
    const levelGroups = new Map<number, string[]>();
    nodeLevels.forEach((level, nodeName) => {
        if (!levelGroups.has(level)) {
            levelGroups.set(level, []);
        }
        levelGroups.get(level)!.push(nodeName);
    });
    
    // Calculate positions
    const sortedLevels = Array.from(levelGroups.keys()).sort((a, b) => a - b);
    
    sortedLevels.forEach(level => {
        const nodesInLevel = levelGroups.get(level)!;
        const startX = START_X;
        
        nodesInLevel.forEach((nodeName, index) => {
            nodes.push({
                id: nodeName,
                type: 'default',
                data: { 
                    label: nodeName,
                },
                position: {
                    x: startX + index * HORIZONTAL_SPACING,
                    y: START_Y + level * VERTICAL_SPACING,
                },
            });
        });
    });
    
    // Create edges
    Object.keys(topology).forEach(nodeName => {
        topology[nodeName].forEach(targetName => {
            edges.push({
                id: `${nodeName}-${targetName}`,
                source: nodeName,
                target: targetName,
                animated: true,
                type: 'smoothstep',
            });
        });
    });
    
    return { nodes, edges };
}
