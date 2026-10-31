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

import {Pencil, Plus, Trash2} from 'lucide-react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {useDrawer} from '@/contexts/DrawerContext';
import {roleBindings, roles} from '@/services/resources';
import type {RoleBindingResource, RoleResource} from '@/services/resources';
import {RoleBindingEditor} from '@/pages/binding/RoleBindingEditor';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {PageHeader} from '@/components/layout/PageHeader';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {runAction} from '@/lib/actions';
import {enumLabel} from '@/lib/utils';

export function RoleBindingPage() {
    const {openDrawer, closeDrawer} = useDrawer();
    const {result, loading, error, execute: loadBindings} = useQuery<null, RoleBindingResource[]>({
        initialQuery: null,
        execute: (_, __, abortController) => roleBindings.list(abortController),
    });
    const {result: availableRoles = []} = useQuery<null, RoleResource[]>({
        initialQuery: null,
        execute: (_, __, abortController) => roles.list(abortController),
    });

    const handleEdit = (binding?: RoleBindingResource) => {
        openDrawer(
            <RoleBindingEditor
                existing={binding}
                roleNames={availableRoles.map(role => role.name)}
                onSuccess={() => {
                    closeDrawer();
                    loadBindings();
                }}
                onCancel={closeDrawer}
            />,
            {title: binding ? `Edit ${binding.name}` : 'Add Role Binding', width: 'min(720px, 92vw)'},
        );
    };

    const handleDelete = async (name: string) => {
        const deleted = await runAction(
            () => roleBindings.delete(name),
            {success: 'Role binding deleted', failure: 'Failed to delete role binding'},
        );
        if (deleted) {
            loadBindings();
        }
    };

    const columns: DataTableColumn<RoleBindingResource>[] = [
        {
            header: 'Name',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {header: 'Role', accessor: 'role', key: 'role'},
        {
            header: 'Subjects',
            key: 'subjects',
            cell: binding => (
                <div className="flex flex-wrap gap-1">
                    {binding.subjects.map(subject => (
                        <Badge key={`${subject.type}/${subject.name}`} variant="outline">
                            {enumLabel(subject.type, 'TYPE_')}: {subject.name}
                        </Badge>
                    ))}
                </div>
            ),
        },
        {header: 'Namespaces', key: 'namespaces', cell: binding => binding.namespaces.join(', ')},
        {
            header: 'Action',
            key: 'action',
            className: 'w-48 text-right',
            cell: binding => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(binding)}><Pencil/> Edit</Button>
                    <ConfirmButton
                        title="Are you sure to delete this role binding?"
                        description={`Its subjects lose what role “${binding.role}” gave them through “${binding.name}”.`}
                        onConfirm={() => handleDelete(binding.name)}
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
                title="Role Binding"
                description="Gives a role to people, groups and service accounts, in the namespaces it names."
                actions={<Button onClick={() => handleEdit()}><Plus/> Add Role Binding</Button>}
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result}
                    loading={loading}
                    error={error}
                    onRetry={loadBindings}
                    getRowKey={binding => binding.name}
                    search={{placeholder: 'Search role bindings...', getValue: binding => binding.name}}
                    emptyMessage="No role bindings yet."
                />
            </DataTableWrapper>
        </div>
    );
}
