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

import React, { useState, useEffect } from 'react';
import { Table, Button, Space, Modal, Form, Input, Select, message, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import { UserApiClient, RoleApiClient } from '../../generated';

const userApiClient = new UserApiClient();
const roleApiClient = new RoleApiClient();

export const UserPage: React.FC = () => {
  const [users, setUsers] = useState<any[]>([]);
  const [roles, setRoles] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [isEdit, setIsEdit] = useState(false);
  const [currentUser, setCurrentUser] = useState<any>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadUsers();
    loadRoles();
  }, []);

  const loadUsers = async () => {
    setLoading(true);
    try {
      const result = await userApiClient.query();
      setUsers(result || []);
    } catch (error) {
      console.error('Failed to load users:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadRoles = async () => {
    try {
      const result = await roleApiClient.allRole();
      setRoles(result || []);
    } catch (error) {
      console.error('Failed to load roles:', error);
    }
  };

  const handleAdd = () => {
    setIsEdit(false);
    setCurrentUser(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (user: any) => {
    setIsEdit(true);
    setCurrentUser(user);
    form.setFieldsValue(user);
    setModalVisible(true);
  };

  const handleSave = async (values: any) => {
    try {
      if (isEdit) {
        await userApiClient.bindRole(currentUser.username, { body: values });
        message.success('User updated successfully');
      } else {
        await userApiClient.addUser(values.username, { body: values });
        message.success('User created successfully');
      }
      setModalVisible(false);
      form.resetFields();
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
      dataIndex: 'username',
      key: 'username',
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

      <Modal
        title={isEdit ? 'Edit User' : 'Add User'}
        open={modalVisible}
        onCancel={() => {
          setModalVisible(false);
          form.resetFields();
        }}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Form.Item
            name="username"
            label="Username"
            rules={[{ required: true, message: 'Please input username!' }]}
          >
            <Input disabled={isEdit} />
          </Form.Item>
          {!isEdit && (
            <Form.Item
              name="password"
              label="Password"
              rules={[{ required: true, message: 'Please input password!' }]}
            >
              <Input.Password />
            </Form.Item>
          )}
          <Form.Item name="roles" label="Roles">
            <Select mode="multiple" placeholder="Select roles">
              {roles.map((role: any) => (
                <Select.Option key={role.roleId} value={role.roleId}>
                  {role.roleId}
                </Select.Option>
              ))}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
