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

import {useQuery} from "@ahoo-wang/fetcher-react";
import {configApiClient} from "../../services/clients.ts";
import type {ConfigHistory} from "../../generated";
import {ConfigVersionDiffer} from "./ConfigVersionDiffer.tsx";
import {History} from "lucide-react";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {Button} from '@/components/ui/button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {Badge} from '@/components/ui/badge';
import dayjs from 'dayjs';

interface ConfigVersionTableProps {
    namespace: string;
    configId: string;
}

export function ConfigVersionTable({namespace, configId}: ConfigVersionTableProps) {
    const {loading, error, result: versions, execute: loadVersions} = useQuery<string, ConfigHistory[]>({
        query: `${namespace}/${configId}`,
        execute: async (_, __, abortController) => {
            const versionList = await configApiClient.getConfigVersions(namespace, configId, {abortController});
            return Promise.all(versionList.map(version =>
                configApiClient.getConfigHistory(namespace, configId, version.version, {abortController})
            ));
        }
    })
    const {openDrawer, closeDrawer} = useDrawer();
    const handleDiffVersion = (record: ConfigHistory) => {
        openDrawer(<ConfigVersionDiffer namespace={namespace} configId={configId} version={record.version}
                                        onSuccess={() => {
                                            closeDrawer();
                                            loadVersions()
                                        }}/>,
            {
                title: 'Config Version Differ',
                width: 'min(88vw, 1320px)',
            }
        )
    }
    const columns: DataTableColumn<ConfigHistory>[] = [
        {header: 'Version', accessor: 'version', key: 'version', sort: (left, right) => left.version - right.version},
        {
            header: 'Operation',
            key: 'operation',
            cell: record => <Badge variant="outline">{record.op}</Badge>,
        },
        {
            header: 'Updated',
            key: 'opTime',
            sort: (left, right) => left.opTime - right.opTime,
            cell: record => dayjs(record.opTime * 1000).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            header: 'Hash',
            key: 'hash',
            cell: record => <code className="text-xs" title={record.hash}>{record.hash.slice(0, 12)}</code>,
        },
        {
            header: 'Action', key: 'action', className: 'w-28 text-right', cell: record => (
                <Button variant="ghost" size="sm" onClick={() => {
                    handleDiffVersion(record)
                }}><History/>Diff</Button>
            )
        }
    ];

    return (
        <div className="space-y-3">
            <p className="text-xs text-muted-foreground">Change attribution is recorded in Audit Log.</p>
            <DataTable
                data={versions}
                getRowKey={record => record.version}
                columns={columns}
                loading={loading}
                error={error}
                onRetry={loadVersions}
                pagination={false}
                emptyMessage="No versions recorded yet."
            />
        </div>
    );
}
