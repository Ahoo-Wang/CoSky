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

import {Select, SelectContent, SelectItem, SelectTrigger, SelectValue} from '@/components/ui/select';
import {SCHEMA_SELECTOR_OPTIONS} from './SchemaSelectorOptions';

export interface SchemaSelectorProps {
    value?: string;
    onChange?: (value: string) => void;
    disabled?: boolean;
}

export function SchemaSelector({value, onChange, disabled}: SchemaSelectorProps) {
    return (
        <Select value={value || undefined} onValueChange={onChange} disabled={disabled}>
            <SelectTrigger className="w-full">
                <SelectValue placeholder="Select schema"/>
            </SelectTrigger>
            <SelectContent>
                {SCHEMA_SELECTOR_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                        {option.label}
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    );
}
