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

import {Button, Input, Space} from 'antd';
import {SearchOutlined} from '@ant-design/icons';
import type {FilterDropdownProps} from 'antd/es/table/interface';

interface SearchFilterProps {
    placeholder?: string;
    value?: string;
    onChange?: (value: string) => void;
    onSearch?: () => void;
    onReset?: () => void;
}

 
export function SearchFilter({
    placeholder = 'Search...',
    value,
    onChange,
    onSearch,
    onReset,
}: SearchFilterProps) {
    return (
        <div style={{padding: 8}}>
            <Input
                placeholder={placeholder}
                value={value}
                onChange={(e) => onChange?.(e.target.value)}
                onPressEnter={() => onSearch?.()}
                style={{width: 188, marginBottom: 8, display: 'block'}}
            />
            <Space>
                <Button
                    type="primary"
                    onClick={() => onSearch?.()}
                    icon={<SearchOutlined/>}
                    size="small"
                    style={{width: 90}}
                >
                    Search
                </Button>
                <Button onClick={() => onReset?.()} size="small" style={{width: 90}}>
                    Reset
                </Button>
            </Space>
        </div>
    );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSearchFilter(dropdownProps: FilterDropdownProps) {
    const {setSelectedKeys, selectedKeys, confirm, clearFilters} = dropdownProps;

    return {
        value: (selectedKeys[0] as string) ?? '',
        onChange: (val: string) => setSelectedKeys(val ? [val] : []),
        onSearch: () => confirm(),
        onReset: () => clearFilters?.(),
    };
}
