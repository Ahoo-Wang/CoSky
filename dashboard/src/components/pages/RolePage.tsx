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
import { RoleApiClient } from '../../generated';
import { useQuery } from '@ahoo-wang/fetcher-react';
import { useDrawer } from '../../contexts/DrawerContext';
import { RoleForm } from '../forms/RoleForm';

const roleApiClient = new RoleApiClient();

type QueryData = { refresh: number };

export const RolePage: React.FC = () => {
  const { result: roles = [], loading, setQuery: refreshRoles } = useQuery<QueryData, any[]>({
    initialQuery: { refresh: 0 },
    execute: (_, __, abortController) => {
      return roleApiClient.allRole({ abortController });
    },
  });

  const [isEdit, setIsEdit] = useState(false);
  const [currentRole, setCurrentRole] = useState<any>(null);
  const { openDrawer, closeDrawer } = useDrawer();

  const loadRoles = () => {
    refreshRoles({ refresh: Date.now() });
  };

  const handleAdd = () => {
    setIsEdit(false);
    setCurrentRole(null);
    openDrawer(
      <RoleForm
        isEdit={false}
        initialValues={null}
        onSubmit={handleSubmit}
        onCancel={closeDrawer}
      />,
      {
        title: 'Add Role',
        width: 500,
      }
    );
  };

  const handleEdit = async (roleName: string) => {
    try {
      const bind = await roleApiClient.getResourceBind(roleName);
      setIsEdit(true);
      const roleData = { roleId: roleName, desc: '', resourceActionBind: bind };
      setCurrentRole({ roleId: roleName, resourceActionBind: bind });
      openDrawer(
        <RoleForm
          isEdit={true}
          initialValues={roleData}
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

  const handleSubmit = async (values: any) => {
    try {
      const roleId = isEdit ? currentRole.roleId : values.roleId;
      await roleApiClient.saveRole(roleId, { body: values });
      message.success('Role saved successfully');
      closeDrawer();
      loadRoles();
    } catch (error) {
      console.error('Failed to save role:', error);
      message.error('Failed to save role');
    }
  };

  const handleDelete = async (roleId: string) => {
    try {
      await roleApiClient.removeRole(roleId);
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
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record.name)}>
            Edit
          </Button>
          <Popconfirm
            title="Are you sure to delete this role?"
            onConfirm={() => handleDelete(record.name)}
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
        <h2>Role</h2>
        <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
          Add Role
        </Button>
      </div>
      <Table
        columns={columns}
        dataSource={roles.map((r: any) => ({ ...r, key: r.name }))}
        loading={loading}
      />
    </div>
  );
};
