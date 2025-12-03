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

import { useState } from 'react'
import { Typography, Table, Button, Space, message, Popconfirm, Modal, Input } from 'antd'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNamespace } from '../contexts/NamespaceContext'
import { namespaceApi } from '../api'

const { Title } = Typography

export default function Namespace() {
  const { namespaces, currentNamespace, setCurrentNamespace, refreshNamespaces } = useNamespace()
  const [loading, setLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [newNamespace, setNewNamespace] = useState('')

  const handleRefresh = async () => {
    setLoading(true)
    await refreshNamespaces()
    setLoading(false)
  }

  const handleDelete = async (namespace: string) => {
    try {
      await namespaceApi.removeNamespace(namespace)
      message.success('Namespace deleted successfully')
      refreshNamespaces()
    } catch (error) {
      console.error('Failed to delete namespace:', error)
      message.error('Failed to delete namespace')
    }
  }

  const handleAdd = async () => {
    if (!newNamespace.trim()) {
      message.warning('Please enter namespace name')
      return
    }
    try {
      await namespaceApi.setNamespace(newNamespace)
      message.success('Namespace created successfully')
      setModalVisible(false)
      setNewNamespace('')
      refreshNamespaces()
    } catch (error) {
      console.error('Failed to create namespace:', error)
      message.error('Failed to create namespace')
    }
  }

  const handleSelect = async (namespace: string) => {
    await setCurrentNamespace(namespace)
    message.success(`Switched to namespace: ${namespace}`)
  }

  const columns = [
    {
      title: 'Namespace',
      dataIndex: 'namespace',
      key: 'namespace',
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: unknown, record: { namespace: string }) => (
        record.namespace === currentNamespace ? 
          <span style={{ color: '#1890ff' }}>Current</span> : 
          null
      ),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: unknown, record: { namespace: string }) => (
        <Space size="middle">
          <Button type="link" onClick={() => handleSelect(record.namespace)}>
            Switch
          </Button>
          <Popconfirm
            title="Are you sure to delete this namespace?"
            onConfirm={() => handleDelete(record.namespace)}
            okText="Yes"
            cancelText="No"
          >
            <Button type="link" danger disabled={record.namespace === 'default'}>
              Delete
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const data = namespaces.map(ns => ({ namespace: ns, key: ns }))

  return (
    <div>
      <div className="content-header">
        <Title level={2}>Namespace Management</Title>
        <Space>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>
            Add Namespace
          </Button>
          <Button icon={<ReloadOutlined />} onClick={handleRefresh} loading={loading}>
            Refresh
          </Button>
        </Space>
      </div>
      <div className="content-body">
        <Table 
          columns={columns} 
          dataSource={data}
          loading={loading}
        />
      </div>
      <Modal
        title="Add Namespace"
        open={modalVisible}
        onOk={handleAdd}
        onCancel={() => setModalVisible(false)}
      >
        <Input
          placeholder="Enter namespace name"
          value={newNamespace}
          onChange={(e) => setNewNamespace(e.target.value)}
        />
      </Modal>
    </div>
  )
}
