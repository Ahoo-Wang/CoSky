import {useEffect} from 'react';
import {useExecutePromise, useQuery} from "@ahoo-wang/fetcher-react";
import {serviceApiClient} from "../../services/clients.ts";
import type {ServiceInstance} from "../../generated";
import {Pencil, Trash2} from "lucide-react";
import dayjs from "dayjs";
import {ServiceInstanceEditor} from "./ServiceInstanceEditor.tsx";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {toast} from 'sonner';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {getInstanceHealth} from './serviceHealth.ts';

export interface ServiceInstanceTableProps {
    namespace: string
    serviceId: string
}

export function ServiceInstanceTable({namespace, serviceId}: ServiceInstanceTableProps) {
    const {result: instances = [], loading: loadingInstances, error, execute: loadInstances} = useQuery({
        query: `${namespace}/${serviceId}`,
        execute: (_, __, abortController) => {
            return serviceApiClient.getInstances(namespace, serviceId, {abortController});
        }
    })
    useEffect(() => {
        const interval = window.setInterval(loadInstances, 30_000);
        return () => window.clearInterval(interval);
    }, [loadInstances]);
    // ponytail: let the Redis consistency event settle before reading the updated instance.
    const refreshInstances = () => window.setTimeout(loadInstances, 250);
    const {loading: loadingExecutePromise, execute} = useExecutePromise({
        onSuccess: () => {
            toast.success('Delete instance success!');
            refreshInstances();
        },
        onError: () => {
            toast.error('Delete instance failed!');
        }
    })
    const {openDrawer, closeDrawer} = useDrawer();
    const handleEditInstance = (serviceInstance: ServiceInstance) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={namespace}
                serviceId={serviceId}
                initialValues={serviceInstance}
                onSuccess={() => {
                    closeDrawer();
                    refreshInstances();
                }}
                onCancel={closeDrawer}
            />,
            {
                title: 'Edit Instance',
            }
        );
    }
    const handleDeleteInstance = async (serviceId: string, instanceId: string) => {
        await execute(() => {
            return serviceApiClient.deregister(namespace, serviceId, instanceId)
        })
    }
    const columns: DataTableColumn<ServiceInstance>[] = [
        {
            header: 'Health',
            key: 'health',
            cell: record => {
                const health = getInstanceHealth(record);
                return <Badge variant={health === 'Healthy' ? 'secondary' : health === 'Expiring soon' ? 'outline' : 'destructive'}>{health}</Badge>;
            },
        },
        {header: 'Schema', accessor: 'schema', key: 'schema'},
        {
            header: 'Host', accessor: 'host', key: 'host',
            sort: (left, right) => left.host.localeCompare(right.host),
        },
        {header: 'Port', accessor: 'port', key: 'port'},
        {
            header: 'Weight', accessor: 'weight', key: 'weight',
            sort: (left, right) => left.weight - right.weight,
        },
        {
            header: 'Ephemeral',
            key: 'isEphemeral',
            cell: record => <Badge variant={record.isEphemeral ? 'secondary' : 'outline'}>{record.isEphemeral ? 'Yes' : 'No'}</Badge>,
        },
        {
            header: 'TTL At',
            key: 'ttlAt',
            sort: (left, right) => left.ttlAt - right.ttlAt,
            cell: record => dayjs(record.ttlAt * 1000).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            header: 'Metadata',
            key: 'metadata',
            cell: record => <code className="text-xs">{JSON.stringify(record.metadata)}</code>,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-40 text-right max-sm:sticky max-sm:right-0 max-sm:z-10 max-sm:w-28 max-sm:bg-card max-sm:shadow-[-8px_0_8px_-8px_rgba(0,0,0,0.25)]',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" className="max-sm:size-10 max-sm:px-0" onClick={() => handleEditInstance(record)} aria-label="Edit instance">
                        <Pencil/> <span className="hidden sm:inline">Edit</span>
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this instance?"
                        description={`Instance “${record.instanceId}” will be deregistered.`}
                        onConfirm={() => handleDeleteInstance(record.serviceId, record.instanceId)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive max-sm:size-10 max-sm:px-0"
                        loading={loadingExecutePromise}
                        aria-label="Delete instance"
                    >
                        <Trash2/> <span className="hidden sm:inline">Delete</span>
                    </ConfirmButton>
                </div>
            ),
        },
    ];

    return (
        <DataTable
            loading={loadingInstances}
            data={instances}
            columns={columns}
            error={error}
            onRetry={loadInstances}
            getRowKey={record => record.instanceId}
            pagination={false}
            emptyMessage="No instances registered yet."
        />
    )
}
