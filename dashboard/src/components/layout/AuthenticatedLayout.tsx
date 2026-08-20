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

import {useEffect, useRef, useState} from 'react';
import type {FocusEvent, FormEvent, KeyboardEvent} from 'react';
import {
    ArrowRight,
    ChevronDown,
    ChevronsLeft,
    ChevronsRight,
    Cloud,
    FileText,
    History,
    LayoutDashboard,
    LogOut,
    Menu,
    Network,
    PanelLeftClose,
    PanelLeftOpen,
    Search,
    Settings,
    Shield,
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
import {GiteeIcon, GitHubIcon} from '@/components/icons/repository-icons';

const primaryItems = [
    {to: '/home', label: 'Dashboard', description: 'System health and topology', icon: LayoutDashboard},
    {to: '/config', label: 'Configuration', description: 'Manage configuration files', icon: FileText},
    {to: '/service', label: 'Service', description: 'Services and instances', icon: Cloud},
    {to: '/namespace', label: 'Namespace', description: 'Isolation boundaries', icon: Network},
];

const securityItems = [
    {to: '/user', label: 'User', description: 'Accounts and role assignments', icon: User},
    {to: '/role', label: 'Role', description: 'Access policies', icon: ShieldCheck},
    {to: '/audit-log', label: 'Audit Log', description: 'Security and operation history', icon: History},
];

const searchTargets = [...primaryItems, ...securityItems];

// Radix sheets/dialogs expose explicit roles; the topology fullscreen uses a native
// <dialog> whose implicit role carries no attribute, hence the dialog[open] check.
const isModalOpen = () =>
    Boolean(document.querySelector('[role="dialog"], [role="alertdialog"], dialog[open]'));

export const AuthenticatedLayout = () => {
    const [collapsed, setCollapsed] = useLayoutCollapsed();
    const [searchValue, setSearchValue] = useState('');
    const [searchOpen, setSearchOpen] = useState(false);
    const [activeSearchIndex, setActiveSearchIndex] = useState(0);
    const [mobileOpen, setMobileOpen] = useState(false);
    const searchInputRef = useRef<HTMLInputElement>(null);
    const {currentUser, signOut} = useSecurityContext();
    const navigate = useNavigate();
    const location = useLocation();
    const {openDrawer, closeDrawer} = useDrawer();
    const apiOrigin = new URL(import.meta.env.VITE_API_BASE_URL || window.location.origin, window.location.origin).origin;
    const environmentName = import.meta.env.VITE_ENVIRONMENT_NAME?.trim() || new URL(apiOrigin).host;
    const searchQuery = searchValue.trim().toLowerCase();
    const filteredSearchTargets = searchTargets.filter(item =>
        `${item.label} ${item.description}`.toLowerCase().includes(searchQuery)
    );
    const commandShortcut = '/';

    useEffect(() => {
        const focusSearch = (event: globalThis.KeyboardEvent) => {
            const isCommandK = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k';
            if (!isCommandK && event.key !== '/') return;
            // The search control is display:none below 720px — leave the shortcuts alone there.
            if (searchInputRef.current?.checkVisibility() !== true) return;
            // Never hijack keys while a modal surface (sheet, dialog, alert dialog) is open.
            if (isModalOpen()) return;
            // '/' stays out of the way of typing; Ctrl/Cmd+K intentionally works from anywhere.
            if (!isCommandK && event.target instanceof HTMLElement && event.target !== searchInputRef.current && event.target.matches('input, textarea, select, [contenteditable="true"]')) return;
            event.preventDefault();
            searchInputRef.current?.focus();
            setSearchOpen(true);
        };
        window.addEventListener('keydown', focusSearch);
        return () => window.removeEventListener('keydown', focusSearch);
    }, []);

    const handleChangePwd = () => {
        openDrawer(
            <ChangePwd onSubmit={closeDrawer} onCancel={closeDrawer}/>,
            {
                title: 'Change Password',
                width: 'min(480px, 92vw)',
            }
        );
    };

    const navigateToSearchTarget = (target: (typeof searchTargets)[number]) => {
        navigate(target.to);
        setSearchValue('');
        setSearchOpen(false);
        searchInputRef.current?.blur();
    };

    const handleSearch = (event: FormEvent) => {
        event.preventDefault();
        const target = filteredSearchTargets[activeSearchIndex];
        if (target) {
            navigateToSearchTarget(target);
            return;
        }
        toast.error(`No page matches “${searchValue.trim()}”.`);
    };

    const handleSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
        if (event.key === 'Enter') {
            event.preventDefault();
            event.currentTarget.form?.requestSubmit();
            return;
        }
        if (event.key === 'Escape') {
            setSearchValue('');
            setSearchOpen(false);
            event.currentTarget.blur();
            return;
        }
        if (!['ArrowDown', 'ArrowUp'].includes(event.key) || filteredSearchTargets.length === 0) return;
        event.preventDefault();
        setSearchOpen(true);
        setActiveSearchIndex(index =>
            event.key === 'ArrowDown'
                ? (index + 1) % filteredSearchTargets.length
                : (index - 1 + filteredSearchTargets.length) % filteredSearchTargets.length
        );
    };

    const handleSearchFocus = () => {
        const nextIndex = filteredSearchTargets.findIndex(item => item.to !== location.pathname);
        setActiveSearchIndex(nextIndex >= 0 ? nextIndex : 0);
        setSearchOpen(true);
    };

    const handleLayoutKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
        if (event.key !== '/') return;
        if (searchInputRef.current?.checkVisibility() !== true) return;
        if (isModalOpen()) return;
        if (event.target instanceof HTMLElement && event.target !== searchInputRef.current && event.target.matches('input, textarea, select, [contenteditable="true"]')) return;
        event.preventDefault();
        event.stopPropagation();
        searchInputRef.current?.focus();
        setSearchOpen(true);
    };

    const handleSearchBlur = (event: FocusEvent<HTMLFormElement>) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setSearchOpen(false);
    };

    return (
        <div className={cn('app-shell', collapsed && 'app-shell-collapsed', mobileOpen && 'app-mobile-open')} onKeyDownCapture={handleLayoutKeyDown}>
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
                    {!collapsed ? (
                        <Collapsible defaultOpen={securityItems.some(item => item.to === location.pathname)}>
                            <CollapsibleTrigger asChild>
                                <button type="button" className="app-nav-item app-nav-group" title="Security">
                                    <Shield/>
                                    <span>Security</span><ChevronDown className="app-nav-chevron"/>
                                </button>
                            </CollapsibleTrigger>
                            <CollapsibleContent className="app-nav-submenu">
                                {securityItems.map(({to, label, icon: Icon}) => (
                                    <NavLink key={to} to={to} onClick={() => setMobileOpen(false)} className={({isActive}) => cn('app-nav-item', isActive && 'active')}>
                                        <Icon/>
                                        <span>{label}</span>
                                    </NavLink>
                                ))}
                            </CollapsibleContent>
                        </Collapsible>
                    ) : securityItems.map(({to, label, icon: Icon}) => (
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
                    <form className="app-command-search" role="search" onSubmit={handleSearch} onBlur={handleSearchBlur}>
                        <Search/>
                        <Input
                            ref={searchInputRef}
                            type="text"
                            role="combobox"
                            value={searchValue}
                            onChange={event => {
                                setSearchValue(event.target.value);
                                setActiveSearchIndex(0);
                                setSearchOpen(true);
                            }}
                            onFocus={handleSearchFocus}
                            onKeyDown={handleSearchKeyDown}
                            placeholder="Search pages..."
                            aria-label="Search navigation"
                            aria-expanded={searchOpen}
                            aria-controls="navigation-search-results"
                            aria-autocomplete="list"
                            aria-activedescendant={searchOpen && filteredSearchTargets.length > 0 ? `navigation-search-result-${activeSearchIndex}` : undefined}
                        />
                        {searchValue ? (
                            <Button type="submit" variant="ghost" size="icon-sm" className="app-command-search-submit" aria-label="Open selected page">
                                <ArrowRight/>
                            </Button>
                        ) : <kbd className="app-command-shortcut">{commandShortcut}</kbd>}
                        {searchOpen && (
                            <div id="navigation-search-results" className="app-command-results" role="listbox" aria-label="Navigation results">
                                <div className="app-command-results-header">
                                    <span>{searchQuery ? 'Matching pages' : 'Quick navigation'}</span>
                                    <small>{filteredSearchTargets.length} result{filteredSearchTargets.length === 1 ? '' : 's'}</small>
                                </div>
                                {filteredSearchTargets.map((item, index) => {
                                    const Icon = item.icon;
                                    const isCurrent = location.pathname === item.to;
                                    return <button
                                        key={item.to}
                                        id={`navigation-search-result-${index}`}
                                        type="button"
                                        role="option"
                                        aria-selected={index === activeSearchIndex}
                                        className="app-command-result"
                                        onMouseEnter={() => setActiveSearchIndex(index)}
                                        onClick={() => navigateToSearchTarget(item)}
                                    >
                                        <span className="app-command-result-icon"><Icon/></span>
                                        <span className="app-command-result-copy"><strong>{item.label}</strong><small>{item.description}</small></span>
                                        {isCurrent ? <span className="app-command-result-current">Current</span> : <ArrowRight/>}
                                    </button>;
                                })}
                                {filteredSearchTargets.length === 0 && (
                                    <div className="app-command-empty" role="status">
                                        <Search/>
                                        <strong>No pages found</strong>
                                        <span>Try “service”, “user”, or “audit”.</span>
                                    </div>
                                )}
                                <div className="app-command-results-footer">
                                    <span><kbd>↑</kbd><kbd>↓</kbd> Navigate</span>
                                    <span><kbd>Enter</kbd> Open</span>
                                    <span><kbd>Esc</kbd> Close</span>
                                </div>
                            </div>
                        )}
                    </form>
                    <div className="app-header-actions">
                        <span className="app-environment" title={`Connected to ${apiOrigin}`}>
                            <span>Environment</span>
                            <strong>{environmentName}</strong>
                        </span>
                        <a href="https://github.com/Ahoo-Wang/CoSky" target="_blank" rel="noopener noreferrer" className="app-icon-link" aria-label="CoSky on GitHub">
                            <GitHubIcon/>
                        </a>
                        <a href="https://gitee.com/AhooWang/CoSky" target="_blank" rel="noopener noreferrer" className="app-icon-link app-icon-link--gitee" aria-label="CoSky on Gitee">
                            <GiteeIcon/>
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
