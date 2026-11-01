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

import type {Locator, Page} from '@playwright/test';
import {expect, test} from './coverage';
import {installApiMock, login} from './mock-api';
import type {MockApi} from './mock-api';

const browserErrors = new WeakMap<Page, string[]>();

// What a failed request prints is not a defect of the page. Nor is what Monaco 0.55 sometimes reports as a diff editor
// closes: the history test goes on to open another one, and to use it.
const EXPECTED_ERROR = /Failed to load resource|AbstractContextKeyService has been disposed/;

test.beforeEach(async ({page}) => {
    const errors: string[] = [];
    browserErrors.set(page, errors);
    page.on('console', message => {
        if (message.type() === 'error' && !EXPECTED_ERROR.test(message.text())) errors.push(message.text());
    });
    page.on('pageerror', error => {
        if (!EXPECTED_ERROR.test(error.message)) errors.push(error.message);
    });
});

test.afterEach(async ({page}) => {
    expect(browserErrors.get(page), 'browser console and page errors').toEqual([]);
});

const toast = (page: Page, text: string) => page.getByText(text, {exact: false}).first();

const row = (scope: Page | Locator, name: string) => scope.getByRole('row').filter({hasText: name}).first();

const lastRequest = (api: MockApi, method: string, path: string) =>
    [...api.requests].reverse().find(request => request.method === method && request.path === path);

const monacoLines = (page: Page) => page.locator('.monaco-editor:visible .view-lines').last();

async function typeInMonaco(page: Page, text: string) {
    await page.locator('.monaco-editor:visible').last().click({position: {x: 120, y: 40}});
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.press('Delete');
    await page.keyboard.insertText(text);
}

async function choose(page: Page, select: string, option: string) {
    await page.getByRole('combobox', {name: select}).click();
    await page.getByRole('option', {name: option, exact: true}).click();
}

async function confirm(page: Page, trigger: Locator) {
    await trigger.click();
    await page.getByRole('alertdialog').getByRole('button', {name: 'Continue'}).click();
}

