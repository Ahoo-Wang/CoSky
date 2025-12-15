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

import React from 'react';
import {Table, Button, Space, message, Popconfirm} from 'antd';
import {PlusOutlined, DeleteOutlined, EditOutlined} from '@ant-design/icons';
import {RoleDto} from '../../generated';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {useDrawer} from '../../contexts/DrawerContext.tsx';
import {RoleEditor} from './RoleEditor.tsx';
import {roleApiClient} from "../../services/clients.ts";


export const RolePage: React.FC = () => {
    const {result: roles = [], loading, setQuery: refreshRoles} = useQuery<null, RoleDto[]>({
        initialQuery: null,
        execute: (_, __, abortController) => {
            return roleApiClient.allRole({abortController});
        },
    });

    const {openDrawer, closeDrawer} = useDrawer();

    const loadRoles = () => {
        refreshRoles(null);
    };

    const handleAdd = () => {
        openDrawer(
            <RoleEditor
                onSubmit={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add Role',
                width: 500,
            }
        );
    };

    const handleEdit = (role: RoleDto) => {
        try {
            openDrawer(
                <RoleEditor
                    initialValues={role}
                    onSubmit={handleSubmit}
                    onCancel={closeDrawer}
                />,
                {
                    title: 'Edit Role',
                    width: 500,
                }
            );
        } catch (error) {
            console.error('Failed to load role:', error);
        }
    };

    const handleSubmit = () => {
        closeDrawer();
        loadRoles();
    };

    const handleDelete = async (roleName: string) => {
        try {
            await roleApiClient.removeRole(roleName);
            message.success('Role deleted successfully');
            loadRoles();
        } catch (error) {
            console.error('Failed to delete role:', error);
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
