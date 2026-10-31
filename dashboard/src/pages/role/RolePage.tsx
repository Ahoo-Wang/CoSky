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
import {roles} from '@/services/resources';
import type {RoleResource} from '@/services/resources';
import {RoleEditor} from '@/pages/role/RoleEditor';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {PageHeader} from '@/components/layout/PageHeader';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {runAction} from '@/lib/actions';

export function RolePage() {
    const {openDrawer, closeDrawer} = useDrawer();
    const {result, loading, error, execute: loadRoles} = useQuery<null, RoleResource[]>({
        initialQuery: null,
        execute: (_, __, abortController) => roles.list(abortController),
    });

    const handleEdit = (role?: RoleResource) => {
        openDrawer(
            <RoleEditor
                existing={role}
                onSuccess={() => {
                    closeDrawer();
                    loadRoles();
                }}
                onCancel={closeDrawer}
            />,
            {title: role ? `Edit ${role.name}` : 'Add Role', width: 'min(880px, 92vw)'},
        );
    };

    const handleDelete = async (name: string) => {
        const deleted = await runAction(
            () => roles.delete(name),
            {success: 'Role deleted', failure: 'Failed to delete role'},
        );
        if (deleted) {
            loadRoles();
        }
    };

    const columns: DataTableColumn<RoleResource>[] = [
        {
            header: 'Name',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {header: 'Description', accessor: 'description', key: 'description'},
        {header: 'Rules', key: 'rules', cell: role => role.rules.length},
        {
            header: 'Action',
            key: 'action',
            className: 'w-48 text-right',
            cell: role => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(role)}><Pencil/> Edit</Button>
                    <ConfirmButton
                        title="Are you sure to delete this role?"
                        description={`Role “${role.name}” will be deleted. Its bindings stop granting anything.`}
                        onConfirm={() => handleDelete(role.name)}
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
                title="Role"
                description="A role says what may be done. A role binding gives it to someone."
                actions={<Button onClick={() => handleEdit()}><Plus/> Add Role</Button>}
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result}
                    loading={loading}
                    error={error}
                    onRetry={loadRoles}
                    getRowKey={role => role.name}
                    search={{placeholder: 'Search roles...', getValue: role => role.name}}
                    emptyMessage="No roles yet."
                />
            </DataTableWrapper>
        </div>
    );
}
