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
import {minRevisionOf, readingAgain, readsBehind, readUpTo, watchedUpTo} from '../../src/services/revisions.ts';

// Each test has a namespace of its own: the positions are the module's.

test('a snapshot of another epoch starts over, though its revision is lower', () => {
    watchedUpTo('failover', '100', 'before', true);
    expect(minRevisionOf('failover')).toBe('100');

    watchedUpTo('failover', '90', 'after', true);

    expect(minRevisionOf('failover')).toBe('90');
});

test('what belongs to another epoch than the latest snapshot counts for nothing', () => {
    watchedUpTo('late', '100', 'before', true);
    readUpTo('late', '100', 'before');
    watchedUpTo('late', '90', 'after', true);

    // An event of a stream still on the old timeline, and a read answered from it.
    watchedUpTo('late', '101', 'before');
    readUpTo('late', '101', 'before');

    expect(minRevisionOf('late')).toBe('90');
    expect(readsBehind('late', '90')).toBe(true);
    readUpTo('late', '90', 'after');
    expect(readsBehind('late', '90')).toBe(false);
});

test('a read further on does not vouch for another that is behind', () => {
    watchedUpTo('summary', '100', 'one', true);
    readingAgain('summary');
    // The services view from a replica still at 100, the instances from one at 101.
    readUpTo('summary', '100');
    readUpTo('summary', '101', 'one');
    watchedUpTo('summary', '101', 'one');

    expect(readsBehind('summary', '101')).toBe(true);

    readingAgain('summary');
    readUpTo('summary', '101');
    readUpTo('summary', '101', 'one');
    expect(readsBehind('summary', '101')).toBe(false);
});

test('within an epoch, the watch only moves forward', () => {
    watchedUpTo('forward', '50', 'one', true);
    watchedUpTo('forward', '40', 'one');
    expect(minRevisionOf('forward')).toBe('50');
    watchedUpTo('forward', '60', 'one');
    expect(minRevisionOf('forward')).toBe('60');
});

test('a namespace nothing has read yet is behind', () => {
    expect(readsBehind('unread', '1')).toBe(true);
    expect(minRevisionOf('unread')).toBeUndefined();
});
