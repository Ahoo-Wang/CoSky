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

import {Table, Button, Space, message, Popconfirm} from 'antd';
import {PlusOutlined, DeleteOutlined, EditOutlined} from '@ant-design/icons';
import {RoleDto} from '../../generated';
import {useDrawer} from '../../contexts/DrawerContext.tsx';
import {RoleEditor} from './RoleEditor.tsx';
import {roleApiClient} from "../../services/clients.ts";
import {useRoles} from "../../hooks/useRoles.ts";

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
                width: 500,
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
                width: 500,
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
            message.success('Role deleted successfully');
            load();
        } catch (error) {
            message.error('Failed to delete role');
        }
    };

    const columns = [
        {
            title: 'Role Name',
            dataIndex: 'name',
            key: 'name',
        },
        {
            title: 'Description',
            dataIndex: 'desc',
            key: 'desc',
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: string, record: RoleDto) => (
                <Space>
                    <Button type="link" icon={<EditOutlined/>} onClick={() => handleEdit(record)}>
                        Edit
                    </Button>
                    <Popconfirm
                        title="Are you sure to delete this role?"
                        onConfirm={() => handleDelete(record.name)}
                        okText="Yes"
                        cancelText="No"
                    >
                        <Button type="link" danger icon={<DeleteOutlined/>}>
                            Delete
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div style={{marginBottom: 16, display: 'flex', justifyContent: 'space-between'}}>
                <h2>Role</h2>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>
                    Add Role
                </Button>
            </div>
            <Table
                columns={columns}
                dataSource={roles}
                loading={loading}
            />
        </div>
    );
};
