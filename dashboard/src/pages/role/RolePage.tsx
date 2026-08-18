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

import {useState} from 'react';
import {Pencil, Plus, Trash2} from 'lucide-react';
import type {CoSecPrincipal, ResourceActionDto, RoleDto} from '../../generated';
import {RoleEditor} from './RoleEditor.tsx';
import {roleApiClient, userApiClient} from "../../services/clients.ts";
import {useRoles} from "../../hooks/useRoles.ts";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {PageHeader} from '../../components/layout/PageHeader.tsx';
import {DataTableWrapper} from '../../components/layout/DataTableWrapper.tsx';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {Badge} from '@/components/ui/badge';
import {useQuery} from '@ahoo-wang/fetcher-react';

const ACTION_LABELS: Record<string, string> = {r: 'Read', w: 'Write', rw: 'Read & write'};

function RolePermissions({roleName}: {roleName: string}) {
    const {result: bindings = [], loading, error, execute: retry} = useQuery<string, ResourceActionDto[]>({
        query: roleName,
        execute: (name, _, abortController) => roleApiClient.getResourceBind(name, {abortController}),
    });
    if (loading) return <span className="text-sm text-muted-foreground">Loading…</span>;
    if (error) return <Button type="button" variant="outline" size="sm" onClick={retry}>Retry permissions</Button>;
    if (bindings.length === 0) return <Badge variant="outline">No resource access</Badge>;
    return <div className="flex max-w-xl flex-wrap gap-1.5">
        {bindings.map(binding => <Badge key={`${binding.namespace}-${binding.action}`} variant="outline">
            {binding.namespace}: {ACTION_LABELS[binding.action] ?? binding.action}
        </Badge>)}
    </div>;
}

export function RolePage() {
    const [roleRevision, setRoleRevision] = useState(0);
    const {roles = [], loading, error, load} = useRoles()
    const {result: users = [], loading: loadingUsers, error: usersError, execute: loadUsers} = useQuery<null, CoSecPrincipal[]>({
        initialQuery: null,
        execute: (_, __, abortController) => userApiClient.query({abortController}),
    });
    const {openDrawer, closeDrawer} = useDrawer();

    const handleAdd = () => {
        openDrawer(
            <RoleEditor
                onSuccess={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add Role',
                width: 'min(680px, 92vw)',
            }
        );
    };

    const handleEdit = (role: RoleDto) => {
        openDrawer(
            <RoleEditor
                initialValues={role}
                onSuccess={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Edit Role',
                width: 'min(680px, 92vw)',
            }
        );
    };

    const handleSubmit = () => {
        closeDrawer();
        setRoleRevision(revision => revision + 1);
        load();
    };

    const handleDelete = async (roleName: string) => {
        try {
            await roleApiClient.removeRole(roleName);
            toast.success('Role deleted successfully');
            load();
        } catch {
            toast.error('Failed to delete role');
        }
    };

    const columns: DataTableColumn<RoleDto>[] = [
        {
            header: 'Role Name',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {
            header: 'Description',
            accessor: 'desc',
            key: 'desc',
        },
        {
            header: 'Resource Permissions',
            key: 'permissions',
            cell: record => record.name === 'admin'
                ? <Badge variant="secondary">System full access</Badge>
                : <RolePermissions key={`${record.name}-${roleRevision}`} roleName={record.name}/>,
        },
        {
            header: 'Members',
            key: 'members',
            cell: record => loadingUsers ? '…' : users.filter(user => user.roles.includes(record.name)).length,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-40 text-right',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(record)} disabled={record.name === 'admin'}
                            aria-label={record.name === 'admin' ? 'Edit admin (system role)' : `Edit ${record.name}`}>
                        <Pencil/> Edit
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this role?"
                        description={`Role “${record.name}” is assigned to ${users.filter(user => user.roles.includes(record.name)).length} user(s). Removing it may revoke their access.`}
                        onConfirm={() => handleDelete(record.name)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                        disabled={record.name === 'admin'}
                        aria-label={record.name === 'admin' ? 'Delete admin (system role)' : `Delete ${record.name}`}
                    >
                        <Trash2/> Delete
                    </ConfirmButton>
                </div>
            ),
        },
    ];

    return (
        <div>
            <PageHeader title="Role" description="Define access policies for CoSky resources."
                        actions={<Button onClick={handleAdd}><Plus/>Add Role</Button>}/>
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={roles}
                    loading={loading || loadingUsers}
                    error={error || usersError}
                    onRetry={() => {
                        load();
                        loadUsers();
                    }}
                    getRowKey={record => record.name}
                    search={{placeholder: 'Search roles...', getValue: record => `${record.name} ${record.desc}`}}
                    emptyMessage="No roles found."
                />
            </DataTableWrapper>
        </div>
    );
};
