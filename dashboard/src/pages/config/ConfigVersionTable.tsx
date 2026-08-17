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
import type {ConfigVersion} from "../../generated";
import {ConfigVersionDiffer} from "./ConfigVersionDiffer.tsx";
import {History} from "lucide-react";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import {Button} from '@/components/ui/button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';

interface ConfigVersionTableProps {
    namespace: string;
    configId: string;
}

export function ConfigVersionTable({namespace, configId}: ConfigVersionTableProps) {
    const {loading, result: versions, execute: loadVersions} = useQuery<string, ConfigVersion[]>({
        query: configId,
        execute: (query, attributes, abortController) => {
            return configApiClient.getConfigVersions(namespace, query, attributes, abortController);
        }
    })
    const {openDrawer, closeDrawer} = useDrawer();
    const handleDiffVersion = (record: ConfigVersion) => {
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
    const columns: DataTableColumn<ConfigVersion>[] = [
        {header: 'Version', accessor: 'version', key: 'version', sort: (left, right) => left.version - right.version},
        {
            header: 'Action', key: 'action', className: 'w-28 text-right', cell: record => (
                <Button variant="ghost" size="sm" onClick={() => {
                    handleDiffVersion(record)
                }}><History/>Diff</Button>
            )
        }
    ];

    return (
        <DataTable
            data={versions}
            getRowKey={record => record.version}
            columns={columns}
            loading={loading}
            pagination={false}
        />
    );
}
