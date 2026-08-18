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

import type {Page, Route} from '@playwright/test';

const now = Date.UTC(2026, 7, 18, 1, 0, 0);
const tokenNow = Math.floor(Date.now() / 1000);
const encode = (value: unknown) => Buffer.from(JSON.stringify(value)).toString('base64url');
const token = `${encode({alg: 'none', typ: 'JWT'})}.${encode({
    sub: 'admin',
    iat: tokenNow,
    exp: tokenNow + 86_400,
    roles: ['admin'],
})}.`;

const logs = [
    {operator: 'admin', ip: '127.0.0.1', resource: 'user-service', action: 'scaled up', status: 200, msg: 'Deployment updated', opTime: now - 120_000},
    {operator: 'admin', ip: '127.0.0.1', resource: 'order-service', action: 'config updated', status: 200, msg: 'Configuration published', opTime: now - 360_000},
    {operator: 'system', ip: '127.0.0.1', resource: 'payment-service', action: 'health check', status: 200, msg: 'Health check passed', opTime: now - 480_000},
    {operator: 'admin', ip: '127.0.0.1', resource: 'rate-limiter', action: 'published', status: 200, msg: 'New configuration', opTime: now - 900_000},
    {operator: 'admin', ip: '127.0.0.1', resource: 'staging', action: 'created', status: 200, msg: 'Namespace created', opTime: now - 3_600_000},
    {operator: 'system', ip: '127.0.0.1', resource: 'inventory-service', action: 'instance failed', status: 500, msg: 'Probe failed', opTime: now - 10_800_000},
    ...Array.from({length: 8}, (_, index) => ({
        operator: index % 2 === 0 ? 'admin' : 'system',
        ip: '127.0.0.1',
        resource: `service-${index + 1}`,
        action: 'updated',
        status: 200,
        msg: 'Resource updated',
        opTime: now - (index + 4) * 3_600_000,
    })),
];

export interface MockApi {
    delayNext?: {method: string; path: RegExp; milliseconds: number};
    emptyCollections: Set<'audit' | 'configs' | 'namespaces' | 'roles' | 'services' | 'topology' | 'users'>;
    failNext?: {method: string; path: RegExp};
    manyServices?: boolean;
    requests: Array<{method: string; path: string; postData: string | null}>;
}

const instances = (serviceId: string) => [{
    schema: 'http',
    host: `${serviceId}.default.svc.cluster.local`,
    port: 8080,
    weight: 100,
    ttlAt: Math.floor(Date.now() / 1000) + 3600,
    metadata: {zone: 'cn-east-1'},
    isEphemeral: true,
    isExpired: false,
    instanceId: `${serviceId}-01`,
    serviceId,
    uri: `http://${serviceId}.default.svc.cluster.local:8080`,
    secure: false,
}];

async function json(route: Route, body: unknown, status = 200) {
    await route.fulfill({status, contentType: 'application/json', body: JSON.stringify(body)});
}

