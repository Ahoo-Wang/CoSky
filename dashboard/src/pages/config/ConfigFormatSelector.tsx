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
import {cn} from '@/lib/utils';
import {CONFIG_FORMAT_SELECTOR_OPTIONS} from './ConfigFormatSelectorOptions';

export interface ConfigFormatSelectorProps {
    value?: string;
    onChange?: (value: string) => void;
    disabled?: boolean;
    className?: string;
}

export function ConfigFormatSelector({value, onChange, disabled, className}: ConfigFormatSelectorProps) {
    return (
        <Select value={value || undefined} onValueChange={onChange} disabled={disabled}>
            <SelectTrigger className={cn('w-full', className)}>
                <SelectValue placeholder="Select format"/>
            </SelectTrigger>
            <SelectContent>
                {CONFIG_FORMAT_SELECTOR_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                        {option.label}
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    );
}
