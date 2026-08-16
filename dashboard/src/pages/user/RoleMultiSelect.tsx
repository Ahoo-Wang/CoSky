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
import {Check, ChevronsUpDown, X} from 'lucide-react';
import {cn} from '@/lib/utils';
import {Button} from '@/components/ui/button';
import {Badge} from '@/components/ui/badge';
import {Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList} from '@/components/ui/command';
import {Popover, PopoverContent, PopoverTrigger} from '@/components/ui/popover';

export interface RoleMultiSelectProps {
    value: string[];
    onChange: (value: string[]) => void;
    options: string[];
    disabled?: boolean;
    placeholder?: string;
}

export function RoleMultiSelect({value, onChange, options, disabled, placeholder = 'Select roles'}: RoleMultiSelectProps) {
    const [open, setOpen] = useState(false);
    const toggle = (role: string) => {
        onChange(value.includes(role) ? value.filter((r) => r !== role) : [...value, role]);
    };
    return (
        <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button variant="outline" role="combobox" disabled={disabled}
                        className="h-auto min-h-9 w-full justify-between font-normal">
                    <span className="flex flex-wrap gap-1">
                        {value.length === 0 && <span className="text-muted-foreground">{placeholder}</span>}
                        {value.map((role) => (
                            <Badge key={role} variant="secondary" className="gap-1">
                                {role}
                                <X
                                    className="h-3 w-3 cursor-pointer"
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        toggle(role);
                                    }}
                                />
                            </Badge>
                        ))}
                    </span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50"/>
                </Button>
            </PopoverTrigger>
            <PopoverContent className="w-(--radix-popover-trigger-width) p-0" align="start">
                <Command>
                    <CommandInput placeholder="Search role..."/>
                    <CommandList>
                        <CommandEmpty>No role found.</CommandEmpty>
                        <CommandGroup>
                            {options.map((role) => (
                                <CommandItem key={role} value={role} onSelect={() => toggle(role)}>
                                    <Check className={cn('mr-2 h-4 w-4', value.includes(role) ? 'opacity-100' : 'opacity-0')}/>
                                    {role}
                                </CommandItem>
                            ))}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    );
}