test.describe('signing in', () => {
    test('a token signs in, a wrong one does not, and signing out ends it', async ({page}) => {
        const api = await installApiMock(page);
        await page.goto('/');
        await page.waitForURL('**/#/login');
        await expect(page.getByRole('button', {name: /Sign in with/})).toHaveCount(0);

        await page.getByLabel('Token', {exact: true}).fill('not-a-token');
        await page.getByRole('button', {name: 'Show token'}).click();
        await expect(page.getByLabel('Token', {exact: true})).toHaveAttribute('type', 'text');
        await page.getByRole('button', {name: 'Hide token'}).click();
        await page.getByRole('button', {name: 'Sign In', exact: true}).click();
        await expect(toast(page, 'Sign-in failed: A valid token is required.')).toBeVisible();
        await expect(page).toHaveURL(/#\/login$/);

        await login(page);
        expect(lastRequest(api, 'GET', '/v1/me')?.headers.authorization).toBe('Bearer cosky_mocktoken_mocksecret');
        await page.locator('[data-slot="account-menu-trigger"]').click();
        await page.getByRole('menuitem', {name: 'Sign out'}).click();
        await page.waitForURL('**/#/login');
    });

    test('a token the server stops accepting sends the page back to sign in', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        api.failing = {method: 'GET', path: /^\/v1\/namespaces\/default\/configs$/, status: 401, message: 'Expired.'};

        await page.getByRole('link', {name: 'Configuration'}).click();

        await page.waitForURL('**/#/login');
        await expect(toast(page, 'no longer valid')).toBeVisible();
    });

    test('a server that cannot be reached offers to try again, and keeps the token', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        api.failing = {method: 'GET', path: /^\/v1\/me$/, status: 503, message: 'Down.'};

        await page.reload();

        await expect(page.getByRole('alert')).toContainText('The server cannot be reached.');
        api.failing = undefined;
        await page.getByRole('button', {name: 'Retry'}).click();
        await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
    });

    test('a server without auth needs no sign-in, and offers no sign-out', async ({page}) => {
        await installApiMock(page, {auth: false});

        await page.goto('/');

        await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
        await page.locator('[data-slot="account-menu-trigger"]').click();
        await expect(page.getByRole('menu')).toContainText('Account');
        await expect(page.getByRole('menuitem', {name: 'Sign out'})).toHaveCount(0);
    });

    test('OIDC signs in with the authorization code and PKCE, and leaves no code in the address', async ({page}) => {
        const api = await installApiMock(page, {oidc: true});
        await page.goto('/#/login');

        const authorization = page.waitForRequest(request => request.url().includes('/authorize'));
        await page.getByRole('button', {name: 'Sign in with idp.example'}).click();
        const url = new URL((await authorization).url());

        expect(url.searchParams.get('response_type')).toBe('code');
        expect(url.searchParams.get('client_id')).toBe('dashboard');
        expect(url.searchParams.get('scope')).toBe('openid groups');
        expect(url.searchParams.get('code_challenge_method')).toBe('S256');
        await page.waitForURL('**/#/home');
        expect(page.url()).not.toContain('code=');
        await expect.poll(() => lastRequest(api, 'GET', '/v1/me')?.headers.authorization).toMatch(/^Bearer ey/);
    });

    const signInWithOidc = async (page: Page) => {
        await page.goto('/#/login');
        await page.getByRole('button', {name: 'Sign in with idp.example'}).click();
        await page.waitForURL('**/#/home');
    };

    test('an ID token about to expire is renewed, and the page someone is on stays as it is', async ({page}) => {
        const api = await installApiMock(page, {oidc: true});
        api.idTokenSeconds = 3;
        await signInWithOidc(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await page.getByRole('button', {name: 'Add', exact: true}).click();
        await page.getByLabel('Name', {exact: true}).fill('in-progress.yaml');
        const before = lastRequest(api, 'GET', '/v1/me')?.headers.authorization;
        const askedWhoAmI = api.requests.filter(request => request.path === '/v1/me').length;

        await expect.poll(
            () => page.evaluate(() => sessionStorage.getItem('cosky.token')),
            {timeout: 5_000},
        ).not.toBe(before?.replace('Bearer ', ''));

        await expect(page.getByLabel('Name', {exact: true})).toHaveValue('in-progress.yaml');
        expect(page.url()).toContain('/config');
        // It is still the same person, and a failure to ask again would take down the page.
        expect(api.requests.filter(request => request.path === '/v1/me')).toHaveLength(askedWhoAmI);
    });

    test('a provider that gives no refresh token leaves the token to expire', async ({page}) => {
        const api = await installApiMock(page, {oidc: true});
        api.refreshTokens = 'not-given';
        api.idTokenSeconds = 2;
        await signInWithOidc(page);
        const token = await page.evaluate(() => sessionStorage.getItem('cosky.token'));

        await page.waitForTimeout(2_500);

        expect(await page.evaluate(() => sessionStorage.getItem('cosky.token'))).toBe(token);
    });

    test('signing out ends the session at the provider too', async ({page}) => {
        await installApiMock(page, {oidc: true});
        await signInWithOidc(page);
        const token = await page.evaluate(() => sessionStorage.getItem('cosky.token'));

        const logout = page.waitForRequest(request => request.url().includes('/logout'));
        await page.locator('[data-slot="account-menu-trigger"]').click();
        await page.getByRole('menuitem', {name: 'Sign out'}).click();
        const url = new URL((await logout).url());

        expect(url.searchParams.get('id_token_hint')).toBe(token);
        expect(url.searchParams.get('client_id')).toBe('dashboard');
        await page.waitForURL('**/#/login');
        expect(await page.evaluate(() => Object.keys(sessionStorage))).not.toContain('cosky.oidc.signIn');
    });

    test('pasting something that only looks like an ID token is refused like any other', async ({page}) => {
        await installApiMock(page);
        await page.goto('/#/login');
        await page.getByLabel('Token', {exact: true}).fill('not.an.idtoken');
        await page.getByRole('button', {name: 'Sign In', exact: true}).click();

        await expect(toast(page, 'Sign-in failed')).toBeVisible();
        expect(await page.evaluate(() => sessionStorage.getItem('cosky.token'))).toBeNull();
        await expect(page.getByRole('button', {name: 'Sign In', exact: true})).toBeEnabled();
    });

    test('a person shows up by name rather than by id', async ({page}) => {
        await installApiMock(page, {oidc: true});
        await signInWithOidc(page);
        await expect(page.locator('[data-slot="account-menu-trigger"]')).toContainText('ada');
        await expect(page.locator('[data-slot="account-menu-trigger"]')).not.toContainText('admin');
    });

    test('an account with no role is told so, with what to bind', async ({page}) => {
        const api = await installApiMock(page, {oidc: true});
        api.noRules = true;
        await signInWithOidc(page);
        const notice = page.getByRole('alert').filter({hasText: 'no permissions'});
        await expect(notice).toContainText('ada-id');
        await expect(notice).toContainText('devs');
    });

    for (const [answer, message] of [
        ['wrong-state', 'does not belong to this sign-in'],
        ['wrong-nonce', 'does not belong to this sign-in'],
        ['denied', 'access_denied'],
    ] as const) {
        test(`OIDC refuses an answer that is ${answer}`, async ({page}) => {
            const api = await installApiMock(page, {oidc: true});
            api.providerAnswer = answer;
            await page.goto('/#/login');

            await page.getByRole('button', {name: 'Sign in with idp.example'}).click();

            await expect(toast(page, message)).toBeVisible();
            await expect(page).toHaveURL(/#\/login$/);
        });
    }
});

test.describe('the shell', () => {
    test('the overview shows counts and the topology, for the namespace chosen', async ({page}) => {
        await installApiMock(page);
        await login(page);

        await expect(page.getByRole('main')).toContainText('Counts and service relationships in default.');
        await expect(page.getByText('Services', {exact: true}).locator('..')).toContainText('2');
        await expect(page.getByText('Instances', {exact: true}).locator('..')).toContainText('2');
        await expect(page.getByText('Configurations', {exact: true}).locator('..')).toContainText('2');
        await expect(page.getByText('Namespaces', {exact: true}).locator('..')).toContainText('2');
        await expect(page.locator('.react-flow__node').filter({hasText: 'gateway'}).first()).toBeVisible();
        await page.locator('.react-flow__node').filter({hasText: 'gateway'}).first().click();

        await page.getByRole('button', {name: 'Open topology fullscreen'}).click();
        await expect(page.getByRole('dialog', {name: 'Service Topology'})).toBeVisible();
        await page.getByRole('button', {name: 'Close topology fullscreen'}).click();
        await expect(page.getByRole('dialog', {name: 'Service Topology'})).toHaveCount(0);

        await page.getByRole('combobox', {name: 'Select Namespace'}).click();
        await page.getByRole('option', {name: 'prod', exact: true}).click();
        await expect(page.getByRole('main')).toContainText('Counts and service relationships in prod.');
        await expect(page.getByText('Instances', {exact: true}).locator('..')).toContainText('0');
    });

    test('command search reaches every page, and its keys stay out of the way', async ({page}) => {
        await installApiMock(page);
        await login(page);
        await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();

        await page.keyboard.press('Control+K');
        const search = page.getByRole('combobox', {name: /search/i});
        await expect(search).toBeFocused();
        await search.fill('binding');
        await page.keyboard.press('Enter');
        await page.waitForURL('**/#/role-binding');

        await page.keyboard.press('/');
        await expect(search).toBeFocused();
        await search.fill('nothing like this');
        await page.keyboard.press('Escape');
        await expect(search).toHaveValue('');
    });

    test('the sidebar collapses, and opens its security group for a security page', async ({page}) => {
        await installApiMock(page);
        await login(page);

        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Service Account'}).click();
        await page.waitForURL('**/#/service-account');
        await expect(page.getByRole('link', {name: 'Role Binding'})).toBeVisible();

        await page.getByRole('button', {name: 'Collapse navigation'}).click();
        await expect(page.getByRole('button', {name: 'Expand navigation'})).toBeVisible();
        await page.getByRole('button', {name: 'Expand navigation'}).click();
    });

    test('a small screen navigates through a sheet', async ({page}) => {
        await installApiMock(page);
        await page.setViewportSize({width: 390, height: 844});
        await login(page);

        await page.getByRole('button', {name: 'Open navigation'}).click();
        await page.getByRole('dialog').getByRole('link', {name: 'Service', exact: true}).click();

        await page.waitForURL('**/#/service');
        await expect(page.getByRole('dialog')).toHaveCount(0);
    });
});

test.describe('configuration', () => {
    test('a new Config is created only if it does not exist, in the format its name suggests', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await expect(row(page, 'application.yaml')).toContainText('YAML');

        await page.getByRole('button', {name: 'Add', exact: true}).click();
        await page.getByLabel('Name', {exact: true}).fill('payments/app.properties');
        await expect(page.getByRole('combobox', {name: 'Format'})).toContainText('Properties');
        await typeInMonaco(page, 'a=1');
        await page.getByRole('button', {name: 'Submit'}).click();

        await expect(toast(page, 'Config saved')).toBeVisible();
        await expect(row(page, 'payments/app.properties')).toContainText('Properties');
        const put = lastRequest(api, 'PUT', '/v1/namespaces/default/configs/payments/app.properties');
        expect(put?.headers['if-none-match']).toBe('*');
        expect(JSON.parse(put?.body ?? '')).toMatchObject({config: {format: 'CONFIG_FORMAT_PROPERTIES', content: 'a=1'}});
    });

    test('an edit guards the revision it read, keeps the labels, and says when someone got there first', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();

        await row(page, 'application.yaml').getByRole('button', {name: 'Edit'}).click();
        await expect(monacoLines(page)).toContainText('9090');
        await expect(page.getByRole('dialog')).toContainText('serviceaccount:admin');
        await choose(page, 'Format', 'Text');
        await choose(page, 'Format', 'YAML');
        await typeInMonaco(page, 'server:\n  port: 1');
        api.put('/v1/namespaces/default/configs/application.yaml', {
            meta: {labels: {team: 'core'}},
            config: {format: 'CONFIG_FORMAT_YAML', content: 'someone else', sha256: 'x'},
        });

        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Failed to save config: It changed since it was read.')).toBeVisible();

        await page.getByRole('button', {name: 'Cancel'}).click();
        await row(page, 'application.yaml').getByRole('button', {name: 'Edit'}).click();
        await expect(monacoLines(page)).toContainText('someone');
        await typeInMonaco(page, 'mine');
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Config saved')).toBeVisible();
        const put = lastRequest(api, 'PUT', '/v1/namespaces/default/configs/application.yaml');
        expect(put?.headers['if-match']).toMatch(/^"\d+"$/);
        expect(JSON.parse(put?.body ?? '').meta.labels).toEqual({team: 'core'});
    });

    test('a Config that cannot be read offers to try again, and changes nothing', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        api.failing = {method: 'GET', path: /configs\/application\.yaml$/, status: 404, message: 'Gone.'};

        await row(page, 'application.yaml').getByRole('button', {name: 'Edit'}).click();

        await expect(page.getByRole('alert')).toContainText('Nothing has been changed.');
        api.failing = undefined;
        await page.getByRole('button', {name: 'Retry'}).click();
        await expect(monacoLines(page)).toContainText('9090');
    });

    test('a name holding a slash stays one path segment', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await confirm(page, row(page, 'shared/datasource.json').getByRole('button', {name: 'Delete'}));

        await expect(toast(page, 'Config deleted')).toBeVisible();
        await expect(page.getByRole('row').filter({hasText: 'shared/datasource.json'})).toHaveCount(0);
        expect(lastRequest(api, 'DELETE', '/v1/namespaces/default/configs/shared/datasource.json')).toBeTruthy();
    });

    test('the history compares with the current version, and rolls back to an old one', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();

        await row(page, 'application.yaml').getByRole('button', {name: /expand/i}).click();
        const version = (sha256: string) => page.getByRole('row').filter({hasText: sha256}).last();
        await expect(version('aaaa1111')).toBeVisible();

        await version('cccc3333').getByRole('button', {name: 'Diff'}).click();
        await expect(page.getByRole('button', {name: /Roll back to revision/})).toBeDisabled();
        await expect(page.locator('.monaco-diff-editor .view-lines').first()).toBeVisible();
        await page.keyboard.press('Escape');

        const oldVersion = await version('aaaa1111').innerText();
        await version('aaaa1111').getByRole('button', {name: 'Diff'}).click();
        const rollback = page.getByRole('button', {name: /Roll back to revision/});
        await expect(rollback).toBeEnabled();
        await confirm(page, rollback);

        await expect(toast(page, 'Rolled back')).toBeVisible();
        const post = lastRequest(api, 'POST', '/v1/namespaces/default/configs/application.yaml:rollback');
        expect(oldVersion).toContain(JSON.parse(post?.body ?? '').toRevision);
        // What it was compared with: a later change is not to be overwritten unseen.
        expect(post?.headers['if-match']).toMatch(/^"\d+"$/);
    });

    test('import sends the archive itself, and export gives one back', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();

        await page.getByRole('button', {name: 'Import'}).click();
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Please select a ZIP file.')).toBeVisible();
        await page.locator('input[type="file"]').setInputFiles({
            name: 'configs.zip',
            mimeType: 'application/zip',
            buffer: Buffer.from('PK-archive'),
        });
        await expect(page.getByRole('dialog')).toContainText('configs.zip');
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Configs imported')).toBeVisible();
        const post = lastRequest(api, 'POST', '/v1/namespaces/default/configs:import');
        expect(post?.headers['content-type']).toBe('application/zip');
        expect(post?.bytes?.toString()).toBe('PK-archive');

        const download = page.waitForEvent('download');
        await page.getByRole('button', {name: 'Export'}).click();
        expect((await download).suggestedFilename()).toMatch(/^cosky_default-.*\.zip$/);
    });

    test('the list follows changes without being asked, and keeps its place', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await expect(row(page, 'application.yaml')).toBeVisible();
        await expect.poll(() => lastRequest(api, 'GET', '/v1/namespaces/default/watch')?.headers.authorization)
            .toBe('Bearer cosky_mocktoken_mocksecret');

        api.put('/v1/namespaces/default/configs/from-elsewhere.txt', {
            meta: {},
            config: {format: 'CONFIG_FORMAT_TEXT', content: 'x', sha256: 'y'},
        });
        api.releaseWatch();

        await expect(row(page, 'from-elsewhere.txt')).toBeVisible();
        await expect(row(page, 'application.yaml')).toBeVisible();
    });

    test('a snapshot covers whatever was missed while no stream was open', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await expect(row(page, 'application.yaml')).toBeVisible();
        await expect.poll(() => lastRequest(api, 'GET', '/v1/namespaces/default/watch')).toBeTruthy();

        api.put('/v1/namespaces/default/configs/missed.txt', {
            meta: {},
            config: {format: 'CONFIG_FORMAT_TEXT', content: 'x', sha256: 'y'},
        });
        api.releaseWatch({snapshot: {epoch: 'e'}});

        await expect(row(page, 'missed.txt')).toBeVisible();
    });

    test('a stream the server canceled stays closed', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        const watches = () => api.requests.filter(request => request.path === '/v1/namespaces/default/watch').length;
        await expect.poll(watches).toBeGreaterThan(0);
        await expect(row(page, 'application.yaml')).toBeVisible();
        const opened = watches();

        api.releaseWatch({canceled: {reason: 'ERROR_REASON_PERMISSION_DENIED'}});
        await page.waitForTimeout(2_000);

        expect(watches()).toBe(opened);
    });

    test('reading again keeps what is on screen until the answer arrives', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await expect(row(page, 'application.yaml')).toBeVisible();
        await expect.poll(() => lastRequest(api, 'GET', '/v1/namespaces/default/watch')).toBeTruthy();
        const lists = () => api.requests.filter(request => request.path === '/v1/namespaces/default/configs').length;
        const before = lists();
        api.slow = {path: /^\/v1\/namespaces\/default\/configs$/, milliseconds: 1_500};

        api.releaseWatch();
        await expect.poll(lists).toBeGreaterThan(before);

        await expect(row(page, 'application.yaml')).toBeVisible({timeout: 300});
    });

    test('another namespace starts empty, so that nothing is acted on in the wrong one', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        await expect(row(page, 'application.yaml')).toBeVisible();

        // Longer than the assertion below waits, so that it can only be about the time before the answer.
        api.slow = {path: /^\/v1\/namespaces\/prod\/configs$/, milliseconds: 20_000};
        await page.getByRole('combobox', {name: 'Select Namespace'}).click();
        await page.getByRole('option', {name: 'prod', exact: true}).click();

        await expect(page.getByRole('main')).toContainText('prod');
        await expect(page.getByRole('row').filter({hasText: 'application.yaml'})).toHaveCount(0);
    });

    test('a subject bound to some namespaces can choose them, though it may not list them', async ({page}) => {
        const api = await installApiMock(page);
        await page.route('**/v1/me', route => route.fulfill({
            json: {
                subject: {type: 'TYPE_SERVICE_ACCOUNT', name: 'order'},
                rules: [
                    {kinds: ['KIND_CONFIG'], verbs: ['VERB_GET'], namespaces: ['prod', 'staging']},
                    {kinds: ['KIND_INSTANCE'], verbs: ['VERB_GET'], namespaces: ['*', 'prod']},
                ],
            },
        }));
        api.failing = {method: 'GET', path: /^\/v1\/namespaces$/, status: 403, message: 'Not for this subject.'};
        await login(page);

        await page.getByRole('combobox', {name: 'Select Namespace'}).click();
        await expect(page.getByRole('option', {name: 'prod', exact: true})).toBeVisible();
        await expect(page.getByRole('option', {name: 'staging', exact: true})).toBeVisible();
        await expect(page.getByRole('option', {name: '*', exact: true})).toHaveCount(0);
    });

    test('a stream that never answers is opened again', async ({page}) => {
        await installApiMock(page);
        await page.addInitScript(() => {
            const original = window.fetch;
            const counted = window as unknown as {watches: number};
            counted.watches = 0;
            window.fetch = (input, init) => {
                if (!String(input).includes('/watch')) return original(input, init);
                // What fetch does with a signal already aborted: no request goes out.
                if (init?.signal?.aborted) return Promise.reject(new DOMException('Aborted', 'AbortError'));
                counted.watches++;
                return new Promise(() => {});
            };
        });
        await page.clock.install();
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        const watches = () => page.evaluate(() => (window as unknown as {watches: number}).watches);
        await expect(row(page, 'application.yaml')).toBeVisible();
        const opened = await watches();
        expect(opened).toBeGreaterThan(0);

        // The fetcher gives up after 2 minutes, and the page tries again a second later.
        await page.clock.runFor(120_000);
        await page.clock.runFor(2_000);
        await page.clock.runFor(2_000);

        await expect.poll(watches).toBeGreaterThan(opened);
    });

    test('a stream that goes silent is opened again', async ({page}) => {
        await installApiMock(page);
        // A stream that answers once and then says nothing, which a route cannot do.
        await page.addInitScript(() => {
            const original = window.fetch;
            const counted = window as unknown as {watches: number};
            counted.watches = 0;
            window.fetch = async (input, init) => {
                if (!String(input).includes('/watch')) return original(input, init);
                counted.watches++;
                const body = new ReadableStream({
                    start: controller => controller.enqueue(new TextEncoder().encode('data:{"progress":"1"}\n\n')),
                });
                return new Response(body, {headers: {'Content-Type': 'text/event-stream'}});
            };
        });
        await page.clock.install();
        await login(page);
        await page.getByRole('link', {name: 'Configuration'}).click();
        const watches = () => page.evaluate(() => (window as unknown as {watches: number}).watches);
        await expect(row(page, 'application.yaml')).toBeVisible();
        // React opens it twice in development, which says nothing about this.
        const opened = await watches();
        expect(opened).toBeGreaterThan(0);

        await page.clock.runFor(29_000);
        expect(await watches()).toBe(opened);
        await page.clock.runFor(2_000);
        await page.clock.runFor(2_000);

        await expect.poll(watches).toBeGreaterThan(opened);
    });

    test('a failed read says so and can be retried', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        api.failing = {method: 'GET', path: /^\/v1\/namespaces\/default\/configs$/, status: 503, message: 'Down.'};

        await page.getByRole('link', {name: 'Configuration'}).click();

        await expect(page.getByRole('main').getByRole('alert')).toBeVisible();
        api.failing = undefined;
        await page.getByRole('button', {name: /retry/i}).click();
        await expect(row(page, 'application.yaml')).toBeVisible();
    });
});

