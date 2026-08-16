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

import {Monitor, Moon, Sun} from 'lucide-react';
import {Button} from '@/components/ui/button';
import {DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger} from '@/components/ui/dropdown-menu';
import {useTheme, type Theme} from '@/theme/ThemeProvider';

const options: { value: Theme; label: string; icon: typeof Sun }[] = [
    {value: 'light', label: 'Light', icon: Sun},
    {value: 'dark', label: 'Dark', icon: Moon},
    {value: 'system', label: 'System', icon: Monitor},
];

export function ThemeToggle() {
    const {theme, setTheme} = useTheme();
    return (
        <DropdownMenu>
            <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" aria-label="Toggle theme">
                    <Sun className="h-5 w-5 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0"/>
                    <Moon className="absolute h-5 w-5 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100"/>
                </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
                {options.map((opt) => (
                    <DropdownMenuItem key={opt.value} onClick={() => setTheme(opt.value)}>
                        <opt.icon className="mr-2 h-4 w-4"/>
                        {opt.label}
                        {theme === opt.value && <span className="ml-auto text-primary">●</span>}
                    </DropdownMenuItem>
                ))}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
