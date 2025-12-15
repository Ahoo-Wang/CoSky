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

import {Table, Button, Space, message, Popconfirm, Select} from 'antd';
import {PlusOutlined, DeleteOutlined, UnlockOutlined} from '@ant-design/icons';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {useDrawer} from '../../contexts/DrawerContext.tsx';
import {AddUserEditor} from './AddUserEditor.tsx';
import {useRoles} from "../../hooks/useRoles.ts";
import {userApiClient} from "../../services/clients.ts";
import {CoSecPrincipal} from "../../generated";
export function UserPage() {
    const {result: users = [], loading, execute: load} = useQuery<null, CoSecPrincipal[]>({
        initialQuery: null,
        execute: (_, __, abortController) => {
            return userApiClient.query({abortController});
        },
    });
    const {roles} = useRoles()
    const roleSelectorOptions = roles.map(role => ({
        label: role.name,
        value: role.desc,
    }))
    const {openDrawer, closeDrawer} = useDrawer();
    const loadUsers = () => {
        load();
    };

    const handleAdd = () => {
        openDrawer(
            <AddUserEditor
                roleSelectorOptions={roleSelectorOptions}
                onSubmit={handleSubmit}
                onCancel={closeDrawer}
            />,
            {
                title: 'Add User',
                width: 500,
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
            message.success('Role bind successfully');
            loadUsers();
        } catch (error) {
            message.error('Failed to bind role');
        }
    };

    const handleDelete = async (username: string) => {
        try {
            await userApiClient.removeUser(username);
            message.success('User deleted successfully');
            loadUsers();
        } catch (error) {
            message.error('Failed to delete user');
        }
    };

    const handleUnlock = async (username: string) => {
        try {
            await userApiClient.unlock(username);
            message.success('User unlocked successfully');
            loadUsers();
        } catch (error) {
            message.error('Failed to unlock user');
        }
    };

    const columns = [
        {
            title: 'Username',
            dataIndex: 'name',
            key: 'name',
        },
        {
            title: 'Roles',
            dataIndex: 'roles',
            key: 'roles',
            render: (roles: string[], record: CoSecPrincipal) => {
                return <Select mode="multiple"
                               placeholder="Select Roles"
                               options={roleSelectorOptions} value={roles}
                               onChange={(value) => handleChangeRole(record.name, value)}
                />
            },
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: CoSecPrincipal) => (
                <Space>
                    <Popconfirm title="Ary you sure to unlock this user?"
                                onConfirm={() => handleUnlock(record.name)}
                    >
                        <Button type="link" icon={<UnlockOutlined/>}>
                            UnLock
                        </Button>
                    </Popconfirm>
                    <Popconfirm
                        title="Are you sure to delete this user?"
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
                <h2>User</h2>
                <Button type="primary" icon={<PlusOutlined/>} onClick={handleAdd}>
                    Add User
                </Button>
            </div>
            <Table
                columns={columns}
                dataSource={users}
                loading={loading}
            />
        </div>
    );
};
