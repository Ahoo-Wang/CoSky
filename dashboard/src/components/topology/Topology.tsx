import {useNamespaceContext} from "../../contexts/NamespaceContext.tsx";
import {useQuery} from "@ahoo-wang/fetcher-react";
import {statApiClient} from "../../services/clients.ts";
import {useMemo} from "react";
import {toReactFlowTopology} from "./topologies.ts";
import {Skeleton} from "antd";
import {Background, Controls, MiniMap, ReactFlow} from "@xyflow/react";
import '@xyflow/react/dist/style.css';

export function Topology() {
    const {currentNamespace} = useNamespaceContext();
    const {result = {}, loading} = useQuery<string, Record<string, string[]>>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return statApiClient.getTopology(namespace, {abortController});
        },
    });

    const {nodes, edges} = useMemo(() => {
        return toReactFlowTopology(result);
    }, [result]);
    if (loading) {
        return <Skeleton/>
    }
    return (
        <ReactFlow
            nodes={nodes}
            edges={edges}
            fitView
        >
            <Background/>
            <Controls/>
            <MiniMap/>
        </ReactFlow>
    );
}