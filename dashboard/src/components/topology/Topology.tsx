import {useCurrentNamespaceContext} from "../../contexts/namespace/CurrentNamespaceContext.tsx";
import {useQuery} from "@ahoo-wang/fetcher-react";
import {statApiClient} from "../../services/clients.ts";
import {useMemo, useState, useCallback, useEffect} from "react";
import {toReactFlowTopology, NODE_TYPE_COLORS} from "./topologies.ts";
import {Skeleton, Input, Space} from "antd";
import {
    Background,
    Controls,
    MiniMap,
    ReactFlow,
    NodeMouseHandler,
    Node,
    Edge,
    OnNodesChange,
    applyNodeChanges
} from "@xyflow/react";
import {ServiceNode, ServiceNodeData} from "./ServiceNode.tsx";
import {SearchOutlined} from "@ant-design/icons";
import '@xyflow/react/dist/style.css';

const nodeTypes = {
    default: ServiceNode,
};

// Type guard to safely check if node data is ServiceNodeData
function isServiceNodeData(data: unknown): data is ServiceNodeData {
    return (
        typeof data === 'object' &&
        data !== null &&
        'label' in data &&
        'nodeType' in data &&
        'inDegree' in data &&
        'outDegree' in data
    );
}

export function Topology() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const [searchTerm, setSearchTerm] = useState('');
    const [highlightedNodes, setHighlightedNodes] = useState<Set<string>>(new Set());
    const [internalNodes, setInternalNodes] = useState<Node[]>([]);

    const {result = {}, loading} = useQuery<string, Record<string, string[]>>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return statApiClient.getTopology(namespace, {abortController});
        },
    });

    const {baseNodes, baseEdges} = useMemo(() => {
        const topology = toReactFlowTopology(result);
        return {
            baseNodes: topology.nodes,
            baseEdges: topology.edges
        };
    }, [result]);

    // Update internal nodes when base topology changes
    useEffect(() => {
        setInternalNodes(baseNodes);
    }, [baseNodes]);

    // Apply search filtering and highlighting
    const {nodes, edges} = useMemo(() => {
        const searchLower = searchTerm.toLowerCase();
        const matchedNodeIds = new Set<string>();

        // Find nodes that match search term
        if (searchTerm) {
            internalNodes.forEach(node => {
                if (isServiceNodeData(node.data) &&
                    node.data.label.toLowerCase().includes(searchLower)) {
                    matchedNodeIds.add(node.id);
                }
            });
        }

        // Determine which nodes to highlight
        const nodesToHighlight = highlightedNodes.size > 0
            ? highlightedNodes
            : matchedNodeIds;

        // Update node styles for highlighting
        const updatedNodes: Node[] = internalNodes.map(node => {
            const isHighlighted = nodesToHighlight.has(node.id);
            const isSearchMatch = matchedNodeIds.has(node.id);

            let style = {...node.style};

            if (searchTerm && !isSearchMatch) {
                // Dim non-matching nodes during search
                style = {
                    ...style,
                    opacity: 0.3,
                };
            } else if (highlightedNodes.size > 0 && !isHighlighted) {
                // Dim non-highlighted nodes when a node is clicked
                style = {
                    ...style,
                    opacity: 0.3,
                };
            } else if (isSearchMatch) {
                // Highlight search matches
                style = {
                    ...style,
                    boxShadow: '0 0 10px 3px rgba(255, 215, 0, 0.8)',
                    border: '2px solid #ffd700',
                };
            }

            return {
                ...node,
                style,
            };
        });

        // Update edge styles for highlighting
        const updatedEdges: Edge[] = baseEdges.map(edge => {
            const isConnected =
                nodesToHighlight.has(edge.source) ||
                nodesToHighlight.has(edge.target);

            let style = {...edge.style};

            if (nodesToHighlight.size > 0 && !isConnected) {
                // Dim non-connected edges
                style = {
                    ...style,
                    opacity: 0.2,
                };
            } else if (isConnected) {
                // Highlight connected edges
                style = {
                    ...style,
                    strokeWidth: 3,
                    stroke: '#ffd700',
                };
            }

            return {
                ...edge,
                style,
            };
        });

        return {
            nodes: updatedNodes,
            edges: updatedEdges,
        };
    }, [internalNodes, baseEdges, searchTerm, highlightedNodes]);

    // Handle node click to highlight connected nodes
    const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
        const connectedNodes = new Set([node.id]);

        // Find all connected nodes (both incoming and outgoing)
        baseEdges.forEach(edge => {
            if (edge.source === node.id) {
                connectedNodes.add(edge.target);
            }
            if (edge.target === node.id) {
                connectedNodes.add(edge.source);
            }
        });

        setHighlightedNodes(connectedNodes);
    }, [baseEdges]);

    // Clear highlights when clicking on pane
    const onPaneClick = useCallback(() => {
        setHighlightedNodes(new Set());
    }, []);

    // Handle node position changes (for dragging)
    const onNodesChange: OnNodesChange = useCallback((changes) => {
        setInternalNodes((nds) => applyNodeChanges(changes, nds));
    }, []);

    if (loading) {
        return <Skeleton/>
    }

    return (
        <div style={{width: '100%', height: '100%', position: 'relative'}}>
            {/* Control Panel */}
            <div style={{
                position: 'absolute',
                top: '10px',
                left: '10px',
                zIndex: 10,
                background: 'rgba(255, 255, 255, 0.95)',
                padding: '12px',
                borderRadius: '8px',
                boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            }}>
                <Space size="middle">
                    {/* Search Input */}
                    <Input
                        placeholder="Search nodes..."
                        prefix={<SearchOutlined/>}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        allowClear
                        style={{width: '250px'}}
                    />
                </Space>
            </div>

            {/* React Flow Canvas */}
            <ReactFlow
                nodes={nodes}
                edges={edges}
                nodeTypes={nodeTypes}
                onNodeClick={onNodeClick}
                onPaneClick={onPaneClick}
                onNodesChange={onNodesChange}
                nodesDraggable={true}
                fitView
                fitViewOptions={{
                    padding: 0.2,
                }}
            >
                <Background/>
                <Controls/>
                <MiniMap
                    nodeColor={(node) => {
                        if (isServiceNodeData(node.data)) {
                            return NODE_TYPE_COLORS[node.data.nodeType].backgroundColor;
                        }
                        // Fallback color for unexpected node data
                        return NODE_TYPE_COLORS.intermediate.backgroundColor;
                    }}
                    maskColor="rgba(0, 0, 0, 0.1)"
                    style={{
                        backgroundColor: 'rgba(255, 255, 255, 0.9)',
                    }}
                />
            </ReactFlow>
        </div>
    );
}