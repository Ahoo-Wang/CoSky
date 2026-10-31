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
import {expect, test} from '@playwright/test';
import type {APIRequestContext, Locator, Page} from '@playwright/test';

const token = process.env.COSKY_REAL_TOKEN ?? '';
const suffix = Date.now().toString(36);
const namespace = `e2e-${suffix}`;

const authorization = {Authorization: `Bearer ${token}`};

const row = (page: Page, name: string) => page.getByRole('row').filter({hasText: name}).first();

const toast = (page: Page, text: string) => page.getByText(text, {exact: false}).first();

async function confirm(page: Page, trigger: Locator) {
    await trigger.click();
    await page.getByRole('alertdialog').getByRole('button', {name: 'Continue'}).click();
}

async function typeInMonaco(page: Page, text: string) {
    await page.locator('.monaco-editor:visible').last().click({position: {x: 120, y: 40}});
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.press('Delete');
    await page.keyboard.insertText(text);
}

async function signIn(page: Page, withToken = token) {
    await page.goto('/#/login');
    await page.getByLabel('Token', {exact: true}).fill(withToken);
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();
    await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
}

async function selectNamespace(page: Page) {
    await page.getByRole('combobox', {name: 'Select Namespace'}).click();
    await page.getByRole('option', {name: namespace, exact: true}).click();
}

async function putConfig(request: APIRequestContext, name: string, content: string) {
    const response = await request.put(`/v1/namespaces/${namespace}/configs/${name}`, {
        headers: authorization,
        data: {config: {format: 'CONFIG_FORMAT_TEXT', content}},
    });
    expect(response.ok()).toBe(true);
}

test.describe.configure({mode: 'serial'});

test('an anonymous visitor is asked to sign in, and a wrong token is refused', async ({page}) => {
    await page.goto('/');
    await page.waitForURL('**/#/login');

    await page.getByLabel('Token', {exact: true}).fill('cosky_not_valid');
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();

    await expect(toast(page, 'Sign-in failed')).toBeVisible();
});

test('a namespace can be created, and what happens in it shows up by itself', async ({page, request}) => {
    await signIn(page);
    await page.getByRole('link', {name: 'Namespace'}).click();
    await page.getByLabel('Namespace name').fill(namespace);
    await page.getByLabel('Namespace description').fill('End to end');
    await page.getByRole('button', {name: 'Add Namespace'}).click();
    await expect(row(page, namespace)).toContainText('End to end');
    await selectNamespace(page);

    await page.getByRole('link', {name: 'Configuration'}).click();
    await expect(page.getByRole('main')).toContainText('No configurations in this namespace yet.');
    await putConfig(request, 'from-elsewhere.txt', 'written by someone else');

    // No reload: the stream brings it.
    await expect(row(page, 'from-elsewhere.txt')).toBeVisible();
});

test('a Config goes through its whole life', async ({page, request}) => {
    await signIn(page);
    await selectNamespace(page);
    await page.getByRole('link', {name: 'Configuration'}).click();

    await page.getByRole('button', {name: 'Add', exact: true}).click();
    await page.getByLabel('Name', {exact: true}).fill('shared/application.yaml');
    await typeInMonaco(page, 'version: one');
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(toast(page, 'Config saved')).toBeVisible();
    await expect(row(page, 'shared/application.yaml')).toContainText('YAML');
    const stored = await request.get(`/v1/namespaces/${namespace}/configs/shared%2Fapplication.yaml`, {headers: authorization});
    expect((await stored.json()).config.sha256).toBe(createHash('sha256').update('version: one').digest('hex'));

    await row(page, 'shared/application.yaml').getByRole('button', {name: 'Edit'}).click();
    await expect(page.locator('.monaco-editor:visible .view-lines').last()).toContainText('one');
    await typeInMonaco(page, 'version: two');
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);

    await row(page, 'shared/application.yaml').getByRole('button', {name: /expand/i}).click();
    const diffs = page.getByRole('button', {name: 'Diff'});
    await expect(diffs).toHaveCount(2);
    await diffs.last().click();
    await confirm(page, page.getByRole('button', {name: /Roll back to revision/}));
    await expect(toast(page, 'Rolled back')).toBeVisible();

    await row(page, 'shared/application.yaml').getByRole('button', {name: 'Edit'}).click();
    await expect(page.locator('.monaco-editor:visible .view-lines').last()).toContainText('one');
    await page.getByRole('button', {name: 'Cancel'}).click();

    const download = page.waitForEvent('download');
    await page.getByRole('button', {name: 'Export'}).click();
    const archive = await (await download).path();

    await confirm(page, row(page, 'shared/application.yaml').getByRole('button', {name: 'Delete'}));
    await expect(page.getByRole('row').filter({hasText: 'shared/application.yaml'})).toHaveCount(0);

    await page.getByRole('button', {name: 'Import'}).click();
    await page.locator('input[type="file"]').setInputFiles(archive);
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(toast(page, 'Configs imported')).toBeVisible();
    await expect(row(page, 'shared/application.yaml')).toBeVisible();
});

