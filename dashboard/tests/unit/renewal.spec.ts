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
import {OIDC_SIGN_IN_KEY, RECEIVED_AT_KEY, session} from '../../src/security/session.ts';
import {currentToken, localTimes, startRenewing} from '../../src/security/renewal.ts';
import {canRenew, signInWithToken, signOut} from '../../src/security/oidc.ts';

const SECOND = 1000;

function storage(): Storage {
    const values = new Map<string, string>();
    return {
        getItem: key => values.get(key) ?? null,
        setItem: (key, value) => void values.set(key, value),
        removeItem: key => void values.delete(key),
    } as Storage;
}

/**
 * @param skew how far the provider's clock is behind this one, in seconds.
 */
function idToken(secondsLeft: number, skew = 0): string {
    const now = Math.floor(Date.now() / SECOND) - skew;
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

    /** As a tab that got [token] when it was issued, a minute ago by its own clock. */
    const signedIn = (token: string, refreshToken?: string) => {
        session.signIn(token);
        sessionStorage.setItem(RECEIVED_AT_KEY, String(Date.now() - 60 * SECOND));
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

    test('signing out while a new token is on its way stays signed out', async () => {
        signedIn(idToken(-5), 'refresh');
        const answered = globalThis.fetch;
        let answer: (() => void) | undefined;
        globalThis.fetch = (async (input: string, init: RequestInit) => {
            await new Promise<void>(resolve => {
                answer = resolve;
            });
            return answered(input, init);
        }) as typeof fetch;

        const renewing = currentToken();
        await expect.poll(() => answer).toBeDefined();
        session.signOut();
        answer?.();
        await renewing;

        expect(session.token()).toBeNull();
        expect(sessionStorage.getItem(OIDC_SIGN_IN_KEY)).toBeNull();
    });

    test('many requests at once renew once', async () => {
        signedIn(idToken(-5), 'refresh');
        const tokens = await Promise.all([currentToken(), currentToken(), currentToken()]);
        expect(tokens).toEqual(['the-new-one', 'the-new-one', 'the-new-one']);
        expect(requests).toHaveLength(1);
    });
    test('a provider away for a moment leaves the refresh token for the next try', async () => {
        const token = idToken(-5);
        signedIn(token, 'refresh');
        globalThis.fetch = (async () => new Response('Service Unavailable', {status: 503})) as typeof fetch;

        expect(await currentToken()).toBe(token);
        expect(canRenew()).toBe(true);
        expect(sessionStorage.getItem(OIDC_SIGN_IN_KEY)).toContain('"refreshToken":"refresh"');
    });

    test('a refresh token the provider refuses for good is dropped', async () => {
        signedIn(idToken(-5), 'refresh');
        globalThis.fetch = (async () => new Response(JSON.stringify({error: 'invalid_grant'}), {status: 400})) as typeof fetch;

        await currentToken();
        expect(canRenew()).toBe(false);
    });

    test('a clock ahead of the provider\'s counts the token from when it arrived', async () => {
        // Ten minutes ahead: by this clock, the five-minute token expired before it arrived.
        const token = idToken(240, 600);
        signedIn(token, 'refresh');

        expect(await currentToken()).toBe(token);
        const stop = startRenewing();
        await new Promise(resolve => setTimeout(resolve, 200));
        stop();
        expect(requests).toHaveLength(0);
    });

    test('a token pasted in ends the OIDC sign-in, even one renewing', async () => {
        signedIn(idToken(-5), 'refresh');
        signInWithToken('cosky_id_secret');

        expect(canRenew()).toBe(false);
        expect(await currentToken()).toBe('cosky_id_secret');
        expect(requests).toHaveLength(0);
    });

    test('signing out sends the provider only an ID token', () => {
        const assigned: string[] = [];
        const global = globalThis as {window?: unknown};
        const previous = global.window;
        global.window = {location: {assign: (url: URL) => assigned.push(String(url))}};
        const endSession = (token: string) => {
            session.signIn(token);
            sessionStorage.setItem(OIDC_SIGN_IN_KEY, JSON.stringify({
                clientId: 'dashboard',
                tokenEndpoint: 'https://idp.example/token',
                endSessionEndpoint: 'https://idp.example/logout',
                redirectUri: 'https://cosky.example/',
            }));
            signOut();
        };

        try {
            endSession('cosky_id_secret');
            expect(assigned).toHaveLength(0);
            endSession(idToken(120));
            expect(assigned).toHaveLength(1);
            expect(assigned[0]).toContain('id_token_hint=header.');
        } finally {
            global.window = previous;
        }
    });
});

test('renewing goes by this computer\'s clock, whichever way the provider\'s is off', () => {
    const issuedAt = 1_000_000;
    // The token arrived when this clock said 1,000 s later than the provider's: behind it, that is.
    const times = localTimes(issuedAt, issuedAt + 300, (issuedAt - 1000) * SECOND);
    expect(times.renewAt).toBe((issuedAt - 1000 + 240) * SECOND);
    expect(times.expiresAt).toBe((issuedAt - 1000 + 300) * SECOND);
    expect(localTimes(undefined, issuedAt + 300, null).expiresAt).toBe((issuedAt + 300) * SECOND);
});
