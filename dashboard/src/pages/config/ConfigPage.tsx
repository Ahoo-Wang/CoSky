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

import {Download, Pencil, Plus, Trash2, Upload} from 'lucide-react';
import '../../monacoConfig';
import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import {useExecutePromise, useQuery} from '@ahoo-wang/fetcher-react';
import {configApiClient} from "../../services/clients.ts";
import {ConfigEditor} from "./ConfigEditor.tsx";
import {ConfigVersionTable} from "./ConfigVersionTable.tsx";
import {ConfigImporter} from "./ConfigImporter.tsx";
import {saveAs} from 'file-saver';
import dayjs from "dayjs";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {PageHeader} from "../../components/layout/PageHeader.tsx";
import {DataTableWrapper} from "../../components/layout/DataTableWrapper.tsx";
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';

type ListConfig = { configId: string }

export function ConfigPage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {openDrawer, closeDrawer} = useDrawer();
    const {result: configs = [], loading, error, execute: loadConfigs} = useQuery<string, ListConfig[]>({
        query: currentNamespace,
        execute: async (namespace, _, abortController) => {
            const responseResult = await configApiClient.getConfigs(namespace, {abortController});
            return responseResult.map(config => {
                return {
                    configId: config,
                }
            });
        },
    });
    const {loading: exportLoading, execute: executeExport} = useExecutePromise({
        onSuccess: () => {
            toast.success('Export config success');
        },
        onError: () => {
            toast.error('Export config failed');
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
            loadConfigs();
            closeDrawer();
        }} onCancel={closeDrawer}/>, {
            title: `${configId === undefined ? "Add" : "Edit"} Config`,
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
            width: 'min(640px, 92vw)',
        });
    };
    const {execute: deleteConfig} = useExecutePromise({
        onSuccess: () => {
            toast.success('Delete config success');
            loadConfigs();
        },
        onError: () => {
            toast.error('Delete config failed');
        }
    })
    const handleDelete = async (configId: string) => {
        await deleteConfig(() => {
            return configApiClient.removeConfig(currentNamespace, configId);
        })
    };


    const expandedRowRender = (record: ListConfig) => {
        return (
            <ConfigVersionTable namespace={currentNamespace} configId={record.configId}/>
        )
    }
    const columns: DataTableColumn<ListConfig>[] = [
        {
            header: 'Config ID',
            accessor: 'configId',
            key: 'configId',
            sort: (left, right) => left.configId.localeCompare(right.configId),
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-48 text-right',
            cell: record => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEditConfig(record.configId)}>
                        <Pencil/> Edit
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this config?"
                        description={`Configuration “${record.configId}” and access to its retained version history will be removed from ${currentNamespace}.`}
                        onConfirm={() => handleDelete(record.configId)}
                        variant="ghost"
                        size="sm"
                        className="text-destructive"
                    >
                        <Trash2/> Delete
                    </ConfirmButton>
                </div>
            ),
        },
    ];

    return (
        <div>
            <PageHeader
                title="Configuration"
                description={`Manage configuration data and version history in ${currentNamespace}.`}
                actions={
                    <>
                        <Button onClick={() => handleEditConfig()}>
                            <Plus/> Add
                        </Button>
                        <Button variant="outline" onClick={handleImportConfig}>
                            <Upload/> Import
                        </Button>
                        <Button variant="outline" loading={exportLoading} onClick={handleExport}>
                            <Download/> Export
                        </Button>
                    </>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={configs}
                    getRowKey={record => record.configId}
                    loading={loading}
                    error={error}
                    onRetry={loadConfigs}
                    expandable={{
                        render: expandedRowRender,
                    }}
                    search={{placeholder: 'Search configurations...', getValue: record => record.configId}}
                    emptyMessage="No configurations in this namespace yet."
                />
            </DataTableWrapper>
        </div>
    );
}
