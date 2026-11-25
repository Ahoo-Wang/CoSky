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

import {expect, test} from '@playwright/test';
import {DEFAULT_PAGE_SIZE, PAGE_SIZES, pageHolding, storedPageSize, storePageSize} from '../../src/lib/pageSize.ts';

/** What a browser keeps, in memory. */
function memoryStorage(): Storage {
    const values = new Map<string, string>();
    return {
        get length() {
            return values.size;
        },
        clear: () => values.clear(),
        getItem: key => values.get(key) ?? null,
        key: index => [...values.keys()][index] ?? null,
        removeItem: key => {
            values.delete(key);
        },
        setItem: (key, value) => {
            values.set(key, value);
        },
    };
}

test('a list shows 10 rows until someone chooses another of the sizes offered', () => {
    expect(PAGE_SIZES).toEqual([10, 20, 50, 100]);
    expect(DEFAULT_PAGE_SIZE).toBe(10);
    expect(storedPageSize('configs', memoryStorage)).toBe(10);
});

test('the size chosen is remembered for that list alone', () => {
    const storage = memoryStorage();

    storePageSize('configs', 50, () => storage);

    expect(storedPageSize('configs', () => storage)).toBe(50);
    expect(storedPageSize('namespaces', () => storage)).toBe(10);
});

test('anything stored but a size offered counts as no choice', () => {
    const storage = memoryStorage();

    for (const stored of ['37', '0', '-10', 'many', '1e3', '']) {
        storage.setItem('cosky:page-size:configs', stored);
        expect(storedPageSize('configs', () => storage), stored).toBe(10);
    }
});

test('without storage, the default is shown and a choice is not kept', () => {
    const turnedOff = (): Storage => {
        throw new DOMException('The operation is insecure.', 'SecurityError');
    };
    const full = memoryStorage();
    full.setItem = () => {
        throw new DOMException('The quota has been exceeded.', 'QuotaExceededError');
    };
    const unreadable = memoryStorage();
    unreadable.getItem = () => {
        throw new DOMException('The operation is insecure.', 'SecurityError');
    };

    expect(() => storePageSize('configs', 50, turnedOff)).not.toThrow();
    expect(storedPageSize('configs', turnedOff)).toBe(10);
    expect(() => storePageSize('configs', 50, () => full)).not.toThrow();
    expect(storedPageSize('configs', () => full)).toBe(10);
    expect(storedPageSize('configs', () => unreadable)).toBe(10);
});

test('a new page size keeps the first row of the page on screen', () => {
    // Rows 1-10.
    expect(pageHolding(1, 10, 50)).toBe(1);
    // Rows 21-30, on rows 21-40.
    expect(pageHolding(3, 10, 20)).toBe(2);
    // Rows 41-60, on rows 1-100.
    expect(pageHolding(3, 20, 100)).toBe(1);
    // Rows 51-100, on rows 51-60.
    expect(pageHolding(2, 50, 10)).toBe(6);
    // Rows 101-200, on rows 101-150.
    expect(pageHolding(2, 100, 50)).toBe(3);
});
