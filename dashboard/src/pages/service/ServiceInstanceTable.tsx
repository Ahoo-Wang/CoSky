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

import type {ColumnDef} from '@tanstack/react-table';
import dayjs from 'dayjs';
import {Pencil, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {Badge} from '@/components/ui/badge';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn} from '@/components/table/columns';
import {useDrawer} from '@/contexts/DrawerContext';
import {serviceApiClient} from '@/services/clients';
import type {ServiceInstance} from '@/generated';
import {ServiceInstanceEditor} from './ServiceInstanceEditor';

export interface ServiceInstanceTableProps {
    namespace: string;
    serviceId: string;
}

export function ServiceInstanceTable({namespace, serviceId}: ServiceInstanceTableProps) {
    const {result: instances = [], loading, execute: loadInstances} = useQuery<string, ServiceInstance[]>({
        query: serviceId,
        execute: (query, _, abortController) => {
            return serviceApiClient.getInstances(namespace, query, {abortController});
        },
    });
    const {openDrawer, closeDrawer} = useDrawer();

    const handleEditInstance = (serviceInstance: ServiceInstance) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={namespace}
                serviceId={serviceId}
                initialValues={serviceInstance}
                onSuccess={closeDrawer}
                onCancel={closeDrawer}
            />,
            {
                title: 'Edit Instance',
            }
        );
    };

    const handleDeleteInstance = async (serviceInstance: ServiceInstance) => {
        try {
            await serviceApiClient.deregister(namespace, serviceInstance.serviceId, serviceInstance.instanceId);
            toast.success('Delete instance success!');
            loadInstances();
        } catch {
            toast.error('Delete instance failed!');
        }
    };

    const columns: ColumnDef<ServiceInstance>[] = [
        {
            accessorKey: 'schema',
            enableSorting: false,
            header: () => <span>Schema</span>,
        },
        {
            accessorKey: 'host',
            header: () => <span>Host</span>,
        },
        {
            accessorKey: 'port',
            enableSorting: false,
            header: () => <span>Port</span>,
        },
        {
            accessorKey: 'weight',
            header: () => <span>Weight</span>,
        },
        {
            accessorKey: 'isEphemeral',
            enableSorting: false,
            header: () => <span>Ephemeral</span>,
            cell: ({row}) => (
                <Badge variant={row.original.isEphemeral ? 'default' : 'secondary'}>
                    {row.original.isEphemeral ? 'true' : 'false'}
                </Badge>
            ),
        },
        {
            accessorKey: 'ttlAt',
            header: () => <span>TtlAt</span>,
            cell: ({row}) => dayjs(row.original.ttlAt * 1000).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            accessorKey: 'metadata',
            enableSorting: false,
            header: () => <span>Metadata</span>,
            cell: ({row}) => JSON.stringify(row.original.metadata),
        },
        createActionColumn<ServiceInstance>({
            items: [
                {
                    key: 'edit',
                    label: 'Edit',
                    icon: <Pencil className="mr-1 h-4 w-4"/>,
                    onClick: (record) => handleEditInstance(record),
                },
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this instance?',
                    onClick: (record) => void handleDeleteInstance(record),
                },
            ],
        }),
    ];

    return (
        <DataTable
            columns={columns}
            data={instances}
            loading={loading}
            getRowId={(row) => row.instanceId}
            showViewOptions={false}
        />
    );
}
