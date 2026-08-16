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
import {Pencil, Plus, Trash2} from 'lucide-react';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn, createSearchColumn} from '@/components/table/columns';
import type {RoleDto} from '@/generated';
import {roleApiClient} from '@/services/clients';
import {useRoles} from '@/hooks/useRoles';
import {useDrawer} from '@/contexts/DrawerContext';
import {useDashboardCommand} from '@/lib/commands';
import {RoleEditor} from './RoleEditor';

export function RolePage() {
    const {roles = [], loading, error, load} = useRoles();
    const {openDrawer, closeDrawer} = useDrawer();

    const handleSubmit = () => {
        closeDrawer();
        load();
    };

    const handleAdd = () => {
        openDrawer(
            <RoleEditor
                onSuccess={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add Role',
            }
        );
    };

    useDashboardCommand('add-role', () => handleAdd());

    const handleEdit = (role: RoleDto) => {
        openDrawer(
            <RoleEditor
                initialValues={role}
                onSuccess={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Edit Role',
            }
        );
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

    const columns: ColumnDef<RoleDto>[] = [
        createSearchColumn<RoleDto>({
            title: 'Role Name',
            accessorKey: 'name',
            placeholder: 'Search role name',
        }),
        {
            accessorKey: 'desc',
            header: () => <span>Description</span>,
        },
        createActionColumn<RoleDto>({
            items: [
                {
                    key: 'edit',
                    label: 'Edit',
                    icon: <Pencil className="mr-1 h-4 w-4"/>,
                    onClick: handleEdit,
                },
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this role?',
                    onClick: (record) => void handleDelete(record.name),
                },
            ],
        }),
    ];

    return (
        <div>
            <PageHeader
                title="Role"
                actions={
                    <Button onClick={handleAdd}>
                        <Plus className="mr-1 h-4 w-4"/>
                        Add Role
                    </Button>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={roles}
                    loading={loading}
                    error={error}
                    onRetry={() => void load()}
                    getRowId={(row) => row.name}
                />
            </DataTableWrapper>
        </div>
    );
}
