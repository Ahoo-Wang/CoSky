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

import {useMe} from '@/contexts/MeContext';

/**
 * What to show someone who has signed in but may do nothing: without this every page only says that it failed to load.
 */
export function NoPermissions() {
    const me = useMe();
    const isUser = me.subject?.type === 'TYPE_USER';

    return (
        <div role="alert" className="max-w-2xl space-y-3 text-sm">
            <h1 className="text-xl font-semibold">Your account has no permissions yet</h1>
            <p>An administrator can give you access with a RoleBinding for any of these subjects:</p>
            <ul className="list-disc space-y-1 pl-5">
                <li>{isUser ? 'User' : 'ServiceAccount'} <code className="break-all">{me.subject?.name}</code></li>
                {(me.groups ?? []).map(group => (
                    <li key={group}>Group <code className="break-all">{group}</code></li>
                ))}
            </ul>
        </div>
    );
}
