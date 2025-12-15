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

import {useMemo} from 'react';
import {Spin} from 'antd';
import {ReactFlow, Background, Controls, MiniMap} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import {useNamespaceContext} from '../../contexts/NamespaceContext.tsx';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {statApiClient} from "../../services/clients.ts";
import {toReactFlowTopology} from "./topologies.ts";

export function TopologyPage() {
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

    return (
        <Spin spinning={loading}>
            <div>
                <h2 style={{marginBottom: 24}}>Topology</h2>
                <div style={{width: '100%', height: '80vh', flex: 1}}>
                    <ReactFlow
                        nodes={nodes}
                        edges={edges}
                        fitView
                        attributionPosition="bottom-left"
                    >
                        <Background/>
                        <Controls/>
                        <MiniMap/>
                    </ReactFlow>
                </div>
            </div>
        </Spin>
    );
};
