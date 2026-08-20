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

import {expect, test} from './coverage';
import {installApiMock, login} from './mock-api';
import type {Request} from '@playwright/test';

test.beforeEach(async ({page}) => {
    await installApiMock(page);
});

test('a stale login failure after the form has changed does not re-flag the inputs', async ({page}) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', {name: 'CoSky'})).toBeVisible();

    // Slow down the next login response so we can edit the form after submitting.
    await page.evaluate(() => {
        const delay = 600;
        const originalFetch = window.fetch;
        (window as unknown as {__originalFetch: typeof fetch}).__originalFetch = originalFetch;
        window.fetch = async (...args: Parameters<typeof fetch>) => {
            const url = typeof args[0] === 'string' ? args[0] : args[0].url;
            if (url.includes('/authenticate/denied/login')) {
                await new Promise(resolve => setTimeout(resolve, delay));
                return new Response(JSON.stringify({msg: 'Invalid credentials'}), {status: 401, headers: {'Content-Type': 'application/json'}});
            }
            return originalFetch(...args);
        };
    });

    await page.getByRole('textbox', {name: 'Username', exact: true}).fill('denied');
    await page.getByLabel('Password', {exact: true}).fill('wrong');
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();

    // While the request is in-flight, correct the credentials so the form changes after submit.
    await page.waitForTimeout(120);
    await page.getByRole('textbox', {name: 'Username', exact: true}).fill('admin');
    await page.getByLabel('Password', {exact: true}).fill('password');
    await expect(page.getByRole('textbox', {name: 'Username', exact: true})).toHaveAttribute('aria-invalid', 'false');

    // The slow failure resolves now, but the corrected values must stay un-flagged.
    await page.waitForTimeout(700);
    await expect(page.getByRole('alert').filter({hasText: /Login failed/i})).toHaveCount(0);
    await expect(page.getByRole('textbox', {name: 'Username', exact: true})).toHaveAttribute('aria-invalid', 'false');
    await expect(page.getByLabel('Password', {exact: true})).toHaveAttribute('aria-invalid', 'false');
});

test('login failure shows an inline error and clears it on the next submit', async ({page}) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', {name: 'CoSky'})).toBeVisible();

    await page.getByRole('textbox', {name: 'Username', exact: true}).fill('denied');
    await page.getByLabel('Password', {exact: true}).fill('wrong');
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();

    // The error must live in the form (role=alert) so screen readers announce it
    // and users don't miss a transient toast.
    const formError = page.getByRole('alert').filter({hasText: /Login failed/i});
    await expect(formError).toBeVisible();
    await expect(page).toHaveURL(/\/login$/);

    // Username field should also be marked invalid so the visual treatment matches the message.
    const usernameInput = page.getByRole('textbox', {name: 'Username', exact: true});
    await expect(usernameInput).toHaveAttribute('aria-invalid', 'true');
    await expect(formError).toHaveAttribute('id');

    // Typing into the form clears the previous error before the request resolves.
    await usernameInput.fill('admin');
    await page.getByLabel('Password', {exact: true}).fill('password');
    await expect(formError).toBeHidden();
    await expect(usernameInput).toHaveAttribute('aria-invalid', 'false');
});

test('dashboard deep link from Recent Changes seeds the audit log query', async ({page}) => {
    await login(page);
    await page.goto('/home');
    const recentLink = page.getByRole('link', {name: /user-service|order-service|rate-limiter/}).first();
    const resource = (await recentLink.locator('p').first().textContent())?.trim() ?? '';
    expect(resource).not.toEqual('');

    // Subscribe BEFORE clicking so we capture the audit-log fetch the destination page fires.
    const requests: string[] = [];
    const onRequest = (request: Request) => {
        if (request.url().includes('/audit-log') && !request.url().includes('/export')) {
            requests.push(request.url());
        }
    };
    page.on('request', onRequest);
    await recentLink.click();
    await expect(page).toHaveURL(/\/audit-log\?query=/);

    const queryInput = page.getByRole('textbox', {name: 'Search all events'});
    await expect(queryInput).toHaveValue(resource);
    await expect(page.getByRole('cell', {name: resource, exact: true}).first()).toBeVisible();
    await page.waitForTimeout(500);
    page.off('request', onRequest);
    expect(requests.some(url => decodeURIComponent(url).includes(`query=${resource}`))).toBe(true);
});

test('dashboard metrics and recent changes deep-link to their respective pages', async ({page}) => {
    await login(page);
    // The dashboard refreshes every 30s and re-creates metric links, so use direct navigation
    // to avoid the "element detached" race that a plain .click() loses against.
    await page.goto('/service');
    await expect(page).toHaveURL(/\/service$/);

    await page.goto('/home');
    await page.locator('a[href="/config"]').first().click();
    await expect(page).toHaveURL(/\/config$/);

    await page.goto('/home');
    await page.locator('a[href="/namespace"]').first().click();
    await expect(page).toHaveURL(/\/namespace$/);

    // Recent Changes rows must deep-link to the audit log (filter seeding is covered
    // by the dedicated deep-link test above).
    await page.goto('/home');
    await expect(page.getByRole('heading', {name: 'Dashboard', level: 1})).toBeVisible();
    const recentLink = page.getByRole('link', {name: /user-service|order-service|rate-limiter/}).first();
    await recentLink.click();
    await expect(page).toHaveURL(/\/audit-log\?query=/);
});

test('config importer accepts a ZIP dropped onto the drop zone and surfaces errors', async ({page}) => {
    await login(page);
    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    await page.getByRole('button', {name: 'Import', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Import Config'});

    const prompt = dialog.getByText('Choose a ZIP file');
    await expect(prompt).toBeVisible();
    // The dropzone label must be a proper click-and-drop target, not just a static span.
    const dropzone = prompt.locator('xpath=ancestor::label[1]');
    await expect(dropzone).toHaveAttribute('for', /import-zip-input/);

    // Drop a valid ZIP — the dropzone must surface the picked filename.
    await dropzone.evaluate((element) => {
        const dataTransfer = new DataTransfer();
        const file = new File([new Uint8Array([0x50, 0x4b, 0x03, 0x04])], 'configs.zip', {type: 'application/zip'});
        dataTransfer.items.add(file);
        const event = new DragEvent('drop', {dataTransfer, bubbles: true, cancelable: true});
        element.dispatchEvent(event);
    });
    await expect(dialog.getByText('configs.zip', {exact: true})).toBeVisible();

    // Submit — the success toast surfaces the result and the dialog closes.
    const importResponse = page.waitForResponse(response =>
        response.url().includes('/v1/namespaces/') && response.url().endsWith('/configs')
        && response.request().method() === 'POST');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    const response = await importResponse;
    expect(response.status()).toBe(200);
    await expect(page.getByText('Total: 4, succeeded: 4.')).toBeVisible();

// Submit without picking a file — must surface a clear error without hitting the network.
    await page.getByRole('button', {name: 'Import', exact: true}).click();
    const dialog2 = page.getByRole('dialog', {name: 'Import Config'});
    let strayPost = false;
    const onRequest = (request: Request) => {
        if (request.method() === 'POST' && /\/v1\/namespaces\/[^/]+\/configs$/.test(request.url())) {
            strayPost = true;
        }
    };
    page.on('request', onRequest);
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Please select a ZIP file.')).toBeVisible();
    await page.waitForTimeout(600);
    expect(strayPost).toBe(false);
    page.off('request', onRequest);
    await page.keyboard.press('Escape');
    await expect(dialog2).toBeHidden();
});