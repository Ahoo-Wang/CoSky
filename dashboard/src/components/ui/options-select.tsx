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

import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select';
import {cn} from '@/lib/utils';

export interface SelectOption {
    label: string;
    value: string;
}

export interface OptionsSelectProps {
    options: SelectOption[];
    value?: string;
    defaultValue?: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    disabled?: boolean;
    className?: string;
    triggerClassName?: string;
    ariaLabel?: string;
}

export function OptionsSelect({
    options,
    value,
    defaultValue,
    onChange,
    placeholder = 'Select an option',
    disabled,
    className,
    triggerClassName,
    ariaLabel,
}: OptionsSelectProps) {
    return (
        <div className={cn('min-w-36', className)}>
            <Select
                value={value}
                defaultValue={defaultValue}
                onValueChange={onChange}
                disabled={disabled}
            >
                <SelectTrigger className={cn('w-full', triggerClassName)} aria-label={ariaLabel ?? placeholder}>
                    <SelectValue placeholder={placeholder}/>
                </SelectTrigger>
                <SelectContent position="popper">
                    {options.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                            {option.label}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
        </div>
    );
}
