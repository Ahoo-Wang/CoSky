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

import { Typography, Table, Button, Space } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNamespace } from '../contexts/NamespaceContext'

const { Title } = Typography

interface ConfigData {
  key: string
  configId: string
  version: number
  modifiedTime: string
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
    title: 'Modified Time',
    dataIndex: 'modifiedTime',
    key: 'modifiedTime',
  },
  {
    title: 'Action',
    key: 'action',
    render: () => (
      <Space size="middle">
        <Button type="link">View</Button>
        <Button type="link">Edit</Button>
        <Button type="link" danger>Delete</Button>
      </Space>
    ),
  },
]

const data: ConfigData[] = []

export default function Config() {
  const { currentNamespace } = useNamespace()

  return (
    <div>
      <div className="content-header">
        <Title level={2}>Configuration Management</Title>
        <p>Namespace: {currentNamespace}</p>
        <Space>
          <Button type="primary" icon={<PlusOutlined />}>
            Add Config
          </Button>
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
