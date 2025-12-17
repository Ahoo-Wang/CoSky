import {Node, Edge} from '@xyflow/react';
import dagre from '@dagrejs/dagre';

export interface ReactFlowTopology {
    nodes: Node[];
    edges: Edge[];
}

export type NodeType = 'source' | 'target' | 'intermediate';

// Node dimension constants
const BASE_NODE_WIDTH = 172;
const NODE_HEIGHT = 36;
const MIN_CHAR_WIDTH = 8; // Approximate character width in pixels
const NODE_PADDING = 40; // Space for icon, margins, and padding

// Dynamic layout thresholds and spacing
const LARGE_GRAPH_THRESHOLD = 50;
const MEDIUM_GRAPH_THRESHOLD = 20;
const LARGE_GRAPH_NODESEP = 80;
const MEDIUM_GRAPH_NODESEP = 60;
const SMALL_GRAPH_NODESEP = 50;
const LARGE_GRAPH_RANKSEP = 100;
const MEDIUM_GRAPH_RANKSEP = 80;
const SMALL_GRAPH_RANKSEP = 60;

// Node type color configuration
export const NODE_TYPE_COLORS = {
    source: {
        backgroundColor: '#1890ff',
        color: '#fff',
        borderColor: '#096dd9'
    },
    target: {
        backgroundColor: '#ff7a45',
        color: '#fff',
        borderColor: '#d4380d'
    },
    intermediate: {
        backgroundColor: '#722ed1',
        color: '#fff',
        borderColor: '#531dab'
    }
} as const;

export function toReactFlowTopology(
    topology: Record<string, string[]>,
    layoutDirection: 'TB' | 'LR' | 'BT' | 'RL' = 'TB'
): ReactFlowTopology {
    const nodes: Node[] = [];
    const edges: Edge[] = [];

    // Collect all unique nodes
    const allNodes = new Set<string>();
    Object.keys(topology).forEach(nodeName => {
        allNodes.add(nodeName);
        topology[nodeName].forEach(targetName => {
            allNodes.add(targetName);
        });
    });

    // Analyze node connections to classify node types
    const outDegree = new Map<string, number>();
    const inDegree = new Map<string, number>();
    
    // Initialize all nodes with 0 degrees
    allNodes.forEach(nodeName => {
        outDegree.set(nodeName, 0);
        inDegree.set(nodeName, 0);
    });
    
    // Calculate degrees
    Object.keys(topology).forEach(nodeName => {
        const targets = topology[nodeName];
        outDegree.set(nodeName, (outDegree.get(nodeName) || 0) + targets.length);
        targets.forEach(targetName => {
            inDegree.set(targetName, (inDegree.get(targetName) || 0) + 1);
        });
    });
    
    // Classify nodes by type
    const getNodeType = (nodeName: string): NodeType => {
        const hasIncoming = (inDegree.get(nodeName) || 0) > 0;
        const hasOutgoing = (outDegree.get(nodeName) || 0) > 0;
        
        if (hasOutgoing && !hasIncoming) return 'source';
        if (hasIncoming && !hasOutgoing) return 'target';
        return 'intermediate';
    };
    
    // Get node styles based on type
    const getNodeStyle = (nodeType: NodeType) => {
        return NODE_TYPE_COLORS[nodeType];
    };

    // Dynamic layout configuration based on node count
    const nodeCount = allNodes.size;
    const nodesep = nodeCount > LARGE_GRAPH_THRESHOLD 
        ? LARGE_GRAPH_NODESEP 
        : nodeCount > MEDIUM_GRAPH_THRESHOLD 
            ? MEDIUM_GRAPH_NODESEP 
            : SMALL_GRAPH_NODESEP;
    const ranksep = nodeCount > LARGE_GRAPH_THRESHOLD 
        ? LARGE_GRAPH_RANKSEP 
        : nodeCount > MEDIUM_GRAPH_THRESHOLD 
            ? MEDIUM_GRAPH_RANKSEP 
            : SMALL_GRAPH_RANKSEP;

    // Create a new directed graph
    const dagreGraph = new dagre.graphlib.Graph().setDefaultEdgeLabel(() => ({}));
    dagreGraph.setGraph({
        rankdir: layoutDirection, 
        ranker: 'tight-tree',
        nodesep,
        ranksep
    });

    // Calculate dynamic node width based on label length
    const getNodeWidth = (label: string): number => {
        return Math.max(BASE_NODE_WIDTH, label.length * MIN_CHAR_WIDTH + NODE_PADDING);
    };

    // Add nodes to dagre graph with dynamic widths
    allNodes.forEach(nodeName => {
        const nodeWidth = getNodeWidth(nodeName);
        dagreGraph.setNode(nodeName, {width: nodeWidth, height: NODE_HEIGHT});
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
        const nodeWidth = getNodeWidth(nodeName);
        const nodeType = getNodeType(nodeName);
        const nodeStyle = getNodeStyle(nodeType);
        
        nodes.push({
            id: nodeName,
            type: 'default',
            data: {
                label: nodeName,
                nodeType,
                inDegree: inDegree.get(nodeName)!,
                outDegree: outDegree.get(nodeName)!,
            },
            style: nodeStyle,
            position: {
                x: nodeWithPosition.x - nodeWidth / 2,
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
