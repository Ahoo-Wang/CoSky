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

import {createHash} from 'node:crypto';
import type {Page, Route} from '@playwright/test';

export const TOKEN = 'cosky_mocktoken_mocksecret';
export const ISSUER = 'https://idp.example';

// eslint-disable-next-line @typescript-eslint/no-explicit-any -- whatever a test stores and reads back
type Json = Record<string, any>;

interface Stored extends Json {
    meta: Json;
}

export interface RecordedRequest {
    method: string;
    path: string;
    headers: Record<string, string>;
    body: string | null;
    bytes: Buffer | null;
}

export interface MockApi {
    /** Without auth, everyone is let in. */
    auth: boolean;
    oidc: boolean;
    empty: boolean;
    /** Such requests fail until this is cleared. */
    failing?: {method: string; path: RegExp; status: number; message: string};
    /** A provider that answers wrongly, to see the page refuse it. */
    providerAnswer?: 'wrong-state' | 'wrong-nonce' | 'denied';
    /** How long an ID token lasts. */
    idTokenSeconds: number;
    /** What the provider does about refresh tokens: refusing them comes after giving one. */
    refreshTokens: 'given' | 'not-given' | 'refused';
    /** An account nobody has given a role yet. */
    noRules: boolean;
    requests: RecordedRequest[];
    /** Such requests take this long. */
    slow?: {path: RegExp; milliseconds: number};
    /** Lets every open Watch stream say this, and end. */
    releaseWatch: (message?: Json) => void;
    put: (path: string, resource: Stored) => void;
    remove: (path: string) => void;
}

const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');

const updated = {updatedAt: '2026-08-18T01:00:00Z', updatedBy: 'serviceaccount:admin'};

async function json(route: Route, body: unknown, status = 200) {
    await route.fulfill({status, contentType: 'application/json', body: JSON.stringify(body)});
}

/**
 * One object per page, so that everything that lists has to read every page, as a server's byte limit can demand.
 */
function pageOf(items: Stored[], field: string, url: URL): Json {
    const offset = Number(url.searchParams.get('pageToken') ?? 0);
    const next = offset + 1;
    return {
        ...(items.length ? {[field]: items.slice(offset, next)} : {}),
        ...(next < items.length ? {nextPageToken: String(next)} : {}),
    };
}

const failure = (route: Route, status: number, message: string) =>
    json(route, {status: 'FAILED', message, detail: {}}, status);

function challengeOf(verifier: string): string {
    return createHash('sha256').update(verifier).digest('base64url');
}

/**
 * The 8.0 REST API in memory, answering the way the server does: proto3 JSON leaves empty fields out, a list comes in
 * pages, and writes honour If-Match and If-None-Match.
 */
