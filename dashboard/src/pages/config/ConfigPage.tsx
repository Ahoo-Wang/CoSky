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

import type {ColumnDef} from '@tanstack/react-table';
import dayjs from 'dayjs';
import {saveAs} from 'file-saver';
import {Download, Pencil, Plus, Trash2, Upload} from 'lucide-react';
import {toast} from 'sonner';
import {useExecutePromise, useQuery} from '@ahoo-wang/fetcher-react';
import {Button} from '@/components/ui/button';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn, createSearchColumn} from '@/components/table/columns';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useDrawer} from '@/contexts/DrawerContext';
import {useDashboardCommand} from '@/lib/commands';
import {configApiClient} from '@/services/clients';
import {ConfigEditor} from './ConfigEditor';
import {ConfigImporter} from './ConfigImporter';
import {ConfigVersionTable} from './ConfigVersionTable';

type ListConfig = { configId: string };

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
                };
            });
        },
    });
    const {loading: exportLoading, execute: executeExport} = useExecutePromise({
        propagateError: true,
        onSuccess: () => {
            toast.success('Export config success');
        },
        onError: () => {
            toast.error('Export config failed');
        },
    });

    const handleExport = async () => {
        await executeExport(async () => {
            const blob = await configApiClient.exportZip(currentNamespace);
            const fileTime = dayjs().format('YYYY-MM-DD-HH-mm-ss');
            saveAs(blob, `cosky_${currentNamespace}-${fileTime}.zip`);
        });
    };

    const handleEditConfig = (configId?: string) => {
        openDrawer(
            <ConfigEditor namespace={currentNamespace} configId={configId} onSuccess={() => {
                loadConfigs();
                closeDrawer();
            }} onCancel={closeDrawer}/>,
            {
                title: `${configId === undefined ? 'Add' : 'Edit'} Config`,
            },
        );
    };

    const handleImportConfig = () => {
        openDrawer(
            <ConfigImporter namespace={currentNamespace}
                            onSuccess={() => {
                                closeDrawer();
                                loadConfigs();
                            }}
                            onCancel={closeDrawer}
            />,
            {
                title: 'Import Config',
            },
        );
    };

    useDashboardCommand('add-config', () => handleEditConfig());

    const {execute: deleteConfig} = useExecutePromise({
        onSuccess: () => {
            toast.success('Delete config success');
            loadConfigs();
        },
        onError: () => {
            toast.error('Delete config failed');
        },
    });

    const handleDelete = async (configId: string) => {
        await deleteConfig(() => {
            return configApiClient.removeConfig(currentNamespace, configId);
        });
    };

    const columns: ColumnDef<ListConfig>[] = [
        createSearchColumn<ListConfig>({
            title: 'Config ID',
            accessorKey: 'configId',
            placeholder: 'Search Config ID',
        }),
        createActionColumn<ListConfig>({
            items: [
                {
                    key: 'edit',
                    label: 'Edit',
                    icon: <Pencil className="mr-1 h-4 w-4"/>,
                    onClick: (record) => handleEditConfig(record.configId),
                },
                {
                    key: 'delete',
                    label: 'Delete',
                    icon: <Trash2 className="mr-1 h-4 w-4"/>,
                    danger: true,
                    confirm: 'Are you sure to delete this config?',
                    onClick: (record) => void handleDelete(record.configId),
                },
            ],
        }),
    ];

    return (
        <div>
            <PageHeader
                title="Configuration"
                actions={
                    <>
                        <Button onClick={() => handleEditConfig()}>
                            <Plus className="mr-1 h-4 w-4"/>
                            Add
                        </Button>
                        <Button variant="outline" onClick={handleImportConfig}>
                            <Upload className="mr-1 h-4 w-4"/>
                            Import
                        </Button>
                        <Button variant="outline" disabled={exportLoading} onClick={() => void handleExport()}>
                            <Download className="mr-1 h-4 w-4"/>
                            Export
                        </Button>
                    </>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={configs}
                    loading={loading}
                    error={error}
                    onRetry={() => void loadConfigs()}
                    getRowId={(row) => row.configId}
                    renderExpanded={(row) => (
                        <ConfigVersionTable namespace={currentNamespace} configId={row.configId}/>
                    )}
                />
            </DataTableWrapper>
        </div>
    );
}
