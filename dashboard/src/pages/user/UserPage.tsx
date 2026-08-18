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

import {Plus, Trash2, Unlock} from 'lucide-react';
import {useQuery, useSecurityContext} from '@ahoo-wang/fetcher-react';
import {AddUserEditor} from './AddUserEditor.tsx';
import {useRoles} from "../../hooks/useRoles.ts";
import {userApiClient} from "../../services/clients.ts";
import type {CoSecPrincipal} from "../../generated";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {PageHeader} from '../../components/layout/PageHeader.tsx';
import {DataTableWrapper} from '../../components/layout/DataTableWrapper.tsx';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {MultiSelect} from '@/components/ui/multi-select';
import {Badge} from '@/components/ui/badge';

export function UserPage() {
    const {currentUser} = useSecurityContext();
    const {result: users = [], loading, execute: load} = useQuery<null, CoSecPrincipal[]>({
        initialQuery: null,
        execute: (_, __, abortController) => {
            return userApiClient.query({abortController});
        },
    });
    const {roles} = useRoles()
    const roleSelectorOptions = roles.map(role => ({
        label: role.name,
        value: role.name,
    }))
    const {openDrawer, closeDrawer} = useDrawer();
    const loadUsers = () => {
        load();
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
                width: 'min(520px, 92vw)',
            }
        );
    };


    const handleSubmit = () => {
        closeDrawer();
        loadUsers();
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

    const columns: DataTableColumn<CoSecPrincipal>[] = [
        {
            header: 'Username',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {
            header: 'Roles',
            key: 'roles',
            cell: record => {
                const isProtected = record.name === 'cosky' || record.name === currentUser.sub;
                if (isProtected) {
                    return <div className="flex min-h-9 flex-wrap items-center gap-1.5">
                        {record.roles.length > 0
                            ? record.roles.map(role => <Badge key={role} variant="secondary">{role}</Badge>)
                            : <span className="text-sm text-muted-foreground">No roles assigned</span>}
                    </div>;
                }
                return <MultiSelect
                               aria-label={`Roles for ${record.name}`}
                               className="min-w-52"
                               options={roleSelectorOptions} value={record.roles}
                               onChange={(value) => handleChangeRole(record.name, value)}
                               placeholder="Select roles"
                />
            },
        },
        {
            header: 'Account',
            key: 'account',
            cell: record => {
                const isProtected = record.name === 'cosky' || record.name === currentUser.sub;
                return <div className="flex flex-wrap gap-1.5">
                    <Badge variant="outline">{record.anonymous ? 'Anonymous' : 'Local'}</Badge>
                    {isProtected && <Badge variant="secondary">Protected</Badge>}
                </div>;
            },
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-44 text-right',
            cell: record => {
                const isProtected = record.name === 'cosky' || record.name === currentUser.sub;
                return <div className="flex justify-end gap-1">
                    <ConfirmButton title="Unlock this user?"
                                description={`User “${record.name}” will be unlocked.`}
                                onConfirm={() => handleUnlock(record.name)}
                                variant="ghost"
                                size="sm"
                                disabled={isProtected}
                    >
                        <Unlock/> Unlock
                    </ConfirmButton>
                    <ConfirmButton
                        title="Are you sure to delete this user?"
                        description={`User “${record.name}” with ${record.roles.length} assigned role${record.roles.length === 1 ? '' : 's'} will be permanently removed.`}
                        onConfirm={() => handleDelete(record.name)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                        disabled={isProtected}
                    >
                        <Trash2/> Delete
                    </ConfirmButton>
                </div>
            },
        },
    ];

    return (
        <div>
            <PageHeader title="User" description="Manage accounts and role assignments."
                        actions={<Button onClick={handleAdd}><Plus/>Add User</Button>}/>
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={users}
                    loading={loading}
                    getRowKey={record => record.name}
                    search={{placeholder: 'Search users...', getValue: record => `${record.name} ${record.roles.join(' ')}`}}
                    emptyMessage="No users found."
                />
            </DataTableWrapper>
        </div>
    );
};
