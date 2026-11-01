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
import {challengeOf, claimsOf} from '../../src/security/oidc.ts';
import {renewAt} from '../../src/security/renewal.ts';

test('the code challenge is the one RFC 7636 gives as its example', async () => {
    expect(await challengeOf('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk'))
        .toBe('E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM');
});

test('the claims of an ID token survive base64url and UTF-8', () => {
    const payload = Buffer.from(JSON.stringify({sub: 'ada', name: '张三~?>'})).toString('base64url');

    expect(claimsOf(`header.${payload}.signature`)).toEqual({sub: 'ada', name: '张三~?>'});
});

test('a token is renewed a minute before it expires, or sooner if it does not last long', () => {
    expect(renewAt(1000, 1000 + 3600)).toBe((1000 + 3540) * 1000);
    expect(renewAt(1000, 1000 + 300)).toBe((1000 + 240) * 1000);
    expect(renewAt(1000, 1000 + 30)).toBe((1000 + 20) * 1000);
});
