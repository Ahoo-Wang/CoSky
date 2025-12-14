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

import React, {useEffect, useRef} from 'react';
import {Spin} from 'antd';
import * as echarts from 'echarts';
import {useNamespace} from '../../contexts/NamespaceContext';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {stateApiClient} from "../../client/clients.ts";

export const TopologyPage: React.FC = () => {
    const {currentNamespace} = useNamespace();
    const chartRef = useRef<HTMLDivElement>(null);
    const chartInstance = useRef<echarts.ECharts | null>(null);

    const {result: services, loading} = useQuery<string, Record<string, string[]>>({
        initialQuery: currentNamespace,
        execute: (namespace, _, abortController) => {
            return stateApiClient.getTopology(namespace, {abortController});
        },
    });

    useEffect(() => {
        if (chartRef.current) {
            chartInstance.current = echarts.init(chartRef.current);
        }
        return () => {
            chartInstance.current?.dispose();
        };
    }, []);

    const renderChart = (topology: Record<string, string[]>) => {
        if (!chartInstance.current || !topology) return;

        const nodes = topology.nodes || [];
        const links = topology.edges || [];

        const option = {
            title: {
                text: 'Service Topology',
            },
            tooltip: {},
            series: [
                {
                    type: 'graph',
                    layout: 'force',
                    data: nodes.map((node: any) => ({
                        name: node.serviceId,
                        symbolSize: 50,
                    })),
                    links: links.map((link: any) => ({
                        source: link.source,
                        target: link.target,
                    })),
                    roam: true,
                    label: {
                        show: true,
                    },
                    force: {
                        repulsion: 100,
                    },
                },
            ],
        };

        chartInstance.current.setOption(option);
    };

    useEffect(() => {
        const topology = {nodes: services.map((s: string) => ({serviceId: s})), edges: []};
        renderChart(topology);
    }, [services]);

    return (
        <Spin spinning={loading}>
            <div>
                <h2 style={{marginBottom: 24}}>Topology</h2>
                <div ref={chartRef} style={{width: '100%', height: 600}}/>
            </div>
        </Spin>
    );
};
