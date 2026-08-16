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

import {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {
    FileText, KeyRound, LayoutDashboard, Moon, Network, Plus,
    ScrollText, Server, Sun, Users,
} from 'lucide-react';
import {
    CommandDialog, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, CommandSeparator,
} from '@/components/ui/command';
import {useNamespacesContext} from '@/contexts/namespace/NamespacesContext';
import {useCurrentNamespaceContext} from '@/contexts/namespace/CurrentNamespaceContext';
import {useTheme} from '@/theme/ThemeProvider';
import {emitCommand, type DashboardCommand} from '@/lib/commands';

const pages = [
    {path: '/home', label: 'Dashboard', icon: LayoutDashboard},
    {path: '/config', label: 'Configuration', icon: FileText},
    {path: '/service', label: 'Service', icon: Server},
    {path: '/namespace', label: 'Namespace', icon: Network},
    {path: '/user', label: 'User', icon: Users},
    {path: '/role', label: 'Role', icon: KeyRound},
    {path: '/audit-log', label: 'Audit Log', icon: ScrollText},
];

const addActions: { cmd: DashboardCommand; label: string; path: string }[] = [
    {cmd: 'add-config', label: 'Add Config', path: '/config'},
    {cmd: 'add-service', label: 'Add Service', path: '/service'},
    {cmd: 'add-user', label: 'Add User', path: '/user'},
    {cmd: 'add-role', label: 'Add Role', path: '/role'},
];

export function CommandPalette() {
    const [open, setOpen] = useState(false);
    const navigate = useNavigate();
    const {namespaces} = useNamespacesContext();
    const {currentNamespace, setCurrent} = useCurrentNamespaceContext();
    const {resolvedTheme, setTheme} = useTheme();

    useEffect(() => {
        const onKeyDown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
                e.preventDefault();
                setOpen((prev) => !prev);
            }
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, []);

    const runCommand = (fn: () => void) => {
        setOpen(false);
        fn();
    };

    return (
        <CommandDialog open={open} onOpenChange={setOpen}>
            <CommandInput placeholder="Type a command or search..."/>
            <CommandList>
                <CommandEmpty>No results found.</CommandEmpty>
                <CommandGroup heading="Pages">
                    {pages.map((page) => (
                        <CommandItem key={page.path} value={page.label}
                                     onSelect={() => runCommand(() => navigate(page.path))}>
                            <page.icon className="mr-2 h-4 w-4"/>
                            {page.label}
                        </CommandItem>
                    ))}
                </CommandGroup>
                <CommandSeparator/>
                <CommandGroup heading="Namespace">
                    {namespaces.map((ns) => (
                        <CommandItem key={ns} value={`namespace ${ns}`}
                                     onSelect={() => runCommand(() => setCurrent(ns))}>
                            <Network className="mr-2 h-4 w-4"/>
                            {ns}
                            {ns === currentNamespace && <span className="ml-auto text-xs text-primary">current</span>}
                        </CommandItem>
                    ))}
                </CommandGroup>
                <CommandSeparator/>
                <CommandGroup heading="Actions">
                    {addActions.map((action) => (
                        <CommandItem key={action.cmd} value={action.label}
                                     onSelect={() => runCommand(() => {
                                         navigate(action.path);
                                         setTimeout(() => emitCommand(action.cmd), 300);
                                     })}>
                            <Plus className="mr-2 h-4 w-4"/>
                            {action.label}
                        </CommandItem>
                    ))}
                    <CommandItem value="toggle theme"
                                 onSelect={() => runCommand(() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark'))}>
                        {resolvedTheme === 'dark' ? <Sun className="mr-2 h-4 w-4"/> : <Moon className="mr-2 h-4 w-4"/>}
                        Toggle Theme
                    </CommandItem>
                </CommandGroup>
            </CommandList>
        </CommandDialog>
    );
}