export async function installApiMock(page: Page) {
    const mockApi: MockApi = {
        emptyCollections: new Set(),
        requests: [],
    };
    await page.route('**/v1/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        const {pathname} = url;
        const method = request.method();
        mockApi.requests.push({method, path: pathname, postData: request.postData()});

        if (mockApi.delayNext?.method === method && mockApi.delayNext.path.test(pathname)) {
            const {milliseconds} = mockApi.delayNext;
            mockApi.delayNext = undefined;
            await new Promise(resolve => setTimeout(resolve, milliseconds));
        }

        if (mockApi.failNext?.method === method && mockApi.failNext.path.test(pathname)) {
            mockApi.failNext = undefined;
            await json(route, {msg: 'Expected test failure'}, 500);
            return;
        }

        if (pathname.includes('/authenticate/denied/login')) {
            await json(route, {msg: 'Invalid credentials'}, 401);
            return;
        }
        if (pathname.includes('/authenticate/') && (pathname.endsWith('/login') || pathname.endsWith('/refresh'))) {
            await json(route, {accessToken: token, refreshToken: token});
            return;
        }
        if (pathname === '/v1/namespaces') {
            await json(route, mockApi.emptyCollections.has('namespaces') ? [] : ['cosky-{system}', 'default', 'production', 'staging']);
            return;
        }
        if (pathname.endsWith('/stat/topology')) {
            await json(route, mockApi.emptyCollections.has('topology') ? {} : {
                'api-gateway': ['user-service', 'order-service', 'payment-service'],
                'user-service': ['user-db'],
                'order-service': ['order-db', 'redis-cache'],
                'payment-service': ['payment-gateway'],
            });
            return;
        }
        if (pathname.endsWith('/stat')) {
            await json(route, {namespaces: 12, configs: 342, services: {total: 134, health: 128}, instances: 512});
            return;
        }
        if (pathname === '/v1/audit-log/export') {
            await route.fulfill({
                status: 200,
                contentType: 'text/csv',
                headers: {'Content-Disposition': 'attachment; filename="cosky_audit_log.csv"'},
                body: 'Timestamp,Operator,Client IP,Resource,Action,Status,Message\n',
            });
            return;
        }
        if (pathname === '/v1/audit-log') {
            const search = url.searchParams.get('query')?.trim().toLowerCase();
            const from = Number(url.searchParams.get('from') ?? Number.NEGATIVE_INFINITY);
            const to = Number(url.searchParams.get('to') ?? Number.POSITIVE_INFINITY);
            const successful = url.searchParams.get('successful');
            const auditLogs = (mockApi.emptyCollections.has('audit') ? [] : logs).filter(log => {
                const matchesQuery = !search || `${log.operator} ${log.ip} ${log.resource} ${log.action} ${log.status} ${log.msg}`.toLowerCase().includes(search);
                const matchesStatus = successful === null || (log.status < 400) === (successful === 'true');
                return matchesQuery && matchesStatus && log.opTime >= from && log.opTime <= to;
            });
            const offset = Number(url.searchParams.get('offset') ?? 0);
            const limit = Number(url.searchParams.get('limit') ?? 10);
            await json(route, {list: auditLogs.slice(offset, offset + limit), total: auditLogs.length});
            return;
        }
        if (pathname === '/v1/roles') {
            await json(route, mockApi.emptyCollections.has('roles') ? [] : [
                {name: 'admin', desc: 'Full platform access'},
                {name: 'developer', desc: 'Read and update services'},
                {name: 'auditor', desc: 'Read-only audit access'},
            ]);
            return;
        }
        if (/^\/v1\/roles\/[^/]+\/bind$/.test(pathname)) {
            await json(route, [{namespace: 'default', action: 'rw'}]);
            return;
        }
        if (pathname === '/v1/users') {
            await json(route, mockApi.emptyCollections.has('users') ? [] : [
                {name: 'admin', id: '1', attributes: {}, authenticated: true, anonymous: false, policies: [], roles: ['admin']},
                {name: 'operator', id: '2', attributes: {}, authenticated: true, anonymous: false, policies: [], roles: ['developer']},
            ]);
            return;
        }
        if (pathname === '/v1/users/existing-user' && method === 'POST') {
            await json(route, false);
            return;
        }
        if (pathname.endsWith('/services/stats')) {
            const services = [
                {serviceId: 'api-gateway', instanceCount: 3},
                {serviceId: 'user-service', instanceCount: 4},
                {serviceId: 'order-service', instanceCount: 6},
                {serviceId: 'payment-service', instanceCount: 3},
            ];
            if (mockApi.manyServices && !pathname.includes('/namespaces/default/')) {
                services.push(...Array.from({length: 10}, (_, index) => ({
                    serviceId: `service-${index + 1}`,
                    instanceCount: 0,
                })));
            }
            await json(route, mockApi.emptyCollections.has('services') ? [] : services);
            return;
        }
        if (/\/services\/[^/]+\/instances$/.test(pathname) && method === 'GET') {
            await json(route, instances(decodeURIComponent(pathname.split('/').at(-2)!)));
            return;
        }
        if (pathname.endsWith('/configs') && method === 'GET') {
            await json(route, mockApi.emptyCollections.has('configs') ? [] : ['application.yaml', 'feature-flags.json', 'rate-limiter.yaml', 'logging.properties']);
            return;
        }
        if (pathname.endsWith('/configs/export')) {
            await route.fulfill({
                status: 200,
                contentType: 'application/zip',
                headers: {'Content-Disposition': 'attachment; filename="cosky-export.zip"'},
                body: 'mock-zip',
            });
            return;
        }
        if (/\/configs\/[^/]+\/versions\/\d+$/.test(pathname)) {
            const configId = decodeURIComponent(pathname.split('/').at(-3)!);
            await json(route, {
                configId,
                hash: '8f4a21c',
                version: Number(pathname.split('/').at(-1)),
                data: 'server:\n  port: 8080\nfeature:\n  enabled: true',
                createTime: Math.floor(now / 1000) - 7200,
                opTime: Math.floor(now / 1000) - 3600,
                op: 'UPDATE',
            });
            return;
        }
        if (/\/configs\/[^/]+\/versions$/.test(pathname)) {
            await json(route, [
                {version: 3, hash: '8f4a21c', createTime: Math.floor(now / 1000) - 3600},
                {version: 2, hash: '7d2a11b', createTime: Math.floor(now / 1000) - 7200},
                {version: 1, hash: '6c1f09a', createTime: Math.floor(now / 1000) - 10_800},
            ]);
            return;
        }
        if (/\/configs\/[^/]+$/.test(pathname) && method === 'GET') {
            const configId = decodeURIComponent(pathname.split('/').at(-1)!);
            await json(route, {
                configId,
                hash: '8f4a21c',
                version: 3,
                data: 'server:\n  port: 8080\nfeature:\n  enabled: true',
                createTime: Math.floor(now / 1000) - 3600,
            });
            return;
        }
        if (pathname.endsWith('/configs') && method === 'POST') {
            await json(route, {total: 4, succeeded: 4});
            return;
        }
        await json(route, true);
    });
    return mockApi;
}

export async function login(page: Page) {
    await page.goto('/login');
    await page.getByRole('textbox', {name: 'Username', exact: true}).fill('admin');
    await page.getByLabel('Password', {exact: true}).fill('password');
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();
    await page.waitForURL('**/home');
}
