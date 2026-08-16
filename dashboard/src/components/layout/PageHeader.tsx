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

export function PageHeader({title, actions}: { title: string; actions?: ReactNode }) {
    return (
        <div className="mb-6 flex items-center justify-between">
            <h2 className="text-2xl font-semibold tracking-tight">{title}</h2>
            {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
    );
}
