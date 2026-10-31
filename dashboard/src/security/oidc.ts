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

const PENDING_KEY = 'cosky.oidc.pending';
const RANDOM_BYTES = 32;

export interface OidcClient {
    issuer: string;
    clientId: string;
    scopes: string[];
}

/**
 * What a sign-in has to remember across the redirect to the provider.
 */
interface PendingSignIn {
    state: string;
    nonce: string;
    verifier: string;
    clientId: string;
    tokenEndpoint: string;
    redirectUri: string;
}

function base64Url(bytes: Uint8Array): string {
    return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomValue(): string {
    return base64Url(crypto.getRandomValues(new Uint8Array(RANDOM_BYTES)));
}

/**
 * The S256 code challenge of RFC 7636.
 */
export async function challengeOf(verifier: string): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
    return base64Url(new Uint8Array(digest));
}

/**
 * The claims of an ID token, unverified: the server verifies its signature, and this only reads what the page needs.
 */
export function claimsOf(idToken: string): Record<string, unknown> {
    const payload = idToken.split('.')[1] ?? '';
    const bytes = Uint8Array.from(atob(payload.replace(/-/g, '+').replace(/_/g, '/')), char => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
}

/**
 * Sends the browser to the provider (authorization code with PKCE). The provider sends it back to this page.
 */
export async function beginSignIn(client: OidcClient): Promise<void> {
    const discovery = await fetch(`${client.issuer.replace(/\/$/, '')}/.well-known/openid-configuration`);
    if (!discovery.ok) {
        throw new Error(`The provider answered ${discovery.status}.`);
    }
    const provider = await discovery.json() as {authorization_endpoint: string; token_endpoint: string};
    const pending: PendingSignIn = {
        state: randomValue(),
        nonce: randomValue(),
        verifier: randomValue(),
        clientId: client.clientId,
        tokenEndpoint: provider.token_endpoint,
        redirectUri: window.location.origin + window.location.pathname,
    };
    const url = new URL(provider.authorization_endpoint);
    url.search = new URLSearchParams({
        response_type: 'code',
        client_id: client.clientId,
        redirect_uri: pending.redirectUri,
        scope: client.scopes.join(' '),
        state: pending.state,
        nonce: pending.nonce,
        code_challenge: await challengeOf(pending.verifier),
        code_challenge_method: 'S256',
    }).toString();
    sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending));
    window.location.assign(url);
}

/**
 * Finishes the sign-in the provider sent the browser back from, once: the code and the pending state are gone from
 * the moment this starts.
 *
 * @return the ID token, or null when no sign-in is pending.
 */
export async function completeSignIn(): Promise<string | null> {
    const parameters = new URLSearchParams(window.location.search);
    const stored = sessionStorage.getItem(PENDING_KEY);
    if (!stored || !parameters.has('state')) {
        return null;
    }
    sessionStorage.removeItem(PENDING_KEY);
    window.history.replaceState(null, '', window.location.pathname + window.location.hash);
    const pending = JSON.parse(stored) as PendingSignIn;
    if (parameters.get('state') !== pending.state) {
        throw new Error('The answer of the provider does not belong to this sign-in.');
    }
    const code = parameters.get('code');
    if (!code) {
        throw new Error(parameters.get('error_description') ?? parameters.get('error') ?? 'The provider gave no code.');
    }
    const response = await fetch(pending.tokenEndpoint, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            grant_type: 'authorization_code',
            code,
            client_id: pending.clientId,
            redirect_uri: pending.redirectUri,
            code_verifier: pending.verifier,
        }),
    });
    if (!response.ok) {
        throw new Error(`The provider answered ${response.status}.`);
    }
    const {id_token: idToken} = await response.json() as {id_token?: string};
    if (!idToken) {
        throw new Error('The provider gave no ID token.');
    }
    if (claimsOf(idToken).nonce !== pending.nonce) {
        throw new Error('The ID token does not belong to this sign-in.');
    }
    return idToken;
}
