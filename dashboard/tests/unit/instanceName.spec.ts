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
import {defaultInstanceName} from '../../src/pages/service/instanceName.ts';

const LONGEST_SERVICE = 's'.repeat(63);
const LONG_DOMAIN = Array.from({length: 3}, () => 'h'.repeat(63)).join('.');
const NAME_PATTERN = /^[A-Za-z0-9._-]{1,253}$/;

// The same cases NamingRulesTest pins for the client: service, host, port and the name of that endpoint.
const PINNED: [string, string, number, string][] = [
    ['order', '10.0.0.1', 8080, 'order_10.0.0.1_8080'],
    ['billing-api', 'node', 8080, 'billing-api_node_8080'],
    ['billing', 'api-node', 8080, 'billing_api-node_8080'],
    ['order', 'fe80::1', 80, 'order_fe80__1_80'],
    ['order', 'fe80--1', 80, 'order_fe80--1_80'],
    ['order', '::ffff:192.0.2.1', 443, 'order___ffff_192.0.2.1_443'],
];

test('an instance is named as the client names it', () => {
    for (const [service, host, port, name] of PINNED) {
        expect(defaultInstanceName(service, host, port)).toBe(name);
        expect(defaultInstanceName(service, host, String(port))).toBe(name);
    }
});

test('fields that hold a dash, and an IPv6 host and a name spelled like it, keep apart', () => {
    expect(defaultInstanceName('billing-api', 'node', 8080)).not.toBe(defaultInstanceName('billing', 'api-node', 8080));
    expect(defaultInstanceName('order', 'fe80::1', 80)).not.toBe(defaultInstanceName('order', 'fe80--1', 80));
});

test('a long name is cut', () => {
    expect(defaultInstanceName('o'.repeat(300), 'host', 80)).toHaveLength(253);
});

test('a name cut short keeps the port, so endpoints of one host stay apart', () => {
    const first = defaultInstanceName(LONGEST_SERVICE, LONG_DOMAIN, 8080);
    const second = defaultInstanceName(LONGEST_SERVICE, LONG_DOMAIN, '8081');
    expect(first).not.toBe(second);
    expect(first).toMatch(/_8080$/);
    expect(second).toMatch(/_8081$/);
    expect(first).toMatch(NAME_PATTERN);
    expect(second).toMatch(NAME_PATTERN);
});

test('hosts that differ only where the name is cut still get different names', () => {
    const first = defaultInstanceName(LONGEST_SERVICE, `${LONG_DOMAIN}.a`, 8080);
    const second = defaultInstanceName(LONGEST_SERVICE, `${LONG_DOMAIN}.b`, 8080);
    expect(first).not.toBe(second);
    expect(first).toHaveLength(253);
});

test('the longest legal service, host and port make the name the client makes', () => {
    const host = `${LONG_DOMAIN}.${'h'.repeat(61)}`;
    expect(host).toHaveLength(253);
    const name = defaultInstanceName(LONGEST_SERVICE, host, 65535);
    // The same digest NamingRulesTest expects of the client.
    const tail = '_db994d6a48b26506_65535';
    expect(name).toBe(`${LONGEST_SERVICE}_${host}`.slice(0, 253 - tail.length) + tail);
    expect(name).toMatch(NAME_PATTERN);
});