test.describe('services', () => {
    test('a service shows its instances, leased or fixed', async ({page}) => {
        await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Service', exact: true}).click();

        await expect(row(page, 'order')).toContainText('1');
        await row(page, 'order').getByRole('button', {name: /expand/i}).click();
        const instance = row(page, 'order-1');
        await expect(instance).toContainText('http://10.0.0.1:8080');
        await expect(instance).toContainText('Leased');
        await expect(instance).toContainText('"zone":"east"');

        await row(page, 'database').getByRole('button', {name: /expand/i}).click();
        await expect(row(page, 'database-1')).toContainText('Fixed');
    });

    test('an instance added here names its service and has no lease', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Service', exact: true}).click();

        await page.getByRole('button', {name: 'Add Instance', exact: true}).click();
        await page.getByLabel('Service', {exact: true}).fill('billing');
        await page.getByLabel('Host', {exact: true}).fill('10.0.0.9');
        await page.getByLabel('Port', {exact: true}).fill('8081');
        await typeInMonaco(page, '[1]');
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Metadata must be a JSON object of strings.')).toBeVisible();
        await typeInMonaco(page, '{"zone":"west"}');
        await page.getByRole('button', {name: 'Submit'}).click();

        await expect(toast(page, 'Instance saved')).toBeVisible();
        await expect(row(page, 'billing')).toContainText('1');
        // The name the client would give this endpoint.
        const put = lastRequest(api, 'PUT', '/v1/namespaces/default/instances/billing-10.0.0.9-8081');
        expect(put?.headers['if-none-match']).toBe('*');
        const body = JSON.parse(put?.body ?? '');
        expect(body.instance).toEqual({
            service: 'billing',
            endpoint: {scheme: 'http', host: '10.0.0.9', port: 8081},
            weight: 1,
            metadata: {zone: 'west'},
        });
        expect(body.meta.lease).toBeUndefined();
    });

    test('an edit keeps the lease and the labels, and a delete removes it', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Service', exact: true}).click();
        await row(page, 'order').getByRole('button', {name: /expand/i}).click();

        await row(page, 'order-1').getByRole('button', {name: 'Edit'}).click();
        await expect(page.getByLabel('Service', {exact: true})).toHaveCount(0);
        await expect(page.getByLabel('Name', {exact: true})).toHaveCount(0);
        await page.getByLabel('Weight', {exact: true}).fill('5');
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Instance saved')).toBeVisible();
        const put = lastRequest(api, 'PUT', '/v1/namespaces/default/instances/order-1');
        const body = JSON.parse(put?.body ?? '');
        expect(put?.headers['if-match']).toMatch(/^"\d+"$/);
        expect(body.meta).toEqual({lease: '42', labels: {pool: 'a'}});
        expect(body.instance.weight).toBe(5);
        await expect(row(page, 'order-1')).toContainText('5');

        await confirm(page, row(page, 'order-1').getByRole('button', {name: 'Delete'}));
        await expect(toast(page, 'Instance deleted')).toBeVisible();
        expect(lastRequest(api, 'DELETE', '/v1/namespaces/default/instances/order-1')).toBeTruthy();
    });

    test('an instance added to a listed service needs no service name, and the page follows changes', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Service', exact: true}).click();
        await row(page, 'database').getByRole('button', {name: 'Add instance', exact: true}).click();
        await expect(page.getByRole('dialog')).toContainText('Add an instance of database');
        await expect(page.getByLabel('Service', {exact: true})).toHaveCount(0);
        await page.getByRole('button', {name: 'Cancel'}).click();

        await row(page, 'order').getByRole('button', {name: /expand/i}).click();
        await expect(row(page, 'order-1')).toBeVisible();
        api.put('/v1/namespaces/default/instances/order-2', {
            meta: {},
            instance: {service: 'order', endpoint: {scheme: 'http', host: '10.0.0.3', port: 8080}},
        });
        await expect.poll(() => lastRequest(api, 'GET', '/v1/namespaces/default/watch')).toBeTruthy();
        api.releaseWatch();
        await expect(row(page, 'order-2')).toBeVisible();
    });
});

