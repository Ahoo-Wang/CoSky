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

import { Typography, Table, Button, Space, DatePicker } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'

const { Title } = Typography
const { RangePicker } = DatePicker

interface AuditLogData {
  key: string
  timestamp: string
  action: string
  operator: string
  resource: string
  details: string
}

const columns = [
  {
    title: 'Timestamp',
    dataIndex: 'timestamp',
    key: 'timestamp',
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
    title: 'Details',
    dataIndex: 'details',
    key: 'details',
  },
]

const data: AuditLogData[] = []

export default function AuditLog() {
  return (
    <div>
      <div className="content-header">
        <Title level={2}>Audit Log</Title>
        <Space>
          <RangePicker />
          <Button icon={<ReloadOutlined />}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table columns={columns} dataSource={data} />
      </div>
    </div>
  )
}
