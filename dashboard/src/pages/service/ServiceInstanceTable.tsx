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

export interface ServiceInstanceTableProps {
    namespace: string
    serviceId: string
}

export function ServiceInstanceTable({namespace, serviceId}: ServiceInstanceTableProps) {
    const {result: instances = [], loading: loadingInstances, execute: loadInstances} = useQuery({
        query: serviceId,
        execute: (query, _, abortController) => {
            return serviceApiClient.getInstances(namespace, query, {abortController});
        }
    })
    const {loading: loadingExecutePromise, execute} = useExecutePromise({
        onSuccess: async () => {
            toast.success('Delete instance success!');
            await loadInstances();
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
                    loadInstances();
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
            className: 'w-40 text-right',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEditInstance(record)}>
                        <Pencil/> Edit
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this instance?"
                        description={`Instance “${record.instanceId}” will be deregistered.`}
                        onConfirm={() => handleDeleteInstance(record.serviceId, record.instanceId)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                        loading={loadingExecutePromise}
                    >
                        <Trash2/> Delete
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
            getRowKey={record => record.instanceId}
            pagination={false}
        />
    )
}
