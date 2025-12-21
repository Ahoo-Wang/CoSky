import {Node, Edge} from '@xyflow/react';

export interface ReactFlowTopology {
    nodes: Node[];
    edges: Edge[];
}

export type NodeType = 'source' | 'target' | 'intermediate';

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
    topology: Record<string, string[]>
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



    // Create layered layout: source (first layer), intermediate (middle), target (last)
    const nodeList = Array.from(allNodes);
    const sources = nodeList.filter(nodeName => getNodeType(nodeName) === 'source').sort();
    const intermediates = nodeList.filter(nodeName => getNodeType(nodeName) === 'intermediate').sort();
    const targets = nodeList.filter(nodeName => getNodeType(nodeName) === 'target').sort();

    const layerSpacingY = 400;
    const nodeSpacingX = 250;
    const rowSpacingY = 150;
    const maxNodesPerRow = 6;

    function layoutLayer(nodes: string[], y: number) {
        const nodeCount = nodes.length;
        if (nodeCount === 0) return [];
        const rows = Math.ceil(nodeCount / maxNodesPerRow);
        const positions: Array<{nodeName: string, x: number, y: number}> = [];

        for (let row = 0; row < rows; row++) {
            const rowStart = row * maxNodesPerRow;
            const rowNodes = nodes.slice(rowStart, rowStart + maxNodesPerRow);
            const rowNodeCount = rowNodes.length;
            const totalWidth = (rowNodeCount - 1) * nodeSpacingX;
            const startX = -totalWidth / 2;
            const rowY = y + row * rowSpacingY;

            rowNodes.forEach((nodeName, index) => {
                positions.push({
                    nodeName,
                    x: startX + index * nodeSpacingX,
                    y: rowY
                });
            });
        }

        return positions;
    }

    const sourcePositions = layoutLayer(sources, 0);
    const intermediatePositions = layoutLayer(intermediates, layerSpacingY);
    const targetPositions = layoutLayer(targets, 2 * layerSpacingY);

    const positionMap = new Map<string, {x: number, y: number}>();
    [...sourcePositions, ...intermediatePositions, ...targetPositions].forEach(({nodeName, x, y}) => {
        positionMap.set(nodeName, {x, y});
    });

    nodeList.forEach((nodeName) => {
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
            position: positionMap.get(nodeName)!,
        });
    });

    // Create edges with bezier curves for smoother, more visually appealing connections
    Object.keys(topology).forEach(nodeName => {
        topology[nodeName].forEach(targetName => {
            edges.push({
                id: `${nodeName}-${targetName}`,
                source: nodeName,
                target: targetName,
                animated: true,    // Animated edges show data flow direction
            });
        });
    });

    return {nodes, edges};
}
