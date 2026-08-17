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

import React from 'react';
import {useCurrentNamespaceContext} from '../../contexts/namespace/CurrentNamespaceContext.tsx';
import {NamespaceSelector} from "../namespace/NamespaceSelector.tsx";
import {Layers3} from "lucide-react";

export const CurrentNamespaceSelector: React.FC = () => {
    const {currentNamespace, setCurrent} = useCurrentNamespaceContext();
    
    return (
        <div className="app-namespace">
            <Layers3/>
            <div>
                <span>Namespace</span>
                <NamespaceSelector
                    value={currentNamespace}
                    onChange={setCurrent}
                    triggerClassName="border-0 bg-transparent p-0 text-white shadow-none focus-visible:ring-0 [&_svg]:text-white/60"
                />
            </div>
        </div>
    );
};
