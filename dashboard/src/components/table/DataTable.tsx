/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
import {Fragment, useState, type ReactNode} from 'react';
import {
    flexRender, getCoreRowModel, getExpandedRowModel, getFilteredRowModel,
    getPaginationRowModel, getSortedRowModel, useReactTable,
    type ColumnDef, type PaginationState, type SortingState,
} from '@tanstack/react-table';
import {ChevronRight} from 'lucide-react';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@/components/ui/table';
import {Skeleton} from '@/components/ui/skeleton';
import {Button} from '@/components/ui/button';
import {cn} from '@/lib/utils';
import {Empty} from '@/components/feedback/Empty';
import {ErrorState} from '@/components/feedback/ErrorState';
import {DataTablePagination} from './DataTablePagination';
import {DataTableViewOptions} from './DataTableViewOptions';

export type DataTablePagination =
    | { mode: 'client'; pageSize?: number }
    | { mode: 'server'; pageIndex: number; pageSize: number; total: number; onPaginationChange: (pageIndex: number, pageSize: number) => void };

export interface DataTableProps<TData> {
    columns: ColumnDef<TData, any>[];
    data: TData[];
    loading?: boolean;
    error?: unknown;
    onRetry?: () => void;
    getRowId?: (row: TData) => string;
    renderExpanded?: (row: TData) => ReactNode;
    toolbar?: ReactNode;
    pagination?: DataTablePagination;
    showViewOptions?: boolean;
}

export function DataTable<TData>({
    columns, data, loading, error, onRetry, getRowId, renderExpanded, toolbar,
    pagination = {mode: 'client', pageSize: 10},
    showViewOptions = true,
}: DataTableProps<TData>) {
    const [sorting, setSorting] = useState<SortingState>([]);
    const [clientPagination, setClientPagination] = useState<PaginationState>({
        pageIndex: 0,
        pageSize: pagination.mode === 'client' ? (pagination.pageSize ?? 10) : 10,
    });
    const isServer = pagination.mode === 'server';
    const serverPagination: PaginationState = isServer
        ? {pageIndex: pagination.pageIndex, pageSize: pagination.pageSize}
        : clientPagination;

    const allColumns: ColumnDef<TData, any>[] = renderExpanded
        ? [{
            id: 'expander',
            enableSorting: false,
            enableHiding: false,
            header: () => null,
            cell: ({row}) => (
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => row.toggleExpanded()}>
                    <ChevronRight className={cn('h-4 w-4 transition-transform', row.getIsExpanded() && 'rotate-90')}/>
                </Button>
            ),
        }, ...columns]
        : columns;

    const table = useReactTable({
        data,
        columns: allColumns,
        getRowId,
        state: {sorting, pagination: serverPagination},
        onSortingChange: setSorting,
        onPaginationChange: isServer
            ? (updater) => {
                const next = typeof updater === 'function' ? updater(serverPagination) : updater;
                pagination.onPaginationChange(next.pageIndex, next.pageSize);
            }
            : setClientPagination,
        manualPagination: isServer,
        pageCount: isServer ? Math.ceil(pagination.total / pagination.pageSize) : undefined,
        getCoreRowModel: getCoreRowModel(),
        getSortedRowModel: getSortedRowModel(),
        getFilteredRowModel: getFilteredRowModel(),
        getPaginationRowModel: isServer ? undefined : getPaginationRowModel(),
        getExpandedRowModel: renderExpanded ? getExpandedRowModel() : undefined,
    });

    const columnCount = table.getAllColumns().length;
    const rows = table.getRowModel().rows;

    let body: ReactNode;
    if (loading && data.length === 0) {
        body = Array.from({length: 5}).map((_, i) => (
            <TableRow key={`skeleton-${i}`}>
                {allColumns.map((_, j) => (
                    <TableCell key={j}><Skeleton className="h-5 w-full"/></TableCell>
                ))}
            </TableRow>
        ));
    } else if (error) {
        body = (
            <TableRow>
                <TableCell colSpan={columnCount}><ErrorState error={error} onRetry={onRetry}/></TableCell>
            </TableRow>
        );
    } else if (rows.length === 0) {
        body = (
            <TableRow>
                <TableCell colSpan={columnCount}><Empty/></TableCell>
            </TableRow>
        );
    } else {
        body = rows.map((row) => (
            <Fragment key={row.id}>
                <TableRow data-state={row.getIsExpanded() ? 'expanded' : undefined}>
                    {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id}>
                            {flexRender(cell.column.columnDef.cell, cell.getContext())}
                        </TableCell>
                    ))}
                </TableRow>
                {row.getIsExpanded() && renderExpanded && (
                    <TableRow>
                        <TableCell colSpan={columnCount} className="bg-muted/40 p-4">
                            {renderExpanded(row.original)}
                        </TableCell>
                    </TableRow>
                )}
            </Fragment>
        ));
    }

    return (
        <div className="flex flex-col">
            {(toolbar || showViewOptions) && (
                <div className="flex items-center justify-between gap-2 border-b px-2 py-2">
                    <div className="flex items-center gap-2">{toolbar}</div>
                    {showViewOptions && <DataTableViewOptions table={table}/>}
                </div>
            )}
            <Table>
                <TableHeader>
                    {table.getHeaderGroups().map((headerGroup) => (
                        <TableRow key={headerGroup.id}>
                            {headerGroup.headers.map((header) => (
                                <TableHead key={header.id}>
                                    {header.isPlaceholder ? null : flexRender(header.column.columnDef.header, header.getContext())}
                                </TableHead>
                            ))}
                        </TableRow>
                    ))}
                </TableHeader>
                <TableBody className={loading && data.length > 0 ? 'opacity-60' : undefined}>
                    {body}
                </TableBody>
            </Table>
            {isServer && (
                <DataTablePagination table={table} pagination={pagination}/>
            )}
            {!isServer && table.getPageCount() > 1 && (
                <div className="flex items-center justify-end gap-2 border-t px-2 py-3">
                    <Button variant="outline" size="sm" disabled={!table.getCanPreviousPage()} onClick={() => table.previousPage()}>Previous</Button>
                    <span className="text-sm text-muted-foreground">
                        Page {table.getState().pagination.pageIndex + 1} of {table.getPageCount()}
                    </span>
                    <Button variant="outline" size="sm" disabled={!table.getCanNextPage()} onClick={() => table.nextPage()}>Next</Button>
                </div>
            )}
        </div>
    );
}
