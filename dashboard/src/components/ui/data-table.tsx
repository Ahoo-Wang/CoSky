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

import {Fragment, useMemo, useState} from 'react';
import type {ReactNode} from 'react';
import {ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight, Inbox, Search, SearchX} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {Skeleton} from '@/components/ui/skeleton';
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table';
import {cn} from '@/lib/utils';

const EMPTY_DATA: never[] = [];

export interface DataTableColumn<T> {
    key: string;
    header: ReactNode;
    accessor?: keyof T;
    cell?: (row: T) => ReactNode;
    sort?: (left: T, right: T) => number;
    className?: string;
}

interface DataTableSearch<T> {
    placeholder: string;
    getValue: (row: T) => string;
}

interface DataTableExpandable<T> {
    render: (row: T) => ReactNode;
    canExpand?: (row: T) => boolean;
}

interface DataTablePagination {
    page?: number;
    pageSize?: number;
    total?: number;
    onChange?: (page: number, pageSize: number) => void;
}

interface DataTableProps<T> {
    data?: T[];
    columns: DataTableColumn<T>[];
    getRowKey: (row: T) => string | number;
    loading?: boolean;
    search?: DataTableSearch<T>;
    expandable?: DataTableExpandable<T>;
    pagination?: false | DataTablePagination;
    emptyMessage?: ReactNode;
    className?: string;
}

