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

import type {Kind, WatchResponse} from '@/generated';
import {watchClient} from '@/services/clients';

const MIN_RETRY_MS = 1_000;
const MAX_RETRY_MS = 30_000;

export interface FollowOptions {
    namespace: string;
    kind: Kind;
    service?: string;
    signal: AbortSignal;
    onChange: () => void;
}

const delay = (milliseconds: number, signal: AbortSignal) => new Promise<void>(resolve => {
    const timer = setTimeout(resolve, milliseconds);
    signal.addEventListener('abort', () => {
        clearTimeout(timer);
        resolve();
    }, {once: true});
});

/**
 * Calls back whenever the objects may have changed, for as long as the signal lasts, so that a page can read them
 * again (design §4.5). A stream that breaks is opened anew, and its snapshot covers whatever was missed meanwhile.
 */
export async function followChanges({namespace, kind, service, signal, onChange}: FollowOptions): Promise<void> {
    let retry = MIN_RETRY_MS;
    const abortController = new AbortController();
    signal.addEventListener('abort', () => abortController.abort(), {once: true});
    while (!signal.aborted) {
        try {
            const stream = await watchClient.watch({
                urlParams: {path: {namespace}, query: {kind, ...(service ? {service} : {})}},
                abortController,
            });
            const reader = stream.getReader();
            for (let next = await reader.read(); !next.done; next = await reader.read()) {
                retry = MIN_RETRY_MS;
                const message = next.value.data as Partial<WatchResponse>;
                // Nothing more will come: the namespace is gone, or this subject may not watch it.
                if (message.canceled) {
                    return;
                }
                if (message.snapshot || message.events || message.gone !== undefined) {
                    onChange();
                }
            }
        } catch {
            // Told apart by the loop: an abort ends it, and anything else opens the stream again.
        }
        await delay(retry, signal);
        retry = Math.min(retry * 2, MAX_RETRY_MS);
    }
}
