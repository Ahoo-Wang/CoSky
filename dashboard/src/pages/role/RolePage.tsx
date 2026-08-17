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
import type {RoleDto} from '../../generated';
import {RoleEditor} from './RoleEditor.tsx';
import {roleApiClient} from "../../services/clients.ts";
import {useRoles} from "../../hooks/useRoles.ts";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {PageHeader} from '../../components/layout/PageHeader.tsx';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';

export function RolePage() {
    const {roles = [], loading, load} = useRoles()
    const {openDrawer, closeDrawer} = useDrawer();

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

    const handleSubmit = () => {
        closeDrawer();
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
            header: 'Action',
            key: 'action',
            className: 'w-40 text-right',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(record)}>
                        <Pencil/> Edit
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this role?"
                        description={`Role “${record.name}” will be removed.`}
                        onConfirm={() => handleDelete(record.name)}
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
            <PageHeader title="Role" description="Define access policies for CoSky resources."
                        actions={<Button onClick={handleAdd}><Plus/>Add Role</Button>}/>
            <DataTable
                columns={columns}
                data={roles}
                loading={loading}
                getRowKey={record => record.name}
                search={{placeholder: 'Search roles...', getValue: record => `${record.name} ${record.desc}`}}
            />
        </div>
    );
};
