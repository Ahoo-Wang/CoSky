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

import {ExchangeError} from '@ahoo-wang/fetcher';
import {ErrorReason, type Kind, type WatchResponse} from '@/generated';
import {watchClient} from '@/services/clients';
import {readingAgain, readsBehind, watchedUpTo} from '@/services/revisions';

const MIN_RETRY_MS = 1_000;
const MAX_RETRY_MS = 30_000;
// The server sends a bookmark while idle, every 10 seconds by default: silence for longer means the stream is dead,
// which a proxy can do without closing it. Opening it again costs little, should a server send them less often.
const SILENCE_MS = 30_000;
const FORBIDDEN = 403;

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
 * @return undefined when nothing arrived for too long.
 */
async function readOrSilence<T>(reader: ReadableStreamDefaultReader<T>): Promise<ReadableStreamReadResult<T> | undefined> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const silence = new Promise<undefined>(resolve => {
        timer = setTimeout(() => resolve(undefined), SILENCE_MS);
    });
    try {
        return await Promise.race([reader.read(), silence]);
    } finally {
        clearTimeout(timer);
    }
}

/**
 * Calls back whenever the objects may have changed, for as long as the signal lasts, so that a page can read them
 * again (design §4.5). A stream that breaks is opened anew, and its snapshot covers whatever was missed meanwhile.
 */
export async function followChanges({namespace, kind, service, signal, onChange}: FollowOptions): Promise<void> {
    let retry = MIN_RETRY_MS;
    while (!signal.aborted) {
        // One per attempt: the fetcher's timeout aborts the one it is given, and that one would fail every later attempt.
        const abortController = new AbortController();
        const abort = () => abortController.abort();
        signal.addEventListener('abort', abort, {once: true});
        try {
            const stream = await watchClient.watch(
                namespace,
                kind,
                undefined,
                service || undefined,
                undefined,
                undefined,
                {abortController},
            );
            const reader = stream.getReader();
            // This stream's timeline, from its snapshot: what it reports belongs to that epoch.
            let epoch: string | undefined;
            for (let next = await readOrSilence(reader); next && !next.done; next = await readOrSilence(reader)) {
                retry = MIN_RETRY_MS;
                const message = next.value.data as Partial<WatchResponse>;
                if (message.canceled) {
                    // The token it was opened with expired: the next one carries the token renewed meanwhile.
                    if (message.canceled.detail?.reason === ErrorReason.ERROR_REASON_UNAUTHENTICATED) {
                        break;
                    }
                    // Nothing more will come: the namespace is gone, or this subject may not watch it.
                    return;
                }
                if (message.snapshot) {
                    epoch = message.snapshot.epoch;
                }
                if (message.snapshot || message.events || message.gone !== undefined) {
                    watchedUpTo(
                        namespace,
                        message.snapshot?.revision ?? message.events?.revision,
                        epoch,
                        message.snapshot !== undefined,
                    );
                    readingAgain(namespace);
                    onChange();
                } else if (message.progress !== undefined) {
                    // An earlier read may have come from a replica that was behind.
                    watchedUpTo(namespace, message.progress, epoch);
                    if (readsBehind(namespace, message.progress)) {
                        readingAgain(namespace);
                        onChange();
                    }
                }
            }
            await reader.cancel();
        } catch (error) {
            // Nothing more will come of one this subject may not watch, as of a canceled stream. An abort ends the loop
            // by itself, and anything else opens the stream again.
            if (error instanceof ExchangeError && error.exchange.response?.status === FORBIDDEN) {
                return;
            }
        } finally {
            signal.removeEventListener('abort', abort);
        }
        await delay(retry, signal);
        retry = Math.min(retry * 2, MAX_RETRY_MS);
    }
}
