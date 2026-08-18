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
import type {FormEvent} from 'react';
import {
    ArrowRight,
    ChevronDown,
    ChevronsLeft,
    ChevronsRight,
    Cloud,
    FileText,
    ExternalLink,
    LayoutDashboard,
    LogOut,
    Menu,
    Network,
    PanelLeftClose,
    PanelLeftOpen,
    Search,
    Settings,
    ShieldCheck,
    User,
    X,
} from 'lucide-react';
import {Outlet, useNavigate, useLocation, NavLink} from 'react-router-dom';
import {CurrentNamespaceSelector} from './CurrentNamespaceSelector.tsx';
import {useSecurityContext} from "@ahoo-wang/fetcher-react";
import {ChangePwd} from "../security/ChangePwd.tsx";
import {ErrorBoundary} from "../error/ErrorBoundary.tsx";
import {useLayoutCollapsed} from "../../hooks/useLayoutCollapsed.ts";
import {useDrawer} from "../../contexts/DrawerContext.tsx";
import CoskyLogo from "../../assets/cosky-logo-constellation.svg";
import {Button} from '@/components/ui/button';
import {Collapsible, CollapsibleContent, CollapsibleTrigger} from '@/components/ui/collapsible';
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {Input} from '@/components/ui/input';
import {cn} from '@/lib/utils';
import {toast} from 'sonner';

const primaryItems = [
    {to: '/home', label: 'Dashboard', icon: LayoutDashboard},
    {to: '/config', label: 'Configuration', icon: FileText},
    {to: '/service', label: 'Service', icon: Cloud},
    {to: '/namespace', label: 'Namespace', icon: Network},
];

const securityItems = [
    {to: '/user', label: 'User', icon: User},
    {to: '/role', label: 'Role', icon: ShieldCheck},
    {to: '/audit-log', label: 'Audit Log', icon: FileText},
];

const searchTargets = [...primaryItems, ...securityItems];

