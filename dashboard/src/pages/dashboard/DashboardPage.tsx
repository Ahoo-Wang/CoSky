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

import {Card, Row, Col, Statistic} from 'antd';
import {
    PartitionOutlined,
    FileOutlined,
    CloudServerOutlined,
    ClusterOutlined
} from '@ant-design/icons';
import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import {GetStatResponse} from '../../generated';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {statApiClient} from "../../services/clients.ts";
import {Topology} from "../../components/topology/Topology.tsx";
import {useRef} from "react";
import {Fullscreen} from "@ahoo-wang/fetcher-viewer";

export function DashboardPage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {
        result: stat = {
            namespaces: 0,
            configs: 0,
            services: {total: 0, health: 0},
            instances: 0,
        }
    } = useQuery<string, GetStatResponse>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return statApiClient.getStat(namespace, {abortController});
        },
    });
    const topologyRef = useRef<HTMLDivElement>(null);
    return (
        <div>
            <h2 style={{
                marginBottom: 32,
                fontSize: '28px',
                fontWeight: 600,
                color: '#262626',
                letterSpacing: '-0.5px',
            }}>Dashboard</h2>
            <Row gutter={[24, 24]}>
                <Col xs={24} sm={12} lg={6}>
                    <Card
                        hoverable
                        style={{
                            borderRadius: 12,
                            border: 'none',
                            background: 'linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%)',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                            transition: 'all 0.3s ease',
                        }}
                        styles={{
                            body: {
                                padding: '24px',
                            }
                        }}
                    >
                        <Statistic
                            title="Namespace Count"
                            value={stat.namespaces}
                            prefix={<PartitionOutlined style={{fontSize: '24px'}}/>}
                            styles={{
                                title: {
                                    color: '#0369a1',
                                    fontWeight: 500,
                                },
                                content: {
                                    color: '#0c4a6e',
                                    fontSize: '32px',
                                }
                            }}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card
                        hoverable
                        style={{
                            borderRadius: 12,
                            border: 'none',
                            background: 'linear-gradient(135deg, #faf5ff 0%, #f3e8ff 100%)',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                            transition: 'all 0.3s ease',
                        }}
                        styles={{
                            body: {
                                padding: '24px',
                            }
                        }}
                    >
                        <Statistic
                            title="Instance Count"
                            value={stat.instances}
                            prefix={<ClusterOutlined style={{fontSize: '24px'}}/>}
                            styles={{
                                title: {
                                    color: '#7c3aed',
                                    fontWeight: 500,
                                },
                                content: {
                                    color: '#6b21a8',
                                    fontSize: '32px',
                                }
                            }}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card
                        hoverable
                        style={{
                            borderRadius: 12,
                            border: 'none',
                            background: 'linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%)',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                            transition: 'all 0.3s ease',
                        }}
                        styles={{
                            body: {
                                padding: '24px',
                            }
                        }}
                    >
                        <Statistic
                            title="Config Count"
                            value={stat.configs}
                            prefix={<FileOutlined style={{fontSize: '24px'}}/>}
                            styles={{
                                title: {
                                    color: '#059669',
                                    fontWeight: 500,
                                },
                                content: {
                                    color: '#047857',
                                    fontSize: '32px',
                                }
                            }}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={12} lg={6}>
                    <Card
                        hoverable
                        style={{
                            borderRadius: 12,
                            border: 'none',
                            background: 'linear-gradient(135deg, #fff1f2 0%, #ffe4e6 100%)',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                            transition: 'all 0.3s ease',
                        }}
                        styles={{
                            body: {
                                padding: '24px',
                            }
                        }}
                    >
                        <Statistic
                            title="Service Count"
                            value={stat.services.health}
                            suffix={`/ ${stat.services.total}`}
                            prefix={<CloudServerOutlined style={{fontSize: '24px'}}/>}
                            styles={{
                                title: {
                                    color: '#e11d48',
                                    fontWeight: 500,
                                },
                                content: {
                                    color: '#be123c',
                                    fontSize: '32px',
                                }
                            }}
                        />
                    </Card>
                </Col>
                <Col xs={24} sm={24} lg={24}>
                    <Card 
                        ref={topologyRef} 
                        title={<span style={{fontSize: '18px', fontWeight: 600}}>Service Topology</span>}
                        style={{
                            height: "100%",
                            display: "flex",
                            flexDirection: "column",
                            borderRadius: 12,
                            border: 'none',
                            boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                        }}
                        styles={{
                            body: {
                                flex: "auto",
                                minHeight: "65vh",
                            }
                        }}
                        extra={<Fullscreen target={topologyRef} size={"small"} type={"dashed"}/>}
                    >
                        <Topology/>
                    </Card>
                </Col>
            </Row>
        </div>
    );
}
