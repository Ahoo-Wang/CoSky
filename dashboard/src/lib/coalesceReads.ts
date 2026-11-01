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

/**
 * Turns changes into reads. Changes that arrive together cause one read, and a read runs to its end before the next:
 * pages abort a read when they start another, so while things keep changing none would ever finish.
 *
 * @param read may return a promise of its end.
 */
export function coalesceReads(read: () => unknown, settleMs: number) {
    let timer: ReturnType<typeof setTimeout> | undefined;
    let reading = false;
    let changedSince = false;
    let stopped = false;

    const onChange = () => {
        if (reading) {
            changedSince = true;
        } else if (timer === undefined) {
            timer = setTimeout(run, settleMs);
        }
    };

    const run = async () => {
        timer = undefined;
        reading = true;
        try {
            await read();
        } catch {
            // The page says so itself.
        } finally {
            reading = false;
        }
        if (changedSince && !stopped) {
            changedSince = false;
            onChange();
        }
    };

    return {
        onChange,
        stop: () => {
            stopped = true;
            clearTimeout(timer);
        },
    };
}
