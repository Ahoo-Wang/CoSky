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
import {OIDC_SIGN_IN_KEY, session} from '../../src/security/session.ts';
import {currentToken} from '../../src/security/renewal.ts';

const SECOND = 1000;

function storage(): Storage {
    const values = new Map<string, string>();
    return {
        getItem: key => values.get(key) ?? null,
        setItem: (key, value) => void values.set(key, value),
        removeItem: key => void values.delete(key),
    } as Storage;
}

function idToken(secondsLeft: number): string {
    const now = Math.floor(Date.now() / SECOND);
    const payload = Buffer.from(JSON.stringify({iat: now - 60, exp: now + secondsLeft})).toString('base64url');
    return `header.${payload}.signature`;
}

test.describe('a request after a sleep', () => {
    let requests: string[];

    test.beforeEach(() => {
        requests = [];
        globalThis.sessionStorage = storage();
        globalThis.fetch = (async (_: string, init: RequestInit) => {
            requests.push(String(init.body));
            return new Response(JSON.stringify({id_token: 'the-new-one', refresh_token: 'rotated'}));
        }) as typeof fetch;
    });

    const signedIn = (token: string, refreshToken?: string) => {
        session.signIn(token);
        sessionStorage.setItem(OIDC_SIGN_IN_KEY, JSON.stringify({
            clientId: 'dashboard',
            tokenEndpoint: 'https://idp.example/token',
            redirectUri: 'https://cosky.example/',
            refreshToken,
        }));
    };

    test('waits for a new token if the one it has expired', async () => {
        signedIn(idToken(-5), 'refresh');
        expect(await currentToken()).toBe('the-new-one');
        expect(requests).toHaveLength(1);
        expect(requests[0]).toContain('grant_type=refresh_token');
        expect(sessionStorage.getItem(OIDC_SIGN_IN_KEY)).toContain('rotated');
    });

    test('goes on with a token that still holds', async () => {
        const token = idToken(120);
        signedIn(token, 'refresh');
        expect(await currentToken()).toBe(token);
        expect(requests).toHaveLength(0);
    });

    test('goes on with what it has when there is no way to renew, and lets the server say so', async () => {
        const token = idToken(-5);
        signedIn(token);
        expect(await currentToken()).toBe(token);
        expect(requests).toHaveLength(0);
    });

    test('a token of a ServiceAccount is left alone', async () => {
        session.signIn('cosky_id_secret');
        expect(await currentToken()).toBe('cosky_id_secret');
        expect(requests).toHaveLength(0);
    });

    test('many requests at once renew once', async () => {
        signedIn(idToken(-5), 'refresh');
        const tokens = await Promise.all([currentToken(), currentToken(), currentToken()]);
        expect(tokens).toEqual(['the-new-one', 'the-new-one', 'the-new-one']);
        expect(requests).toHaveLength(1);
    });
});
