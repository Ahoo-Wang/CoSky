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

import {useState} from 'react';
import {Plus} from 'lucide-react';
import '@/monacoConfig';
import {useQuery} from '@ahoo-wang/fetcher-react';
import type {ServiceSummary} from '@/generated';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useDrawer} from '@/contexts/DrawerContext';
import {useWatchRefresh} from '@/hooks/useWatchRefresh';
import {viewsClient} from '@/services/clients';
import {Kind} from '@/services/resources';
import {ServiceInstanceEditor} from '@/pages/service/ServiceInstanceEditor';
import {ServiceInstanceTable} from '@/pages/service/ServiceInstanceTable';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {PageHeader} from '@/components/layout/PageHeader';
import {Button} from '@/components/ui/button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';

export function ServicePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {openDrawer, closeDrawer} = useDrawer();
    // One stream per page, whatever is expanded: browsers allow few connections per server over HTTP/1.1.
    const [changes, setChanges] = useState(0);
    const {result, loading, error, execute: loadServices} = useQuery<string, ServiceSummary[]>({
        query: currentNamespace,
        execute: async (namespace, _, abortController) => {
            const view = await viewsClient.viewServices({urlParams: {path: {namespace}}, abortController});
            return view.services ?? [];
        },
    });
    useWatchRefresh(currentNamespace, Kind.KIND_INSTANCE, () => {
        loadServices();
        setChanges(count => count + 1);
    });

    const handleAddInstance = (service?: string) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={currentNamespace}
                service={service}
                onSuccess={() => {
                    closeDrawer();
                    loadServices();
                    setChanges(count => count + 1);
                }}
                onCancel={closeDrawer}
            />,
            {title: service ? `Add an instance of ${service}` : 'Add Instance'},
        );
    };

    const columns: DataTableColumn<ServiceSummary>[] = [
        {
            header: 'Service',
            accessor: 'service',
            key: 'service',
            sort: (left, right) => left.service.localeCompare(right.service),
        },
        {
            header: 'Instances',
            key: 'instanceCount',
            sort: (left, right) => (left.instanceCount ?? 0) - (right.instanceCount ?? 0),
            cell: summary => summary.instanceCount ?? 0,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-44 text-right',
            cell: summary => (
                <Button variant="outline" size="sm" onClick={() => handleAddInstance(summary.service)}>
                    <Plus/> Add instance
                </Button>
            ),
        },
    ];

    return (
        <div>
            <PageHeader
                title="Service"
                description={`Inspect services and their instances in ${currentNamespace}.`}
                actions={<Button onClick={() => handleAddInstance()}><Plus/> Add Instance</Button>}
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result}
                    loading={loading}
                    error={error}
                    onRetry={loadServices}
                    getRowKey={summary => summary.service}
                    expandable={{
                        render: summary => (
                            <ServiceInstanceTable
                                namespace={currentNamespace}
                                service={summary.service}
                                changes={changes}
                            />
                        ),
                    }}
                    search={{placeholder: 'Search services...', getValue: summary => summary.service}}
                    emptyMessage="No services registered in this namespace yet."
                />
            </DataTableWrapper>
        </div>
    );
}
