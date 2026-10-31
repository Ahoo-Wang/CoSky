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

import {useQuery} from '@ahoo-wang/fetcher-react';
import {History} from 'lucide-react';
import dayjs from 'dayjs';
import {useDrawer} from '@/contexts/DrawerContext';
import {configs} from '@/services/resources';
import type {ConfigResource} from '@/services/resources';
import {ConfigVersionDiffer} from '@/pages/config/ConfigVersionDiffer';
import {Button} from '@/components/ui/button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';

interface ConfigVersionTableProps {
    namespace: string;
    name: string;
}

export function ConfigVersionTable({namespace, name}: ConfigVersionTableProps) {
    const {openDrawer, closeDrawer} = useDrawer();
    const {result: versions, loading, error, execute: loadVersions} = useQuery<string, ConfigResource[]>({
        query: `${namespace}/${name}`,
        execute: (_, __, abortController) => configs.history(namespace, name, abortController),
    });

    const handleDiff = (version: ConfigResource) => {
        openDrawer(
            <ConfigVersionDiffer
                namespace={namespace}
                name={name}
                version={version}
                onSuccess={() => {
                    closeDrawer();
                    loadVersions();
                }}
            />,
            {title: 'Compare with the current version', width: 'min(88vw, 1320px)'},
        );
    };

    const columns: DataTableColumn<ConfigResource>[] = [
        {header: 'Revision', accessor: 'revision', key: 'revision'},
        {
            header: 'Updated',
            key: 'updatedAt',
            cell: version => dayjs(version.updatedAt).format('YYYY-MM-DD HH:mm:ss'),
        },
        {header: 'By', accessor: 'updatedBy', key: 'updatedBy'},
        {
            header: 'SHA-256',
            key: 'sha256',
            cell: version => <code className="text-xs" title={version.sha256}>{version.sha256.slice(0, 12)}</code>,
        },
        {
            header: 'Action',
            key: 'action',
            className: 'w-28 text-right',
            cell: version => (
                <Button variant="ghost" size="sm" onClick={() => handleDiff(version)}><History/>Diff</Button>
            ),
        },
    ];

    return (
        <DataTable
            data={versions}
            getRowKey={version => version.revision}
            columns={columns}
            loading={loading}
            error={error}
            onRetry={loadVersions}
            pagination={false}
            emptyMessage="No versions recorded yet."
        />
    );
}
