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

export interface DefinitionItem {
    label: string;
    value: ReactNode;
}

export function DefinitionList({items}: {items: DefinitionItem[]}) {
    return (
        <dl className="grid overflow-hidden rounded-xl border bg-card sm:grid-cols-2">
            {items.map((item) => (
                <div key={item.label} className="grid grid-cols-[9rem_1fr] border-b px-4 py-3 last:border-b-0 sm:even:border-l">
                    <dt className="text-sm text-muted-foreground">{item.label}</dt>
                    <dd className="break-words text-sm font-medium text-foreground">{item.value}</dd>
                </div>
            ))}
        </dl>
    );
}
