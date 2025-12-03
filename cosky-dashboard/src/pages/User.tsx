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

import { useEffect, useState, useCallback } from 'react'
import { Typography, Table, Button, Space, message, Popconfirm, Tag } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { userApi } from '../api'
import type { CoSecPrincipal } from '../generated'

const { Title } = Typography

export default function User() {
  const [users, setUsers] = useState<CoSecPrincipal[]>([])
  const [loading, setLoading] = useState(false)

  const fetchUsers = useCallback(async () => {
    try {
      setLoading(true)
      const response = await userApi.query()
      setUsers(response as CoSecPrincipal[])
    } catch (error) {
      console.error('Failed to fetch users:', error)
      message.error('Failed to fetch users')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchUsers()
  }, [fetchUsers])

  const handleDelete = async (username: string) => {
    try {
      await userApi.removeUser(username)
      message.success('User deleted successfully')
      fetchUsers()
    } catch (error) {
      console.error('Failed to delete user:', error)
      message.error('Failed to delete user')
    }
  }

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
      render: (roles: string[]) => (
        <>
          {roles?.map(role => (
            <Tag key={role} color="blue">{role}</Tag>
          ))}
        </>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: unknown, record: CoSecPrincipal) => (
        <Tag color={record.authenticated ? 'green' : 'default'}>
          {record.authenticated ? 'Active' : 'Inactive'}
        </Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, record: CoSecPrincipal) => (
        <Space size="middle">
          <Button type="link">Edit</Button>
          <Popconfirm
            title="Are you sure to delete this user?"
            onConfirm={() => handleDelete(record.name)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div className="content-header">
        <Title level={2}>User Management</Title>
        <Space>
          <Button type="primary" icon={<PlusOutlined />}>
            Add User
          </Button>
          <Button icon={<ReloadOutlined />} onClick={fetchUsers} loading={loading}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table 
          columns={columns} 
          dataSource={users}
          rowKey="id"
          loading={loading}
        />
      </div>
    </div>
  )
}
