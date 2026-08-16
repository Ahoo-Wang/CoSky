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
import {History} from 'lucide-react';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {DataTable} from '@/components/table/DataTable';
import {createActionColumn} from '@/components/table/columns';
import {useDrawer} from '@/contexts/DrawerContext';
import {configApiClient} from '@/services/clients';
import type {ConfigVersion} from '@/generated';
import {ConfigVersionDiffer} from './ConfigVersionDiffer';

export interface ConfigVersionTableProps {
    namespace: string;
    configId: string;
}

export function ConfigVersionTable({namespace, configId}: ConfigVersionTableProps) {
    const {result: versions = [], loading, execute: loadVersions} = useQuery<string, ConfigVersion[]>({
        query: configId,
        execute: (query, _, abortController) => {
            return configApiClient.getConfigVersions(namespace, query, {abortController});
        },
    });
    const {openDrawer, closeDrawer} = useDrawer();

    const handleDiffVersion = (record: ConfigVersion) => {
        openDrawer(
            <ConfigVersionDiffer namespace={namespace} configId={configId} version={record.version}
                                 onSuccess={() => {
                                     closeDrawer();
                                     loadVersions();
                                 }}/>,
            {
                title: 'Config Version Differ',
                defaultSize: '80vw',
            },
        );
    };

    const columns: ColumnDef<ConfigVersion>[] = [
        {
            accessorKey: 'version',
            enableSorting: false,
            header: () => <span>Version</span>,
        },
        createActionColumn<ConfigVersion>({
            items: [
                {
                    key: 'diff',
                    label: 'Diff',
                    icon: <History className="mr-1 h-4 w-4"/>,
                    onClick: (record) => handleDiffVersion(record),
                },
            ],
        }),
    ];

    return (
        <DataTable
            columns={columns}
            data={versions}
            loading={loading}
            getRowId={(row) => `${row.version}`}
            showViewOptions={false}
        />
    );
}
