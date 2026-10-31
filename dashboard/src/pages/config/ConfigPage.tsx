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

import {useState} from 'react';
import {Download, Pencil, Plus, Trash2, Upload} from 'lucide-react';
import '@/monacoConfig';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {saveAs} from 'file-saver';
import dayjs from 'dayjs';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useDrawer} from '@/contexts/DrawerContext';
import {useWatchRefresh} from '@/hooks/useWatchRefresh';
import {configs, Kind} from '@/services/resources';
import type {ConfigResource} from '@/services/resources';
import {ConfigEditor} from '@/pages/config/ConfigEditor';
import {ConfigImporter} from '@/pages/config/ConfigImporter';
import {ConfigVersionTable} from '@/pages/config/ConfigVersionTable';
import {formatLabel} from '@/pages/config/formats';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {PageHeader} from '@/components/layout/PageHeader';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {ConfirmButton} from '@/components/ui/confirm-button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {runAction} from '@/lib/actions';

export function ConfigPage() {
    const {currentNamespace} = useCurrentNamespaceContext();
    const {openDrawer, closeDrawer} = useDrawer();
    const [exporting, setExporting] = useState(false);
    const {result = [], loading, error, execute: loadConfigs} = useQuery<string, ConfigResource[]>({
        query: currentNamespace,
        execute: (namespace, _, abortController) => configs.list(namespace, abortController),
    });
    useWatchRefresh(currentNamespace, Kind.KIND_CONFIG, loadConfigs);

    const handleExport = async () => {
        setExporting(true);
        await runAction(async () => {
            const archive = await configs.exportArchive(currentNamespace);
            saveAs(archive, `cosky_${currentNamespace}-${dayjs().format('YYYY-MM-DD-HH-mm-ss')}.zip`);
        }, {success: 'Configurations exported', failure: 'Failed to export configurations'});
        setExporting(false);
    };

    const handleEdit = (config?: ConfigResource) => {
        openDrawer(
            <ConfigEditor
                namespace={currentNamespace}
                name={config?.name}
                onSuccess={() => {
                    closeDrawer();
                    loadConfigs();
                }}
                onCancel={closeDrawer}
            />,
            {title: config ? 'Edit Config' : 'Add Config'},
        );
    };

    const handleImport = () => {
        openDrawer(
            <ConfigImporter
                namespace={currentNamespace}
                onSuccess={() => {
                    closeDrawer();
                    loadConfigs();
                }}
                onCancel={closeDrawer}
            />,
            {title: 'Import Configs', width: 'min(640px, 92vw)'},
        );
    };

    const handleDelete = async (name: string) => {
        const deleted = await runAction(
            () => configs.delete(currentNamespace, name),
            {success: 'Config deleted', failure: 'Failed to delete config'},
        );
        if (deleted) {
            loadConfigs();
        }
    };

    const columns: DataTableColumn<ConfigResource>[] = [
        {
            header: 'Name',
            accessor: 'name',
            key: 'name',
            sort: (left, right) => left.name.localeCompare(right.name),
        },
        {
            header: 'Format',
            key: 'format',
            cell: config => <Badge variant="outline">{formatLabel(config.format)}</Badge>,
        },
        {
            header: 'Updated',
            key: 'updated',
            className: 'max-sm:hidden',
            cell: config => `${dayjs(config.updatedAt).format('YYYY-MM-DD HH:mm:ss')} by ${config.updatedBy}`,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-48 text-right',
            cell: config => (
                <div className="flex justify-end gap-1">
                    <Button variant="ghost" size="sm" onClick={() => handleEdit(config)}>
                        <Pencil/> Edit
                    </Button>
                    <ConfirmButton
                        title="Are you sure to delete this config?"
                        description={`Configuration “${config.name}” will be deleted. Its history stays until it is created again, and can bring it back.`}
                        onConfirm={() => handleDelete(config.name)}
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
                        <Button onClick={() => handleEdit()}><Plus/> Add</Button>
                        <Button variant="outline" onClick={handleImport}><Upload/> Import</Button>
                        <Button variant="outline" loading={exporting} onClick={handleExport}><Download/> Export</Button>
                    </>
                }
            />
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result}
                    getRowKey={config => config.name}
                    loading={loading}
                    error={error}
                    onRetry={loadConfigs}
                    expandable={{
                        render: config => <ConfigVersionTable namespace={currentNamespace} name={config.name}/>,
                    }}
                    search={{placeholder: 'Search configurations...', getValue: config => config.name}}
                    emptyMessage="No configurations in this namespace yet."
                />
            </DataTableWrapper>
        </div>
    );
}
