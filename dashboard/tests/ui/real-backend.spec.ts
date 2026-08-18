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
import type {APIRequestContext, Page} from '@playwright/test';
import {readFile} from 'node:fs/promises';

const username = process.env.COSKY_REAL_USERNAME ?? 'cosky';
const password = process.env.COSKY_REAL_PASSWORD ?? '';
const apiBaseURL = process.env.COSKY_REAL_API_URL ?? '';
const suffix = Date.now().toString(36);
const namespace = `e2e-${suffix}`;
const configId = `config-${suffix}.yaml`;
const serviceId = `service-${suffix}`;
const roleName = `role-${suffix}`;
const userName = `user-${suffix}`;
const userPassword = 'E2e-user-password-1';
const changedUserPassword = 'E2e-user-password-2';

async function login(page: Page, loginUsername = username, loginPassword = password) {
    await page.goto('/login');
    await page.getByRole('textbox', {name: 'Username', exact: true}).fill(loginUsername);
    await page.getByRole('textbox', {name: 'Password', exact: true}).fill(loginPassword);
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();
    await page.waitForURL('**/home');
}

async function selectNamespace(page: Page, value: string) {
    await page.getByRole('combobox', {name: 'Select Namespace'}).click();
    await page.getByRole('option', {name: value, exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Select Namespace'})).toContainText(value);
}

async function insertMonacoText(page: Page, label: string, value: string, afterFirstCharacter = false) {
    await expect(page.locator(`[aria-label="${label}"]`)).toBeAttached();
    await page.locator('.monaco-editor:visible').last().click({position: {x: 120, y: 40}});
    if (afterFirstCharacter) {
        await page.keyboard.press('Home');
        await page.keyboard.press('ArrowRight');
    }
    await page.keyboard.insertText(value);
}

async function confirm(page: Page) {
    await page.getByRole('button', {name: 'Continue'}).click();
}

const accountButtonName = (name: string) => `${name.slice(0, 1).toUpperCase()} ${name}`;

async function cleanup(request: APIRequestContext) {
    const authentication = await request.post(new URL(`/v1/authenticate/${username}/login`, apiBaseURL).toString(), {
        data: {password},
    });
    if (!authentication.ok()) return;
    const {accessToken} = await authentication.json() as {accessToken: string};
    const options = {headers: {Authorization: `Bearer ${accessToken}`}};
    await request.delete(new URL(`/v1/users/${userName}`, apiBaseURL).toString(), options);
    await request.delete(new URL(`/v1/roles/${roleName}`, apiBaseURL).toString(), options);
    await request.delete(new URL(`/v1/namespaces/${namespace}/configs/${configId}`, apiBaseURL).toString(), options);
    await request.delete(new URL(`/v1/namespaces/${namespace}/services/${serviceId}`, apiBaseURL).toString(), options);
    await request.delete(new URL(`/v1/namespaces/${namespace}`, apiBaseURL).toString(), options);
}

test.skip(!process.env.COSKY_REAL_E2E || !password, 'Set COSKY_REAL_E2E=1 and COSKY_REAL_PASSWORD to run against an isolated CoSky API.');
test.afterEach(async ({request}) => {
    if (process.env.COSKY_REAL_E2E && password && apiBaseURL) {
        await cleanup(request);
    }
});

test('all dashboard operations work against the real REST API and Redis', async ({page}) => {
    test.setTimeout(180_000);
    page.setDefaultTimeout(10_000);
    const serverErrors: string[] = [];
    const pageErrors: string[] = [];
    page.on('pageerror', error => pageErrors.push(error.message));
    page.on('response', response => {
        if (response.url().includes('/v1/') && response.status() >= 500) {
            serverErrors.push(`${response.status()} ${response.request().method()} ${response.url()}`);
        }
    });

    await page.goto('/login');
    await page.getByRole('textbox', {name: 'Username', exact: true}).fill(username);
    await page.getByRole('textbox', {name: 'Password', exact: true}).fill('definitely-wrong');
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText(/Login failed/)).toBeVisible();

    await login(page);
    await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
    await expect(page.getByText('Service Topology').first()).toBeVisible();

    await page.getByRole('link', {name: 'Namespace', exact: true}).click();
    await page.getByRole('textbox', {name: 'Enter namespace'}).fill(namespace);
    await page.getByRole('button', {name: 'Add Namespace'}).click();
    await expect(page.getByText('Add namespace success!')).toBeVisible();
    await page.getByRole('textbox', {name: 'Search namespaces...'}).fill(namespace);
    await expect(page.getByRole('cell', {name: namespace, exact: true})).toBeVisible();
    await selectNamespace(page, namespace);

    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    await page.getByRole('button', {name: 'Add', exact: true}).click();
    await page.getByRole('textbox', {name: 'Config ID'}).fill(`config-${suffix}`);
    await insertMonacoText(page, 'Config data editor', 'feature:\n  enabled: true\n');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Config saved successfully').last()).toBeVisible();
    await expect(page.getByRole('cell', {name: configId, exact: true})).toBeVisible();

    const configRow = page.getByRole('row').filter({hasText: configId}).first();
    await configRow.getByRole('button', {name: 'Edit'}).click();
    await insertMonacoText(page, 'Config data editor', '# revision 2\n');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Config saved successfully').last()).toBeVisible();

    await configRow.getByRole('button', {name: 'Expand row'}).click();
    const historyTable = page.getByRole('table').nth(1);
    await expect(historyTable.getByRole('cell', {name: '1', exact: true})).toBeVisible();
    await historyTable.getByRole('button', {name: 'Diff'}).first().click();
    await expect(page.getByRole('dialog', {name: 'Config Version Differ'})).toBeVisible();
    await page.getByRole('button', {name: 'Rollback to version 1'}).click();
    await confirm(page);
    await expect(page.getByText('Rollback success')).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', {name: 'Export', exact: true}).click();
    const download = await downloadPromise;
    const exportedZip = await download.path();
    expect(exportedZip).toBeTruthy();
    await expect(page.getByText('Export config success')).toBeVisible();

    await configRow.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('Delete config success')).toBeVisible();
    await page.getByRole('button', {name: 'Import', exact: true}).click();
    await page.locator('input[type="file"]').setInputFiles({
        name: 'configs.zip',
        mimeType: 'application/zip',
        buffer: await readFile(exportedZip!),
    });
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Total: 1, succeeded: 1.')).toBeVisible();
    await expect(page.getByRole('cell', {name: configId, exact: true})).toBeVisible();

    await page.getByRole('link', {name: 'Service', exact: true}).click();
    await page.getByRole('textbox', {name: 'Enter service ID'}).fill(serviceId);
    await page.getByRole('button', {name: 'Add Service'}).click();
    await expect(page.getByText('Add service success!')).toBeVisible();
    await page.getByRole('textbox', {name: 'Search services...'}).fill(serviceId);
    const serviceRow = page.getByRole('row').filter({hasText: serviceId}).first();
    await serviceRow.getByRole('button', {name: 'Add instance'}).click();
    await page.getByRole('textbox', {name: 'Host'}).fill('127.0.0.1');
    await page.getByRole('spinbutton', {name: 'Port'}).fill('18081');
    await insertMonacoText(page, 'Instance metadata editor', '"zone":"e2e"', true);
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save instance success!').last()).toBeVisible();
    await serviceRow.getByRole('button', {name: 'Expand row'}).click();
    const instanceTable = page.getByRole('table').nth(1);
    await expect(instanceTable.getByRole('cell', {name: '127.0.0.1', exact: true})).toBeVisible();
    await instanceTable.getByRole('button', {name: 'Edit'}).click();
    await page.getByRole('switch', {name: 'Ephemeral instance'}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save instance success!').last()).toBeVisible();
    await expect(instanceTable.getByText('{"zone":"e2e"}')).toBeVisible();
    await expect(instanceTable.getByText('No', {exact: true})).toBeVisible();
    await instanceTable.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('Delete instance success!')).toBeVisible();
    await serviceRow.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('Service deleted successfully')).toBeVisible();

    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'Role', exact: true}).click();
    await page.getByRole('button', {name: 'Add Role'}).click();
    await page.getByRole('textbox', {name: 'Role Name'}).fill(roleName);
    await page.getByRole('textbox', {name: 'Description'}).fill('End-to-end role');
    await page.getByRole('button', {name: 'Add permission'}).click();
    await page.getByRole('combobox', {name: 'Select Namespace'}).last().click();
    await page.getByRole('option', {name: namespace, exact: true}).click();
    await page.getByRole('combobox', {name: 'Select Resource Action'}).click();
    await page.getByRole('option', {name: 'Read and write'}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save role success!').last()).toBeVisible();

    await page.getByRole('textbox', {name: 'Search roles...'}).fill(roleName);
    const roleRow = page.getByRole('row').filter({hasText: roleName}).first();
    await expect(roleRow.getByText(`${namespace}: Read & write`, {exact: true})).toBeVisible();
    await roleRow.getByRole('button', {name: 'Edit'}).click();
    await expect(page.getByRole('combobox', {name: 'Select Resource Action'})).toContainText('Read and write');
    await page.getByRole('textbox', {name: 'Description'}).fill('Updated end-to-end role');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save role success!').last()).toBeVisible();

    await page.getByRole('link', {name: 'User', exact: true}).click();
    await page.getByRole('button', {name: 'Add User'}).click();
    await page.getByRole('textbox', {name: 'Username'}).fill(userName);
    await page.getByRole('textbox', {name: 'Password'}).fill(userPassword);
    await page.getByRole('button', {name: 'Select roles'}).click();
    await page.getByRole('menuitemcheckbox', {name: roleName}).click();
    await page.getByRole('menuitemcheckbox', {name: 'admin'}).click();
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Add user success!')).toBeVisible();
    await expect(page.getByText('Bind role success!')).toBeVisible();

    await page.getByRole('button', {name: accountButtonName(username), exact: true}).click();
    await page.getByRole('menuitem', {name: 'Sign out'}).click();
    await login(page, userName, userPassword);
    await page.getByRole('button', {name: accountButtonName(userName), exact: true}).click();
    await page.getByRole('menuitem', {name: 'Change password'}).click();
    await page.getByRole('textbox', {name: 'Old Password'}).fill(userPassword);
    await page.getByRole('textbox', {name: 'New Password'}).fill(changedUserPassword);
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Change password success!')).toBeVisible();
    await page.getByRole('button', {name: accountButtonName(userName), exact: true}).click();
    await page.getByRole('menuitem', {name: 'Sign out'}).click();
    await login(page, userName, changedUserPassword);
    await page.getByRole('button', {name: accountButtonName(userName), exact: true}).click();
    await page.getByRole('menuitem', {name: 'Sign out'}).click();
    await login(page);
    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'User', exact: true}).click();
    await page.getByRole('textbox', {name: 'Search users...'}).fill(userName);
    const userRow = page.getByRole('row').filter({hasText: userName}).first();
    await userRow.getByRole('button', {name: `Roles for ${userName}`}).click();
    await page.getByRole('menuitemcheckbox', {name: roleName}).click();
    await expect(page.getByText('Role bind successfully')).toBeVisible();
    await page.keyboard.press('Escape');
    await userRow.getByRole('button', {name: 'Unlock'}).click();
    await confirm(page);
    await expect(page.getByText('User unlocked successfully')).toBeVisible();
    await userRow.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('User deleted successfully')).toBeVisible();

    await page.getByRole('link', {name: 'Role', exact: true}).click();
    await roleRow.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('Role deleted successfully')).toBeVisible();

    await page.getByRole('link', {name: 'Audit Log', exact: true}).click();
    await expect(page.getByText(/\d+ items/)).toBeVisible();
    await page.getByRole('textbox', {name: 'Search all events'}).fill(userName);
    await page.getByRole('button', {name: 'Apply'}).click();
    await expect(page.getByRole('cell', {name: username, exact: true}).first()).toBeVisible();
    await page.getByRole('button', {name: 'Details'}).first().click();
    await expect(page.getByRole('dialog', {name: 'Audit Event Details'})).toContainText(userName);
    await page.getByRole('button', {name: 'Close', exact: true}).click();
    const auditDownloadPromise = page.waitForEvent('download');
    await page.getByRole('button', {name: 'Export CSV'}).click();
    expect((await auditDownloadPromise).suggestedFilename()).toMatch(/^cosky_audit_log_\d{8}_\d{6}\.csv$/);

    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    const importedConfigRow = page.getByRole('row').filter({hasText: configId}).first();
    await importedConfigRow.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('Delete config success')).toBeVisible();

    await selectNamespace(page, 'cosky-{system}');
    await page.getByRole('link', {name: 'Namespace', exact: true}).click();
    await page.getByRole('textbox', {name: 'Search namespaces...'}).fill(namespace);
    const namespaceRow = page.getByRole('row').filter({hasText: namespace}).first();
    await namespaceRow.getByRole('button', {name: 'Delete'}).click();
    await confirm(page);
    await expect(page.getByText('Namespace deleted successfully')).toBeVisible();

    expect(serverErrors, 'real API 5xx responses').toEqual([]);
    expect(pageErrors, 'uncaught browser errors').toEqual([]);
});
