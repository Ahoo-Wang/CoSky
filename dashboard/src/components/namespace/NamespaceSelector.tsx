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
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';

export interface NamespaceSelectorProps {
    value?: string;
    onChange?: (value: string) => void;
    placeholder?: string;
    className?: string;
    disabled?: boolean;
}

export function NamespaceSelector({value, onChange, placeholder = 'Select Namespace', className, disabled}: NamespaceSelectorProps) {
    const {namespaces, loading} = useNamespacesContext();
    const [open, setOpen] = useState(false);
    return (
        <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button variant="outline" role="combobox" aria-expanded={open} disabled={disabled || loading}
                        className={cn('justify-between font-normal', className)}>
                    <span className="truncate">{value ?? placeholder}</span>
                    <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50"/>
                </Button>
            </PopoverTrigger>
            <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
                <Command>
                    <CommandInput placeholder="Search namespace..."/>
                    <CommandList>
                        <CommandEmpty>No namespace found.</CommandEmpty>
                        <CommandGroup>
                            {namespaces.map((ns) => (
                                <CommandItem key={ns} value={ns}
                                             onSelect={(current) => {
                                                 onChange?.(current);
                                                 setOpen(false);
                                             }}>
                                    <Check className={cn('mr-2 h-4 w-4', value === ns ? 'opacity-100' : 'opacity-0')}/>
                                    {ns}
                                </CommandItem>
                            ))}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    );
}
