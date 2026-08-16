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

import {Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {isSystemNamespace} from './namespaces';
import {namespaceApiClient} from '@/services/clients';
import {AddNamespaceForm} from './AddNamespaceForm';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn} from '@/components/table/columns';

export function NamespacePage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {namespaces, loading, refresh} = useNamespacesContext();

    const handleDelete = async (namespace: string) => {
        try {
            await namespaceApiClient.removeNamespace(namespace);
            toast.success('Namespace deleted successfully');
            refresh();
        } catch {
            toast.error('Failed to delete namespace');
        }
    };

    const isDisabled = (namespace: string) =>
        isSystemNamespace(namespace) || currentNamespace === namespace;

    const data = namespaces.map((ns) => ({name: ns}));

    const columns = [
        {
            accessorKey: 'name',
            header: () => <span>Namespace</span>,
        },
        createActionColumn<{ name: string }>({
            items: [
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this namespace?',
                    disabled: (record) => isDisabled(record.name),
                    onClick: (record) => void handleDelete(record.name),
                },
            ],
        }),
    ];

    return (
        <div>
            <PageHeader title="Namespace" actions={<AddNamespaceForm onSuccess={refresh}/>}/>
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={data}
                    loading={loading}
                    getRowId={(row) => row.name}
                />
            </DataTableWrapper>
        </div>
    );
}
