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

import {useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode} from 'react';
import {Sheet, SheetContent, SheetHeader, SheetTitle} from '@/components/ui/sheet';
import {DrawerContext, type DrawerOptions} from './DrawerContext';

function parseSize(size?: string): number {
    if (size?.endsWith('vw')) {
        return (window.innerWidth * parseFloat(size)) / 100;
    }
    return window.innerWidth * 0.6;
}

const CLOSE_CLEAR_DELAY = 300;

export function DrawerProvider({children}: { children: ReactNode }) {
    const [open, setOpen] = useState(false);
    const [content, setContent] = useState<ReactNode>(null);
    const [options, setOptions] = useState<DrawerOptions>({});
    const [width, setWidth] = useState<number>(window.innerWidth * 0.6);
    const clearTimerRef = useRef<number | null>(null);

    const clearPendingTimer = () => {
        if (clearTimerRef.current !== null) {
            window.clearTimeout(clearTimerRef.current);
            clearTimerRef.current = null;
        }
    };

    const openDrawer = (drawerContent: ReactNode, drawerOptions: DrawerOptions = {}) => {
        clearPendingTimer();
        setContent(drawerContent);
        setOptions(drawerOptions);
        setWidth(parseSize(drawerOptions.defaultSize));
        setOpen(true);
    };

    const closeDrawer = () => {
        setOpen(false);
        clearPendingTimer();
        clearTimerRef.current = window.setTimeout(() => {
            clearTimerRef.current = null;
            setContent(null);
            setOptions({});
        }, CLOSE_CLEAR_DELAY);
    };

    const startResize = (e: ReactPointerEvent) => {
        e.preventDefault();
        const startX = e.clientX;
        const startWidth = width;
        const onMove = (ev: PointerEvent) => {
            const next = startWidth + (startX - ev.clientX);
            setWidth(Math.min(Math.max(next, 360), window.innerWidth * 0.95));
        };
        const onUp = () => {
            window.removeEventListener('pointermove', onMove);
            window.removeEventListener('pointerup', onUp);
        };
        window.addEventListener('pointermove', onMove);
        window.addEventListener('pointerup', onUp);
    };

    return (
        <DrawerContext.Provider value={{openDrawer, closeDrawer}}>
            {children}
            <Sheet open={open} onOpenChange={(next) => { if (!next) closeDrawer(); }}>
                <SheetContent
                    side="right"
                    className="flex w-full flex-col p-0 sm:max-w-none"
                    style={{width, maxWidth: '95vw'}}
                >
                    <div
                        aria-hidden
                        onPointerDown={startResize}
                        className="absolute left-0 top-0 z-20 h-full w-1 cursor-ew-resize hover:bg-primary/30 active:bg-primary/50"
                    />
                    <SheetHeader className="border-b px-6 py-4">
                        <SheetTitle>{options.title}</SheetTitle>
                    </SheetHeader>
                    <div className="flex-1 overflow-y-auto px-6 py-4">
                        {content}
                    </div>
                </SheetContent>
            </Sheet>
        </DrawerContext.Provider>
    );
}
