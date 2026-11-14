/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
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

import {useMemo, useState, useCallback} from "react";
import {
    toReactFlowTopology,
    NODE_TYPE_COLORS,
    HIGHLIGHT_COLOR,
    DIM_OPACITY,
    getConnectedNodeIds,
    isServiceNodeData,
} from "@/components/topology/topologies";
import type {Node, Edge, NodeMouseHandler, OnNodesChange} from "@xyflow/react";
import {
    Background,
    Controls,
    MiniMap,
    ReactFlow,
    applyNodeChanges,
} from "@xyflow/react";
import {ServiceNode} from "@/components/topology/ServiceNode";
import {Network, Search} from "lucide-react";
import {Input} from "@/components/ui/input";
import {Skeleton} from "@/components/ui/skeleton";
import '@xyflow/react/dist/style.css';

const nodeTypes = {
    service: ServiceNode,
};

interface TopologyProps {
    /** What each consumer depends on; none while it is being read. */
    consumers?: Record<string, string[]>;
    error?: unknown;
    onRetry: () => void;
}

export function Topology({consumers, error, onRetry}: TopologyProps) {
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedNodeId, setSelectedNodeId] = useState<string>();

    const {baseNodes, baseEdges} = useMemo(() => {
        const topology = toReactFlowTopology(consumers ?? {});
        return {
            baseNodes: topology.nodes,
            baseEdges: topology.edges
        };
    }, [consumers]);

    const [internalNodes, setInternalNodes] = useState(baseNodes);
    // The topology is read again and again. Only relationships that changed start the graph over: reading the same
    // ones again keeps what someone moved, searched for or selected.
    const relationships = JSON.stringify(consumers ?? {});
    const [shownRelationships, setShownRelationships] = useState(relationships);
    if (shownRelationships !== relationships) {
        setShownRelationships(relationships);
        setInternalNodes(baseNodes);
        setSearchTerm('');
        setSelectedNodeId(undefined);
    }

    const {nodes, edges} = useMemo(() => {
        const hasSearch = searchTerm.length > 0;
        const highlightedNodes = selectedNodeId
            ? getConnectedNodeIds(selectedNodeId, baseEdges)
            : new Set<string>();
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
                        // Around the rounded box of the ServiceNode itself.
                        borderRadius: 12,
                    },
                };
            }
            return node;
        });

        const updatedEdges: Edge[] = baseEdges.map(edge => {
            const isConnected = selectedNodeId
                ? edge.source === selectedNodeId || edge.target === selectedNodeId
                : nodesToHighlight.has(edge.source) || nodesToHighlight.has(edge.target);

            if (nodesToHighlight.size > 0 && !isConnected) {
                return {...edge, style: {...edge.style, opacity: 0.08}};
            }
            if (isConnected) {
                return {
                    ...edge,
                    style: {...edge.style, opacity: 1, strokeWidth: 3, stroke: HIGHLIGHT_COLOR},
                };
            }
            return edge;
        });

        return {nodes: updatedNodes, edges: updatedEdges};
    }, [internalNodes, baseEdges, searchTerm, selectedNodeId]);

    const onNodeClick: NodeMouseHandler = useCallback((_event, node) => {
        setSelectedNodeId(node.id);
    }, []);

    const onPaneClick = useCallback(() => {
        setSelectedNodeId(undefined);
    }, []);

    const onNodesChange: OnNodesChange = useCallback((changes) => {
        setInternalNodes((nds) => applyNodeChanges(changes, nds));
    }, []);

    if (error) {
        return <div role="alert" className="grid h-full min-h-96 place-items-center text-center">
            <div className="space-y-3">
                <Network className="mx-auto size-10 text-destructive/70"/>
                <p className="font-medium">Could not load service topology</p>
                <button type="button" className="text-sm font-medium text-primary hover:underline" onClick={onRetry}>Retry</button>
            </div>
        </div>;
    }
    if (!consumers) {
        return <Skeleton className="h-full min-h-96 w-full"/>;
    }
    if (nodes.length === 0) {
        return (
            <div className="grid h-full place-items-center text-center">
                <div className="space-y-3">
                    <Network className="mx-auto size-10 text-muted-foreground/60"/>
                    <div>
                        <p className="font-medium">No service relationships yet</p>
                        <p className="mt-1 text-sm text-muted-foreground">A line appears when an application watches another service, within half a minute.</p>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="flex h-full min-h-0 flex-col">
            <div className="flex flex-none items-center justify-between gap-3 border-b py-2">
                <div className="relative">
                    <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
                    <Input
                        placeholder="Search nodes..."
                        value={searchTerm}
                        onChange={(e) => {
                            setSearchTerm(e.target.value);
                            setSelectedNodeId(undefined);
                        }}
                        className="w-56 pl-8"
                        style={{paddingLeft: '2.25rem'}}
                    />
                </div>
                <span className="hidden text-xs text-muted-foreground sm:inline">Select a service to trace direct dependencies</span>
            </div>
            <div className="min-h-0 flex-1">
                <ReactFlow
                    // Other relationships are laid out anew, and fit into view again: fitView only acts as it mounts.
                    key={relationships}
                    nodes={nodes}
                    edges={edges}
                    nodeTypes={nodeTypes}
                    onNodeClick={onNodeClick}
                    onPaneClick={onPaneClick}
                    onNodesChange={onNodesChange}
                    nodesDraggable={true}
                    proOptions={{hideAttribution: true}}
                    fitView
                    fitViewOptions={{
                        padding: 0.2,
                    }}
                >
                    <Background/>
                    <Controls/>
                    <MiniMap
                        className="max-sm:hidden"
                        position="top-right"
                        nodeColor={(node) => {
                            if (isServiceNodeData(node.data)) {
                                return NODE_TYPE_COLORS[node.data.nodeType]?.backgroundColor
                                    ?? NODE_TYPE_COLORS.intermediate.backgroundColor;
                            }
                            return NODE_TYPE_COLORS.intermediate.backgroundColor;
                        }}
                        maskColor="rgba(0, 0, 0, 0.1)"
                        style={{
                            backgroundColor: 'rgba(255, 255, 255, 0.94)',
                            width: 140,
                            height: 96,
                        }}
                    />
                </ReactFlow>
            </div>
        </div>
    );
}
