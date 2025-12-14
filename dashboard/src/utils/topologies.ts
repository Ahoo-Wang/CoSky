import { Node, Edge } from '@xyflow/react';

export interface TopologyNode {
    name: string,
    symbolSize: number
}

export interface TopologyLink {
    source: string,
    target: string
}

export interface Topology {
    nodes: TopologyNode[],
    links: TopologyLink[]
}

export interface ReactFlowTopology {
    nodes: Node[];
    edges: Edge[];
}

export function toTopology(topology: Record<string, string[]>): Topology {
    const nodes: TopologyNode[] = [];
    const links: TopologyLink[] = [];

    Object.keys(topology).forEach(nodeName => {
        putIfAbsent(nodes, nodeName);
        topology[nodeName].forEach(targetName => {
            putIfAbsent(nodes, targetName);
            links.push({source: nodeName, target: targetName});
            const targetNode = getNodeName(nodes, targetName);
            if (targetNode) {
                targetNode.symbolSize = targetNode.symbolSize + 2;
            }
        })
    })

    return {
        nodes,
        links
    }

}

const LAYOUT_CENTER_X = 400;
const LAYOUT_CENTER_Y = 300;
const LAYOUT_RADIUS = 200;

export function toReactFlowTopology(topology: Record<string, string[]>): ReactFlowTopology {
    const nodeMap = new Map<string, number>();
    const nodes: Node[] = [];
    const edges: Edge[] = [];
    
    // First pass: collect all unique nodes and count incoming connections
    Object.keys(topology).forEach(nodeName => {
        if (!nodeMap.has(nodeName)) {
            nodeMap.set(nodeName, 0);
        }
        topology[nodeName].forEach(targetName => {
            nodeMap.set(targetName, (nodeMap.get(targetName) || 0) + 1);
        });
    });
    
    // Second pass: create ReactFlow nodes with positions
    let index = 0;
    nodeMap.forEach((_, nodeName) => {
        const angle = (index / nodeMap.size) * 2 * Math.PI;
        nodes.push({
            id: nodeName,
            type: 'default',
            data: { 
                label: nodeName,
            },
            position: {
                x: LAYOUT_CENTER_X + LAYOUT_RADIUS * Math.cos(angle),
                y: LAYOUT_CENTER_Y + LAYOUT_RADIUS * Math.sin(angle),
            },
        });
        index++;
    });
    
    // Third pass: create edges
    Object.keys(topology).forEach(nodeName => {
        topology[nodeName].forEach(targetName => {
            edges.push({
                id: `${nodeName}-${targetName}`,
                source: nodeName,
                target: targetName,
                animated: true,
            });
        });
    });
    
    return { nodes, edges };
}

function putIfAbsent(nodes: TopologyNode[], nodeName: string) {
    if (nodes.filter(_nodeName => {
        return _nodeName.name === nodeName;
    }).length === 0) {
        nodes.push({name: nodeName, symbolSize: 1})
    }
}


function getNodeName(nodes: TopologyNode[], nodeName: string) {
    return nodes.find(_nodeName => {
        return _nodeName.name === nodeName;
    });
}