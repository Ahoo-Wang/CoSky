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
import { Table, Button, Input, Space, Modal, message, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined, ImportOutlined, HistoryOutlined } from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { saveAs } from 'file-saver';
import { useNamespace } from '../../contexts/NamespaceContext';
import { ConfigApiClient } from '../../generated';

const configApiClient = new ConfigApiClient();

export const ConfigPage: React.FC = () => {
  const { currentNamespace } = useNamespace();
  const [configs, setConfigs] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [editVisible, setEditVisible] = useState(false);
  const [importVisible, setImportVisible] = useState(false);
  const [versionVisible, setVersionVisible] = useState(false);
  const [currentConfig, setCurrentConfig] = useState<any>(null);
  const [configData, setConfigData] = useState('');
  const [importData, setImportData] = useState('');
  const [versions, setVersions] = useState<any[]>([]);
  const [searchValue, setSearchValue] = useState('');

  useEffect(() => {
    loadConfigs();
  }, [currentNamespace]);

  const loadConfigs = async () => {
    setLoading(true);
    try {
      const result = await configApiClient.getConfigs(currentNamespace);
      setConfigs(result || []);
    } catch (error) {
      console.error('Failed to load configs:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleEdit = async (configId?: string) => {
    if (configId) {
      try {
        const config = await configApiClient.getConfig(currentNamespace, configId);
        setCurrentConfig(config);
        setConfigData(config.data || '');
      } catch (error) {
        console.error('Failed to load config:', error);
      }
    } else {
      setCurrentConfig(null);
      setConfigData('');
    }
    setEditVisible(true);
  };

  const handleSave = async () => {
    try {
      if (currentConfig) {
        await configApiClient.setConfig(currentNamespace, currentConfig.configId, { body: configData });
      } else {
        const configId = prompt('Enter config ID:');
        if (configId) {
          await configApiClient.setConfig(currentNamespace, configId, { body: configData });
        }
      }
      message.success('Config saved successfully');
      setEditVisible(false);
      loadConfigs();
    } catch (error) {
      console.error('Failed to save config:', error);
      message.error('Failed to save config');
    }
  };

  const handleDelete = async (configId: string) => {
    try {
      await configApiClient.removeConfig(currentNamespace, configId);
      message.success('Config deleted successfully');
      loadConfigs();
    } catch (error) {
      console.error('Failed to delete config:', error);
      message.error('Failed to delete config');
    }
  };

  const handleExport = async () => {
    try {
      const data = await configApiClient.exportZip(currentNamespace);
      const blob = new Blob([data], { type: 'application/zip' });
      saveAs(blob, `configs-${currentNamespace}.zip`);
      message.success('Configs exported successfully');
    } catch (error) {
      console.error('Failed to export configs:', error);
      message.error('Failed to export configs');
    }
  };

  const handleImport = async () => {
    try {
      const formData = new FormData();
      const blob = new Blob([importData], { type: 'application/zip' });
      formData.append('file', blob);
      await configApiClient.importZip(currentNamespace, { body: formData });
      message.success('Configs imported successfully');
      setImportVisible(false);
      setImportData('');
      loadConfigs();
    } catch (error) {
      console.error('Failed to import configs:', error);
      message.error('Failed to import configs');
    }
  };

  const handleViewVersions = async (configId: string) => {
    try {
      const result = await configApiClient.getConfigVersions(currentNamespace, configId);
      setVersions(result || []);
      setCurrentConfig({ configId });
      setVersionVisible(true);
    } catch (error) {
      console.error('Failed to load versions:', error);
      message.error('Failed to load versions');
    }
  };

  const columns = [
    {
      title: 'Config ID',
      dataIndex: 'configId',
      key: 'configId',
      filteredValue: searchValue ? [searchValue] : null,
      onFilter: (value: any, record: any) => 
        record.configId.toLowerCase().includes(value.toLowerCase()),
    },
    {
      title: 'Action',
      key: 'action',
      render: (_: any, record: any) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record.configId)}>
            Edit
          </Button>
          <Button type="link" icon={<HistoryOutlined />} onClick={() => handleViewVersions(record.configId)}>
            Versions
          </Button>
          <Popconfirm
            title="Are you sure to delete this config?"
            onConfirm={() => handleDelete(record.configId)}
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

  const filteredConfigs = configs.map((config: any) => ({ ...config, key: config.configId || config }));

  return (
    <div>
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
        <h2>Configuration</h2>
        <Space>
          <Input.Search
            placeholder="Search Config ID"
            allowClear
            onSearch={setSearchValue}
            style={{ width: 200 }}
          />
          <Button type="primary" icon={<PlusOutlined />} onClick={() => handleEdit()}>
            Add
          </Button>
          <Button danger icon={<ImportOutlined />} onClick={() => setImportVisible(true)}>
            Import
          </Button>
          <Button danger icon={<ExportOutlined />} onClick={handleExport}>
            Export
          </Button>
        </Space>
      </div>
      <Table columns={columns} dataSource={filteredConfigs} loading={loading} />

      <Modal
        title={currentConfig ? `Edit Config: ${currentConfig.configId}` : 'Add Config'}
        open={editVisible}
        onCancel={() => setEditVisible(false)}
        onOk={handleSave}
        width={800}
      >
        <Editor
          height="400px"
          defaultLanguage="yaml"
          value={configData}
          onChange={(value) => setConfigData(value || '')}
          options={{
            minimap: { enabled: false },
          }}
        />
      </Modal>

      <Modal
        title="Import Configs"
        open={importVisible}
        onCancel={() => setImportVisible(false)}
        onOk={handleImport}
        width={800}
      >
        <Editor
          height="400px"
          defaultLanguage="json"
          value={importData}
          onChange={(value) => setImportData(value || '')}
          options={{
            minimap: { enabled: false },
          }}
        />
      </Modal>

      <Modal
        title={`Config Versions: ${currentConfig?.configId}`}
        open={versionVisible}
        onCancel={() => setVersionVisible(false)}
        footer={null}
        width={800}
      >
        <Table
          dataSource={versions.map((v: any, i: number) => ({ ...v, key: i }))}
          columns={[
            { title: 'Version', dataIndex: 'version', key: 'version' },
            { title: 'Create Time', dataIndex: 'createTime', key: 'createTime' },
          ]}
        />
      </Modal>
    </div>
  );
};