test('a fixed instance is added, changed and removed', async ({page}) => {
    await signIn(page);
    await selectNamespace(page);
    await page.getByRole('link', {name: 'Service', exact: true}).click();

    await page.getByRole('button', {name: 'Add Instance', exact: true}).click();
    await page.getByLabel('Service', {exact: true}).fill('database');
    await page.getByLabel('Host', {exact: true}).fill('10.0.0.9');
    await page.getByLabel('Port', {exact: true}).fill('3306');
    await typeInMonaco(page, '{"zone":"east"}');
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(toast(page, 'Instance saved')).toBeVisible();

    await row(page, 'database').getByRole('button', {name: /expand/i}).click();
    await expect(row(page, '10.0.0.9-3306')).toContainText('Fixed');
    await expect(row(page, '10.0.0.9-3306')).toContainText('"zone":"east"');

    await row(page, '10.0.0.9-3306').getByRole('button', {name: 'Edit'}).click();
    await page.getByLabel('Weight', {exact: true}).fill('7');
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(row(page, '10.0.0.9-3306')).toContainText('7');

    await page.getByRole('link', {name: 'Dashboard'}).click();
    await expect(page.getByText('Instances', {exact: true}).locator('..')).toContainText('1');

    await page.getByRole('link', {name: 'Service', exact: true}).click();
    await row(page, 'database').getByRole('button', {name: /expand/i}).click();
    await confirm(page, row(page, '10.0.0.9-3306').getByRole('button', {name: 'Delete'}));
    await expect(page.getByRole('main')).toContainText('No services registered in this namespace yet.');
});

test('a new service account signs in, and may only do what its role says', async ({page}) => {
    const account = `reader-${suffix}`;
    await signIn(page);
    await page.getByRole('button', {name: 'Security'}).click();

    await page.getByRole('link', {name: 'Service Account'}).click();
    await page.getByLabel('Service account name').fill(account);
    await page.getByRole('button', {name: 'Add Service Account'}).click();
    await row(page, account).getByRole('button', {name: 'New token'}).click();
    const newToken = page.getByRole('textbox', {name: 'New token'});
    await expect(newToken).toHaveValue(/^cosky_/);
    const plaintext = await newToken.inputValue();
    await page.getByRole('button', {name: 'Done'}).click();

    await page.getByRole('link', {name: 'Role', exact: true}).click();
    await page.getByRole('button', {name: 'Add Role'}).click();
    await page.getByLabel('Name', {exact: true}).fill(account);
    await page.getByRole('button', {name: 'Add rule'}).click();
    await page.getByRole('button', {name: 'Kinds of rule 1'}).click();
    for (const kind of ['Namespace', 'Config', 'Instance']) {
        await page.getByRole('menuitemcheckbox', {name: kind, exact: true}).click();
    }
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Verbs of rule 1'}).click();
    await page.getByRole('menuitemcheckbox', {name: 'Get'}).click();
    await page.getByRole('menuitemcheckbox', {name: 'Watch'}).click();
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(toast(page, 'Role saved')).toBeVisible();

    await page.getByRole('link', {name: 'Role Binding'}).click();
    await page.getByRole('button', {name: 'Add Role Binding'}).click();
    await page.getByLabel('Name', {exact: true}).fill(account);
    await page.getByRole('combobox', {name: 'Role'}).click();
    await page.getByRole('option', {name: account, exact: true}).click();
    await page.getByRole('button', {name: 'Add subject'}).click();
    await page.getByLabel('Name of subject 1').fill(account);
    await page.getByRole('button', {name: 'Submit'}).click();
    await expect(toast(page, 'Role binding saved')).toBeVisible();

    await page.locator('[data-slot="account-menu-trigger"]').click();
    await page.getByRole('menuitem', {name: 'Sign out'}).click();
    await page.waitForURL('**/#/login');

    await signIn(page, plaintext);
    await expect(page.locator('[data-slot="account-menu-trigger"]')).toContainText(account);
    await selectNamespace(page);
    await page.getByRole('link', {name: 'Configuration'}).click();
    await expect(row(page, 'from-elsewhere.txt')).toBeVisible();

    await confirm(page, row(page, 'from-elsewhere.txt').getByRole('button', {name: 'Delete'}));
    await expect(toast(page, 'Failed to delete config')).toBeVisible();
    await expect(row(page, 'from-elsewhere.txt')).toBeVisible();
});

test('a namespace that holds something cannot be deleted, and an empty one can', async ({page, request}) => {
    await signIn(page);
    await page.getByRole('link', {name: 'Namespace'}).click();

    await confirm(page, row(page, namespace).getByRole('button', {name: 'Delete'}));
    await expect(toast(page, 'Failed to delete namespace')).toBeVisible();

    for (const name of ['from-elsewhere.txt', 'shared%2Fapplication.yaml']) {
        const response = await request.delete(`/v1/namespaces/${namespace}/configs/${name}`, {headers: authorization});
        expect(response.ok()).toBe(true);
    }
    await confirm(page, row(page, namespace).getByRole('button', {name: 'Delete'}));
    await expect(toast(page, 'Namespace deleted')).toBeVisible();
    await expect(page.getByRole('row').filter({hasText: namespace})).toHaveCount(0);
});
