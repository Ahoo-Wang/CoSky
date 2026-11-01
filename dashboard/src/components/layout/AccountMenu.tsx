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

import {useSyncExternalStore} from 'react';
import {ChevronDown, LogOut} from 'lucide-react';
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
import {useMe} from '@/contexts/MeContext';
import {displayNameOf, signOut} from '@/security/oidc';
import {session} from '@/security/session';

/**
 * Who is signed in. A server without auth lets everyone in, so there is nothing to sign out of.
 */
export function AccountMenu() {
    const me = useMe();
    const token = useSyncExternalStore(session.subscribe, session.token);
    const name = displayNameOf(token) || me.subject?.name || 'anonymous';

    return (
        <DropdownMenu>
            <DropdownMenuTrigger
                data-slot="account-menu-trigger"
                render={
                    <Button
                        variant="ghost"
                        className="h-10 text-white hover:bg-shell-overlay hover:text-white aria-expanded:bg-shell-overlay"
                    />
                }
            >
                <span className="grid size-7 place-items-center rounded-full bg-shell-accent text-[12px] font-semibold text-white">
                    {name.slice(0, 1).toUpperCase()}
                </span>
                <span className="max-[960px]:hidden">{name}</span>
                <ChevronDown/>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-52">
                <DropdownMenuGroup>
                    <DropdownMenuLabel>Account</DropdownMenuLabel>
                    {token && (
                        <>
                            <DropdownMenuSeparator/>
                            <DropdownMenuItem variant="destructive" onClick={signOut}>
                                <LogOut/> Sign out
                            </DropdownMenuItem>
                        </>
                    )}
                </DropdownMenuGroup>
            </DropdownMenuContent>
        </DropdownMenu>
    );
}
