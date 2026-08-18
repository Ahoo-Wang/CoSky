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

import type {FormEvent} from 'react';
import {useState} from 'react';
import type {AuditLog, QueryLogResponse} from '../../generated';
import dayjs from 'dayjs';
import {useQuery} from '@ahoo-wang/fetcher-react';
import {Download, Eye, RotateCcw, Search} from 'lucide-react';
import {saveAs} from 'file-saver';
import {auditLogApiClient} from '../../services/clients.ts';
import {PageHeader} from '../../components/layout/PageHeader.tsx';
import {DataTableWrapper} from '../../components/layout/DataTableWrapper.tsx';
import {useDrawer} from '../../contexts/DrawerContext.tsx';
import {Badge} from '@/components/ui/badge';
import {Button} from '@/components/ui/button';
import {DataTable} from '@/components/ui/data-table';
import type {DataTableColumn} from '@/components/ui/data-table';
import {DefinitionList} from '@/components/ui/definition-list';
import {Input} from '@/components/ui/input';
import {Label} from '@/components/ui/label';
import {OptionsSelect} from '@/components/ui/options-select';
import {toast} from 'sonner';

type AuditStatus = 'all' | 'successful' | 'failed';

interface AuditFilters {
    query: string;
    from: string;
    to: string;
    status: AuditStatus;
}

interface AuditQuery extends AuditFilters {
    page: number;
    pageSize: number;
}

const EMPTY_FILTERS: AuditFilters = {query: '', from: '', to: '', status: 'all'};
const STATUS_OPTIONS = [
    {label: 'All statuses', value: 'all'},
    {label: 'Successful', value: 'successful'},
    {label: 'Failed', value: 'failed'},
];

const toTimestamp = (value: string) => value ? dayjs(value).valueOf() : undefined;
const toSuccessful = (status: AuditStatus) => status === 'all' ? undefined : status === 'successful';

function AuditLogDetails({log}: {log: AuditLog}) {
    return <DefinitionList items={[
        {label: 'Timestamp', value: dayjs(log.opTime).format('YYYY-MM-DD HH:mm:ss.SSS')},
        {label: 'Operator', value: log.operator},
        {label: 'Client IP', value: log.ip},
        {label: 'Resource', value: <code>{log.resource}</code>},
        {label: 'Action', value: log.action},
        {label: 'Status', value: <Badge variant={log.status < 400 ? 'secondary' : 'destructive'}>{log.status}</Badge>},
        {label: 'Message', value: log.msg || <span className="font-normal text-muted-foreground">No message recorded.</span>},
    ]}/>;
}

