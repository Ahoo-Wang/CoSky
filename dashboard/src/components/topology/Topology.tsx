/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)]
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {statApiClient} from '../../services/clients.ts';
import {useMemo, useState, useCallback} from 'react';
import {
    toReactFlowTopology,
    flowThemes,
    DIM_OPACITY,
    getConnectedNodeIds,
    isServiceNodeData,
} from './topologies.ts';
import {Skeleton} from '@/components/ui/skeleton';
import {useTheme} from '@/theme/ThemeProvider';
import type {Node, Edge, NodeMouseHandler, OnNodesChange} from '@xyflow/react';
import {
    Background,
    Controls,
    MiniMap,
    ReactFlow,
    applyNodeChanges,
} from '@xyflow/react';
import {ServiceNode} from './ServiceNode.tsx';
import '@xyflow/react/dist/style.css';

const nodeTypes = {
    default: ServiceNode,
};

export function Topology({searchTerm}: { searchTerm: string }) {
    const {currentNamespace} = useCurrentNamespaceContext();
    const [highlightedNodes, setHighlightedNodes] = useState<Set<string>>(new Set());
    const {resolvedTheme} = useTheme();
    const flowTheme = flowThemes[resolvedTheme];
    const {edge: edgeColor} = flowTheme;
    const highlightColor = resolvedTheme === 'dark' ? '#a5b4fc' : '#667eea';

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
            return {
                nodes: internalNodes,
                edges: baseEdges.map(edge => ({
                    ...edge,
                    style: {...edge.style, stroke: edgeColor},
                })),
            };
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
                        boxShadow: `0 0 10px 3px ${highlightColor}cc`,
                        border: `2px solid ${highlightColor}`,
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
                return {
                    ...edge,
                    style: {...edge.style, stroke: edgeColor, opacity: 0.2},
                };
            }
            if (isConnected) {
                return {
                    ...edge,
                    style: {...edge.style, strokeWidth: 3, stroke: highlightColor},
                };
            }
            return {
                ...edge,
                style: {...edge.style, stroke: edgeColor},
            };
        });

        return {nodes: updatedNodes, edges: updatedEdges};
    }, [internalNodes, baseEdges, searchTerm, highlightedNodes, edgeColor, highlightColor]);

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
        return <Skeleton className="h-full w-full"/>;
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
            <Background color={flowTheme.background}/>
            <Controls/>
            <MiniMap
                nodeColor={() => flowTheme.nodeBorder}
                maskColor={resolvedTheme === 'dark' ? 'rgba(0, 0, 0, 0.55)' : 'rgba(0, 0, 0, 0.1)'}
                style={{
                    backgroundColor: resolvedTheme === 'dark' ? '#18181b' : '#ffffff',
                }}
            />
        </ReactFlow>
    );
}