test.describe('namespaces', () => {
    test('a namespace is added with its description, and only an empty one can be deleted', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('link', {name: 'Namespace'}).click();
        await expect(row(page, 'prod')).toContainText('Production');
        await expect(row(page, 'default').getByRole('button', {name: 'Delete'})).toBeDisabled();

        await page.getByLabel('Namespace name').fill('staging');
        await page.getByLabel('Namespace description').fill('Rehearsals');
        await page.getByRole('button', {name: 'Add Namespace'}).click();
        await expect(toast(page, 'Namespace added')).toBeVisible();
        await expect(row(page, 'staging')).toContainText('Rehearsals');
        await expect(page.getByLabel('Namespace name')).toHaveValue('');

        api.failing = {method: 'DELETE', path: /namespaces\/staging$/, status: 409, message: 'It is not empty.'};
        await confirm(page, row(page, 'staging').getByRole('button', {name: 'Delete'}));
        await expect(toast(page, 'Failed to delete namespace: It is not empty.')).toBeVisible();
        api.failing = undefined;
        await confirm(page, row(page, 'staging').getByRole('button', {name: 'Delete'}));
        await expect(toast(page, 'Namespace deleted')).toBeVisible();
        await expect(page.getByRole('row').filter({hasText: 'staging'})).toHaveCount(0);
    });
});

