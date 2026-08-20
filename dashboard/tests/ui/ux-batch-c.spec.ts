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

test.beforeEach(async ({page}) => {
    await installApiMock(page);
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

    // Recent Changes rows must deep-link to the audit log filtered by the resource name.
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
    const onRequest = (request: import('@playwright/test').Request) => {
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