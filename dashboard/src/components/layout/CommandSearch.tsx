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
import {ArrowRight, Search} from 'lucide-react';
import {useLocation, useNavigate} from 'react-router-dom';
import {toast} from 'sonner';
import {Button} from '@/components/ui/button';
import {Input} from '@/components/ui/input';
import {cn} from '@/lib/utils';
import {navigationItems} from './navigation';
import type {NavigationItem} from './navigation';

// Base UI dialogs and sheets expose explicit roles; the topology fullscreen uses a native
// <dialog> whose implicit role carries no attribute, hence the dialog[open] check.
const isModalOpen = () =>
    Boolean(document.querySelector('[role="dialog"], [role="alertdialog"], dialog[open]'));

const isTypingTarget = (target: EventTarget | null, searchInput: HTMLInputElement | null) =>
    target instanceof HTMLElement && target !== searchInput &&
    target.matches('input, textarea, select, [contenteditable="true"]');

const kbdClass = 'min-w-[18px] rounded-[4px] border border-shell-overlay-strong bg-shell-overlay-subtle px-1 py-px text-center font-[inherit] text-[9px] text-shell-key';

/**
 * Page search in the header: filters the navigation, opens with `/` or Ctrl/⌘+K, and is driven by the keyboard.
 */
export function CommandSearch() {
    const [searchValue, setSearchValue] = useState('');
    const [searchOpen, setSearchOpen] = useState(false);
    const [activeIndex, setActiveIndex] = useState(0);
    const inputRef = useRef<HTMLInputElement>(null);
    const navigate = useNavigate();
    const location = useLocation();
    const query = searchValue.trim().toLowerCase();
    const results = navigationItems.filter(item => `${item.label} ${item.description}`.toLowerCase().includes(query));

    useEffect(() => {
        const focusSearch = (event: globalThis.KeyboardEvent) => {
            const isCommandK = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k';
            if (!isCommandK && event.key !== '/') return;
            // The search control is hidden on small screens: leave the shortcuts alone there.
            if (inputRef.current?.checkVisibility() !== true) return;
            // Never hijack keys while a modal surface (sheet, dialog, alert dialog) is open.
            if (isModalOpen()) return;
            // '/' stays out of the way of typing; Ctrl/Cmd+K intentionally works from anywhere.
            if (!isCommandK && isTypingTarget(event.target, inputRef.current)) return;
            event.preventDefault();
            event.stopPropagation();
            inputRef.current?.focus();
            setSearchOpen(true);
        };
        // Capture, so the shortcut wins over handlers inside the page.
        window.addEventListener('keydown', focusSearch, true);
        return () => window.removeEventListener('keydown', focusSearch, true);
    }, []);

    const open = (target: NavigationItem) => {
        navigate(target.to);
        setSearchValue('');
        setSearchOpen(false);
        inputRef.current?.blur();
    };

    const handleSubmit = (event: FormEvent) => {
        event.preventDefault();
        const target = results[activeIndex];
        if (target) {
            open(target);
            return;
        }
        toast.error(`No page matches “${searchValue.trim()}”.`);
    };

    const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
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
        if (!['ArrowDown', 'ArrowUp'].includes(event.key) || results.length === 0) return;
        event.preventDefault();
        setSearchOpen(true);
        setActiveIndex(index =>
            event.key === 'ArrowDown'
                ? (index + 1) % results.length
                : (index - 1 + results.length) % results.length
        );
    };

    const handleFocus = () => {
        const nextIndex = results.findIndex(item => item.to !== location.pathname);
        setActiveIndex(nextIndex >= 0 ? nextIndex : 0);
        setSearchOpen(true);
    };

    const handleBlur = (event: FocusEvent<HTMLFormElement>) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setSearchOpen(false);
    };

    return (
        <form className="relative w-full max-[720px]:hidden" role="search" onSubmit={handleSubmit} onBlur={handleBlur}>
            <Search className="absolute top-1/2 left-3 w-[17px] -translate-y-1/2 text-shell-muted"/>
            <Input
                ref={inputRef}
                type="text"
                role="combobox"
                value={searchValue}
                onChange={event => {
                    setSearchValue(event.target.value);
                    setActiveIndex(0);
                    setSearchOpen(true);
                }}
                onFocus={handleFocus}
                onKeyDown={handleKeyDown}
                placeholder="Search pages..."
                aria-label="Search navigation"
                aria-expanded={searchOpen}
                aria-controls="navigation-search-results"
                aria-autocomplete="list"
                aria-activedescendant={searchOpen && results.length > 0 ? `navigation-search-result-${activeIndex}` : undefined}
                className={cn(
                    'h-10 border-shell-overlay-strong bg-shell-overlay-subtle pr-[66px] pl-[38px] text-white placeholder:text-shell-subtle',
                    'focus-visible:border-shell-focus focus-visible:shadow-[0_0_0_2px_var(--shell-focus-ring)] focus-visible:ring-0',
                )}
            />
            {searchValue ? (
                <Button type="submit" variant="ghost" size="icon-sm" aria-label="Open selected page"
                        className="absolute top-1/2 right-[6px] -translate-y-1/2 text-shell-muted hover:bg-shell-overlay hover:text-white">
                    <ArrowRight/>
                </Button>
            ) : (
                <kbd className="absolute top-1/2 right-[10px] -translate-y-1/2 rounded-[6px] border border-shell-overlay-strong bg-shell-overlay-subtle px-[6px] py-[2px] font-[inherit] text-[10px] leading-[1.4] text-shell-muted">
                    /
                </kbd>
            )}
            {searchOpen && (
                <div
                    id="navigation-search-results"
                    role="listbox"
                    aria-label="Navigation results"
                    className="absolute top-[calc(100%+8px)] left-0 z-60 w-full overflow-hidden rounded-[12px] border border-shell-overlay-strong bg-shell-raised shadow-[0_18px_48px_rgba(7,7,20,0.38)]"
                >
                    <div className="flex items-center justify-between px-[10px] pt-[9px] pb-[7px] text-[11px] tracking-[0.05em] text-shell-subtle uppercase">
                        <span>{query ? 'Matching pages' : 'Quick navigation'}</span>
                        <small>{results.length} result{results.length === 1 ? '' : 's'}</small>
                    </div>
                    {results.map((item, index) => {
                        const Icon = item.icon;
                        const isCurrent = location.pathname === item.to;
                        return (
                            <button
                                key={item.to}
                                id={`navigation-search-result-${index}`}
                                type="button"
                                role="option"
                                aria-selected={index === activeIndex}
                                onMouseEnter={() => setActiveIndex(index)}
                                onClick={() => open(item)}
                                className="mx-1 flex min-h-[52px] w-[calc(100%-8px)] cursor-pointer items-center gap-[10px] rounded-[8px] border-0 bg-transparent px-[9px] py-[7px] text-left text-shell-foreground aria-selected:bg-shell-selected aria-selected:text-white [&>svg]:size-4 [&>svg]:text-shell-faint"
                            >
                                <span className="grid size-8 flex-none place-items-center rounded-[8px] border border-shell-accent-line bg-shell-accent-wash text-shell-accent-text [&_svg]:size-4">
                                    <Icon/>
                                </span>
                                <span className="grid min-w-0 flex-1 gap-[2px]">
                                    <strong className="text-[13px] font-semibold">{item.label}</strong>
                                    <small className="overflow-hidden text-[11px] text-ellipsis whitespace-nowrap text-shell-subtle">{item.description}</small>
                                </span>
                                {isCurrent
                                    ? <span className="rounded-full bg-shell-overlay px-[7px] py-[3px] text-[10px] text-shell-pill">Current</span>
                                    : <ArrowRight/>}
                            </button>
                        );
                    })}
                    {results.length === 0 && (
                        <div role="status" className="grid min-h-[112px] place-items-center content-center gap-1 text-shell-foreground">
                            <Search className="mb-[3px] w-5 text-shell-faint"/>
                            <strong className="text-[13px]">No pages found</strong>
                            <span className="text-[11px] text-shell-subtle">Try “service”, “user”, or “audit”.</span>
                        </div>
                    )}
                    <div className="mt-[5px] flex items-center justify-start gap-[14px] border-t border-shell-overlay px-[10px] py-2 text-[11px] text-shell-subtle [&>span]:flex [&>span]:items-center [&>span]:gap-1">
                        <span><kbd className={kbdClass}>↑</kbd><kbd className={kbdClass}>↓</kbd> Navigate</span>
                        <span><kbd className={kbdClass}>Enter</kbd> Open</span>
                        <span><kbd className={kbdClass}>Esc</kbd> Close</span>
                    </div>
                </div>
            )}
        </form>
    );
}
