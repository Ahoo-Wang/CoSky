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

import {useEffect} from 'react';

export type DashboardCommand = 'add-config' | 'add-service' | 'add-user' | 'add-role';

const EVENT_NAME = 'cosky:dashboard-command';

let pendingCommand: DashboardCommand | null = null;

export function emitCommand(cmd: DashboardCommand) {
    pendingCommand = cmd;
    window.dispatchEvent(new CustomEvent(EVENT_NAME, {detail: cmd}));
}

export function useDashboardCommand(cmd: DashboardCommand, handler: () => void) {
    useEffect(() => {
        if (pendingCommand === cmd) {
            pendingCommand = null;
            handler();
        }
        const listener = (e: Event) => {
            if ((e as CustomEvent<DashboardCommand>).detail === cmd) {
                handler();
            }
        };
        window.addEventListener(EVENT_NAME, listener);
        return () => window.removeEventListener(EVENT_NAME, listener);
    }, [cmd, handler]);
}
