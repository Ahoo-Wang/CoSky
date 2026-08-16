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

import {useState} from 'react';
import type {Column} from '@tanstack/react-table';
import {ArrowDown, ArrowUp, ArrowUpDown, Search} from 'lucide-react';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';
import {SearchFilter} from './SearchFilter';

export interface DataTableColumnHeaderProps<TData> {
    column: Column<TData, unknown>;
    title: string;
    placeholder?: string;
    sortable?: boolean;
    searchable?: boolean;
}

export function DataTableColumnHeader<TData>({column, title, placeholder, sortable = true, searchable = true}: DataTableColumnHeaderProps<TData>) {
    const [open, setOpen] = useState(false);
    const filterValue = (column.getFilterValue() as string) ?? '';
    const sorted = column.getIsSorted();
    return (
        <div className="flex items-center gap-1">
            <span>{title}</span>
            {sortable && (
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => column.toggleSorting(sorted === 'asc')}>
                    {sorted === 'asc' ? <ArrowUp className="h-3.5 w-3.5"/> : sorted === 'desc' ? <ArrowDown className="h-3.5 w-3.5"/> : <ArrowUpDown className="h-3.5 w-3.5 opacity-50"/>}
                </Button>
            )}
            {searchable && (
                <Popover open={open} onOpenChange={setOpen}>
                    <PopoverTrigger asChild>
                        <Button variant="ghost" size="icon" className="h-6 w-6">
                            <Search className={cn('h-3.5 w-3.5', filterValue ? 'text-primary' : 'opacity-50')}/>
                        </Button>
                    </PopoverTrigger>
                    <PopoverContent className="w-64" align="start">
                        <SearchFilter
                            placeholder={placeholder ?? `Search ${title}`}
                            value={filterValue}
                            onChange={(v) => column.setFilterValue(v || undefined)}
                            onSearch={() => setOpen(false)}
                            onReset={() => column.setFilterValue(undefined)}
                        />
                    </PopoverContent>
                </Popover>
            )}
        </div>
    );
}
