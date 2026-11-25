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

import React, {type ReactNode, useEffect, useRef, useState} from "react";
import {useLocation} from "react-router-dom";
import {DrawerContext} from "@/contexts/DrawerContext";
import type {DrawerOptions} from "@/contexts/DrawerContext";
import {Sheet, SheetContent, SheetHeader, SheetTitle} from "@/components/ui/sheet";

interface Shown {
    /** Which opening it is, so that what closes one leaves the next alone. */
    id: number;
    open: boolean;
    content: ReactNode;
    options: DrawerOptions;
}

export const DrawerProvider: React.FC<{ children: ReactNode }> = ({children}) => {
    const [shown, setShown] = useState<Shown>({id: 0, open: false, content: null, options: {}});
    const openings = useRef(0);
    // The opening on screen, until it is closed; 0 when there is none.
    const showing = useRef(0);
    const {key: location} = useLocation();
    const [shownAt, setShownAt] = useState(location);
    // Where an operation that ends now finds the page, which its closure saw only as it was when it began.
    const currentLocation = useRef(location);
    useEffect(() => {
        currentLocation.current = location;
        // A new page closes what the last one showed, as below.
        showing.current = 0;
    }, [location]);
    // The router shows the next page in a transition, once whatever it loads is there: until then, the page left
    // behind is still the location here, but the address has changed at once.
    const pageNow = () => `${currentLocation.current} ${window.location.href}`;

    // What it shows belongs to the page it was opened on, which going back or following a link leaves.
    if (shownAt !== location) {
        setShownAt(location);
        setShown({...shown, open: false});
    }

    const close = (id: number) => {
        if (showing.current === id) {
            showing.current = 0;
        }
        setShown(current => current.id === id ? {...current, open: false} : current);
    };

    const openDrawer = (render: (close: () => void) => ReactNode, options: DrawerOptions = {}) => {
        const id = ++openings.current;
        showing.current = id;
        setShown({id, open: true, content: render(() => close(id)), options});
    };

    const reserveDrawer = () => {
        const since = openings.current;
        const startedAt = pageNow();
        return (render: (close: () => void) => ReactNode, options: DrawerOptions = {}) => {
            if (openings.current !== since || pageNow() !== startedAt) {
                return false;
            }
            openDrawer(render, options);
            return true;
        };
    };

    const holdDrawer = () => {
        const held = showing.current;
        const startedAt = pageNow();
        return () => held !== 0 && showing.current === held && pageNow() === startedAt;
    };
    return (
        <DrawerContext.Provider value={{openDrawer, reserveDrawer, holdDrawer}}>
            {children}
            <Sheet open={shown.open} onOpenChange={(nextOpen) => !nextOpen && close(shown.id)}>
                <SheetContent
                    className="w-[calc(100vw-1.5rem)] max-w-none gap-0 sm:w-[min(60vw,960px)] sm:max-w-none"
                    style={{width: shown.options.width}}
                >
                    <SheetHeader className="border-b px-6 py-5">
                        <SheetTitle>{shown.options.title ?? 'Details'}</SheetTitle>
                    </SheetHeader>
                    <div className="min-h-0 flex-1 overflow-y-auto p-6">
                        {shown.content}
                    </div>
                </SheetContent>
            </Sheet>
        </DrawerContext.Provider>
    );
};
