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

import {Pencil, Trash2} from 'lucide-react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {useDrawer} from '@/contexts/DrawerContext';
import {instances} from '@/services/resources';
import type {InstanceResource} from '@/services/resources';
import {ServiceInstanceEditor} from '@/pages/service/ServiceInstanceEditor';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {runAction} from '@/lib/actions';

const NO_LEASE = '0';

export interface ServiceInstanceTableProps {
    namespace: string;
    service: string;
    /** Reads again whenever this changes. */
    changes: number;
}

export function ServiceInstanceTable({namespace, service, changes}: ServiceInstanceTableProps) {
    const {openDrawer, closeDrawer} = useDrawer();
    const {result, loading, error, execute: loadInstances} = useQuery<string, InstanceResource[]>({
        query: `${namespace}/${service}/${changes}`,
        execute: (_, __, abortController) => instances.list(namespace, service, abortController),
    });

    const handleEdit = (instance: InstanceResource) => {
        openDrawer(
            <ServiceInstanceEditor
                namespace={namespace}
                service={service}
                existing={instance}
                onSuccess={() => {
                    closeDrawer();
                    loadInstances();
                }}
                onCancel={closeDrawer}
            />,
            {title: 'Edit Instance'},
        );
    };

    const handleDelete = async (name: string) => {
        const deleted = await runAction(
            () => instances.delete(namespace, name),
            {success: 'Instance deleted', failure: 'Failed to delete instance'},
        );
        if (deleted) {
            loadInstances();
        }
    };

    const columns: DataTableColumn<InstanceResource>[] = [
        {
            header: 'Name',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {
            header: 'Endpoint',
            key: 'endpoint',
            cell: instance => `${instance.scheme}://${instance.host}:${instance.port}`,
        },
        {
            header: 'Weight',
            accessor: 'weight',
            key: 'weight',
            sort: (left, right) => left.weight - right.weight,
        },
        {
            header: 'Lifetime',
            key: 'lease',
            cell: instance => (
                <Badge variant={instance.lease === NO_LEASE ? 'outline' : 'secondary'}>
                    {instance.lease === NO_LEASE ? 'Fixed' : 'Leased'}
                </Badge>
            ),
        },
        {
            header: 'Metadata',
            key: 'metadata',
            cell: instance => <code className="text-xs">{JSON.stringify(instance.metadata)}</code>,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-40 text-right',
            cell: instance => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(instance)}><Pencil/> Edit</Button>
                    <ConfirmButton
                        title="Are you sure to delete this instance?"
                        description={`Instance “${instance.name}” will be removed. If its application is still running, it registers again.`}
                        onConfirm={() => handleDelete(instance.name)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                    >
                        <Trash2/> Delete
                    </ConfirmButton>
                </div>
            ),
        },
    ];

    return (
        <DataTable
            loading={loading}
            data={result}
            columns={columns}
            error={error}
            onRetry={loadInstances}
            getRowKey={instance => instance.name}
            pagination={false}
            emptyMessage="No instances registered yet."
        />
    );
}
