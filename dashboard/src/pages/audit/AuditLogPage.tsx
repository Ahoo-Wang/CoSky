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
import type {ColumnDef} from '@tanstack/react-table';
import dayjs from 'dayjs';
import {useQuery} from '@ahoo-wang/fetcher-react';
import type {AuditLog, QueryLogResponse} from '@/generated';
import {auditLogApiClient} from '@/services/clients';
import {PageHeader} from '@/components/layout/PageHeader';
import {DataTableWrapper} from '@/components/layout/DataTableWrapper';
import {DataTable} from '@/components/table/DataTable';

type Paging = {
    pageIndex: number;
    pageSize: number;
};

const INITIAL_PAGING: Paging = {
    pageIndex: 0,
    pageSize: 10,
};

export function AuditLogPage() {
    const [paging, setPaging] = useState<Paging>(INITIAL_PAGING);
    const {result, loading, error, execute, setQuery} = useQuery<Paging, QueryLogResponse>({
        initialQuery: INITIAL_PAGING,
        execute: (query, _, abortController) => {
            return auditLogApiClient.queryLog(
                query.pageIndex * query.pageSize,
                query.pageSize,
                {abortController},
            );
        },
    })

    const columns: ColumnDef<AuditLog>[] = [
        {
            accessorKey: 'opTime',
            header: () => <span>Timestamp</span>,
            cell: ({row}) => dayjs(row.original.opTime).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            accessorKey: 'operator',
            header: () => <span>Operator</span>,
        },
        {
            accessorKey: 'ip',
            header: () => <span>ClientIP</span>,
        },
        {
            accessorKey: 'resource',
            header: () => <span>Resource</span>,
        },
        {
            accessorKey: 'action',
            header: () => <span>Action</span>,
        },
        {
            accessorKey: 'status',
            header: () => <span>Status</span>,
        },
        {
            accessorKey: 'msg',
            header: () => <span>Msg</span>,
        },
    ];

    return (
        <div>
            <PageHeader title="Audit Log"/>
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result?.list ?? []}
                    loading={loading}
                    error={error}
                    onRetry={() => void execute()}
                    getRowId={(row) => `${row.operator}-${row.opTime}`}
                    pagination={{
                        mode: 'server',
                        pageIndex: paging.pageIndex,
                        pageSize: paging.pageSize,
                        total: result?.total ?? 0,
                        onPaginationChange: (pageIndex, pageSize) => {
                            setPaging({pageIndex, pageSize});
                            setQuery({pageIndex, pageSize});
                        },
                    }}
                />
            </DataTableWrapper>
        </div>
    );
}
