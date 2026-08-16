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

import {useState} from 'react';
import {Check, ChevronsUpDown} from 'lucide-react';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList} from '@/components/ui/command';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';
import {RESOURCE_ACTION_OPTIONS} from './ResourceActionSelectorOptions';

export interface ResourceActionOption {
    label: string;
    value: string;
}

export interface ResourceActionSelectorProps {
    value?: string;
    onChange?: (value: string) => void;
    options?: ResourceActionOption[];
    disabled?: boolean;
    placeholder?: string;
    className?: string;
}

export function ResourceActionSelector({
                                           value,
                                           onChange,
                                           options = RESOURCE_ACTION_OPTIONS,
                                           disabled,
                                           placeholder = 'Select Resource Action',
                                           className,
                                       }: ResourceActionSelectorProps) {
    const [open, setOpen] = useState(false);
    const selected = options.find((option) => option.value === value);
    return (
        <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button variant="outline" role="combobox" aria-expanded={open} disabled={disabled}
                        className={cn('w-full justify-between font-normal', className)}>
                    <span className="truncate">{selected?.label ?? placeholder}</span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50"/>
                </Button>
            </PopoverTrigger>
            <PopoverContent className="w-(--radix-popover-trigger-width) p-0" align="start">
                <Command>
                    <CommandInput placeholder="Search resource action..."/>
                    <CommandList>
                        <CommandEmpty>No resource action found.</CommandEmpty>
                        <CommandGroup>
                            {options.map((option) => (
                                <CommandItem key={option.value} value={option.value} keywords={[option.label]}
                                             onSelect={(current) => {
                                                 onChange?.(current);
                                                 setOpen(false);
                                             }}>
                                    <Check className={cn('mr-2 h-4 w-4', value === option.value ? 'opacity-100' : 'opacity-0')}/>
                                    {option.label}
                                </CommandItem>
                            ))}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    );
}
