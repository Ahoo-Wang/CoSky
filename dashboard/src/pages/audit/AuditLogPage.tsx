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

import type {AuditLog, QueryLogResponse} from '../../generated';
import dayjs from 'dayjs';
import {useQuery} from "@ahoo-wang/fetcher-react";
import {auditLogApiClient} from "../../services/clients.ts";
import {useState} from 'react';
import {PageHeader} from '../../components/layout/PageHeader.tsx';
import {DataTableWrapper} from '../../components/layout/DataTableWrapper.tsx';
import {Badge} from '@/components/ui/badge';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';

type Paging = {
    page: number;
    pageSize: number;
};

export function AuditLogPage() {
    const [page, setPage] = useState(1);
    const {result, loading, setQuery} = useQuery<Paging, QueryLogResponse>({
        initialQuery: {
            page: 1,
            pageSize: 10,
        },
        execute: (query, _, abortController) => {
            return auditLogApiClient.queryLog((query.page - 1) * query.pageSize, query.pageSize, {abortController});
        },
    })

    const columns: DataTableColumn<AuditLog>[] = [
        {
            header: 'Timestamp',
            key: 'opTime',
            sort: (left, right) => left.opTime - right.opTime,
            cell: record => dayjs(record.opTime).format('YYYY-MM-DD HH:mm:ss'),
        },
        {
            header: 'Operator',
            accessor: 'operator',
            key: 'operator',
        },
        {
            header: 'Client IP',
            accessor: 'ip',
            key: 'ip',
        },
        {
            header: 'Resource',
            accessor: 'resource',
            key: 'resource',
        },
        {
            header: 'Action',
            accessor: 'action',
            key: 'action',
        },
        {
            header: 'Status',
            key: 'status',
            sort: (left, right) => left.status - right.status,
            cell: record => <Badge variant={record.status < 400 ? 'secondary' : 'destructive'}>{record.status}</Badge>,
        },
        {
            header: 'Message',
            accessor: 'msg',
            key: 'msg',
            className: 'max-w-72 whitespace-normal',
        },
    ];

    return (
        <div>
            <PageHeader title="Audit Log" description="Review administrative and security-sensitive operations."/>
            <DataTableWrapper>
                <DataTable
                    columns={columns}
                    data={result?.list}
                    getRowKey={(record) => `${record.operator}-${record.opTime}`}
                    search={{
                        placeholder: 'Filter this page by time, operator, status, resource, or action...',
                        getValue: record => `${dayjs(record.opTime).format('YYYY-MM-DD HH:mm:ss')} ${record.operator} ${record.ip} ${record.resource} ${record.action} ${record.status} ${record.msg}`,
                    }}
                    pagination={{
                        page,
                        pageSize: 10,
                        total: result?.total,
                        onChange: (page, pageSize) => {
                            setPage(page);
                            setQuery({page, pageSize});
                        }
                    }}
                    loading={loading}
                    emptyMessage="No audit events recorded yet."
                />
            </DataTableWrapper>
        </div>
    );
}
