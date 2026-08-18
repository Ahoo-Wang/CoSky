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

import {useEffect} from 'react';
import {Plus, Trash2} from 'lucide-react';
import '../../monacoConfig';
import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {serviceApiClient} from "../../services/clients.ts";
import type {ServiceStat} from "../../generated";
import {ServiceInstanceTable} from "./ServiceInstanceTable.tsx";
import {AddServiceForm} from "./AddServiceForm.tsx";
import {ServiceInstanceEditor} from "./ServiceInstanceEditor.tsx";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {PageHeader} from "../../components/layout/PageHeader.tsx";
import {DataTableWrapper} from "../../components/layout/DataTableWrapper.tsx";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {Badge} from '@/components/ui/badge';

export function ServicePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {result: services = [], loading, error, execute: loadServices} = useQuery<string, ServiceStat[]>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return serviceApiClient.getServiceStats(namespace, {abortController});
        },
    });

    useEffect(() => {
        const interval = window.setInterval(loadServices, 30_000);
        return () => window.clearInterval(interval);
    }, [loadServices]);

    const {openDrawer, closeDrawer} = useDrawer();

    const handleDeleteService = async (serviceId: string) => {
        try {
            await serviceApiClient.removeService(currentNamespace, serviceId);
            toast.success('Service deleted successfully');
            loadServices();
        } catch {
            toast.error('Failed to delete service');
        }
    };

    const handleAddInstance = (serviceId: string) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={currentNamespace}
                serviceId={serviceId}
                onSuccess={() => {
                    closeDrawer();
                    // ponytail: let the Redis consistency event settle before refreshing the count.
                    window.setTimeout(loadServices, 250);
                }}
                onCancel={closeDrawer}
            />,
            {
                title: `Add [${serviceId}] Instance`,
            }
        );
    };


    const expandedRowRender = (record: ServiceStat) => {
        return (
            <ServiceInstanceTable namespace={currentNamespace} serviceId={record.serviceId}/>
        );
    };

    const columns: DataTableColumn<ServiceStat>[] = [
        {
            header: 'Service ID',
            key: 'serviceId',
            sort: (left, right) => left.serviceId.localeCompare(right.serviceId),
            cell: record => (
                <>
                    <span>{record.serviceId}</span>
                    <span className="ml-2 text-xs text-muted-foreground sm:hidden">
                        {record.instanceCount} instance{record.instanceCount === 1 ? '' : 's'}
                    </span>
                </>
            ),
        },
        {
            header: 'Instance Count',
            accessor: 'instanceCount',
            key: 'instanceCount',
            className: 'max-sm:hidden',
            sort: (left, right) => left.instanceCount - right.instanceCount,
        },
        {
            header: 'Registration',
            key: 'registration',
            cell: record => <Badge variant={record.instanceCount > 0 ? 'secondary' : 'destructive'}>
                {record.instanceCount > 0 ? 'Registered' : 'No instances'}
            </Badge>,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-56 text-right max-sm:sticky max-sm:right-0 max-sm:z-10 max-sm:w-28 max-sm:bg-card max-sm:shadow-[-8px_0_8px_-8px_rgba(0,0,0,0.25)]',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button variant="outline" size="sm" className="max-sm:size-10 max-sm:px-0" onClick={() => handleAddInstance(record.serviceId)} aria-label="Add instance">
                        <Plus/> <span className="hidden sm:inline">Add instance</span>
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this service?"
                        description={`Service “${record.serviceId}” has ${record.instanceCount} registered instance${record.instanceCount === 1 ? '' : 's'}. Removing it may interrupt discovery for dependents.`}
                        onConfirm={() => handleDeleteService(record.serviceId)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive max-sm:size-10 max-sm:px-0"
                        aria-label="Delete service"
                    >
                        <Trash2/> <span className="hidden sm:inline">Delete</span>
                    </ConfirmButton>
                </div>
            ),
        },
    ];

    return (
        <div>
            <PageHeader
                title="Service"
                description={`Register services and inspect instances in ${currentNamespace}.`}
                actions={<AddServiceForm namespace={currentNamespace} onSuccess={loadServices}/>}
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={services}
                    loading={loading}
                    error={error}
                    onRetry={loadServices}
                    getRowKey={record => record.serviceId}
                    expandable={{
                        render: expandedRowRender,
                    }}
                    search={{placeholder: 'Search services...', getValue: record => record.serviceId}}
                    emptyMessage="No services registered in this namespace yet."
                />
            </DataTableWrapper>
        </div>
    );
}
