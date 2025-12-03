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
import { Typography, Table, Button, Space, message, Tag } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { useNamespace } from '../contexts/NamespaceContext'
import { serviceApi } from '../api'
import type { ServiceStat } from '../generated'

const { Title } = Typography

export default function Service() {
  const { currentNamespace } = useNamespace()
  const [services, setServices] = useState<ServiceStat[]>([])
  const [loading, setLoading] = useState(false)

  const fetchServices = useCallback(async () => {
    try {
      setLoading(true)
      const response = await serviceApi.getServiceStats(currentNamespace)
      setServices(response as ServiceStat[])
    } catch (error) {
      console.error('Failed to fetch services:', error)
      message.error('Failed to fetch services')
    } finally {
      setLoading(false)
    }
  }, [currentNamespace])

  useEffect(() => {
    fetchServices()
  }, [fetchServices])

  const columns = [
    {
      title: 'Service Name',
      dataIndex: 'serviceId',
      key: 'serviceId',
    },
    {
      title: 'Instance Count',
      dataIndex: 'instanceCount',
      key: 'instanceCount',
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: unknown, record: ServiceStat) => (
        <Tag color={record.instanceCount > 0 ? 'green' : 'red'}>
          {record.instanceCount > 0 ? 'Healthy' : 'No Instances'}
        </Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      render: () => (
        <Space size="middle">
          <Button type="link">View Instances</Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <div className="content-header">
        <Title level={2}>Service Discovery</Title>
        <p>Namespace: {currentNamespace}</p>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchServices} loading={loading}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table 
          columns={columns} 
          dataSource={services} 
          rowKey="serviceId"
          loading={loading}
        />
      </div>
    </div>
  )
}
