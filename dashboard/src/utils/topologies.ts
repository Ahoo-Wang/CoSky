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
    
    // Calculate node levels using topological sort approach
    const nodeLevels = new Map<string, number>();
    const inDegree = new Map<string, number>();
    
    // Initialize in-degree for all nodes
    allNodes.forEach(nodeName => {
        inDegree.set(nodeName, incomingEdges.get(nodeName)?.size || 0);
        nodeLevels.set(nodeName, 0);
    });
    
    // Find root nodes (nodes with no incoming edges)
    const queue: string[] = [];
    allNodes.forEach(nodeName => {
        if (inDegree.get(nodeName) === 0) {
            queue.push(nodeName);
        }
    });
    
    // Process nodes level by level
    while (queue.length > 0) {
        const node = queue.shift()!;
        const currentLevel = nodeLevels.get(node)!;
        
        const targets = outgoingEdges.get(node);
        if (targets) {
            targets.forEach(target => {
                // Update target level to be at least one level below current node
                const targetLevel = nodeLevels.get(target)!;
                nodeLevels.set(target, Math.max(targetLevel, currentLevel + 1));
                
                // Decrease in-degree and add to queue if all dependencies processed
                const newInDegree = inDegree.get(target)! - 1;
                inDegree.set(target, newInDegree);
                
                if (newInDegree === 0) {
                    queue.push(target);
                }
            });
        }
    }
    
    // Handle nodes in circular dependencies by placing them at the next available level
    const levelValues = Array.from(nodeLevels.values());
    const maxLevel = levelValues.length > 0 ? Math.max(...levelValues) : 0;
    allNodes.forEach(nodeName => {
        if (inDegree.get(nodeName)! > 0) {
            // Node is part of a cycle, place it at a level after the max
            nodeLevels.set(nodeName, maxLevel + 1);
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
        
        nodesInLevel.forEach((nodeName, index) => {
            nodes.push({
                id: nodeName,
                type: 'default',
                data: { 
                    label: nodeName,
                },
                position: {
                    x: START_X + index * HORIZONTAL_SPACING,
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