export async function installApiMock(page: Page, options: Partial<Pick<MockApi, 'auth' | 'oidc' | 'empty'>> = {}) {
    let revision = 100;
    const nextRevision = () => String(++revision);
    const objects = new Map<string, Stored>();
    const history = new Map<string, Stored[]>();
    const issued = new Set<string>([TOKEN]);
    const pendingWatches: Array<(message: Json) => void> = [];

    const store = (path: string, resource: Stored) => {
        const name = path.replace(/^.*\/(?:configs|instances|namespaces|serviceaccounts|roles|rolebindings)\//, '');
        const stored = {...resource, meta: {...updated, ...resource.meta, name, revision: nextRevision()}};
        objects.set(path, stored);
        if (path.includes('/configs/')) {
            history.set(path, [stored, ...(history.get(path) ?? [])]);
        }
        return stored;
    };

    const mockApi: MockApi = {
        auth: options.auth ?? true,
        oidc: options.oidc ?? false,
        idTokenSeconds: 300,
        refreshTokens: 'given',
        noRules: false,
        empty: options.empty ?? false,
        requests: [],
        releaseWatch: (message = {events: {revision: nextRevision(), events: []}}) =>
            pendingWatches.splice(0).forEach(release => release(message)),
        put: store,
        remove: path => objects.delete(path),
    };

    if (!mockApi.empty) {
        store('/v1/namespaces/default', {meta: {}, namespace: {}});
        store('/v1/namespaces/prod', {meta: {}, namespace: {description: 'Production'}});
        store('/v1/namespaces/default/configs/application.yaml', {
            meta: {},
            config: {format: 'CONFIG_FORMAT_YAML', content: 'server:\n  port: 8080\n', sha256: 'aaaa1111bbbb2222'},
        });
        store('/v1/namespaces/default/configs/application.yaml', {
            meta: {labels: {team: 'core'}},
            config: {format: 'CONFIG_FORMAT_YAML', content: 'server:\n  port: 9090\n', sha256: 'cccc3333dddd4444'},
        });
        store('/v1/namespaces/default/configs/shared/datasource.json', {
            meta: {},
            config: {format: 'CONFIG_FORMAT_JSON', content: '{"url":"jdbc"}', sha256: 'eeee5555ffff6666'},
        });
        store('/v1/namespaces/default/instances/order-1', {
            meta: {lease: '42', labels: {pool: 'a'}},
            instance: {
                service: 'order',
                endpoint: {scheme: 'http', host: '10.0.0.1', port: 8080},
                weight: 2,
                metadata: {zone: 'east'},
            },
        });
        store('/v1/namespaces/default/instances/database-1', {
            meta: {},
            instance: {service: 'database', endpoint: {scheme: 'tcp', host: '10.0.0.2', port: 3306}},
        });
        store('/v1/serviceaccounts/admin', {
            meta: {},
            serviceAccount: {description: 'Created at bootstrap', tokens: [{id: 'first', sha256: 'hash'}]},
        });
        store('/v1/roles/admin', {
            meta: {},
            role: {
                description: 'Everything',
                rules: [{kinds: ['KIND_CONFIG'], namespaces: ['*'], verbs: ['VERB_GET', 'VERB_PUT']}],
            },
        });
        store('/v1/rolebindings/admin', {
            meta: {},
            roleBinding: {
                role: 'admin',
                subjects: [{type: 'TYPE_SERVICE_ACCOUNT', name: 'admin'}],
                namespaces: ['*'],
            },
        });
    }

    const authorized = (authorization: string | undefined) =>
        !mockApi.auth || (authorization !== undefined && issued.has(authorization.replace('Bearer ', '')));

    const collectionOf = (path: string) => [...objects.entries()]
        .filter(([key]) => key.startsWith(`${path}/`) && (path === '/v1/namespaces' ? key.split('/').length === 4 : true))
        .map(([, value]) => value);

    const serviceOf = (resource: Stored) => resource.instance?.service as string;

    const servicesIn = (namespace: string) => {
        const instances = collectionOf(`/v1/namespaces/${namespace}/instances`);
        return [...new Set(instances.map(serviceOf))].sort().map(service => ({
            service,
            instanceCount: instances.filter(instance => serviceOf(instance) === service).length,
        }));
    };

    await page.route('**/v1/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        // Names arrive encoded, as one path segment.
        const path = decodeURIComponent(url.pathname);
        const method = request.method();
        const headers = await request.allHeaders();
        mockApi.requests.push({method, path, headers, body: request.postData(), bytes: request.postDataBuffer()});

        if (path === '/v1/auth/config') {
            await json(route, mockApi.oidc ? {oidc: {issuer: ISSUER, clientId: 'dashboard', scopes: ['openid', 'groups']}} : {});
            return;
        }
        if (!authorized(headers.authorization)) {
            await failure(route, 401, 'A valid token is required.');
            return;
        }
        if (mockApi.failing?.method === method && mockApi.failing.path.test(path)) {
            await failure(route, mockApi.failing.status, mockApi.failing.message);
            return;
        }
        if (mockApi.slow?.path.test(path)) {
            await new Promise(resolve => setTimeout(resolve, mockApi.slow?.milliseconds));
        }
        if (path === '/v1/me') {
            await json(route, mockApi.auth
                ? mockApi.noRules
                    ? {subject: {type: 'TYPE_USER', name: 'ada-id'}, groups: ['devs'], rules: []}
                    : {
                        subject: {type: 'TYPE_SERVICE_ACCOUNT', name: 'admin'},
                        rules: [{kinds: ['KIND_NAMESPACE'], namespaces: ['*'], verbs: ['VERB_GET']}],
                    }
                : {subject: {name: 'anonymous'}});
            return;
        }

        const watch = path.match(/^\/v1\/namespaces\/([^/]+)\/watch$/);
        if (watch) {
            // Holds until released, like a stream with nothing to say.
            const message = await new Promise<Json>(resolve => pendingWatches.push(resolve));
            await route.fulfill({
                status: 200,
                contentType: 'text/event-stream',
                body: `data: ${JSON.stringify({watchId: '0', ...message})}\n\n`,
            }).catch(() => undefined);
            return;
        }
        const view = path.match(/^\/v1\/namespaces\/([^/]+)\/views\/(services|topology|stats)$/);
        if (view) {
            const [, namespace, name] = view;
            if (name === 'services') {
                const services = servicesIn(namespace);
                await json(route, services.length ? {services} : {});
            } else if (name === 'topology') {
                await json(route, mockApi.empty ? {} : {
                    edges: [
                        {consumer: 'gateway', producer: 'order'},
                        {consumer: 'gateway', producer: 'user'},
                        {consumer: 'order', producer: 'database'},
                    ],
                });
            } else {
                const instances = collectionOf(`/v1/namespaces/${namespace}/instances`);
                await json(route, {
                    serviceCount: servicesIn(namespace).length,
                    instanceCount: instances.length,
                    configCount: collectionOf(`/v1/namespaces/${namespace}/configs`).length,
                });
            }
            return;
        }
        const historyPath = path.match(/^(.*\/configs\/.+)\/history$/);
        if (historyPath) {
            await json(route, pageOf(history.get(historyPath[1]) ?? [], 'revisions', url));
            return;
        }
        const rollback = path.match(/^(.*\/configs\/.+):rollback$/);
        if (rollback && method === 'POST') {
            const {toRevision} = request.postDataJSON() as {toRevision: string};
            const version = history.get(rollback[1])?.find(item => item.meta.revision === toRevision);
            const stored = store(rollback[1], {meta: {}, config: version?.config});
            await json(route, {revision: stored.meta.revision, changed: true});
            return;
        }
        const archive = path.match(/^\/v1\/namespaces\/([^/]+)\/configs:(import|export)$/);
        if (archive) {
            if (archive[2] === 'import') {
                await json(route, {items: [{name: 'imported.yaml', revision: nextRevision(), changed: true}]});
            } else {
                await route.fulfill({status: 200, contentType: 'application/zip', body: Buffer.from('PK\u0005\u0006')});
            }
            return;
        }
        const tokens = path.match(/^(\/v1\/serviceaccounts\/[^/]+)\/tokens(?:\/(.+))?$/);
        if (tokens) {
            const account = objects.get(tokens[1]) as Stored;
            if (method === 'POST') {
                const id = `token${objects.size}${account.serviceAccount.tokens?.length ?? 0}`;
                const plaintext = `cosky_${id}_plaintextsecret`;
                account.serviceAccount.tokens = [...(account.serviceAccount.tokens ?? []), {id, sha256: 'hash'}];
                await json(route, {tokenId: id, token: plaintext});
            } else {
                account.serviceAccount.tokens = account.serviceAccount.tokens.filter((token: Json) => token.id !== tokens[2]);
                await json(route, {revision: nextRevision()});
            }
            return;
        }

        const existing = objects.get(path);
        if (method === 'GET') {
            const isList = /\/(configs|instances)$/.test(path) || /^\/v1\/(namespaces|serviceaccounts|roles|rolebindings)$/.test(path);
            if (isList) {
                const service = url.searchParams.get('service');
                const items = collectionOf(path).filter(item => !service || serviceOf(item) === service);
                await json(route, {...pageOf(items, 'items', url), revision: String(revision), epoch: 'e'});
            } else if (existing) {
                await json(route, existing);
            } else {
                await failure(route, 404, `${path} was not found.`);
            }
            return;
        }
        if (method === 'PUT') {
            const ifMatch = headers['if-match']?.replaceAll('"', '');
            if (ifMatch && existing?.meta.revision !== ifMatch) {
                await failure(route, 409, 'It changed since it was read.');
                return;
            }
            if (headers['if-none-match'] === '*' && existing) {
                await failure(route, 409, 'It exists already.');
                return;
            }
            const stored = store(path, request.postDataJSON() as Stored);
            await json(route, {revision: stored.meta.revision, changed: true});
            return;
        }
        if (method === 'DELETE') {
            objects.delete(path);
            await json(route, {revision: nextRevision()});
            return;
        }
        await failure(route, 404, `No mock for ${method} ${path}.`);
    });

    // An identity provider, checking PKCE as a real one does.
    let challenge = '';
    let nonce = '';
    let renewals = 0;
    await page.route(`${ISSUER}/**`, async route => {
        const url = new URL(route.request().url());
        if (url.pathname === '/.well-known/openid-configuration') {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                headers: {'Access-Control-Allow-Origin': '*'},
                body: JSON.stringify({
                    authorization_endpoint: `${ISSUER}/authorize`,
                    token_endpoint: `${ISSUER}/token`,
                    end_session_endpoint: `${ISSUER}/logout`,
                }),
            });
            return;
        }
        if (url.pathname === '/authorize') {
            challenge = url.searchParams.get('code_challenge') ?? '';
            nonce = url.searchParams.get('nonce') ?? '';
            const back = new URL(url.searchParams.get('redirect_uri') as string);
            const state = url.searchParams.get('state') as string;
            if (mockApi.providerAnswer === 'denied') {
                back.search = new URLSearchParams({state, error: 'access_denied'}).toString();
            } else {
                back.search = new URLSearchParams({
                    code: 'the-code',
                    state: mockApi.providerAnswer === 'wrong-state' ? 'someone-elses' : state,
                }).toString();
            }
            await route.fulfill({status: 302, headers: {Location: back.toString()}});
            return;
        }
        if (url.pathname === '/logout') {
            const back = url.searchParams.get('post_logout_redirect_uri') as string;
            await route.fulfill({status: 302, headers: {Location: back}});
            return;
        }
        const form = new URLSearchParams(route.request().postData() ?? '');
        const renewal = form.get('grant_type') === 'refresh_token';
        const verified = renewal
            ? form.get('refresh_token') === `refresh-${renewals}` && mockApi.refreshTokens === 'given'
            : form.get('code') === 'the-code'
                && form.get('grant_type') === 'authorization_code'
                && challengeOf(form.get('code_verifier') ?? '') === challenge;
        if (renewal && verified) {
            renewals++;
        }
        const now = Math.floor(Date.now() / 1000);
        const idToken = [
            encode({alg: 'none'}),
            encode({
                sub: 'ada-id',
                preferred_username: 'ada',
                iat: now,
                exp: now + mockApi.idTokenSeconds,
                renewal: renewals,
                nonce: mockApi.providerAnswer === 'wrong-nonce' ? 'someone-elses' : nonce,
            }),
            '',
        ].join('.');
        issued.add(idToken);
        await route.fulfill({
            status: verified ? 200 : 400,
            contentType: 'application/json',
            headers: {'Access-Control-Allow-Origin': '*'},
            body: JSON.stringify(verified
                ? {id_token: idToken, ...(mockApi.refreshTokens === 'not-given' ? {} : {refresh_token: `refresh-${renewals}`})}
                : {error: 'invalid_grant'}),
        });
    });

    return mockApi;
}

export async function login(page: Page) {
    await page.goto('/#/login');
    await page.getByLabel('Token', {exact: true}).fill(TOKEN);
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();
    await page.waitForURL('**/#/home');
}
