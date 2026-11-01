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

import {OIDC_SIGN_IN_KEY, session} from './session';

const PENDING_KEY = 'cosky.oidc.pending';
const RANDOM_BYTES = 32;
const JWT_PARTS = 3;

interface TokenResponse {
    id_token?: string;
    refresh_token?: string;
}

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
    endSessionEndpoint?: string;
    redirectUri: string;
}

/**
 * What a finished sign-in needs later on. The refresh token is only there if the provider gave one, and is no more
 * exposed here than the ID token next to it: both end with the tab.
 */
interface SignIn {
    clientId: string;
    tokenEndpoint: string;
    endSessionEndpoint?: string;
    redirectUri: string;
    refreshToken?: string;
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
    const provider = await discovery.json() as {
        authorization_endpoint: string;
        token_endpoint: string;
        end_session_endpoint?: string;
    };
    const pending: PendingSignIn = {
        state: randomValue(),
        nonce: randomValue(),
        verifier: randomValue(),
        clientId: client.clientId,
        tokenEndpoint: provider.token_endpoint,
        endSessionEndpoint: provider.end_session_endpoint,
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
    const {id_token: idToken, refresh_token: refreshToken} = await response.json() as TokenResponse;
    if (!idToken) {
        throw new Error('The provider gave no ID token.');
    }
    if (claimsOf(idToken).nonce !== pending.nonce) {
        throw new Error('The ID token does not belong to this sign-in.');
    }
    const {clientId, tokenEndpoint, endSessionEndpoint, redirectUri} = pending;
    remember({clientId, tokenEndpoint, endSessionEndpoint, redirectUri, refreshToken});
    return idToken;
}

/**
 * Asks for a newer ID token. A provider may not give refresh tokens, or stop honoring one, and then the token in use
 * lasts until it expires.
 *
 * @return the ID token, or null when there is none to be had.
 */
export async function renewIdToken(): Promise<string | null> {
    const signIn = signInOfThisTab();
    if (!signIn?.refreshToken) {
        return null;
    }
    const response = await fetch(signIn.tokenEndpoint, {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: new URLSearchParams({
            grant_type: 'refresh_token',
            refresh_token: signIn.refreshToken,
            client_id: signIn.clientId,
        }),
    });
    const tokens = response.ok ? await response.json() as TokenResponse : null;
    // Whoever it was may have signed out while this was on its way, and then nothing of it should come back.
    if (!signInOfThisTab()) {
        return null;
    }
    remember({...signIn, refreshToken: tokens ? tokens.refresh_token ?? signIn.refreshToken : undefined});
    return tokens?.id_token ?? null;
}

/**
 * Whether [renewIdToken] has anything to go on.
 */
export function canRenew(): boolean {
    return !!signInOfThisTab()?.refreshToken;
}

/**
 * Signs out here and at the provider. Otherwise the next sign-in would pass without a password, which on a shared
 * computer means passing as someone else.
 */
export function signOut(): void {
    const token = session.token();
    const signIn = signInOfThisTab();
    session.signOut();
    if (token && signIn?.endSessionEndpoint) {
        const url = new URL(signIn.endSessionEndpoint);
        url.search = new URLSearchParams({
            id_token_hint: token,
            client_id: signIn.clientId,
            post_logout_redirect_uri: signIn.redirectUri,
        }).toString();
        window.location.assign(url);
    }
}

/**
 * The claims of [token] if it is an ID token rather than a ServiceAccount token.
 */
export function idTokenClaims(token: string | null): Record<string, unknown> | null {
    return token?.split('.').length === JWT_PARTS ? claimsOf(token) : null;
}

/**
 * What to call the person: `sub` is what the server goes by, and on many providers that is an opaque id.
 */
export function displayNameOf(token: string | null): string | null {
    const claims = idTokenClaims(token);
    const name = [claims?.preferred_username, claims?.name, claims?.email].find(value => typeof value === 'string');
    return (name as string | undefined) ?? null;
}

function signInOfThisTab(): SignIn | null {
    const stored = sessionStorage.getItem(OIDC_SIGN_IN_KEY);
    return stored ? JSON.parse(stored) as SignIn : null;
}

function remember(signIn: SignIn): void {
    sessionStorage.setItem(OIDC_SIGN_IN_KEY, JSON.stringify(signIn));
}
