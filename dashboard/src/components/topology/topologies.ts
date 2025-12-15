import {Node, Edge} from '@xyflow/react';
import dagre from '@dagrejs/dagre';

export interface ReactFlowTopology {
    nodes: Node[];
    edges: Edge[];
}

const NODE_WIDTH = 172;
const NODE_HEIGHT = 36;

export function toReactFlowTopology(topology: Record<string, string[]>): ReactFlowTopology {
    const nodes: Node[] = [];
    const edges: Edge[] = [];

    // Create a new directed graph
    const dagreGraph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({rankdir: 'TB',ranker:'tight-tree'});

    // Collect all unique nodes
    const allNodes = new Set<string>();
    Object.keys(topology).forEach(nodeName => {
        allNodes.add(nodeName);
        topology[nodeName].forEach(targetName => {
            allNodes.add(targetName);
        });
    });

    // Add nodes to dagre graph
    allNodes.forEach(nodeName => {
        dagreGraph.setNode(nodeName, {width: NODE_WIDTH, height: NODE_HEIGHT});
    });

    // Add edges to dagre graph
    Object.keys(topology).forEach(nodeName => {
        topology[nodeName].forEach(targetName => {
            dagreGraph.setEdge(nodeName, targetName);
        });
    });

    // Calculate layout
    dagre.layout(dagreGraph);

    // Create ReactFlow nodes with positions from dagre
    dagreGraph.nodes().forEach(nodeName => {
        const nodeWithPosition = dagreGraph.node(nodeName);
        nodes.push({
            id: nodeName,
            type: 'default',
            data: {
                label: nodeName,
            },
            position: {
                x: nodeWithPosition.x - NODE_WIDTH / 2,
                y: nodeWithPosition.y - NODE_HEIGHT / 2,
            },
        });
    });

    // Create edges with visual configuration for hierarchical layout
    Object.keys(topology).forEach(nodeName => {
        topology[nodeName].forEach(targetName => {
            edges.push({
                id: `${nodeName}-${targetName}`,
                source: nodeName,
                target: targetName,
                animated: true,    // Animated edges show data flow direction
                type: 'smoothstep', // Smoothstep edges create clean 90-degree turns suitable for hierarchical layouts
            });
        });
    });

    return {nodes, edges};
}
