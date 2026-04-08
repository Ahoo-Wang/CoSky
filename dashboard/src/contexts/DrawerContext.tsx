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

import type { ReactNode} from 'react';
import React, {createContext, useState} from 'react';
import type { DrawerProps} from 'antd';
import {Drawer} from 'antd';

interface DrawerContextType {
    openDrawer: (content: ReactNode, props?: Partial<DrawerProps>) => void;
    closeDrawer: () => void;
}

export const DrawerContext = createContext<DrawerContextType | undefined>(undefined);

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