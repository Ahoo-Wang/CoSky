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
import { useNamespace } from '../contexts/NamespaceContext'
import { configApi } from '../api'
import type { ConfigVersion } from '../generated'

const { Title } = Typography

export default function Config() {
  const { currentNamespace } = useNamespace()
  const [configs, setConfigs] = useState<ConfigVersion[]>([])
  const [loading, setLoading] = useState(false)

  const fetchConfigs = useCallback(async () => {
    try {
      setLoading(true)
      const response = await configApi.getConfigs(currentNamespace)
      setConfigs(response as ConfigVersion[])
    } catch (error) {
      console.error('Failed to fetch configs:', error)
      message.error('Failed to fetch configs')
    } finally {
      setLoading(false)
    }
  }, [currentNamespace])

  useEffect(() => {
    fetchConfigs()
  }, [fetchConfigs])

  const handleDelete = async (configId: string) => {
    try {
      await configApi.removeConfig(currentNamespace, configId)
      message.success('Config deleted successfully')
      fetchConfigs()
    } catch (error) {
      console.error('Failed to delete config:', error)
      message.error('Failed to delete config')
    }
  }

  const columns = [
    {
      title: 'Config ID',
      dataIndex: 'configId',
      key: 'configId',
    },
    {
      title: 'Version',
      dataIndex: 'version',
      key: 'version',
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, record: ConfigVersion) => (
        <Space size="middle">
          <Button type="link">View</Button>
          <Button type="link">Edit</Button>
          <Popconfirm
            title="Are you sure to delete this config?"
            onConfirm={() => handleDelete(record.configId)}
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
        <Title level={2}>Configuration Management</Title>
        <p>Namespace: {currentNamespace}</p>
        <Space>
          <Button type="primary" icon={<PlusOutlined />}>
            Add Config
          </Button>
          <Button icon={<ReloadOutlined />} onClick={fetchConfigs} loading={loading}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table 
          columns={columns} 
          dataSource={configs} 
          rowKey="configId"
          loading={loading}
        />
      </div>
    </div>
  )
}
