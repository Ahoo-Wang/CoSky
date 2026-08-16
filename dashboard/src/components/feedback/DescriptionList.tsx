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

import {Fragment, type ReactNode} from 'react';

export function DescriptionList({items}: { items: { label: ReactNode; value: ReactNode }[] }) {
    return (
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            {items.map((item, index) => (
                <Fragment key={index}>
                    <dt className="text-muted-foreground">{item.label}</dt>
                    <dd className="font-medium break-all">{item.value}</dd>
                </Fragment>
            ))}
        </dl>
    );
}
