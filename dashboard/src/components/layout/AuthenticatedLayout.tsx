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

import {useRef, useState} from 'react';
import {Menu, PanelLeftClose, PanelLeftOpen, X} from 'lucide-react';
import {Outlet, useLocation} from 'react-router-dom';
import {GiteeIcon, GitHubIcon} from '@/components/icons/repository-icons';
import {ErrorBoundary} from '@/components/error/ErrorBoundary';
import {Button} from '@/components/ui/button';
import {Sheet, SheetClose, SheetContent, SheetTitle} from '@/components/ui/sheet';
import {useLayoutCollapsed} from '@/hooks/useLayoutCollapsed';
import {cn} from '@/lib/utils';
import {AccountMenu} from '@/components/layout/AccountMenu';
import {CommandSearch} from '@/components/layout/CommandSearch';
import {CurrentNamespaceSelector} from '@/components/layout/CurrentNamespaceSelector';
import {ShellPanel, SidebarContent} from '@/components/layout/Sidebar';

const iconLinkClass = 'grid size-[34px] place-items-center rounded-[8px] text-shell-foreground hover:bg-shell-overlay hover:text-white max-[960px]:hidden [&_svg]:w-[18px]';

/**
 * The application frame around every signed-in page: sidebar, header and content.
 * Below 720px the sidebar becomes a navigation sheet.
 */
export function AuthenticatedLayout() {
    const [collapsed, setCollapsed] = useLayoutCollapsed();
    const [mobileOpen, setMobileOpen] = useState(false);
    const mobileMenuButtonRef = useRef<HTMLButtonElement>(null);
    const location = useLocation();
    const environmentName = import.meta.env.VITE_ENVIRONMENT_NAME?.trim() || window.location.host;

    return (
        <>
            <div className={cn(
                'grid min-h-screen bg-canvas transition-[grid-template-columns] duration-[180ms] ease-[ease] max-[720px]:block',
                collapsed ? 'grid-cols-[72px_minmax(0,1fr)]' : 'grid-cols-[232px_minmax(0,1fr)]',
            )}>
                <ShellPanel as="aside" className="sticky top-0 z-30 max-[720px]:hidden">
                    <SidebarContent collapsed={collapsed} currentPath={location.pathname} onCollapse={() => setCollapsed(!collapsed)}/>
                </ShellPanel>

                <div className="flex min-h-screen min-w-0 flex-col">
                    <header className={cn(
                        'sticky top-0 z-20 grid min-h-[76px] items-center gap-6 border-b border-shell-overlay bg-shell px-7 text-white',
                        'grid-cols-[minmax(200px,auto)_minmax(280px,640px)_minmax(180px,auto)]',
                        'max-[960px]:grid-cols-[auto_minmax(220px,1fr)_auto] max-[960px]:px-[18px]',
                        'max-[720px]:grid-cols-[auto_1fr_auto] max-[720px]:gap-[10px]',
                    )}>
                        <div className="flex items-center gap-3 max-[720px]:col-[1/3]">
                            <Button ref={mobileMenuButtonRef} variant="ghost" size="icon" className="hidden max-[720px]:inline-flex"
                                    aria-label="Open navigation" aria-haspopup="dialog" aria-expanded={mobileOpen}
                                    onClick={() => setMobileOpen(true)}>
                                <Menu/>
                            </Button>
                            <Button variant="ghost" size="icon-sm" onClick={() => setCollapsed(!collapsed)}
                                    aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}
                                    className="text-shell-foreground hover:bg-shell-overlay hover:text-white max-[720px]:hidden">
                                {collapsed ? <PanelLeftOpen/> : <PanelLeftClose/>}
                            </Button>
                            <CurrentNamespaceSelector/>
                        </div>
                        <CommandSearch/>
                        <div className="flex items-center justify-end gap-3 max-[720px]:col-start-3">
                            <span className="grid max-w-[180px] leading-[1.15] max-[960px]:hidden" title={`Connected to ${window.location.origin}`}>
                                <span className="text-[10px] tracking-[0.06em] text-shell-caption uppercase">Environment</span>
                                <strong className="overflow-hidden text-[12px] font-semibold text-ellipsis whitespace-nowrap text-shell-strong">{environmentName}</strong>
                            </span>
                            <a href="https://github.com/Ahoo-Wang/CoSky" target="_blank" rel="noopener noreferrer" className={iconLinkClass} aria-label="CoSky on GitHub">
                                <GitHubIcon/>
                            </a>
                            <a href="https://gitee.com/AhooWang/CoSky" target="_blank" rel="noopener noreferrer" className={cn(iconLinkClass, 'hover:text-brand-gitee')} aria-label="CoSky on Gitee">
                                <GiteeIcon/>
                            </a>
                            <AccountMenu/>
                        </div>
                    </header>
                    <main className="min-w-0 flex-1 px-9 pt-[34px] pb-7 max-[960px]:px-5 max-[960px]:py-6">
                        <ErrorBoundary>
                            <Outlet/>
                        </ErrorBoundary>
                    </main>
                </div>
            </div>
            {/* A sibling of the layout, not its parent: dialogs opened from pages must not count as nested in it. */}
            <Sheet open={mobileOpen} onOpenChange={setMobileOpen}>
                <SheetContent side="left" showCloseButton={false} aria-modal="true" finalFocus={mobileMenuButtonRef}
                              className="w-[232px] max-w-none gap-0 border-0 bg-shell p-0 text-shell-foreground sm:max-w-none">
                    <SheetTitle className="sr-only">Navigation</SheetTitle>
                    <ShellPanel>
                        <SheetClose
                            render={
                                <Button variant="ghost" size="icon-sm" aria-label="Close navigation"
                                        className="absolute top-[23px] right-3 z-3 text-shell-foreground"/>
                            }
                        >
                            <X/>
                        </SheetClose>
                        <SidebarContent collapsed={false} currentPath={location.pathname} onNavigate={() => setMobileOpen(false)}/>
                    </ShellPanel>
                </SheetContent>
            </Sheet>
        </>
    );
}
