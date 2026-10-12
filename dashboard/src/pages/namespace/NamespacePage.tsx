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
import {isSystemNamespace} from "@/pages/namespace/namespaces";
import {namespaceApiClient} from "@/services/clients";
import {AddNamespaceForm} from "@/pages/namespace/AddNamespaceForm";
import { useCurrentNamespaceContext} from "@/contexts/namespace/CurrentNamespaceContext";
import {useNamespacesContext} from "@/contexts/namespace/NamespacesContext";
import {PageHeader} from "@/components/layout/PageHeader";
import {DataTableWrapper} from "@/components/layout/DataTableWrapper";
import {ConfirmButton} from "@/components/ui/confirm-button";
import {DataTable} from "@/components/ui/data-table";
import type {DataTableColumn} from "@/components/ui/data-table";
import {runAction} from '@/lib/actions';

export function NamespacePage() {
    const {currentNamespace} = useCurrentNamespaceContext()
    const {namespaces, loading, error, refresh} = useNamespacesContext();

    const handleDelete = async (namespace: string) => {
        if (await runAction(() => namespaceApiClient.removeNamespace(namespace), {success: 'Namespace deleted successfully', failure: 'Failed to delete namespace'})) {
            refresh();
        }
    };

    const isDisabled = (namespace: string) => {
        return isSystemNamespace(namespace) || currentNamespace === namespace;
    };

    const columns: DataTableColumn<string>[] = [
        {
            header: 'Namespace',
            key: 'namespace',
            cell: namespace => <span className="font-medium">{namespace}</span>,
            sort: (left, right) => left.localeCompare(right),
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-36 text-right',
            cell: record => (
                <ConfirmButton
                    title="Are you sure to delete this namespace?"
                    description={`Namespace “${record}” will be removed from the selector. Namespaced configuration and service data are not automatically deleted.`}
                    onConfirm={() => handleDelete(record)}
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    disabled={isDisabled(record)}
                >
                    <Trash2/> Delete
                </ConfirmButton>
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
                    getRowKey={(record) => record}
                    search={{placeholder: 'Search namespaces...', getValue: value => value}}
                    emptyMessage="No namespaces found."
                />
            </DataTableWrapper>
        </div>
    );
};
