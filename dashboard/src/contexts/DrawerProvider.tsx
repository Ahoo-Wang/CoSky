import React, {type ReactNode, useState} from "react";
import {DrawerContext} from "./DrawerContext.tsx";
import type {DrawerOptions} from "./DrawerContext.tsx";
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
