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

import {useRef, useState} from 'react';
import {FileText, HeartPulse, Network, Server, type LucideIcon} from 'lucide-react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {Card, CardAction, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Input} from '@/components/ui/input';
import {PageHeader} from '@/components/layout/PageHeader';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import type {GetStatResponse} from '@/generated';
import {statApiClient} from '@/services/clients';
import {Topology} from '@/components/topology/Topology';
import {FullscreenButton} from '@/components/topology/FullscreenButton';
import {useCountUp} from '@/hooks/useCountUp';

/* === Card Data === */
interface StatCard {
    title: string;
    value: number;
    suffix?: string;
    icon: LucideIcon;
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

    const namespaces = useCountUp(stat.namespaces);
    const instances = useCountUp(stat.instances);
    const configs = useCountUp(stat.configs);
    const services = useCountUp(stat.services.health);

    const [searchTerm, setSearchTerm] = useState('');
    const topologyRef = useRef<HTMLDivElement>(null);

    const statCards: StatCard[] = [
        {title: 'Namespace Count', value: namespaces, icon: Network},
        {title: 'Instance Count', value: instances, icon: Server},
        {title: 'Config Count', value: configs, icon: FileText},
        {
            title: 'Service Health',
            value: services,
            suffix: `/ ${stat.services.total}`,
            icon: HeartPulse,
        },
    ];

    return (
        <div>
            <PageHeader title="Dashboard"/>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
                {statCards.map((card) => {
                    const StatIcon = card.icon;
                    return (
                        <Card key={card.title} className="transition-shadow hover:shadow-md">
                            <CardContent className="flex items-center justify-between">
                                <div className="space-y-1">
                                    <p className="text-sm font-medium text-muted-foreground">{card.title}</p>
                                    <p className="text-3xl font-bold tabular-nums">
                                        {card.value.toLocaleString()}
                                        {card.suffix && (
                                            <span className="ml-1 text-base font-medium text-muted-foreground">
                                                {card.suffix}
                                            </span>
                                        )}
                                    </p>
                                </div>
                                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                                    <StatIcon className="h-5 w-5"/>
                                </div>
                            </CardContent>
                        </Card>
                    );
                })}
            </div>
            <Card ref={topologyRef} className="mt-4">
                <CardHeader>
                    <CardTitle>Service Topology</CardTitle>
                    <CardAction className="flex items-center gap-2">
                        <Input
                            placeholder="Search nodes..."
                            value={searchTerm}
                            onChange={(event) => setSearchTerm(event.target.value)}
                            className="w-48"
                        />
                        <FullscreenButton targetRef={topologyRef}/>
                    </CardAction>
                </CardHeader>
                <CardContent className="h-[65vh]">
                    <Topology searchTerm={searchTerm}/>
                </CardContent>
            </Card>
        </div>
    );
}
