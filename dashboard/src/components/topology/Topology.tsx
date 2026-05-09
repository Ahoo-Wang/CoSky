import {useCurrentNamespaceContext} from "../../contexts/namespace/CurrentNamespaceContext.tsx";
import {useQuery} from "@ahoo-wang/fetcher-react";
import {statApiClient} from "../../services/clients.ts";
import {useMemo, useState, useCallback} from "react";
import {
    toReactFlowTopology,
    NODE_TYPE_COLORS,
    HIGHLIGHT_COLOR,
    DIM_OPACITY,
    getConnectedNodeIds,
    isServiceNodeData,
} from "./topologies.ts";
import {Skeleton, Input} from "antd";
import type {Node, Edge, NodeMouseHandler, OnNodesChange} from "@xyflow/react";
import {
    Background,
    Controls,
    MiniMap,
    ReactFlow,
    Panel,
    applyNodeChanges,
} from "@xyflow/react";
import {ServiceNode} from "./ServiceNode.tsx";
import {SearchOutlined} from "@ant-design/icons";
import '@xyflow/react/dist/style.css';

const nodeTypes = {
    default: ServiceNode,
};

export function Topology() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const [searchTerm, setSearchTerm] = useState('');
    const [highlightedNodes, setHighlightedNodes] = useState<Set<string>>(new Set());

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

    const [internalNodes, setInternalNodes] = useState(baseNodes);
    // getDerivedStateFromProps pattern: sync when baseNodes reference changes
    const [prevBaseNodes, setPrevBaseNodes] = useState(baseNodes);
    if (prevBaseNodes !== baseNodes) {
        setPrevBaseNodes(baseNodes);
        setInternalNodes(baseNodes);
    }

    const {nodes, edges} = useMemo(() => {
        const hasSearch = searchTerm.length > 0;
        const hasHighlight = highlightedNodes.size > 0;

        if (!hasSearch && !hasHighlight) {
            return {nodes: internalNodes, edges: baseEdges};
        }

        const searchLower = searchTerm.toLowerCase();
        const matchedNodeIds = new Set<string>();

        if (hasSearch) {
            for (const node of internalNodes) {
                if (isServiceNodeData(node.data) &&
                    node.data.label.toLowerCase().includes(searchLower)) {
                    matchedNodeIds.add(node.id);
                }
            }
        }

        const nodesToHighlight = hasHighlight ? highlightedNodes : matchedNodeIds;

        const updatedNodes: Node[] = internalNodes.map(node => {
            const isHighlighted = nodesToHighlight.has(node.id);
            const isSearchMatch = matchedNodeIds.has(node.id);

            if (hasSearch && !isSearchMatch) {
                return {...node, style: {...node.style, opacity: DIM_OPACITY}};
            }
            if (hasHighlight && !isHighlighted) {
                return {...node, style: {...node.style, opacity: DIM_OPACITY}};
            }
            if (isSearchMatch) {
                return {
                    ...node,
                    style: {
                        ...node.style,
                        boxShadow: `0 0 10px 3px ${HIGHLIGHT_COLOR}cc`,
                        border: `2px solid ${HIGHLIGHT_COLOR}`,
                    },
                };
            }
            return node;
        });

        const updatedEdges: Edge[] = baseEdges.map(edge => {
            const isConnected =
                nodesToHighlight.has(edge.source) ||
                nodesToHighlight.has(edge.target);

            if (nodesToHighlight.size > 0 && !isConnected) {
                return {...edge, style: {...edge.style, opacity: 0.2}};
            }
            if (isConnected) {
                return {
                    ...edge,
                    style: {...edge.style, strokeWidth: 3, stroke: HIGHLIGHT_COLOR},
                };
            }
            return edge;
        });

        return {nodes: updatedNodes, edges: updatedEdges};
    }, [internalNodes, baseEdges, searchTerm, highlightedNodes]);

    const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
        setHighlightedNodes(getConnectedNodeIds(node.id, baseEdges));
    }, [baseEdges]);

    const onPaneClick = useCallback(() => {
        setHighlightedNodes(new Set());
    }, []);

    const onNodesChange: OnNodesChange = useCallback((changes) => {
        setInternalNodes((nds) => applyNodeChanges(changes, nds));
    }, []);

    if (loading) {
        return <Skeleton/>
    }

    return (
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
            <Panel style={{
                padding: '8px',
                borderRadius: '8px',
                boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            }}>
                <Input
                    placeholder="Search nodes..."
                    prefix={<SearchOutlined/>}
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    allowClear
                />
            </Panel>
            <Background/>
            <Controls/>
            <MiniMap
                nodeColor={(node) => {
                    if (isServiceNodeData(node.data)) {
                        return NODE_TYPE_COLORS[node.data.nodeType]?.backgroundColor
                            ?? NODE_TYPE_COLORS.intermediate.backgroundColor;
                    }
                    return NODE_TYPE_COLORS.intermediate.backgroundColor;
                }}
                maskColor="rgba(0, 0, 0, 0.1)"
                style={{
                    backgroundColor: 'rgba(255, 255, 255, 0.9)',
                }}
            />
        </ReactFlow>
    );
}
