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

import React, { useState } from 'react';
import { Table, Button, Input, Space, Modal, message, Popconfirm } from 'antd';
import { PlusOutlined, DeleteOutlined, EditOutlined, ExportOutlined, ImportOutlined, HistoryOutlined } from '@ant-design/icons';
import { saveAs } from 'file-saver';
import { useNamespaceContext } from '../../contexts/NamespaceContext';
import { ConfigApiClient } from '../../generated';
import { useQuery } from '@ahoo-wang/fetcher-react';
import { useDrawer } from '../../contexts/DrawerContext';
import { ConfigEditForm } from '../forms/ConfigEditForm';
import { ConfigImportForm } from '../forms/ConfigImportForm';
import { ConfigVersionsView } from '../forms/ConfigVersionsView';

const configApiClient = new ConfigApiClient();

export const ConfigPage: React.FC = () => {
  const { currentNamespace } = useNamespaceContext();
  const { result: configs = [], loading, setQuery } = useQuery<string, any[]>({
    initialQuery: currentNamespace,
    execute: (namespace, _, abortController) => {
      return configApiClient.getConfigs(namespace, { abortController });
    },
  });
  const [currentConfig, setCurrentConfig] = useState<any>(null);
  const [searchValue, setSearchValue] = useState('');
  const { openDrawer, closeDrawer } = useDrawer();

  const loadConfigs = () => {
    setQuery(currentNamespace);
  };

  const handleEdit = async (configId?: string) => {
    if (configId) {
      try {
        const config = await configApiClient.getConfig(currentNamespace, configId);
        setCurrentConfig(config);
        openDrawer(
          <ConfigEditForm
            config={config}
            onSave={handleSaveConfig}
            onCancel={closeDrawer}
          />,
          {
            title: 'Edit Config',
            width: 800,
          }
        );
      } catch (error) {
        console.error('Failed to load config:', error);
      }
    } else {
      // For new config, show a modal to get the config ID first
      Modal.confirm({
        title: 'Enter Config ID',
        content: (
          <Input
            placeholder="Config ID"
            id="newConfigId"
            autoFocus
          />
        ),
        onOk: async () => {
          const input = document.getElementById('newConfigId') as HTMLInputElement;
          const configId = input?.value;
          if (configId) {
            const newConfig = { configId, data: '' };
            setCurrentConfig(newConfig);
            openDrawer(
              <ConfigEditForm
                config={newConfig}
                onSave={handleSaveConfig}
                onCancel={closeDrawer}
              />,
              {
                title: 'Add Config',
                width: 800,
              }
            );
          }
        },
      });
    }
  };

  const handleSaveConfig = async (configData: string) => {
    try {
      if (currentConfig) {
        await configApiClient.setConfig(currentNamespace, currentConfig.configId, { body: configData });
        message.success('Config saved successfully');
        closeDrawer();
        loadConfigs();
      } else {
        message.error('Config ID is required');
      }
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

  const handleImportClick = () => {
    openDrawer(
      <ConfigImportForm
        onImport={handleImportConfigs}
        onCancel={closeDrawer}
      />,
      {
        title: 'Import Configs',
        width: 800,
      }
    );
  };

  const handleImportConfigs = async (importData: string) => {
    try {
      const formData = new FormData();
      const blob = new Blob([importData], { type: 'application/zip' });
      formData.append('file', blob);
      await configApiClient.importZip(currentNamespace, { body: formData });
      message.success('Configs imported successfully');
      closeDrawer();
      loadConfigs();
    } catch (error) {
      console.error('Failed to import configs:', error);
      message.error('Failed to import configs');
    }
  };

  const handleViewVersions = async (configId: string) => {
    try {
      const result = await configApiClient.getConfigVersions(currentNamespace, configId);
      openDrawer(
        <ConfigVersionsView
          configId={configId}
          versions={result || []}
          onClose={closeDrawer}
        />,
        {
          title: 'Config Versions',
          width: 800,
        }
      );
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
          <Button danger icon={<ImportOutlined />} onClick={handleImportClick}>
            Import
          </Button>
          <Button danger icon={<ExportOutlined />} onClick={handleExport}>
            Export
          </Button>
        </Space>
      </div>
      <Table columns={columns} dataSource={filteredConfigs} loading={loading} />
    </div>
  );
};
