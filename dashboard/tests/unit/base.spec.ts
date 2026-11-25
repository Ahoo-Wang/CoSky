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
import {combineURLs} from '@ahoo-wang/fetcher';
import {dashboardBaseOf} from '../../src/lib/base.ts';

test('the Dashboard the server hosts is at the root', () => {
    expect(dashboardBaseOf('https://cosky.example.com/')).toBe('https://cosky.example.com/');
    expect(dashboardBaseOf('https://cosky.example.com/index.html')).toBe('https://cosky.example.com/');
});

test('behind a proxy, the Dashboard is under its prefix', () => {
    expect(dashboardBaseOf('https://example.com/cosky/')).toBe('https://example.com/cosky/');
    expect(dashboardBaseOf('https://example.com/ops/cosky/index.html')).toBe('https://example.com/ops/cosky/');
});

test('the query a provider sends back and the route in the fragment are no part of it', () => {
    expect(dashboardBaseOf('https://example.com/cosky/?code=abc&state=xyz#/config'))
        .toBe('https://example.com/cosky/');
});

test('an address without its trailing slash is not under the prefix, which is why the proxy redirects it', () => {
    expect(dashboardBaseOf('https://example.com/cosky')).toBe('https://example.com/');
});

test('the API is under the Dashboard, wherever that is', () => {
    expect(combineURLs(dashboardBaseOf('https://example.com/cosky/#/home'), '/v1/me'))
        .toBe('https://example.com/cosky/v1/me');
    expect(combineURLs(dashboardBaseOf('http://127.0.0.1:8080/'), '/v1/namespaces/default/watch'))
        .toBe('http://127.0.0.1:8080/v1/namespaces/default/watch');
});
