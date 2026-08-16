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

import {Outlet} from 'react-router-dom';
import {useSecurityContext} from '@ahoo-wang/fetcher-react';
import {SidebarInset, SidebarProvider} from '@/components/ui/sidebar';
import {AppSidebar} from './AppSidebar';
import {AppHeader} from './AppHeader';
import {Watermark} from './Watermark';
import {ErrorBoundary} from '@/components/error/ErrorBoundary';
import {useLayoutCollapsed} from '@/hooks/useLayoutCollapsed';

export function AuthenticatedLayout() {
    const [collapsed, setCollapsed] = useLayoutCollapsed();
    const {currentUser} = useSecurityContext();
    return (
        <SidebarProvider
            open={!collapsed}
            onOpenChange={(open) => setCollapsed(!open)}
        >
            <AppSidebar/>
            <SidebarInset>
                <AppHeader/>
                <main className="flex flex-1 flex-col p-6">
                    <Watermark content={currentUser.sub}>
                        <div className="flex flex-1 flex-col rounded-xl border bg-card p-6 shadow-sm">
                            <ErrorBoundary>
                                <Outlet/>
                            </ErrorBoundary>
                        </div>
                    </Watermark>
                </main>
                <footer className="py-4 text-center text-sm text-muted-foreground">
                    <a href="https://github.com/Ahoo-Wang/CoSky" target="_blank" rel="noopener noreferrer"
                       title="High-performance, low-cost microservice governance platform. Service Discovery and Configuration Service."
                       className="font-medium text-primary hover:underline">
                        CoSky
                    </a>
                    {' © 2021-present'}
                </footer>
            </SidebarInset>
        </SidebarProvider>
    );
}
