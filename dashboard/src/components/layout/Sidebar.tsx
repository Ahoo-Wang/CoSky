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
import type {ComponentProps} from 'react';
import {ChevronDown, ChevronsLeft, ChevronsRight, Shield} from 'lucide-react';
import {NavLink} from 'react-router-dom';
import CoskyLogo from '@/assets/cosky-logo-constellation.svg';
import CoskyMark from '@/assets/cosky-logo-constellation-icon.svg';
import {Button} from '@/components/ui/button';
import {Collapsible, CollapsibleContent, CollapsibleTrigger} from '@/components/ui/collapsible';
import {cn} from '@/lib/utils';
import {primaryItems, securityItems} from '@/components/layout/navigation';

/**
 * The dark panel the sidebar is drawn on, with the constellation mark as a watermark.
 */
export function ShellPanel({as: Tag = 'div', className, children, ...props}: ComponentProps<'div'> & {as?: 'div' | 'aside'}) {
    return (
        <Tag
            className={cn('relative flex h-screen flex-col overflow-hidden bg-shell text-shell-foreground shadow-[1px_0_0_var(--shell-overlay)]', className)}
            {...props}
        >
            {children}
            <div
                aria-hidden
                className="pointer-events-none absolute inset-x-0 bottom-0 h-[34%] opacity-36"
                // Quoted: Vite inlines the SVG as a data URI, which an unquoted url() cannot hold.
                style={{
                    backgroundImage: `url("${CoskyMark}")`,
                    backgroundPosition: 'center 80%',
                    backgroundSize: '140px',
                    backgroundRepeat: 'no-repeat',
                }}
            />
        </Tag>
    );
}

const navItemClass = (collapsed: boolean, active = false) => cn(
    'flex min-h-[42px] w-full items-center gap-3 rounded-[9px] px-3 text-[14px] font-medium text-shell-muted',
    'transition-[color,background] duration-150 ease-[ease] hover:bg-shell-nav-active hover:text-white',
    '[&_svg]:size-[18px] [&_svg]:flex-none',
    active && 'bg-shell-nav-active text-white shadow-[inset_2px_0_0_var(--shell-accent-strong)]',
    collapsed && 'justify-center px-0',
);

/**
 * Brand, navigation and the collapse control, shared by the desktop sidebar and the mobile navigation sheet.
 */
export function SidebarContent({collapsed, currentPath, onCollapse, onNavigate}: {
    collapsed: boolean;
    currentPath: string;
    onCollapse?: () => void;
    onNavigate?: () => void;
}) {
    const inSecurity = securityItems.some(item => item.to === currentPath);
    const [securityOpen, setSecurityOpen] = useState(inSecurity);
    const [openedFor, setOpenedFor] = useState(currentPath);
    // Opening a security page expands its group; otherwise the group keeps the state the user chose.
    if (openedFor !== currentPath) {
        setOpenedFor(currentPath);
        if (inSecurity) setSecurityOpen(true);
    }
    return <>
        <NavLink
            to="/home"
            onClick={onNavigate}
            className={cn(
                'relative z-1 flex h-[76px] flex-none items-center gap-[10px] border-b border-shell-overlay px-5',
                'text-[19px] font-bold tracking-[-0.02em] text-white',
                collapsed && 'justify-center px-0',
            )}
        >
            <img src={CoskyLogo} alt="CoSky" className="size-[34px] flex-none"/>
            {!collapsed && (
                <span className="grid gap-px">
                    <strong className="text-[18px] leading-[1.1]">CoSky</strong>
                    <small className="text-[9px] font-[450] tracking-normal text-shell-subtle">Microservice Governance</small>
                </span>
            )}
        </NavLink>
        <nav className="relative z-1 flex flex-1 flex-col gap-[5px] px-[10px] py-[22px]" aria-label="Primary navigation">
            {primaryItems.map(({to, label, icon: Icon}) => (
                <NavLink key={to} to={to} title={label} onClick={onNavigate} className={({isActive}) => navItemClass(collapsed, isActive)}>
                    <Icon/>
                    {!collapsed && <span>{label}</span>}
                </NavLink>
            ))}
            {!collapsed ? (
                <Collapsible open={securityOpen} onOpenChange={setSecurityOpen}>
                    <CollapsibleTrigger className={cn(navItemClass(false), 'group/security [&>span]:flex-1 [&>span]:text-left')} title="Security">
                        <Shield/>
                        <span>Security</span>
                        <ChevronDown className="size-[14px]! transition-transform duration-150 ease-[ease] group-data-panel-open/security:rotate-180"/>
                    </CollapsibleTrigger>
                    <CollapsibleContent className="grid gap-[3px] pt-1 pr-0 pb-[3px] pl-4">
                        {securityItems.map(({to, label, icon: Icon}) => (
                            <NavLink key={to} to={to} onClick={onNavigate} className={({isActive}) => navItemClass(false, isActive)}>
                                <Icon/>
                                <span>{label}</span>
                            </NavLink>
                        ))}
                    </CollapsibleContent>
                </Collapsible>
            ) : securityItems.map(({to, label, icon: Icon}) => (
                <NavLink key={to} to={to} title={label} onClick={onNavigate} className={({isActive}) => navItemClass(true, isActive)}>
                    <Icon/>
                </NavLink>
            ))}
        </nav>
        {onCollapse && (
            <Button
                variant="ghost"
                onClick={onCollapse}
                className={cn(
                    'relative z-1 m-3 justify-start text-shell-muted hover:bg-shell-overlay hover:text-white',
                    collapsed && 'justify-center px-0',
                )}
            >
                {collapsed ? <ChevronsRight/> : <ChevronsLeft/>}
                {!collapsed && 'Collapse'}
            </Button>
        )}
    </>;
}
