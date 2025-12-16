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

import React from 'react';
import {Table, Button, Space, message, Popconfirm} from 'antd';
import {
    PlusOutlined,
    DeleteOutlined,
    EditOutlined,
    ExportOutlined,
    ImportOutlined,
} from '@ant-design/icons';
import {useNamespaceContext} from '../../contexts/NamespaceContext.tsx';
import {useExecutePromise, useQuery} from '@ahoo-wang/fetcher-react';
import {configApiClient} from "../../services/clients.ts";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {ConfigEditor} from "./ConfigEditor.tsx";
import {ConfigVersionTable} from "./ConfigVersionTable.tsx";
import {ConfigImporter} from "./ConfigImporter.tsx";
import {saveAs} from 'file-saver';
import dayjs from "dayjs";

export const ConfigPage: React.FC = () => {
    const {currentNamespace} = useNamespaceContext();
    const {openDrawer, closeDrawer} = useDrawer();
    const {result: configs = [], loading, execute: loadConfigs} = useQuery<string, string[]>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => {
            return configApiClient.getConfigs(namespace, {abortController});
        },
    });
    const {loading: exportLoading, execute: executeExport} = useExecutePromise({
        propagateError: true,
        onSuccess: () => {
            message.success('Export config success');
        },
        onError: () => {
            message.error('Export config failed')
        }
    })

    const handleExport = async () => {
        await executeExport(async () => {
            const blob = await configApiClient.exportZip(currentNamespace);
            const fileTime = dayjs().format('YYYY-MM-DD-HH-mm-ss');
            saveAs(blob, `cosky_${currentNamespace}-${fileTime}.zip`);
        })
    }

    const handleEditConfig = (configId?: string) => {
        openDrawer(<ConfigEditor namespace={currentNamespace} configId={configId} onSuccess={() => {
            closeDrawer();
            loadConfigs();
        }} onCancel={closeDrawer}/>, {
            title: 'Add Config',
        });
    };
    const handleImportConfig = () => {
        openDrawer(<ConfigImporter namespace={currentNamespace}
                                   onSuccess={() => {
                                       closeDrawer();
                                       loadConfigs();
                                   }}
                                   onCancel={closeDrawer}
        />, {
            title: 'Import Config',
        });
    };
    const {execute: deleteConfig} = useExecutePromise({
        onSuccess: () => {
            message.success('Delete config success');
            loadConfigs();
        },
        onError: () => {
            message.error('Delete config failed')
        }
    })
    const handleDelete = async (configId: string) => {
        await deleteConfig(() => {
            return configApiClient.removeConfig(currentNamespace, configId);
        })
    };


    const expandedRowRender = (record: string) => {
        return (
            <ConfigVersionTable namespace={currentNamespace} configId={record}/>
        )
    }
    const columns = [
        {
            title: 'Config ID',
            key: 'configId'
        },
        {
            title: 'Action',
            key: 'action',
            render: (_: any, record: string) => (
                <Space>
                    <Button type="link" icon={<EditOutlined/>}
                            onClick={() => handleEditConfig(record)}
                    >
                        Edit
                    </Button>
                    <Popconfirm
                        title="Are you sure to delete this config?"
                        onConfirm={() => handleDelete(record)}
                        okText="Yes"
                        cancelText="No"
                    >
                        <Button type="link" danger icon={<DeleteOutlined/>}>
                            Delete
                        </Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

    return (
        <div>
            <div style={{
                marginBottom: 24, 
                display: 'flex', 
                justifyContent: 'space-between',
                alignItems: 'center',
            }}>
                <h2 style={{
                    margin: 0,
                    fontSize: '28px',
                    fontWeight: 600,
                    color: '#262626',
                    letterSpacing: '-0.5px',
                }}>Configuration</h2>
                <Space size="middle">
                    <Button type="primary" icon={<PlusOutlined/>}
                            onClick={() => handleEditConfig()}
                            size="large"
                    >
                        Add
                    </Button>
                    <Button icon={<ImportOutlined/>}
                            onClick={handleImportConfig}
                            size="large"
                    >
                        Import
                    </Button>
                    <Button icon={<ExportOutlined/>} loading={exportLoading}
                            onClick={handleExport}
                            size="large"
                    >
                        Export
                    </Button>
                </Space>
            </div>
            <Table 
                columns={columns} 
                dataSource={configs}
                rowKey={(record) => record}
                loading={loading}
                expandable={{
                    expandedRowRender
                }}
                style={{
                    background: '#fff',
                    borderRadius: 12,
                    overflow: 'hidden',
                    boxShadow: '0 2px 8px rgba(0, 0, 0, 0.08)',
                }}
            />
        </div>
    );
};