test.describe('security', () => {
    test('a service account gets a token shown once, which can be revoked', async ({page}) => {
        await installApiMock(page);
        await login(page);
        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Service Account'}).click();
        await expect(row(page, 'admin')).toContainText('Created at bootstrap');

        await page.getByLabel('Service account name').fill('deployer');
        await page.getByLabel('Service account description').fill('The pipeline');
        await page.getByRole('button', {name: 'Add Service Account'}).click();
        await expect(toast(page, 'Service account added')).toBeVisible();

        await row(page, 'deployer').getByRole('button', {name: 'New token'}).click();
        await expect(page.getByRole('textbox', {name: 'New token'})).toHaveValue(/^cosky_.*_plaintextsecret$/);
        await page.getByRole('button', {name: 'Done'}).click();

        await row(page, 'deployer').getByRole('button', {name: /expand/i}).click();
        await confirm(page, page.getByRole('button', {name: 'Revoke'}).first());
        await expect(toast(page, 'Token revoked')).toBeVisible();

        await confirm(page, row(page, 'deployer').getByRole('button', {name: 'Delete'}));
        await expect(toast(page, 'Service account deleted')).toBeVisible();
    });

    test('a failed token says why', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Service Account'}).click();
        api.failing = {method: 'POST', path: /tokens$/, status: 400, message: 'At most 2 tokens.'};

        await row(page, 'admin').getByRole('button', {name: 'New token'}).click();

        await expect(toast(page, 'Failed to create token: At most 2 tokens.')).toBeVisible();
    });

    test('a role binding can give the built-in roles, which are not among those listed', async ({page}) => {
        await installApiMock(page);
        await login(page);
        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Role Binding'}).click();

        await page.getByRole('button', {name: 'Add Role Binding'}).click();
        await page.getByRole('combobox', {name: 'Role'}).click();
        await expect(page.getByRole('option', {name: 'viewer', exact: true})).toBeVisible();
        await expect(page.getByRole('option', {name: 'client', exact: true})).toBeVisible();
        await expect(page.getByRole('option', {name: 'admin', exact: true})).toHaveCount(1);
    });

    test('editing a role or a role binding keeps the labels it does not show', async ({page}) => {
        const api = await installApiMock(page);
        api.put('/v1/roles/reader', {
            meta: {labels: {team: 'core'}},
            role: {description: 'Reads', rules: [{kinds: ['KIND_CONFIG'], verbs: ['VERB_GET'], namespaces: ['*']}]},
        });
        api.put('/v1/rolebindings/people', {
            meta: {labels: {team: 'core'}},
            roleBinding: {role: 'reader', subjects: [{type: 'TYPE_USER', name: 'alice'}], namespaces: ['*']},
        });
        await login(page);

        await page.goto('/#/role');
        await row(page, 'reader').getByRole('button', {name: 'Edit'}).click();
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Role saved')).toBeVisible();
        expect(JSON.parse(lastRequest(api, 'PUT', '/v1/roles/reader')?.body ?? '').meta.labels).toEqual({team: 'core'});

        await page.goto('/#/role-binding');
        await row(page, 'people').getByRole('button', {name: 'Edit'}).click();
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Role binding saved')).toBeVisible();
        expect(JSON.parse(lastRequest(api, 'PUT', '/v1/rolebindings/people')?.body ?? '').meta.labels)
            .toEqual({team: 'core'});
    });

    test('a role holds rules of kinds, verbs, namespaces and names', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Role', exact: true}).click();

        await page.getByRole('button', {name: 'Add Role'}).click();
        await page.getByLabel('Name', {exact: true}).fill('reader');
        await page.getByLabel('Description', {exact: true}).fill('Reads Configs');
        await page.getByRole('button', {name: 'Add rule'}).click();
        await page.getByRole('button', {name: 'Add rule'}).click();
        await page.getByRole('button', {name: 'Remove rule'}).last().click();
        await page.getByRole('button', {name: 'Kinds of rule 1'}).click();
        await page.getByRole('menuitemcheckbox', {name: 'Config'}).click();
        await page.keyboard.press('Escape');
        await page.getByRole('button', {name: 'Verbs of rule 1'}).click();
        await page.getByRole('menuitemcheckbox', {name: 'Get'}).click();
        await page.getByRole('menuitemcheckbox', {name: 'Watch'}).click();
        await page.keyboard.press('Escape');
        await page.getByLabel('Namespaces', {exact: true}).fill('prod, staging');
        await page.getByLabel('Names', {exact: true}).fill('order-*');
        await page.getByRole('button', {name: 'Submit'}).click();

        await expect(toast(page, 'Role saved')).toBeVisible();
        await expect(row(page, 'reader')).toContainText('Reads Configs');
        expect(JSON.parse(lastRequest(api, 'PUT', '/v1/roles/reader')?.body ?? '').role).toEqual({
            description: 'Reads Configs',
            rules: [{
                kinds: ['KIND_CONFIG'],
                verbs: ['VERB_GET', 'VERB_WATCH'],
                namespaces: ['prod', 'staging'],
                names: ['order-*'],
            }],
        });

        await row(page, 'admin').getByRole('button', {name: 'Edit'}).click();
        await expect(page.getByLabel('Name', {exact: true})).toBeDisabled();
        await expect(page.getByLabel('Namespaces', {exact: true})).toHaveValue('*');
        await page.getByRole('button', {name: 'Cancel'}).click();

        await confirm(page, row(page, 'reader').getByRole('button', {name: 'Delete'}));
        await expect(toast(page, 'Role deleted')).toBeVisible();
    });

    test('a role binding gives a role to subjects, in namespaces', async ({page}) => {
        const api = await installApiMock(page);
        await login(page);
        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Role Binding'}).click();
        await expect(row(page, 'admin')).toContainText('Service Account: admin');

        await page.getByRole('button', {name: 'Add Role Binding'}).click();
        await page.getByLabel('Name', {exact: true}).fill('people');
        await choose(page, 'Role', 'admin');
        await page.getByLabel('Namespaces', {exact: true}).fill('prod');
        await page.getByRole('button', {name: 'Add subject'}).click();
        await page.getByRole('button', {name: 'Add subject'}).click();
        await page.getByRole('button', {name: 'Remove subject 2'}).click();
        await choose(page, 'Type of subject 1', 'Group');
        await page.getByLabel('Name of subject 1').fill('operators');
        await page.getByRole('button', {name: 'Submit'}).click();

        await expect(toast(page, 'Role binding saved')).toBeVisible();
        await expect(row(page, 'people')).toContainText('Group: operators');
        expect(JSON.parse(lastRequest(api, 'PUT', '/v1/rolebindings/people')?.body ?? '').roleBinding).toEqual({
            role: 'admin',
            subjects: [{type: 'TYPE_GROUP', name: 'operators'}],
            namespaces: ['prod'],
        });

        await row(page, 'people').getByRole('button', {name: 'Edit'}).click();
        await expect(page.getByLabel('Name of subject 1')).toHaveValue('operators');
        await page.getByRole('button', {name: 'Submit'}).click();
        await expect(toast(page, 'Role binding saved')).toBeVisible();
        expect(lastRequest(api, 'PUT', '/v1/rolebindings/people')?.headers['if-match']).toMatch(/^"\d+"$/);

        await confirm(page, row(page, 'people').getByRole('button', {name: 'Delete'}));
        await expect(toast(page, 'Role binding deleted')).toBeVisible();
    });

    test('pages say when there is nothing yet', async ({page}) => {
        await installApiMock(page, {empty: true});
        await login(page);

        await page.getByRole('link', {name: 'Configuration'}).click();
        await expect(page.getByRole('main')).toContainText('No configurations in this namespace yet.');
        await page.getByRole('link', {name: 'Service', exact: true}).click();
        await expect(page.getByRole('main')).toContainText('No services registered in this namespace yet.');
        await page.getByRole('link', {name: 'Namespace'}).click();
        await expect(page.getByRole('main')).toContainText('No namespaces found.');
        await page.getByRole('button', {name: 'Security'}).click();
        await page.getByRole('link', {name: 'Service Account'}).click();
        await expect(page.getByRole('main')).toContainText('No service accounts yet.');
        await page.getByRole('link', {name: 'Role', exact: true}).click();
        await expect(page.getByRole('main')).toContainText('No roles yet.');
        await page.getByRole('link', {name: 'Role Binding'}).click();
        await expect(page.getByRole('main')).toContainText('No role bindings yet.');
    });
});
