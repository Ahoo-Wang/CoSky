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

export function ServicePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {result: services = [], loading, execute: loadServices} = useQuery<string, ServiceStat[]>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return serviceApiClient.getServiceStats(namespace, {abortController});
        },
    });

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
                    loadServices();
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
            accessor: 'serviceId',
            key: 'serviceId',
            sort: (left, right) => left.serviceId.localeCompare(right.serviceId),
        },
        {
            header: 'Instance Count',
            accessor: 'instanceCount',
            key: 'instanceCount',
            sort: (left, right) => left.instanceCount - right.instanceCount,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-56 text-right',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button size="sm" onClick={() => handleAddInstance(record.serviceId)}>
                        <Plus/> Add instance
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this service?"
                        description={`Service “${record.serviceId}” and its registration will be removed.`}
                        onConfirm={() => handleDeleteService(record.serviceId)}
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
                    getRowKey={record => record.serviceId}
                    expandable={{
                        render: expandedRowRender,
                    }}
                    search={{placeholder: 'Search services...', getValue: record => record.serviceId}}
                />
            </DataTableWrapper>
        </div>
    );
}
