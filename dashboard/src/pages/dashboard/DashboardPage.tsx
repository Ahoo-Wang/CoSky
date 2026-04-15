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

import {Card, Row, Col} from 'antd';
import {
    PartitionOutlined,
    FileOutlined,
    CloudServerOutlined,
    ClusterOutlined
} from '@ant-design/icons';
import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import type {GetStatResponse} from '../../generated';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {statApiClient} from "../../services/clients.ts";
import {Topology} from "../../components/topology/Topology.tsx";
import {useRef, useEffect, useState} from "react";
import {Fullscreen} from "@ahoo-wang/fetcher-viewer";

/* === Count-up Animation Hook === */
const useCountUp = (end: number, duration: number = 1500) => {
    const [count, setCount] = useState(0);
    const [started, setStarted] = useState(false);

    useEffect(() => {
        if (!started) return;
        let startTime: number;
        let animationFrame: number;

        const animate = (timestamp: number) => {
            if (!startTime) startTime = timestamp;
            const progress = Math.min((timestamp - startTime) / duration, 1);
            const easeOutQuart = 1 - Math.pow(1 - progress, 4);
            setCount(Math.floor(easeOutQuart * end));

            if (progress < 1) {
                animationFrame = requestAnimationFrame(animate);
            }
        };

        animationFrame = requestAnimationFrame(animate);
        return () => cancelAnimationFrame(animationFrame);
    }, [end, duration, started]);

    return {count, start: () => setStarted(true)};
};

/* === Card Data === */
interface CardData {
    title: string;
    value: number;
    suffix: string;
    prefix: React.ReactNode;
    gradient: string;
}

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

    const namespaces = useCountUp(stat.namespaces);
    const instances = useCountUp(stat.instances);
    const configs = useCountUp(stat.configs);
    const services = useCountUp(stat.services.health);

    useEffect(() => {
        const timer = setTimeout(() => {
            namespaces.start();
            instances.start();
            configs.start();
            services.start();
        }, 300);
        return () => clearTimeout(timer);
    }, []);

    const cardsData: CardData[] = [
        {
            title: 'Namespace Count',
            value: namespaces.count,
            suffix: '',
            prefix: <PartitionOutlined/>,
            gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        },
        {
            title: 'Instance Count',
            value: instances.count,
            suffix: '',
            prefix: <ClusterOutlined/>,
            gradient: 'linear-gradient(135deg, #7c3aed 0%, #a855f7 100%)',
        },
        {
            title: 'Config Count',
            value: configs.count,
            suffix: '',
            prefix: <FileOutlined/>,
            gradient: 'linear-gradient(135deg, #059669 0%, #10b981 100%)',
        },
        {
            title: 'Service Health',
            value: services.count,
            suffix: `/ ${stat.services.total}`,
            prefix: <CloudServerOutlined/>,
            gradient: 'linear-gradient(135deg, #e11d48 0%, #f43f5e 100%)',
        },
    ];

    return (
        <div className="dashboard-container">
            <h2 className="dashboard-title">Dashboard</h2>
            <Row gutter={[24, 24]}>
                {cardsData.map((card) => (
                    <Col key={card.title} xs={24} sm={12} lg={6}>
                        <Card
                            hoverable
                            className="stat-card"
                            style={{background: card.gradient}}
                            styles={{body: {padding: '24px'}}}
                        >
                            <div className="stat-card-content">
                                <div className="stat-info">
                                    <div className="stat-title">{card.title}</div>
                                    <div className="stat-value">
                                        {card.value.toLocaleString()}
                                        <span className="stat-suffix">{card.suffix}</span>
                                    </div>
                                </div>
                                <div className="stat-icon">
                                    {card.prefix}
                                </div>
                            </div>
                            <div className="stat-glow"/>
                        </Card>
                    </Col>
                ))}
                <Col xs={24} sm={24} lg={24}>
                    <Card
                        ref={topologyRef}
                        className="topology-card"
                        title={<span className="topology-title">Service Topology</span>}
                        extra={<Fullscreen target={topologyRef} size={"small"} type={"dashed"}/>}
                        style={{height: "100%"}}
                        styles={{body: {height: "65vh", padding: '16px'}}}
                    >
                        <Topology/>
                    </Card>
                </Col>
            </Row>

            <style>{`
                .dashboard-container {
                    animation: fadeInUp 0.6s ease-out;
                }
                .dashboard-title {
                    margin-bottom: 32px;
                    font-size: 28px;
                    font-weight: 600;
                    color: #262626;
                    letter-spacing: -0.5px;
                }
                .stat-card {
                    border-radius: 16px !important;
                    border: none !important;
                    position: relative;
                    overflow: hidden;
                    transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1);
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12);
                }
                .stat-card::before {
                    content: "";
                    position: absolute;
                    inset: 0;
                    border-radius: 16px;
                    padding: 1px;
                    background: linear-gradient(135deg, rgba(255,255,255,0.4), rgba(255,255,255,0.1));
                    -webkit-mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
                    -webkit-mask-composite: xor;
                    mask-composite: exclude;
                    pointer-events: none;
                }
                .stat-card:hover {
                    transform: translateY(-4px) scale(1.02);
                    box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2) !important;
                }
                .stat-card .ant-card-body {
                    position: relative;
                    z-index: 1;
                }
                .stat-card-content {
                    display: flex;
                    align-items: flex-start;
                    justify-content: space-between;
                }
                .stat-info {
                    flex: 1;
                }
                .stat-title {
                    color: rgba(255,255,255,0.8);
                    font-size: 14px;
                    font-weight: 500;
                    margin-bottom: 8px;
                }
                .stat-value {
                    color: #fff;
                    font-size: 36px;
                    font-weight: 700;
                    line-height: 1;
                    text-shadow: 0 2px 8px rgba(0,0,0,0.15);
                }
                .stat-suffix {
                    font-size: 16px;
                    font-weight: 400;
                    opacity: 0.8;
                    margin-left: 4px;
                }
                .stat-icon {
                    width: 48px;
                    height: 48px;
                    border-radius: 12px;
                    background: rgba(255,255,255,0.2);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 24px;
                    color: #fff;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.15);
                }
                .stat-glow {
                    position: absolute;
                    top: -50px;
                    right: -50px;
                    width: 150px;
                    height: 150px;
                    border-radius: 50%;
                    background: rgba(255,255,255,0.1);
                    filter: blur(40px);
                    pointer-events: none;
                }
                .topology-card {
                    border-radius: 16px !important;
                    border: none !important;
                    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
                }
                .topology-title {
                    font-size: 18px;
                    font-weight: 600;
                    background: linear-gradient(135deg, #667eea, #764ba2);
                    -webkit-background-clip: text;
                    -webkit-text-fill-color: transparent;
                }
                @keyframes fadeInUp {
                    from {
                        opacity: 0;
                        transform: translateY(20px);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0);
                    }
                }
            `}</style>
        </div>
    );
}
