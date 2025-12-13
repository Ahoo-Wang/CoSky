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
import { Typography, Table, Button, Space, message, Popconfirm } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { roleApi } from '../api'
import type { RoleDto } from '../generated'

const { Title } = Typography

export default function Role() {
  const [roles, setRoles] = useState<RoleDto[]>([])
  const [loading, setLoading] = useState(false)

  const fetchRoles = useCallback(async () => {
    try {
      setLoading(true)
      const response = await roleApi.allRole()
      setRoles(response as RoleDto[])
    } catch (error) {
      console.error('Failed to fetch roles:', error)
      message.error('Failed to fetch roles')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchRoles()
  }, [fetchRoles])

  const handleDelete = async (roleName: string) => {
    try {
      await roleApi.removeRole(roleName)
      message.success('Role deleted successfully')
      fetchRoles()
    } catch (error) {
      console.error('Failed to delete role:', error)
      message.error('Failed to delete role')
    }
  }

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
      render: (_: unknown, record: RoleDto) => (
        <Space size="middle">
          <Button type="link">Edit</Button>
          <Popconfirm
            title="Are you sure to delete this role?"
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
        <Title level={2}>Role Management</Title>
        <Space>
          <Button type="primary" icon={<PlusOutlined />}>
            Add Role
          </Button>
          <Button icon={<ReloadOutlined />} onClick={fetchRoles} loading={loading}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table 
          columns={columns} 
          dataSource={roles}
          rowKey="name"
          loading={loading}
        />
      </div>
    </div>
  )
}
