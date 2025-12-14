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

import React, {createContext, useContext, useCallback} from 'react';
import {KeyStorage, typedIdentitySerializer} from "@ahoo-wang/fetcher-storage";
import {useKeyStorage} from "@ahoo-wang/fetcher-react";

const NAMESPACE_KEY = 'cosky:ns:current';
const SYSTEM_NAMESPACE = 'cosky-{system}';
const SYSTEM_NAMESPACES = new Set(['cosky-{default}', SYSTEM_NAMESPACE]);

interface NamespaceContextType {
    currentNamespace: string;
    setCurrent: (namespace: string) => void;
    isSystem: (namespace: string) => boolean;
    reset: () => void;
}

export const currentNamespaceStorage = new KeyStorage<string>({
    key: NAMESPACE_KEY,
    serializer: typedIdentitySerializer()
})

const NamespaceContext = createContext<NamespaceContextType | undefined>(undefined);

export const NamespaceProvider: React.FC<{ children: React.ReactNode }> = ({children}) => {
    const [currentNamespace, setCurrentNamespace] = useKeyStorage(currentNamespaceStorage, SYSTEM_NAMESPACE)

    const reset = useCallback(() => {
        setCurrentNamespace(SYSTEM_NAMESPACE);
    }, [setCurrentNamespace]);

    const isSystem = useCallback((namespace: string): boolean => {
        return SYSTEM_NAMESPACES.has(namespace);
    }, []);

    const value: NamespaceContextType = {
        currentNamespace: currentNamespace,
        setCurrent: setCurrentNamespace,
        isSystem,
        reset,
    };

    return <NamespaceContext.Provider value={value}>{children}</NamespaceContext.Provider>;
};

export const useNamespace = (): NamespaceContextType => {
    const context = useContext(NamespaceContext);
    if (!context) {
        throw new Error('useNamespace must be used within a NamespaceProvider');
    }
    return context;
};
