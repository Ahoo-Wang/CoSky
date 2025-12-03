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
import { Typography, Table, Button, Space, DatePicker, message, Tag } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { auditLogApi } from '../api'
import type { AuditLog as AuditLogType } from '../generated'

const { Title } = Typography
const { RangePicker } = DatePicker

export default function AuditLog() {
  const [logs, setLogs] = useState<AuditLogType[]>([])
  const [loading, setLoading] = useState(false)

  const fetchLogs = useCallback(async () => {
    try {
      setLoading(true)
      const response = await auditLogApi.queryLog()
      setLogs(response.list)
    } catch (error) {
      console.error('Failed to fetch audit logs:', error)
      message.error('Failed to fetch audit logs')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  const columns = [
    {
      title: 'Timestamp',
      dataIndex: 'opTime',
      key: 'opTime',
      render: (opTime: number) => new Date(opTime).toLocaleString(),
    },
    {
      title: 'Action',
      dataIndex: 'action',
      key: 'action',
    },
    {
      title: 'Operator',
      dataIndex: 'operator',
      key: 'operator',
    },
    {
      title: 'Resource',
      dataIndex: 'resource',
      key: 'resource',
    },
    {
      title: 'IP',
      dataIndex: 'ip',
      key: 'ip',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: number) => (
        <Tag color={status === 200 ? 'green' : 'red'}>
          {status}
        </Tag>
      ),
    },
    {
      title: 'Message',
      dataIndex: 'msg',
      key: 'msg',
    },
  ]

  return (
    <div>
      <div className="content-header">
        <Title level={2}>Audit Log</Title>
        <Space>
          <RangePicker />
          <Button icon={<ReloadOutlined />} onClick={fetchLogs} loading={loading}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table 
          columns={columns} 
          dataSource={logs}
          rowKey={(record) => `${record.opTime}-${record.operator}-${record.action}`}
          loading={loading}
        />
      </div>
    </div>
  )
}
