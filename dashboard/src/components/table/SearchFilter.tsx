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

import {Input} from '@/components/ui/input';
import {Button} from '@/components/ui/button';

export interface SearchFilterProps {
    placeholder?: string;
    value?: string;
    onChange?: (value: string) => void;
    onSearch?: () => void;
    onReset?: () => void;
}

export function SearchFilter({placeholder = 'Search...', value, onChange, onSearch, onReset}: SearchFilterProps) {
    return (
        <div className="flex w-56 flex-col gap-2">
            <Input
                placeholder={placeholder}
                value={value ?? ''}
                onChange={(e) => onChange?.(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') onSearch?.(); }}
            />
            <div className="flex justify-end gap-2">
                <Button variant="ghost" size="sm" onClick={() => onReset?.()}>Reset</Button>
                <Button size="sm" onClick={() => onSearch?.()}>Search</Button>
            </div>
        </div>
    );
}
