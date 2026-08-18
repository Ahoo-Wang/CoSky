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

import {useEffect, useRef, useState} from 'react';
import {Link} from 'react-router-dom';
import {Activity, Box, CircleCheck, FileSliders, Layers3, Maximize2, Minimize2, TriangleAlert} from 'lucide-react';
import dayjs from 'dayjs';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import type {GetStatResponse, QueryLogResponse} from '../../generated';
import {auditLogApiClient, statApiClient} from '../../services/clients.ts';
import {Topology} from '../../components/topology/Topology.tsx';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {Card, CardAction, CardContent, CardHeader, CardTitle} from '@/components/ui/card';
import {Skeleton} from '@/components/ui/skeleton';

export function DashboardPage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const topologyDialogRef = useRef<HTMLDialogElement>(null);
    const [topologyDialogOpen, setTopologyDialogOpen] = useState(false);
    const [lastUpdated, setLastUpdated] = useState<{namespace: string; at: Date}>();
    const {result: stat = {
        namespaces: 0,
        configs: 0,
        services: {total: 0, health: 0},
        instances: 0,
    }, error: statError, execute: loadStat} = useQuery<string, GetStatResponse>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => statApiClient.getStat(namespace, {abortController}),
        onSuccess: () => setLastUpdated({namespace: currentNamespace, at: new Date()}),
    });
    const {result: recentChanges, loading: loadingChanges, error: changesError, execute: loadRecentChanges} = useQuery<null, QueryLogResponse>({
        initialQuery: null,
        execute: (_, __, abortController) => auditLogApiClient.queryLog(0, 6, {abortController}),
    });

    useEffect(() => {
        const interval = window.setInterval(() => {
            loadStat();
            loadRecentChanges();
        }, 30_000);
        return () => window.clearInterval(interval);
    }, [loadRecentChanges, loadStat]);

    const metrics = [
        {
            label: 'Healthy Services',
            value: stat.services.health,
            detail: `/ ${stat.services.total}`,
            icon: Activity,
            tone: 'bg-emerald-50 text-emerald-600',
        },
        {label: 'Instances', value: stat.instances, detail: '', icon: Box, tone: 'bg-violet-50 text-violet-600'},
        {label: 'Configurations', value: stat.configs, detail: '', icon: FileSliders, tone: 'bg-amber-50 text-amber-600'},
        {label: 'Namespaces', value: stat.namespaces, detail: '', icon: Layers3, tone: 'bg-blue-50 text-blue-600'},
    ];

    return (
        <div className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
                    <p className="mt-1 text-sm text-muted-foreground">System health and service relationships in {currentNamespace}.</p>
                </div>
                <span className="text-xs text-muted-foreground">
                    {statError
                        ? 'Update failed — retrying automatically'
                        : lastUpdated?.namespace === currentNamespace
                            ? `Updated at ${dayjs(lastUpdated.at).format('HH:mm:ss')}`
                            : 'Updating…'}
                </span>
            </div>

            <Card className="py-0">
                <CardContent className="grid grid-cols-2 p-0 xl:grid-cols-4">
                    {metrics.map(({label, value, detail, icon: Icon, tone}) => (
                        <div key={label} className="flex min-h-24 items-center gap-3 border-b p-4 odd:border-r [&:nth-last-child(-n+2)]:border-b-0 xl:min-h-28 xl:border-r xl:border-b-0 xl:p-6 xl:last:border-r-0">
                            <div className={`grid size-10 flex-none place-items-center rounded-full xl:size-14 ${tone}`}>
                                <Icon className="size-5 xl:size-6"/>
                            </div>
                            <div>
                                <p className="text-sm text-muted-foreground">{label}</p>
                                <p className="mt-1 text-2xl font-semibold tracking-tight xl:text-3xl">
                                    {value.toLocaleString()}
                                    {detail && <span className="ml-1 text-sm font-normal text-muted-foreground">{detail}</span>}
                                </p>
                            </div>
                        </div>
                    ))}
                </CardContent>
            </Card>

            <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_340px]">
                <Card className="min-w-0">
                    <CardHeader className="border-b">
                        <CardTitle>Service Topology</CardTitle>
                        <CardAction>
                            <Button variant="outline" size="icon-sm"
                                    onClick={() => {
                                        topologyDialogRef.current?.showModal();
                                        setTopologyDialogOpen(true);
                                    }}
                                    title="Open fullscreen"
                                    aria-label="Open topology fullscreen">
                                <Maximize2/>
                            </Button>
                        </CardAction>
                    </CardHeader>
                    <CardContent className="h-[360px] px-3 sm:h-[520px] xl:h-[660px]">
                        <Topology/>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader className="border-b">
                        <CardTitle>Recent Changes</CardTitle>
                        <CardAction>
                            <Button asChild variant="link" size="sm"><Link to="/audit-log">View all</Link></Button>
                        </CardAction>
                    </CardHeader>
                    <CardContent className="divide-y px-0">
                        {loadingChanges && Array.from({length: 5}).map((_, index) => (
                            <div key={index} className="flex gap-3 px-4 py-4"><Skeleton className="size-8 rounded-full"/><Skeleton className="h-10 flex-1"/></div>
                        ))}
                        {!loadingChanges && changesError && (
                            <div role="alert" className="px-5 py-12 text-center text-sm text-destructive">Could not load recent changes. Retrying automatically.</div>
                        )}
                        {!loadingChanges && !changesError && (recentChanges?.list.length ?? 0) === 0 && (
                            <div className="px-5 py-16 text-center text-sm text-muted-foreground">No recent changes.</div>
                        )}
                        {recentChanges?.list.map((change, index) => {
                            const successful = change.status < 400;
                            const StatusIcon = successful ? CircleCheck : TriangleAlert;
                            return (
                                <div key={`${change.operator}-${change.opTime}-${index}`} className="flex min-h-[68px] gap-3 px-4 py-4">
                                    <span className={`grid size-8 flex-none place-items-center rounded-full ${successful ? 'bg-emerald-50 text-emerald-600' : 'bg-amber-50 text-amber-600'}`}>
                                        <StatusIcon className="size-4"/>
                                    </span>
                                    <div className="min-w-0 flex-1">
                                        <p className="truncate text-sm font-medium">{change.resource}</p>
                                        <div className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                                            <Badge variant="secondary">{change.action}</Badge>
                                            <span>{change.operator}</span>
                                        </div>
                                    </div>
                                    <time className="flex-none text-xs text-muted-foreground">{dayjs(change.opTime).format('HH:mm')}</time>
                                </div>
                            );
                        })}
                    </CardContent>
                </Card>
            </div>

            <dialog ref={topologyDialogRef}
                    aria-labelledby="topology-dialog-title"
                    onClose={() => setTopologyDialogOpen(false)}
                    className="fixed inset-4 m-0 h-[calc(100vh-2rem)] w-[calc(100vw-2rem)] max-w-none rounded-xl border bg-background p-0 text-foreground shadow-2xl backdrop:bg-black/35">
                <div className="flex h-full flex-col">
                    <div className="flex items-center justify-between border-b px-5 py-4">
                        <h2 id="topology-dialog-title" className="text-base font-semibold">Service Topology</h2>
                        <form method="dialog">
                            <Button variant="outline" size="icon-sm" aria-label="Close topology fullscreen" title="Close fullscreen">
                                <Minimize2/>
                            </Button>
                        </form>
                    </div>
                    <div className="min-h-0 flex-1 p-3">
                        {topologyDialogOpen && <Topology/>}
                    </div>
                </div>
            </dialog>
        </div>
    );
}
