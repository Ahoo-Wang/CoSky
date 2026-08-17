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

import {ChevronDown} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {
    DropdownMenu,
    DropdownMenuCheckboxItem,
    DropdownMenuContent,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import type {SelectOption} from '@/components/ui/options-select';
import {cn} from '@/lib/utils';

interface MultiSelectProps {
    id?: string;
    options: SelectOption[];
    value?: string[];
    onChange?: (value: string[]) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
    'aria-label'?: string;
}

export function MultiSelect({
    id,
    options,
    value = [],
    onChange,
    placeholder = 'Select roles',
    className,
    disabled,
    'aria-label': ariaLabel,
}: MultiSelectProps) {
    const selectedLabels = options.filter(option => value.includes(option.value)).map(option => option.label);
    const toggle = (optionValue: string) => {
        onChange?.(value.includes(optionValue)
            ? value.filter(item => item !== optionValue)
            : [...value, optionValue]);
    };

    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button
                    id={id}
                    type="button"
                    variant="outline"
                    disabled={disabled}
                    aria-label={ariaLabel ?? placeholder}
                    className={cn('h-auto min-h-9 w-full justify-between px-3 font-normal', className)}
                >
                    <span className={cn('truncate', selectedLabels.length === 0 && 'text-muted-foreground')}>
                        {selectedLabels.length > 0 ? selectedLabels.join(', ') : placeholder}
                    </span>
                    <ChevronDown className="text-muted-foreground"/>
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start" className="min-w-52">
                <DropdownMenuLabel>{placeholder}</DropdownMenuLabel>
                <DropdownMenuSeparator/>
                {options.length === 0 && <div className="px-2 py-5 text-center text-xs text-muted-foreground">No options available.</div>}
                {options.map(option => (
                    <DropdownMenuCheckboxItem
                        key={option.value}
                        checked={value.includes(option.value)}
                        onCheckedChange={() => toggle(option.value)}
                        onSelect={event => event.preventDefault()}
                    >
                        {option.label}
                    </DropdownMenuCheckboxItem>
                ))}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