export const AuthenticatedLayout = () => {
    const [collapsed, setCollapsed] = useLayoutCollapsed();
    const [searchValue, setSearchValue] = useState('');
    const [mobileOpen, setMobileOpen] = useState(false);
    const {currentUser, signOut} = useSecurityContext();
    const navigate = useNavigate();
    const location = useLocation();
    const {openDrawer, closeDrawer} = useDrawer();
    const apiOrigin = new URL(import.meta.env.VITE_API_BASE_URL || window.location.origin, window.location.origin).origin;
    const environmentName = import.meta.env.VITE_ENVIRONMENT_NAME?.trim() || new URL(apiOrigin).host;

    const handleChangePwd = () => {
        openDrawer(
            <ChangePwd onSubmit={closeDrawer} onCancel={closeDrawer}/>,
            {
                title: 'Change Password',
                width: 'min(480px, 92vw)',
            }
        );
    };

    const handleSearch = (event: FormEvent) => {
        event.preventDefault();
        const query = searchValue.trim().toLowerCase();
        if (!query) return;
        const target = searchTargets.find(item => item.label.toLowerCase().includes(query));
        if (target) {
            navigate(target.to);
            setSearchValue('');
            return;
        }
        toast.error(`No page matches “${searchValue.trim()}”.`);
    };

    return (
        <div className={cn('app-shell', collapsed && 'app-shell-collapsed', mobileOpen && 'app-mobile-open')}>
            <aside className="app-sidebar">
                <NavLink to="/home" className="app-brand" onClick={() => setMobileOpen(false)}>
                    <img src={CoskyLogo} alt="CoSky"/>
                    {!collapsed && <span className="app-brand-copy"><strong>CoSky</strong><small>Microservice Governance</small></span>}
                </NavLink>
                <Button variant="ghost" size="icon-sm" className="app-mobile-close" onClick={() => setMobileOpen(false)} aria-label="Close navigation"><X/></Button>
                <nav className="app-nav" aria-label="Primary navigation">
                    {primaryItems.map(({to, label, icon: Icon}) => (
                        <NavLink key={to} to={to} title={label} onClick={() => setMobileOpen(false)} className={({isActive}) => cn('app-nav-item', isActive && 'active')}>
                            <Icon/>
                            {!collapsed && <span>{label}</span>}
                        </NavLink>
                    ))}
                    <Collapsible defaultOpen={securityItems.some(item => item.to === location.pathname)}>
                        <CollapsibleTrigger asChild>
                            <button type="button" className="app-nav-item app-nav-group" title="Security">
                                <ShieldCheck/>
                                {!collapsed && <><span>Security</span><ChevronDown className="app-nav-chevron"/></>}
                            </button>
                        </CollapsibleTrigger>
                        {!collapsed && (
                            <CollapsibleContent className="app-nav-submenu">
                                {securityItems.map(({to, label, icon: Icon}) => (
                                    <NavLink key={to} to={to} onClick={() => setMobileOpen(false)} className={({isActive}) => cn('app-nav-item', isActive && 'active')}>
                                        <Icon/>
                                        <span>{label}</span>
                                    </NavLink>
                                ))}
                            </CollapsibleContent>
                        )}
                    </Collapsible>
                    {collapsed && securityItems.map(({to, label, icon: Icon}) => (
                        <NavLink key={to} to={to} title={label} onClick={() => setMobileOpen(false)} className={({isActive}) => cn('app-nav-item', isActive && 'active')}>
                            <Icon/>
                        </NavLink>
                    ))}
                </nav>
                <Button variant="ghost" className="app-collapse" onClick={() => setCollapsed(!collapsed)}>
                    {collapsed ? <ChevronsRight/> : <ChevronsLeft/>}
                    {!collapsed && 'Collapse'}
                </Button>
            </aside>

            <div className="app-main">
                <header className="app-header">
                    <div className="app-header-start">
                        <Button variant="ghost" size="icon" className="app-mobile-menu" onClick={() => {setCollapsed(false); setMobileOpen(true);}} aria-label="Open navigation">
                            <Menu/>
                        </Button>
                        <Button variant="ghost" size="icon-sm" className="app-desktop-menu" onClick={() => setCollapsed(!collapsed)} aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}>
                            {collapsed ? <PanelLeftOpen/> : <PanelLeftClose/>}
                        </Button>
                        <CurrentNamespaceSelector/>
                    </div>
                    <form className="app-command-search" role="search" onSubmit={handleSearch}>
                        <Search/>
                        <Input
                            type="search"
                            value={searchValue}
                            onChange={event => setSearchValue(event.target.value)}
                            placeholder="Go to Dashboard, Configuration, Service..."
                            aria-label="Search navigation"
                        />
                        <Button type="submit" variant="ghost" size="icon-sm" className="app-command-search-submit" aria-label="Go to page">
                            <ArrowRight/>
                        </Button>
                    </form>
                    <div className="app-header-actions">
                        <span className="app-environment" title={`Connected to ${apiOrigin}`}>
                            <span>Environment</span>
                            <strong>{environmentName}</strong>
                        </span>
                        <a href="https://github.com/Ahoo-Wang/CoSky" target="_blank" rel="noopener noreferrer" className="app-icon-link" aria-label="CoSky on GitHub">
                            <ExternalLink/>
                        </a>
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <Button variant="ghost" className="app-user-button">
                                    <span className="app-avatar">{currentUser.sub.slice(0, 1).toUpperCase()}</span>
                                    <span className="app-user-name">{currentUser.sub}</span>
                                    <ChevronDown/>
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end" className="w-52">
                                <DropdownMenuLabel>Account</DropdownMenuLabel>
                                <DropdownMenuSeparator/>
                                <DropdownMenuItem onSelect={handleChangePwd}>
                                    <Settings/> Change password
                                </DropdownMenuItem>
                                <DropdownMenuItem variant="destructive" onSelect={signOut}>
                                    <LogOut/> Sign out
                                </DropdownMenuItem>
                            </DropdownMenuContent>
                        </DropdownMenu>
                    </div>
                </header>
                <main className="app-content">
                    <ErrorBoundary>
                        <Outlet/>
                    </ErrorBoundary>
                </main>
            </div>
        </div>
    );
};
