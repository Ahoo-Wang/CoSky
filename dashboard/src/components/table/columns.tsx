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
import {useState, type ReactNode} from 'react';
import type {ColumnDef} from '@tanstack/react-table';
import {Button} from '@/components/ui/button';
import {ConfirmDialog} from '@/components/feedback/ConfirmDialog';
import {DataTableColumnHeader} from './DataTableColumnHeader';

export interface SearchColumnProps<T> {
    title: string;
    accessorKey: Extract<keyof T, string>;
    placeholder?: string;
    enableSorting?: boolean;
}

export function createSearchColumn<T>(props: SearchColumnProps<T>): ColumnDef<T, any> {
    return {
        accessorKey: props.accessorKey,
        enableSorting: props.enableSorting ?? true,
        filterFn: 'includesString',
        header: ({column}) => (
            <DataTableColumnHeader
                column={column}
                title={props.title}
                placeholder={props.placeholder}
                sortable={props.enableSorting ?? true}
            />
        ),
    };
}

export interface ActionItem<T = unknown> {
    key: string;
    label: string;
    icon?: ReactNode;
    danger?: boolean;
    confirm?: string;
    disabled?: (record: T) => boolean;
    onClick: (record: T) => void;
}

// eslint-disable-next-line react-refresh/only-export-components -- helper column factory file by design
function ActionCell<T>({items, record}: { items: ActionItem<T>[]; record: T }) {
    const [pending, setPending] = useState<ActionItem<T> | null>(null);
    return (
        <div className="flex items-center gap-1">
            {items.map((item) => (
                <Button
                    key={item.key}
                    variant="link"
                    size="sm"
                    disabled={item.disabled?.(record)}
                    className={item.danger ? 'text-destructive hover:text-destructive' : undefined}
                    onClick={() => {
                        if (item.confirm) {
                            setPending(item);
                        } else {
                            item.onClick(record);
                        }
                    }}
                >
                    {item.icon}
                    {item.label}
                </Button>
            ))}
            <ConfirmDialog
                open={pending !== null}
                onOpenChange={(open) => { if (!open) setPending(null); }}
                title={pending?.confirm ?? ''}
                danger={pending?.danger}
                onConfirm={() => {
                    pending?.onClick(record);
                    setPending(null);
                }}
            />
        </div>
    );
}

export function createActionColumn<T>({items}: { items: ActionItem<T>[] }): ColumnDef<T, any> {
    return {
        id: 'actions',
        enableSorting: false,
        enableHiding: false,
        header: () => <span>Action</span>,
        cell: ({row}) => <ActionCell items={items} record={row.original}/>,
    };
}
