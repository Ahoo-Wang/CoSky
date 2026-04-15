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

import React from 'react';
import type {ColumnType} from 'antd/es/table';
import {Button, Popconfirm, Space} from 'antd';
import {SearchOutlined} from '@ant-design/icons';
import {SearchFilter, useSearchFilter} from './SearchFilter';

// eslint-disable-next-line react-refresh/only-export-components
export {SearchFilter, useSearchFilter};

export interface SearchColumnProps<T> {
    title: string;
    dataIndex: Extract<keyof T, string | number>;
    placeholder?: string;
    sorter?: boolean;
}

// eslint-disable-next-line react-refresh/only-export-components
export function createSearchColumn<T>(props: SearchColumnProps<T>): ColumnType<T> {
    const {title, dataIndex, placeholder, sorter} = props;
    return {
        title,
        dataIndex,
        key: String(dataIndex),
        sorter: sorter ? (a: T, b: T) => {
            const aVal = a[dataIndex];
            const bVal = b[dataIndex];
            if (typeof aVal === 'string' && typeof bVal === 'string') {
                return aVal.localeCompare(bVal);
            }
            if (typeof aVal === 'number' && typeof bVal === 'number') {
                return aVal - bVal;
            }
            return 0;
        } : undefined,
        filterDropdown: (dropdownProps) => {
            const {value, onChange, onSearch, onReset} = (() => {
                const {setSelectedKeys, selectedKeys, confirm, clearFilters} = dropdownProps;
                return {
                    value: (selectedKeys[0] as string) ?? '',
                    onChange: (val: string) => setSelectedKeys(val ? [val] : []),
                    onSearch: () => confirm(),
                    onReset: () => clearFilters?.(),
                };
            })();

            return (
                <SearchFilter
                    placeholder={placeholder ?? `Search ${String(title)}`}
                    value={value}
                    onChange={onChange}
                    onSearch={onSearch}
                    onReset={onReset}
                />
            );
        },
        filterIcon: (filtered: boolean) => (
            <SearchOutlined style={{color: filtered ? '#1890ff' : undefined}}/>
        ),
        onFilter: (value: React.Key | boolean, record: T) => {
            const recordValue = record[dataIndex];
            return String(recordValue).toLowerCase().includes(String(value).toLowerCase());
        },
    };
}

export interface ActionItem<T = unknown> {
    key: string;
    label: string;
    icon?: React.ReactNode;
    danger?: boolean;
    confirm?: string;
    onClick: (record: T) => void;
}

export interface ActionColumnProps<T = unknown> {
    items: ActionItem<T>[];
}

// eslint-disable-next-line react-refresh/only-export-components
export function createActionColumn<T>(props: ActionColumnProps<T>): ColumnType<T> {
    return {
        title: 'Action',
        key: 'action',
        render: (_: unknown, record: T) => (
            <Space>
                {props.items.map((item) => {
                    if (item.confirm) {
                        return (
                            <Popconfirm
                                key={item.key}
                                title={item.confirm}
                                onConfirm={() => item.onClick(record)}
                            >
                                <Button type="link" danger={item.danger} icon={item.icon}>
                                    {item.label}
                                </Button>
                            </Popconfirm>
                        );
                    }
                    return (
                        <Button
                            key={item.key}
                            type="link"
                            danger={item.danger}
                            icon={item.icon}
                            onClick={() => item.onClick(record)}
                        >
                            {item.label}
                        </Button>
                    );
                })}
            </Space>
        ),
    };
}
