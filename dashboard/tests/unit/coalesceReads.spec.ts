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
import {coalesceReads} from '../../src/lib/coalesceReads.ts';

const delay = (milliseconds: number) => new Promise(resolve => setTimeout(resolve, milliseconds));

test('changes that arrive together cause one read', async () => {
    let reads = 0;
    const {onChange, stop} = coalesceReads(() => {
        reads += 1;
    }, 20);
    onChange();
    onChange();
    onChange();
    await delay(80);
    expect(reads).toBe(1);
    stop();
});

test('changes that never stop still get read', async () => {
    let reads = 0;
    const {onChange, stop} = coalesceReads(() => {
        reads += 1;
    }, 20);
    for (let elapsed = 0; elapsed < 200; elapsed += 5) {
        onChange();
        await delay(5);
    }
    expect(reads).toBeGreaterThan(1);
    stop();
});

test('a read runs to its end, and what changed meanwhile is read after it', async () => {
    let running = 0;
    let most = 0;
    let reads = 0;
    const {onChange, stop} = coalesceReads(async () => {
        running += 1;
        most = Math.max(most, running);
        reads += 1;
        await delay(50);
        running -= 1;
    }, 5);
    onChange();
    await delay(20);
    onChange();
    onChange();
    await delay(150);
    expect(most).toBe(1);
    expect(reads).toBe(2);
    stop();
});

test('a read that fails does not stop the next one', async () => {
    let reads = 0;
    const {onChange, stop} = coalesceReads(async () => {
        reads += 1;
        throw new Error('The page says so itself.');
    }, 5);
    onChange();
    await delay(30);
    onChange();
    await delay(30);
    expect(reads).toBe(2);
    stop();
});

test('nothing is read after it stops', async () => {
    let reads = 0;
    const {onChange, stop} = coalesceReads(() => {
        reads += 1;
    }, 10);
    onChange();
    stop();
    await delay(40);
    expect(reads).toBe(0);
});
