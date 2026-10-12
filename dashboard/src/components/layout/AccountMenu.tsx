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

import {ChevronDown, LogOut, Settings} from 'lucide-react';
import {useSecurityContext} from '@ahoo-wang/fetcher-react';
import {ChangePwd} from '@/components/security/ChangePwd';
import {Button} from '@/components/ui/button';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuGroup,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {useDrawer} from '@/contexts/DrawerContext';

/**
 * The signed-in user's menu: change password and sign out.
 */
export function AccountMenu() {
    const {currentUser, signOut} = useSecurityContext();
    const {openDrawer, closeDrawer} = useDrawer();

    const changePassword = () => {
        openDrawer(
            <ChangePwd onSubmit={closeDrawer} onCancel={closeDrawer}/>,
            {title: 'Change Password', width: 'min(480px, 92vw)'},
        );
    };

    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                data-slot="account-menu-trigger"
                render={
                    <Button
                        variant="ghost"
                        className="h-10 text-white hover:bg-shell-overlay hover:text-white aria-expanded:bg-shell-overlay aria-expanded:text-white"
                    />
                }
            >
                <span className="grid size-7 place-items-center rounded-full bg-shell-accent text-[12px] font-bold text-white">
                    {currentUser.sub.slice(0, 1).toUpperCase()}
                </span>
                <span className="max-[960px]:hidden">{currentUser.sub}</span>
                <ChevronDown/>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52">
                <DropdownMenuGroup>
                    <DropdownMenuLabel>Account</DropdownMenuLabel>
                    <DropdownMenuSeparator/>
                    <DropdownMenuItem onClick={changePassword}>
                        <Settings/> Change password
                    </DropdownMenuItem>
                    <DropdownMenuItem variant="destructive" onClick={signOut}>
                        <LogOut/> Sign out
                    </DropdownMenuItem>
                </DropdownMenuGroup>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