export function DataTable<T>({
    data = EMPTY_DATA,
    columns,
    getRowKey,
    loading,
    search,
    expandable,
    pagination = {},
    emptyMessage,
    className,
}: DataTableProps<T>) {
    const [query, setQuery] = useState('');
    const [sortKey, setSortKey] = useState<string>();
    const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
    const [localPage, setLocalPage] = useState(1);
    const [expandedRows, setExpandedRows] = useState<Set<string | number>>(new Set());
    const [previousData, setPreviousData] = useState(data);
    if (pagination !== false && pagination.page === undefined && previousData !== data) {
        setPreviousData(data);
        setLocalPage(1);
    }
    const page = pagination === false ? 1 : (pagination.page ?? localPage);
    const pageSize = pagination === false ? Math.max(data.length, 1) : (pagination.pageSize ?? 10);

    const filteredRows = useMemo(() => {
        const normalizedQuery = query.trim().toLowerCase();
        const filtered = normalizedQuery && search
            ? data.filter(row => search.getValue(row).toLowerCase().includes(normalizedQuery))
            : [...data];
        const column = columns.find(item => item.key === sortKey);
        if (column?.sort) {
            filtered.sort((left, right) => column.sort!(left, right) * (sortDirection === 'asc' ? 1 : -1));
        }
        return filtered;
    }, [columns, data, query, search, sortDirection, sortKey]);

    const hasFilter = Boolean(search && query.trim());
    const total = hasFilter || pagination === false ? filteredRows.length : (pagination.total ?? filteredRows.length);
    const pageCount = Math.max(1, Math.ceil(total / pageSize));
    const safePage = Math.min(page, pageCount);
    const rows = pagination === false || (pagination.total !== undefined && !hasFilter)
        ? filteredRows
        : filteredRows.slice((safePage - 1) * pageSize, safePage * pageSize);

    const changePage = (nextPage: number) => {
        const safePage = Math.min(Math.max(nextPage, 1), pageCount);
        if (pagination !== false) {
            pagination.onChange?.(safePage, pageSize);
        }
        setLocalPage(safePage);
    };

    const changeSort = (column: DataTableColumn<T>) => {
        if (!column.sort) return;
        if (sortKey === column.key) {
            setSortDirection(direction => direction === 'asc' ? 'desc' : 'asc');
        } else {
            setSortKey(column.key);
            setSortDirection('asc');
        }
    };

    const toggleExpanded = (key: string | number) => {
        setExpandedRows(current => {
            const next = new Set(current);
            if (next.has(key)) next.delete(key);
            else next.add(key);
            return next;
        });
    };

    return (
        <div className={cn('space-y-3', className)}>
            {search && (
                <div className="relative w-full max-w-xl">
                    <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
                    <Input
                        value={query}
                        onChange={(event) => {
                            setQuery(event.target.value);
                            setLocalPage(1);
                        }}
                        placeholder={search.placeholder}
                        className="pl-8"
                        style={{paddingLeft: '2.25rem'}}
                        aria-label={search.placeholder}
                    />
                </div>
            )}
            <div className="overflow-hidden rounded-xl border bg-card">
                <Table>
                        <TableHeader>
                            <TableRow>
                                {expandable && <TableHead className="w-10"/>}
                                {columns.map(column => (
                                    <TableHead key={column.key} className={column.className}>
                                        {column.sort ? (
                                            <button
                                                type="button"
                                                onClick={() => changeSort(column)}
                                                className="inline-flex items-center gap-1.5 font-medium hover:text-foreground"
                                            >
                                                {column.header}
                                                {sortKey !== column.key && <ArrowUpDown className="size-3.5"/>}
                                                {sortKey === column.key && sortDirection === 'asc' && <ArrowUp className="size-3.5"/>}
                                                {sortKey === column.key && sortDirection === 'desc' && <ArrowDown className="size-3.5"/>}
                                            </button>
                                        ) : column.header}
                                    </TableHead>
                                ))}
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {loading && Array.from({length: 5}).map((_, index) => (
                                <TableRow key={index}>
                                    {expandable && <TableCell><Skeleton className="size-6"/></TableCell>}
                                    {columns.map(column => (
                                        <TableCell key={column.key}><Skeleton className="h-4 w-full max-w-40"/></TableCell>
                                    ))}
                                </TableRow>
                            ))}
                            {!loading && rows.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={columns.length + (expandable ? 1 : 0)} className="h-40 text-center text-muted-foreground">
                                        <div className="grid place-items-center gap-2">
                                            {hasFilter
                                                ? <SearchX className="size-7 opacity-50"/>
                                                : <Inbox className="size-7 opacity-50"/>}
                                            <span>{hasFilter ? 'No matching results.' : (emptyMessage ?? 'No data yet.')}</span>
                                        </div>
                                    </TableCell>
                                </TableRow>
                            )}
                            {!loading && rows.map(row => {
                                const key = getRowKey(row);
                                const canExpand = expandable && (expandable.canExpand?.(row) ?? true);
                                const isExpanded = expandedRows.has(key);
                                return (
                                    <Fragment key={key}>
                                        <TableRow data-state={isExpanded ? 'selected' : undefined}>
                                            {expandable && (
                                                <TableCell>
                                                    {canExpand && (
                                                        <Button
                                                            type="button"
                                                            variant="ghost"
                                                            size="icon-sm"
                                                            onClick={() => toggleExpanded(key)}
                                                            aria-label={isExpanded ? 'Collapse row' : 'Expand row'}
                                                        >
                                                            <ChevronRight className={cn('transition-transform', isExpanded && 'rotate-90')}/>
                                                        </Button>
                                                    )}
                                                </TableCell>
                                            )}
                                            {columns.map(column => (
                                                <TableCell key={column.key} className={column.className}>
                                                    {column.cell
                                                        ? column.cell(row)
                                                        : column.accessor
                                                            ? String(row[column.accessor] ?? '')
                                                            : null}
                                                </TableCell>
                                            ))}
                                        </TableRow>
                                        {canExpand && isExpanded && (
                                            <TableRow>
                                                <TableCell colSpan={columns.length + 1} className="bg-muted/25 p-4">
                                                    {expandable.render(row)}
                                                </TableCell>
                                            </TableRow>
                                        )}
                                    </Fragment>
                                );
                            })}
                        </TableBody>
                </Table>
            </div>
            {pagination !== false && total > 0 && (
                <div className="flex items-center justify-between gap-4 text-sm text-muted-foreground">
                    <span>{total} {hasFilter ? `matching item${total === 1 ? '' : 's'} on this page` : `item${total === 1 ? '' : 's'}`}</span>
                    <div className="flex items-center gap-2">
                        <Button variant="outline" size="icon-sm" disabled={safePage <= 1} onClick={() => changePage(safePage - 1)} aria-label="Previous page">
                            <ChevronLeft/>
                        </Button>
                        <span className="min-w-20 text-center">{safePage} / {pageCount}</span>
                        <Button variant="outline" size="icon-sm" disabled={safePage >= pageCount} onClick={() => changePage(safePage + 1)} aria-label="Next page">
                            <ChevronRight/>
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
}
