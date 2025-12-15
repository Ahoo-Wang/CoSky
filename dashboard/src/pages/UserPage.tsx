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

import React, { useState } from 'react';
import { Table, Button, Space, message, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { UserApiClient, RoleApiClient } from '../generated';
import { useQuery } from '@ahoo-wang/fetcher-react';
import { useDrawer } from '../contexts/DrawerContext.tsx';
import { UserForm } from '../components/forms/UserForm.tsx';

const userApiClient = new UserApiClient();
const roleApiClient = new RoleApiClient();

type QueryData = { refresh: number };

export const UserPage: React.FC = () => {
  const { result: users = [], loading, setQuery: refreshUsers } = useQuery<QueryData, any[]>({
    initialQuery: { refresh: 0 },
    execute: (_, __, abortController) => {
      return userApiClient.query({ abortController });
    },
  });

  const { result: roles = [] } = useQuery<QueryData, any[]>({
    initialQuery: { refresh: 0 },
    autoExecute: true,
    execute: (_, __, abortController) => {
      return roleApiClient.allRole({ abortController });
    },
  });

  const [isEdit, setIsEdit] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const { openDrawer, closeDrawer } = useDrawer();

  const loadUsers = () => {
    refreshUsers({ refresh: Date.now() });
  };

  const handleAdd = () => {
    setIsEdit(false);
    setCurrentUser(null);
    openDrawer(
      <UserForm
        isEdit={false}
        initialValues={null}
        roles={roles}
        onSubmit={handleSubmit}
        onCancel={closeDrawer}
      />,
      {
        title: 'Add User',
        width: 500,
      }
    );
  };

  const handleEdit = (user: any) => {
    setIsEdit(true);
    setCurrentUser(user);
    openDrawer(
      <UserForm
        isEdit={true}
        initialValues={user}
        roles={roles}
        onSubmit={handleSubmit}
        onCancel={closeDrawer}
      />,
      {
        title: 'Edit User',
        width: 500,
      }
    );
  };

  const handleSubmit = async (values: any) => {
    try {
      if (isEdit) {
        await userApiClient.bindRole(currentUser.username, { body: values });
        message.success('User updated successfully');
      } else {
        await userApiClient.addUser(values.username, { body: values });
        message.success('User created successfully');
      }
      closeDrawer();
      loadUsers();
    } catch (error) {
      console.error('Failed to save user:', error);
      message.error('Failed to save user');
    }
  };

  const handleDelete = async (username: string) => {
    try {
      await userApiClient.removeUser(username);
      message.success('User deleted successfully');
      loadUsers();
    } catch (error) {
      console.error('Failed to delete user:', error);
      message.error('Failed to delete user');
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
      render: (roles: string[]) => roles?.join(', ') || '-',
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            Edit
          </Button>
          <Popconfirm
            title="Are you sure to delete this user?"
            onConfirm={() => handleDelete(record.username)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <h2>User</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          Add User
        </Button>
      </div>
      <Table
        columns={columns}
        dataSource={users.map((u: any) => ({ ...u, key: u.username }))}
        loading={loading}
      />
    </div>
  );
};
