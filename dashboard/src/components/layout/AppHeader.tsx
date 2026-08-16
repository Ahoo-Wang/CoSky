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

import {LogOut, User as UserIcon} from 'lucide-react';
import {useSecurityContext} from '@ahoo-wang/fetcher-react';
import {SidebarTrigger} from '@/components/ui/sidebar';
import {Separator} from '@/components/ui/separator';
import {Button} from '@/components/ui/button';
import {
    DropdownMenu, DropdownMenuContent, DropdownMenuItem,
    DropdownMenuSeparator, DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {CurrentNamespaceSelector} from './CurrentNamespaceSelector';
import {ThemeToggle} from './ThemeToggle';
import {ChangePwd} from '@/components/security/ChangePwd';
import {useDrawer} from '@/contexts/DrawerContext';

export function AppHeader() {
    const {currentUser, signOut} = useSecurityContext();
    const {openDrawer, closeDrawer} = useDrawer();
    const handleChangePwd = () => {
        openDrawer(
            <ChangePwd onSubmit={closeDrawer} onCancel={closeDrawer}/>,
            {title: 'Change Password', defaultSize: '20vw'},
        );
    };
    return (
        <header className="flex h-14 shrink-0 items-center justify-between gap-4 border-b bg-background px-4">
            <div className="flex items-center gap-2">
                <SidebarTrigger/>
                <Separator orientation="vertical" className="h-6"/>
                <CurrentNamespaceSelector/>
            </div>
            <div className="flex items-center gap-2">
                <ThemeToggle/>
                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                        <Button variant="ghost" className="gap-2">
                            <UserIcon className="h-4 w-4"/>
                            {currentUser.sub}
                        </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={handleChangePwd}>Change Password</DropdownMenuItem>
                        <DropdownMenuSeparator/>
                        <DropdownMenuItem variant="destructive" onClick={signOut}>
                            <LogOut className="mr-2 h-4 w-4"/>
                            Sign out
                        </DropdownMenuItem>
                    </DropdownMenuContent>
                </DropdownMenu>
            </div>
        </header>
    );
}
