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
import {Plus, Trash2, Unlock} from 'lucide-react';
import {toast} from 'sonner';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn, createSearchColumn} from '@/components/table/columns';
import {AddUserEditor} from './AddUserEditor';
import {RoleMultiSelect} from './RoleMultiSelect';
import {useRoles} from '@/hooks/useRoles';
import {userApiClient} from '@/services/clients';
import type {CoSecPrincipal} from '@/generated';
import {useDrawer} from '@/contexts/DrawerContext';

export function UserPage() {
    const {result: users = [], loading, error, execute: load} = useQuery<null, CoSecPrincipal[]>({
        initialQuery: null,
        execute: (_, __, abortController) => {
            return userApiClient.query({abortController});
        },
    });
    const {roles} = useRoles();
    const roleSelectorOptions = roles.map((role) => role.name);
    const {openDrawer, closeDrawer} = useDrawer();
    const loadUsers = () => {
        load();
    };

    const handleSubmit = () => {
        closeDrawer();
        loadUsers();
    };

    const handleAdd = () => {
        openDrawer(
            <AddUserEditor
                roleSelectorOptions={roleSelectorOptions}
                onSuccess={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add User',
            }
        );
    };

    const handleChangeRole = async (username: string, roles: string[]) => {
        try {
            await userApiClient.bindRole(username, {body: roles});
            toast.success('Role bind successfully');
            loadUsers();
        } catch {
            toast.error('Failed to bind role');
        }
    };

    const handleDelete = async (username: string) => {
        try {
            await userApiClient.removeUser(username);
            toast.success('User deleted successfully');
            loadUsers();
        } catch {
            toast.error('Failed to delete user');
        }
    };

    const handleUnlock = async (username: string) => {
        try {
            await userApiClient.unlock(username);
            toast.success('User unlocked successfully');
            loadUsers();
        } catch {
            toast.error('Failed to unlock user');
        }
    };

    const columns: ColumnDef<CoSecPrincipal>[] = [
        createSearchColumn<CoSecPrincipal>({
            title: 'Username',
            accessorKey: 'name',
            placeholder: 'Search username',
        }),
        {
            accessorKey: 'roles',
            enableSorting: false,
            header: () => <span>Roles</span>,
            cell: ({row}) => (
                <div className="min-w-[220px]">
                    <RoleMultiSelect
                        value={row.original.roles}
                        options={roleSelectorOptions}
                        placeholder="Select Roles"
                        onChange={(roles) => void handleChangeRole(row.original.name, roles)}
                    />
                </div>
            ),
        },
        createActionColumn<CoSecPrincipal>({
            items: [
                {
                    key: 'unlock',
                    label: 'Unlock',
                    icon: <Unlock className="mr-1 h-4 w-4"/>,
                    confirm: 'Ary you sure to unlock this user?',
                    onClick: (record) => void handleUnlock(record.name),
                },
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this user?',
                    onClick: (record) => void handleDelete(record.name),
                },
            ],
        }),
    ];

    return (
        <div>
            <PageHeader
                title="User"
                actions={
                    <Button onClick={handleAdd}>
                        <Plus className="mr-1 h-4 w-4"/>
                        Add User
                    </Button>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={users}
                    loading={loading}
                    error={error}
                    onRetry={() => void load()}
                    getRowId={(row) => row.name}
                />
            </DataTableWrapper>
        </div>
    );
}
