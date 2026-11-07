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

import {useRef} from 'react';

/**
 * Reads a page together with the rows it has expanded, as one read that ends when all of them have. Whatever waits for
 * it, such as the next read of useWatchRefresh, so waits for the rows too: a row that read again on each change by
 * itself would be cut short by the next one, and show nothing new for as long as changes keep coming.
 *
 * @return `read`, and `follow`, which makes a row read along with the page until the function it returns is called.
 */
export function useRowReads(readPage: () => Promise<unknown>) {
    const rows = useRef(new Set<() => Promise<unknown>>());
    const read = () => Promise.all([readPage(), ...Array.from(rows.current, readRow => readRow())]);
    const follow = (readRow: () => Promise<unknown>) => {
        rows.current.add(readRow);
        return () => {
            rows.current.delete(readRow);
        };
    };
    return {read, follow};
}
