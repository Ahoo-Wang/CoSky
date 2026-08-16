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
import {Plus, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn, createSearchColumn} from '@/components/table/columns';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useDrawer} from '@/contexts/DrawerContext';
import {useDashboardCommand} from '@/lib/commands';
import {serviceApiClient} from '@/services/clients';
import type {ServiceStat} from '@/generated';
import {AddServiceForm} from './AddServiceForm';
import {ServiceInstanceEditor} from './ServiceInstanceEditor';
import {ServiceInstanceTable} from './ServiceInstanceTable';

export function ServicePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {result: services = [], loading, error, execute: loadServices} = useQuery<string, ServiceStat[]>({
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
                onSuccess={closeDrawer}
                onCancel={closeDrawer}
            />,
            {
                title: `Add [${serviceId}] Instance`,
            }
        );
    };

    const handleServiceAdded = () => {
        closeDrawer();
        loadServices();
    };

    const handleAddService = () => {
        openDrawer(
            <AddServiceForm namespace={currentNamespace} onSuccess={handleServiceAdded}/>,
            {
                title: 'Add Service',
            }
        );
    };

    useDashboardCommand('add-service', () => handleAddService());

    const columns: ColumnDef<ServiceStat>[] = [
        createSearchColumn<ServiceStat>({
            title: 'Service ID',
            accessorKey: 'serviceId',
            placeholder: 'Search Service ID',
        }),
        {
            accessorKey: 'instanceCount',
            header: () => <span>Instance Count</span>,
        },
        createActionColumn<ServiceStat>({
            items: [
                {
                    key: 'addInstance',
                    label: 'Add instance',
                    icon: <Plus className="mr-1 h-4 w-4"/>,
                    onClick: (record) => handleAddInstance(record.serviceId),
                },
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this service?',
                    onClick: (record) => void handleDeleteService(record.serviceId),
                },
            ],
        }),
    ];

    return (
        <div>
            <PageHeader
                title="Service"
                actions={
                    <Button onClick={handleAddService}>
                        <Plus className="mr-1 h-4 w-4"/>
                        Add Service
                    </Button>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={services}
                    loading={loading}
                    error={error}
                    onRetry={() => void loadServices()}
                    getRowId={(row) => row.serviceId}
                    renderExpanded={(row) => (
                        <ServiceInstanceTable namespace={currentNamespace} serviceId={row.serviceId}/>
                    )}
                />
            </DataTableWrapper>
        </div>
    );
}
