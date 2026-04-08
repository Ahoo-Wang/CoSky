import React, {type ReactNode, useState} from "react";
import type { DrawerProps} from "antd";
import {Drawer} from "antd";
import {DrawerContext} from "./DrawerContext.tsx";

export const DrawerProvider: React.FC<{ children: ReactNode }> = ({children}) => {
    const [open, setOpen] = useState(false);
    const [content, setContent] = useState<ReactNode>(null);
    const [drawerProps, setDrawerProps] = useState<Partial<DrawerProps>>({});

    const openDrawer = (drawerContent: ReactNode, props: Partial<DrawerProps> = {}) => {
        setContent(drawerContent);
        setDrawerProps(props);
        setOpen(true);
    };

    const closeDrawer = () => {
        setOpen(false);
        setTimeout(() => {
            setContent(null);
            setDrawerProps({});
        }, 300);
    };

    const handleClose = () => {
        closeDrawer();
    };
    return (
        <DrawerContext.Provider value={{openDrawer, closeDrawer}}>
            {children}
            <Drawer
                open={open}
                onClose={handleClose}
                defaultSize={'60vw'}
                resizable
                placement="right"
                {...drawerProps}
            >
                {content}
            </Drawer>
        </DrawerContext.Provider>
    );
};