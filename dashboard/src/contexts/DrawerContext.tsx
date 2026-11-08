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

import type {ReactNode} from 'react';
import { useContext} from 'react';
import {createContext} from 'react';

export interface DrawerOptions {
    title?: ReactNode;
    width?: string;
}

interface DrawerContextType {
    /**
     * @param render what to show, given what closes it. That closes this drawer only: an operation that ends after its
     * drawer was closed must leave alone whatever has been opened since.
     */
    openDrawer: (render: (close: () => void) => ReactNode, options?: DrawerOptions) => void;
    /**
     * For what an operation shows when it ends: the drawer it returns opens only if nothing else has opened since and
     * the page is still the one the operation started on, so that a late answer does not replace what someone is
     * doing now.
     *
     * @return opens the drawer as [openDrawer] does, and says whether it did.
     */
    reserveDrawer: () => (render: (close: () => void) => ReactNode, options?: DrawerOptions) => boolean;
}

export const DrawerContext = createContext<DrawerContextType | undefined>(undefined);

export const useDrawer = () => {
    const context = useContext(DrawerContext);
    if (!context) {
        throw new Error('useDrawer must be used within DrawerProvider');
    }
    return context;
};