export function AuditLogPage() {
    const [page, setPage] = useState(1);
    const [draftFilters, setDraftFilters] = useState<AuditFilters>(EMPTY_FILTERS);
    const [filters, setFilters] = useState<AuditFilters>(EMPTY_FILTERS);
    const [exporting, setExporting] = useState(false);
    const {openDrawer} = useDrawer();
    const {result, loading, setQuery} = useQuery<AuditQuery, QueryLogResponse>({
        initialQuery: {...EMPTY_FILTERS, page: 1, pageSize: 10},
        execute: (query, _, abortController) => auditLogApiClient.searchLog(
            (query.page - 1) * query.pageSize,
            query.pageSize,
            query.query.trim() || undefined,
            toTimestamp(query.from),
            toTimestamp(query.to),
            toSuccessful(query.status),
            {abortController},
        ),
    });

    const applyFilters = (event: FormEvent) => {
        event.preventDefault();
        const from = toTimestamp(draftFilters.from);
        const to = toTimestamp(draftFilters.to);
        if (from !== undefined && to !== undefined && from > to) {
            toast.error('From must be earlier than To.');
            return;
        }
        setFilters(draftFilters);
        setPage(1);
        setQuery({...draftFilters, page: 1, pageSize: 10});
    };

    const clearFilters = () => {
        setDraftFilters(EMPTY_FILTERS);
        setFilters(EMPTY_FILTERS);
        setPage(1);
        setQuery({...EMPTY_FILTERS, page: 1, pageSize: 10});
    };

    const exportLogs = async () => {
        setExporting(true);
        try {
            const blob = await auditLogApiClient.exportLog(
                filters.query.trim() || undefined,
                toTimestamp(filters.from),
                toTimestamp(filters.to),
                toSuccessful(filters.status),
            );
            saveAs(blob, `cosky_audit_log_${dayjs().format('YYYYMMDD_HHmmss')}.csv`);
            toast.success('Audit log exported');
        } catch {
            toast.error('Failed to export audit log');
        } finally {
            setExporting(false);
        }
    };

    const columns: DataTableColumn<AuditLog>[] = [
        {
            header: 'Timestamp',
            key: 'opTime',
            sort: (left, right) => left.opTime - right.opTime,
            cell: record => dayjs(record.opTime).format('YYYY-MM-DD HH:mm:ss'),
        },
        {header: 'Operator', accessor: 'operator', key: 'operator'},
        {header: 'Client IP', accessor: 'ip', key: 'ip'},
        {
            header: 'Resource',
            key: 'resource',
            className: 'max-w-80',
            cell: record => <code className="block truncate text-xs" title={record.resource}>{record.resource}</code>,
        },
        {header: 'Action', accessor: 'action', key: 'action'},
        {
            header: 'Status',
            key: 'status',
            sort: (left, right) => left.status - right.status,
            cell: record => <Badge variant={record.status < 400 ? 'secondary' : 'destructive'}>{record.status}</Badge>,
        },
        {
            header: 'Message',
            key: 'msg',
            className: 'max-w-56',
            cell: record => record.msg
                ? <span className="block truncate" title={record.msg}>{record.msg}</span>
                : <span className="text-muted-foreground">—</span>,
        },
        {
            header: 'Details',
            key: 'details',
            className: 'w-24 text-right',
            cell: record => <Button variant="ghost" size="sm" onClick={() => openDrawer(
                <AuditLogDetails log={record}/>,
                {title: 'Audit Event Details', width: 'min(720px, 92vw)'},
            )}><Eye/> Details</Button>,
        },
    ];

    return (
        <div>
            <PageHeader
                title="Audit Log"
                description="Search and review administrative and security-sensitive operations."
                actions={<Button variant="outline" loading={exporting} onClick={exportLogs}><Download/>Export CSV</Button>}
            />
            <DataTableWrapper>
                <form className="mb-4 grid gap-3 xl:grid-cols-[minmax(18rem,1fr)_13rem_13rem_11rem_auto]" onSubmit={applyFilters}>
                    <div className="space-y-1.5">
                        <Label htmlFor="audit-query">Search all events</Label>
                        <Input id="audit-query" value={draftFilters.query}
                               onChange={event => setDraftFilters(current => ({...current, query: event.target.value}))}
                               placeholder="Operator, IP, resource, action, status, or message"/>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="audit-from">From</Label>
                        <Input id="audit-from" type="datetime-local" step="1" value={draftFilters.from}
                               onChange={event => setDraftFilters(current => ({...current, from: event.target.value}))}/>
                    </div>
                    <div className="space-y-1.5">
                        <Label htmlFor="audit-to">To</Label>
                        <Input id="audit-to" type="datetime-local" step="1" value={draftFilters.to}
                               onChange={event => setDraftFilters(current => ({...current, to: event.target.value}))}/>
                    </div>
                    <div className="space-y-1.5">
                        <Label>Status</Label>
                        <OptionsSelect ariaLabel="Filter by status" value={draftFilters.status}
                                       onChange={value => setDraftFilters(current => ({...current, status: value as AuditStatus}))}
                                       options={STATUS_OPTIONS}/>
                    </div>
                    <div className="flex items-end gap-2">
                        <Button type="submit"><Search/>Apply</Button>
                        <Button type="button" variant="ghost" onClick={clearFilters} aria-label="Clear audit filters"><RotateCcw/></Button>
                    </div>
                </form>
                <DataTable
                    columns={columns}
                    data={result?.list}
                    getRowKey={(record) => `${record.operator}-${record.opTime}-${record.resource}`}
                    pagination={{
                        page,
                        pageSize: 10,
                        total: result?.total,
                        onChange: (nextPage, pageSize) => {
                            setPage(nextPage);
                            setQuery({...filters, page: nextPage, pageSize});
                        },
                    }}
                    loading={loading}
                    emptyMessage={filters.query || filters.from || filters.to || filters.status !== 'all'
                        ? 'No audit events match these filters.'
                        : 'No audit events recorded yet.'}
                />
            </DataTableWrapper>
        </div>
    );
}
