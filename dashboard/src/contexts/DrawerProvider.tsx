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

import React, {type ReactNode, useState} from "react";
import {DrawerContext} from "@/contexts/DrawerContext";
import type {DrawerOptions} from "@/contexts/DrawerContext";
import {Sheet, SheetContent, SheetHeader, SheetTitle} from "@/components/ui/sheet";

export const DrawerProvider: React.FC<{ children: ReactNode }> = ({children}) => {
    const [open, setOpen] = useState(false);
    const [content, setContent] = useState<ReactNode>(null);
    const [options, setOptions] = useState<DrawerOptions>({});

    const openDrawer = (drawerContent: ReactNode, drawerOptions: DrawerOptions = {}) => {
        setContent(drawerContent);
        setOptions(drawerOptions);
        setOpen(true);
    };

    const closeDrawer = () => {
        setOpen(false);
    };
    return (
        <DrawerContext.Provider value={{openDrawer, closeDrawer}}>
            {children}
            <Sheet open={open} onOpenChange={(nextOpen) => !nextOpen && closeDrawer()}>
                <SheetContent
                    className="w-[calc(100vw-1.5rem)] max-w-none gap-0 sm:w-[min(60vw,960px)] sm:max-w-none"
                    style={{width: options.width}}
                >
                    <SheetHeader className="border-b px-6 py-5">
                        <SheetTitle>{options.title ?? 'Details'}</SheetTitle>
                    </SheetHeader>
                    <div className="min-h-0 flex-1 overflow-y-auto p-6">
                        {content}
                    </div>
                </SheetContent>
            </Sheet>
        </DrawerContext.Provider>
    );
};
