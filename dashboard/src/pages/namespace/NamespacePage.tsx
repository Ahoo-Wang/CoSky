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

import {Plug, Trash2} from 'lucide-react';
import {Kind} from '@/generated';
import {useDrawer} from '@/contexts/DrawerContext';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';
import {useWatchRefresh} from '@/hooks/useWatchRefresh';
import {namespaces as namespacesApi, SYSTEM_NAMESPACE} from '@/services/resources';
import type {NamespaceResource} from '@/services/resources';
import {AddNamespaceForm} from '@/pages/namespace/AddNamespaceForm';
import {ConnectApplication} from '@/pages/namespace/ConnectApplication';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {PageHeader} from '@/components/layout/PageHeader';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {runAction} from '@/lib/actions';

export function NamespacePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {namespaces, loading, error, refresh} = useNamespacesContext();
    const {openDrawer} = useDrawer();
    useWatchRefresh(SYSTEM_NAMESPACE, Kind.KIND_NAMESPACE, refresh);

    const handleDelete = async (namespace: NamespaceResource) => {
        await runAction(
            () => namespacesApi.delete(namespace.name, namespace.revision),
            {success: 'Namespace deleted', failure: 'Failed to delete namespace'},
        );
        // Deleted, or refused because it changed since it was read: either way, the page shows what is there now.
        refresh();
    };

    const columns: DataTableColumn<NamespaceResource>[] = [
        {
            header: 'Namespace',
            key: 'name',
            cell: namespace => <span className="font-medium">{namespace.name}</span>,
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {header: 'Description', accessor: 'description', key: 'description'},
        {
            header: 'Action',
            key: 'action',
            className: 'w-56 text-right',
            cell: namespace => (
                <>
                    <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => openDrawer(
                            close => <ConnectApplication namespace={namespace.name} onClose={close}/>,
                            {title: `Connect an application to ${namespace.name}`},
                        )}
                    >
                        <Plug/> Connect
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this namespace?"
                        description={`Namespace “${namespace.name}” will be deleted. Only an empty namespace can be.`}
                        onConfirm={() => handleDelete(namespace)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                        disabled={namespace.name === currentNamespace}
                    >
                        <Trash2/> Delete
                    </ConfirmButton>
                </>
            ),
        },
    ];

    return (
        <div>
            <PageHeader
                title="Namespace"
                description="Manage isolation boundaries for services and configurations."
                actions={<AddNamespaceForm onSuccess={refresh}/>}
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={namespaces}
                    loading={loading}
                    error={error}
                    onRetry={refresh}
                    getRowKey={namespace => namespace.name}
                    search={{placeholder: 'Search namespaces...', getValue: namespace => namespace.name}}
                    emptyMessage="No namespaces found."
                />
            </DataTableWrapper>
        </div>
    );
}
