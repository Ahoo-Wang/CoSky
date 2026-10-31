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

import React, {type ReactNode} from "react";
import {useKeyStorage} from "@ahoo-wang/fetcher-react";
import {currentNamespaceStorage, DEFAULT_NAMESPACE} from "@/contexts/namespace/CurrentNamespaceStorage";
import type { CurrentNamespaceContextType} from "@/contexts/namespace/CurrentNamespaceContext";
import {CurrentNamespaceContext} from "@/contexts/namespace/CurrentNamespaceContext";

export const CurrentNamespaceProvider: React.FC<{ children: ReactNode }> = ({children}) => {
    const [currentNamespace, setCurrentNamespace] = useKeyStorage(currentNamespaceStorage, DEFAULT_NAMESPACE)

    const reset = () => {
        setCurrentNamespace(DEFAULT_NAMESPACE);
    };


    const value: CurrentNamespaceContextType = {
        currentNamespace: currentNamespace,
        setCurrent: setCurrentNamespace,
        reset,
    };

    return <CurrentNamespaceContext.Provider value={value}>{children}</CurrentNamespaceContext.Provider>;
};