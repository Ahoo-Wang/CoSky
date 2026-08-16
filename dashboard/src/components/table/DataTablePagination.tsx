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

import type {Table} from '@tanstack/react-table';
import {ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import type {DataTablePagination as PaginationProp} from './DataTable';

interface Props<TData> {
    table: Table<TData>;
    pagination: Extract<PaginationProp, { mode: 'server' }>;
}

export function DataTablePagination<TData>({pagination}: Props<TData>) {
    const {pageIndex, pageSize, total, onPaginationChange} = pagination;
    const pageCount = Math.max(Math.ceil(total / pageSize), 1);
    return (
        <div className="flex items-center justify-between px-2 py-3">
            <span className="text-sm text-muted-foreground">Total {total} items</span>
            <div className="flex items-center gap-4">
                <div className="flex items-center gap-2">
                    <span className="text-sm text-muted-foreground">Rows per page</span>
                    <Select value={String(pageSize)} onValueChange={(v) => onPaginationChange(0, Number(v))}>
                        <SelectTrigger className="h-8 w-[70px]"><SelectValue/></SelectTrigger>
                        <SelectContent>
                            {[10, 20, 50, 100].map((size) => (
                                <SelectItem key={size} value={String(size)}>{size}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>
                <span className="text-sm text-muted-foreground">Page {pageIndex + 1} of {pageCount}</span>
                <div className="flex items-center gap-1">
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex <= 0} onClick={() => onPaginationChange(0, pageSize)}>
                        <ChevronsLeft className="h-4 w-4"/>
                    </Button>
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex <= 0} onClick={() => onPaginationChange(pageIndex - 1, pageSize)}>
                        <ChevronLeft className="h-4 w-4"/>
                    </Button>
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex >= pageCount - 1} onClick={() => onPaginationChange(pageIndex + 1, pageSize)}>
                        <ChevronRight className="h-4 w-4"/>
                    </Button>
                    <Button variant="outline" size="icon" className="h-8 w-8" disabled={pageIndex >= pageCount - 1} onClick={() => onPaginationChange(pageCount - 1, pageSize)}>
                        <ChevronsRight className="h-4 w-4"/>
                    </Button>
                </div>
            </div>
        </div>
    );
}
