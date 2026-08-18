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
import {isSystemNamespace} from "./namespaces.ts";
import {namespaceApiClient} from "../../services/clients.ts";
import {AddNamespaceForm} from "./AddNamespaceForm.tsx";
import { useCurrentNamespaceContext} from "../../contexts/namespace/CurrentNamespaceContext.tsx";
import {useNamespacesContext} from "../../contexts/namespace/NamespacesContext.tsx";
import {PageHeader} from "../../components/layout/PageHeader.tsx";
import {DataTableWrapper} from "../../components/layout/DataTableWrapper.tsx";
import {toast} from "sonner";
import {ConfirmButton} from "@/components/ui/confirm-button";
import {DataTable} from "@/components/ui/data-table";
import type {DataTableColumn} from "@/components/ui/data-table";

export function NamespacePage() {
    const {currentNamespace} = useCurrentNamespaceContext()
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
                    getRowKey={(record) => record}
                    search={{placeholder: 'Search namespaces...', getValue: value => value}}
                    emptyMessage="No namespaces found."
                />
            </DataTableWrapper>
        </div>
    );
};
