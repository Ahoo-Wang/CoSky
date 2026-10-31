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

import {useEffect, useRef, useState} from 'react';
import {Box, Cloud, FileSliders, Layers3, Maximize2, Minimize2} from 'lucide-react';
import dayjs from 'dayjs';
import {useQuery} from '@ahoo-wang/fetcher-react';
import type {StatsResponse} from '@/generated';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';
import {viewsClient} from '@/services/clients';
import {Topology} from '@/components/topology/Topology';
import {Button} from '@/components/ui/button';
import {Card, CardAction, CardContent, CardHeader, CardTitle} from '@/components/ui/card';

const REFRESH_MS = 30_000;

export function DashboardPage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {namespaces} = useNamespacesContext();
    const topologyDialogRef = useRef<HTMLDialogElement>(null);
    const [topologyDialogOpen, setTopologyDialogOpen] = useState(false);
    const [lastUpdated, setLastUpdated] = useState<{namespace: string; at: Date}>();
    const {result: stats, error, execute: loadStats} = useQuery<string, Partial<StatsResponse>>({
        query: currentNamespace,
        execute: (namespace, _, abortController) =>
            viewsClient.viewStats({urlParams: {path: {namespace}}, abortController}),
        onSuccess: () => setLastUpdated({namespace: currentNamespace, at: new Date()}),
    });

    // Counts need no stream of their own: browsers allow few connections per server over HTTP/1.1.
    useEffect(() => {
        const interval = window.setInterval(loadStats, REFRESH_MS);
        return () => window.clearInterval(interval);
    }, [loadStats]);

    const metrics = [
        {label: 'Services', value: stats?.serviceCount ?? 0, icon: Cloud, tone: 'bg-emerald-50 text-emerald-600'},
        {label: 'Instances', value: stats?.instanceCount ?? 0, icon: Box, tone: 'bg-violet-50 text-violet-600'},
        {label: 'Configurations', value: stats?.configCount ?? 0, icon: FileSliders, tone: 'bg-amber-50 text-amber-600'},
        {label: 'Namespaces', value: namespaces.length, icon: Layers3, tone: 'bg-blue-50 text-blue-600'},
    ];

    return (
        <div className="space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h1 className="text-3xl font-semibold tracking-tight">Dashboard</h1>
                    <p className="mt-1 text-sm text-muted-foreground">Counts and service relationships in {currentNamespace}.</p>
                </div>
                <span className="text-xs text-muted-foreground">
                    {error
                        ? 'Update failed — retrying automatically'
                        : lastUpdated?.namespace === currentNamespace
                            ? `Updated at ${dayjs(lastUpdated.at).format('HH:mm:ss')}`
                            : 'Updating…'}
                </span>
            </div>

            <Card className="py-0">
                <CardContent className="grid grid-cols-2 p-0 xl:grid-cols-4">
                    {metrics.map(({label, value, icon: Icon, tone}) => (
                        <div key={label} className="flex min-h-24 items-center gap-3 border-b p-4 odd:border-r [&:nth-last-child(-n+2)]:border-b-0 xl:border-b-0 xl:border-r xl:last:border-r-0">
                            <div className={`grid size-10 flex-none place-items-center rounded-full xl:size-14 ${tone}`}>
                                <Icon className="size-5 xl:size-6"/>
                            </div>
                            <div>
                                <p className="text-sm text-muted-foreground">{label}</p>
                                <p className="mt-1 text-2xl font-semibold tracking-tight xl:text-3xl">{value.toLocaleString()}</p>
                            </div>
                        </div>
                    ))}
                </CardContent>
            </Card>

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

            <dialog ref={topologyDialogRef}
                    aria-labelledby="topology-dialog-title"
                    // Unmount the fullscreen graph as the dialog closes, not on the later, asynchronous close event,
                    // so that the page never holds two copies of the topology.
                    onCancel={() => setTopologyDialogOpen(false)}
                    onClose={() => setTopologyDialogOpen(false)}
                    className="fixed inset-4 m-0 h-[calc(100vh-2rem)] w-[calc(100vw-2rem)] max-w-none rounded-xl border bg-background p-0 backdrop:bg-black/50">
                <div className="flex h-full flex-col">
                    <div className="flex items-center justify-between border-b px-5 py-4">
                        <h2 id="topology-dialog-title" className="text-base font-semibold">Service Topology</h2>
                        <form method="dialog">
                            <Button type="submit" variant="outline" size="icon-sm" aria-label="Close topology fullscreen" title="Exit fullscreen"
                                    onClick={() => setTopologyDialogOpen(false)}>
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
