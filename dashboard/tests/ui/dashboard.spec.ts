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
import type {Page} from '@playwright/test';
import {installApiMock, login} from './mock-api';
import type {MockApi} from './mock-api';

const browserErrors = new WeakMap<Page, string[]>();
const apiMocks = new WeakMap<Page, MockApi>();

test.beforeEach(async ({page}) => {
    const errors: string[] = [];
    browserErrors.set(page, errors);
    page.on('console', message => {
        if (message.type() === 'error') errors.push(message.text());
    });
    page.on('pageerror', error => errors.push(error.message));
    apiMocks.set(page, await installApiMock(page));
});

test.afterEach(async ({page}) => {
    expect(browserErrors.get(page), 'browser console and page errors').toEqual([]);
});

test('rejects invalid credentials without leaving the login screen', async ({page}) => {
    await page.goto('/login');
    await page.getByRole('textbox', {name: 'Username', exact: true}).fill('denied');
    await page.getByRole('textbox', {name: 'Password', exact: true}).fill('wrong');
    await page.getByRole('button', {name: 'Sign In', exact: true}).click();

    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByText('Login failed. Invalid credentials')).toBeVisible();
    expect(browserErrors.get(page)).toContain('Failed to load resource: the server responded with a status of 401 (Unauthorized)');
    browserErrors.set(page, []);
});

test('dashboard supports health inspection, namespace switching, and sidebar controls', async ({page}) => {
    const api = apiMocks.get(page)!;
    await login(page);

    await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
    await expect(page.getByText('Healthy Services')).toBeVisible();
    await expect(page.getByText(/128\s*\/\s*134/)).toBeVisible();
    await expect(page.getByText('Service Topology').first()).toBeVisible();
    await expect(page.getByText('Recent Changes')).toBeVisible();
    await expect(page.getByText('inventory-service')).toBeVisible();

    await page.getByRole('button', {name: 'Open topology fullscreen'}).click();
    await expect(page.getByRole('dialog', {name: 'Service Topology'})).toBeVisible();
    await page.getByRole('button', {name: 'Close topology fullscreen'}).click();
    await expect(page.getByRole('dialog', {name: 'Service Topology'})).toBeHidden();

    await page.getByTestId('rf__node-api-gateway').click();
    const directEdge = page.getByTestId('rf__edge-api-gateway-user-service').locator('.react-flow__edge-path');
    const secondHopEdge = page.getByTestId('rf__edge-user-service-user-db').locator('.react-flow__edge-path');
    await expect(directEdge).toHaveCSS('opacity', '1');
    await expect(directEdge).toHaveCSS('stroke-width', '3px');
    await expect(secondHopEdge).toHaveCSS('opacity', '0.08');

    await page.getByRole('combobox', {name: 'Select Namespace'}).click();
    await page.getByRole('option', {name: 'default', exact: true}).click();
    await expect(page.getByRole('combobox', {name: 'Select Namespace'})).toContainText('default');
    await expect(page.getByText('System health and service relationships in default.')).toBeVisible();

    await page.getByRole('button', {name: 'Collapse navigation'}).click();
    await expect(page.getByRole('button', {name: 'Expand navigation'})).toBeVisible();
    await page.getByRole('button', {name: 'Expand navigation'}).click();
    await expect(page.getByText('Microservice Governance')).toBeVisible();

    await page.getByRole('textbox', {name: 'Search nodes...'}).fill('order');
    await expect(page.getByTestId('rf__node-order-service').getByText('order-service', {exact: true})).toBeVisible();

    api.emptyCollections.add('topology');
    await page.reload();
    await expect(page.getByText('No service relationships yet')).toBeVisible();
});

test('configuration workflow covers search, history, editor, and import', async ({page}) => {
    await login(page);
    await page.getByRole('link', {name: 'Configuration', exact: true}).click();

    const search = page.getByRole('textbox', {name: 'Search configurations...'});
    await search.fill('feature');
    await expect(page.getByText('feature-flags.json', {exact: true})).toBeVisible();
    await expect(page.getByText('application.yaml', {exact: true})).toBeHidden();
    await search.clear();

    await page.getByRole('button', {name: 'Expand row'}).first().click();
    await expect(page.getByRole('cell', {name: '3', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Diff', exact: true}).first().click();
    await expect(page.getByRole('dialog', {name: 'Config Version Differ'})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Rollback to version 3'})).toBeVisible();
    await page.getByRole('button', {name: 'Close', exact: true}).click();

    await page.getByRole('button', {name: 'Add', exact: true}).click();
    const addConfigDialog = page.getByRole('dialog', {name: 'Add Config'});
    const addConfigDialogBox = await addConfigDialog.boundingBox();
    expect(addConfigDialogBox?.width).toBeGreaterThan(800);
    expect(await addConfigDialog.locator('[data-slot="sheet-header"]').evaluate(element => getComputedStyle(element).paddingLeft)).toBe('24px');
    await page.getByRole('textbox', {name: 'Config ID'}).fill('new-config');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Config saved successfully')).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Add Config'})).toBeHidden();

    await page.getByRole('button', {name: 'Import', exact: true}).click();
    expect((await page.getByRole('dialog', {name: 'Import Config'}).boundingBox())?.width).toBeLessThanOrEqual(641);
    await page.locator('input[type="file"]').setInputFiles({
        name: 'configs.zip',
        mimeType: 'application/zip',
        buffer: Buffer.from('mock zip'),
    });
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Total: 4, succeeded: 4.')).toBeVisible();
});

test('service workflow expands instances and registers a new instance', async ({page}) => {
    await login(page);
    await page.getByRole('link', {name: 'Service', exact: true}).click();

    await page.getByRole('button', {name: 'Expand row'}).first().click();
    await expect(page.getByText('api-gateway.default.svc.cluster.local')).toBeVisible();
    await page.getByRole('button', {name: 'Add instance', exact: true}).first().click();

    await expect(page.getByRole('dialog', {name: 'Add [api-gateway] Instance'})).toBeVisible();
    expect((await page.locator('.monaco-editor:visible').boundingBox())?.height).toBeGreaterThanOrEqual(360);
    await page.getByRole('textbox', {name: 'Host'}).fill('api-gateway-02.default.svc.cluster.local');
    await page.getByRole('spinbutton', {name: 'Port'}).fill('8081');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save instance success!')).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Add [api-gateway] Instance'})).toBeHidden();
});

test('administration workflows cover namespaces, users, roles, and audit logs', async ({page}) => {
    await login(page);
    await page.getByRole('link', {name: 'Namespace', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Delete'}).first()).toBeDisabled();
    await page.getByRole('textbox', {name: 'Enter namespace'}).fill('qa');
    await page.getByRole('button', {name: 'Add Namespace'}).click();
    await expect(page.getByText('Add namespace success!')).toBeVisible();

    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'User', exact: true}).click();
    await page.getByRole('button', {name: 'Roles for operator'}).click();
    await page.getByRole('menuitemcheckbox', {name: 'auditor'}).click();
    await expect(page.getByText('Role bind successfully')).toBeVisible();
    await page.keyboard.press('Escape');

    await page.getByRole('button', {name: 'Add User'}).click();
    expect((await page.getByRole('dialog', {name: 'Add User'}).boundingBox())?.width).toBeLessThanOrEqual(521);
    await page.getByRole('textbox', {name: 'Username'}).fill('qa-user');
    await page.getByRole('textbox', {name: 'Password'}).fill('secret');
    await page.getByRole('button', {name: 'Select roles'}).click();
    await page.getByRole('menuitemcheckbox', {name: 'admin'}).click();
    await page.keyboard.press('Escape');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Add user success!')).toBeVisible();

    await page.getByRole('link', {name: 'Role', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Delete admin (system role)'})).toBeDisabled();
    await page.getByRole('button', {name: 'Add Role'}).click();
    expect((await page.getByRole('dialog', {name: 'Add Role'}).boundingBox())?.width).toBeLessThanOrEqual(681);
    await page.getByRole('textbox', {name: 'Role Name'}).fill('qa-role');
    await page.getByRole('textbox', {name: 'Description'}).fill('QA access');
    await page.getByRole('button', {name: 'Add permission'}).click();
    await page.getByRole('combobox', {name: 'Select Namespace'}).last().click();
    await page.getByRole('option', {name: 'default', exact: true}).click();
    await page.getByRole('combobox', {name: 'Select Resource Action'}).click();
    await page.getByRole('option', {name: 'Read and write'}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save role success!')).toBeVisible();

    await page.getByRole('link', {name: 'Audit Log', exact: true}).click();
    await expect(page.getByRole('cell', {name: 'inventory-service'})).toBeVisible();
    await expect(page.getByText('14 items')).toBeVisible();
    await page.getByRole('textbox', {name: 'Filter this page by time, operator, status, resource, or action...'}).fill('inventory');
    await expect(page.getByRole('cell', {name: 'inventory-service'})).toBeVisible();
    await expect(page.getByRole('cell', {name: 'order-service'})).toBeHidden();
    await expect(page.getByText('1 matching item on this page')).toBeVisible();
});

test('remaining mutations cover edit, export, rollback, delete, password, and sign-out', async ({page}) => {
    const api = apiMocks.get(page)!;
    await login(page);

    const accountButton = page.locator('.app-user-button');
    await accountButton.click();
    await expect(accountButton).toHaveCSS('color', 'rgb(255, 255, 255)');
    await expect(accountButton).toHaveCSS('background-color', 'rgba(255, 255, 255, 0.08)');
    await page.getByRole('menuitem', {name: 'Change password'}).click();
    await page.getByRole('textbox', {name: 'Old Password'}).fill('password');
    await page.getByRole('textbox', {name: 'New Password'}).fill('new-password');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Change password success!')).toBeVisible();

    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    await page.getByRole('button', {name: 'Edit', exact: true}).first().click();
    await expect(page.getByRole('dialog', {name: 'Edit Config'})).toBeVisible();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Config saved successfully')).toBeVisible();

    const downloadPromise = page.waitForEvent('download');
    await page.getByRole('button', {name: 'Export', exact: true}).click();
    const download = await downloadPromise;
    expect(download.suggestedFilename()).toMatch(/^cosky_.+\.zip$/);
    await expect(page.getByText('Export config success')).toBeVisible();

    await page.getByRole('button', {name: 'Expand row'}).first().click();
    await page.getByRole('button', {name: 'Diff', exact: true}).first().click();
    await page.getByRole('button', {name: 'Rollback to version 3'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Rollback success')).toBeVisible();

    const configRow = page.getByRole('row').filter({hasText: 'application.yaml'}).first();
    await configRow.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Delete config success')).toBeVisible();

    await page.getByRole('link', {name: 'Service', exact: true}).click();
    await page.getByRole('textbox', {name: 'Enter service ID'}).fill('qa-service');
    await page.getByRole('button', {name: 'Add Service'}).click();
    await expect(page.getByText('Add service success!')).toBeVisible();
    await page.getByRole('button', {name: 'Expand row'}).first().click();
    const instanceTable = page.getByRole('table').nth(1);
    await instanceTable.getByRole('button', {name: 'Edit'}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save instance success!')).toBeVisible();
    await instanceTable.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Delete instance success!')).toBeVisible();
    const serviceRow = page.getByRole('row').filter({hasText: 'api-gateway'}).first();
    await serviceRow.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Service deleted successfully')).toBeVisible();

    await page.getByRole('link', {name: 'Namespace', exact: true}).click();
    const namespaceRow = page.getByRole('row').filter({hasText: 'production'});
    await namespaceRow.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Namespace deleted successfully')).toBeVisible();

    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'User', exact: true}).click();
    const operatorRow = page.getByRole('row').filter({hasText: 'operator'});
    await operatorRow.getByRole('button', {name: 'Unlock'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('User unlocked successfully')).toBeVisible();
    await operatorRow.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('User deleted successfully')).toBeVisible();

    await page.getByRole('link', {name: 'Role', exact: true}).click();
    const developerRow = page.getByRole('row').filter({hasText: 'developer'}).first();
    await developerRow.getByRole('button', {name: 'Edit'}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Save role success!')).toBeVisible();
    await developerRow.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Role deleted successfully')).toBeVisible();

    await page.getByRole('button', {name: 'A admin'}).click();
    await page.getByRole('menuitem', {name: 'Sign out'}).click();
    await expect(page).toHaveURL(/\/login$/);

    for (const [method, path] of [
        ['PATCH', '/v1/users/admin/password'],
        ['GET', '/configs/export'],
        ['PUT', '/to/3'],
        ['DELETE', '/configs/application.yaml'],
        ['PUT', '/services/qa-service'],
        ['DELETE', '/instances/api-gateway-01'],
        ['DELETE', '/services/api-gateway'],
        ['DELETE', '/namespaces/production'],
        ['DELETE', '/users/operator/unlock'],
        ['DELETE', '/users/operator'],
        ['DELETE', '/roles/developer'],
    ] as const) {
        expect(api.requests.some(request => request.method === method && request.path.endsWith(path)), `${method} ${path}`).toBe(true);
    }
});

test('tables expose loading, empty, sorting, pagination, and server-error states', async ({page}) => {
    const api = apiMocks.get(page)!;
    await login(page);

    api.manyServices = true;
    api.delayNext = {method: 'GET', path: /\/services\/stats$/, milliseconds: 800};
    await page.getByRole('link', {name: 'Service', exact: true}).click();
    await expect(page.locator('[data-slot="skeleton"]').first()).toBeVisible();
    await expect(page.getByText('api-gateway', {exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Next page'}).click();
    await expect(page.getByText('2 / 2')).toBeVisible();
    await page.getByRole('combobox', {name: 'Select Namespace'}).click();
    await page.getByRole('option', {name: 'default', exact: true}).click();
    await expect(page.getByText('1 / 1')).toBeVisible();
    await expect(page.getByText('api-gateway', {exact: true})).toBeVisible();
    api.manyServices = false;

    api.emptyCollections.add('configs');
    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    await expect(page.getByText('No configurations in this namespace yet.')).toBeVisible();
    api.emptyCollections.delete('configs');
    await page.reload();

    const configHeader = page.getByRole('button', {name: 'Config ID'});
    await configHeader.click();
    await expect(page.locator('tbody tr').first()).toContainText('application.yaml');
    await configHeader.click();
    await expect(page.locator('tbody tr').first()).toContainText('rate-limiter.yaml');

    api.failNext = {method: 'DELETE', path: /\/configs\/application\.yaml$/};
    const configRow = page.getByRole('row').filter({hasText: 'application.yaml'}).first();
    await configRow.getByRole('button', {name: 'Delete'}).click();
    await page.getByRole('button', {name: 'Continue'}).click();
    await expect(page.getByText('Delete config failed')).toBeVisible();
    expect(browserErrors.get(page)?.some(error => error.includes('500'))).toBe(true);
    browserErrors.set(page, []);

    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'Audit Log', exact: true}).click();
    await expect(page.getByText('14 items')).toBeVisible();
    await page.getByRole('button', {name: 'Next page'}).click();
    await expect(page.getByText('2 / 2')).toBeVisible();
    await expect(page.getByRole('cell', {name: 'service-5'})).toBeVisible();
});

test('route guards, command search, password visibility, and form validation stay operable', async ({page}) => {
    const api = apiMocks.get(page)!;
    await page.goto('/config');
    await expect(page).toHaveURL(/\/login$/);

    const password = page.getByRole('textbox', {name: 'Password', exact: true});
    await expect(password).toHaveAttribute('type', 'password');
    await page.getByRole('button', {name: 'Show password'}).click();
    await expect(password).toHaveAttribute('type', 'text');
    await page.getByRole('button', {name: 'Hide password'}).click();
    await login(page);

    const commandSearch = page.getByRole('searchbox', {name: 'Search navigation'});
    await commandSearch.fill('configuration');
    await commandSearch.press('Enter');
    await expect(page).toHaveURL(/\/config$/);
    await commandSearch.fill('missing-page');
    await page.getByRole('button', {name: 'Go to page'}).click();
    await expect(page.getByText('No page matches “missing-page”.')).toBeVisible();

    await page.getByRole('button', {name: 'Import', exact: true}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Please select a ZIP file.')).toBeVisible();
    await page.getByRole('button', {name: 'Close', exact: true}).click();

    await page.getByRole('button', {name: 'Add', exact: true}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    const configId = page.getByRole('textbox', {name: 'Config ID'});
    await expect(page.locator('#config-file-name-error')).toHaveText('Please enter file name!');
    await expect(configId).toBeFocused();
    await expect(configId).toHaveAttribute('aria-invalid', 'true');
    await page.getByRole('button', {name: 'Close', exact: true}).click();

    api.failNext = {method: 'GET', path: /\/configs\/application\.yaml$/};
    await page.getByRole('button', {name: 'Edit', exact: true}).first().click();
    await expect(page.getByRole('alert')).toContainText('Failed to load this configuration');
    await expect(page.getByRole('button', {name: 'Submit', exact: true})).toBeHidden();
    await page.getByRole('button', {name: 'Retry'}).click();
    await expect(page.getByRole('button', {name: 'Submit', exact: true})).toBeVisible();
    await page.getByRole('button', {name: 'Close', exact: true}).click();
    browserErrors.set(page, []);

    await page.getByRole('link', {name: 'Service', exact: true}).click();
    await page.getByRole('button', {name: 'Add instance', exact: true}).first().click();
    await page.getByRole('textbox', {name: 'Host'}).fill('invalid-metadata.local');
    await page.getByRole('spinbutton', {name: 'Port'}).fill('8080');
    await page.locator('.monaco-editor:visible').click({position: {x: 120, y: 40}});
    await page.keyboard.insertText('invalid');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Metadata must be a valid JSON object.')).toBeVisible();
    await page.getByRole('button', {name: 'Close', exact: true}).click();

    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'Role', exact: true}).click();
    await page.getByRole('button', {name: 'Add Role'}).click();
    await page.getByRole('textbox', {name: 'Role Name'}).fill('incomplete-role');
    await page.getByRole('textbox', {name: 'Description'}).fill('Validation check');
    await page.getByRole('button', {name: 'Add permission'}).click();
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.locator('#role-binding-error')).toHaveText('Complete every permission binding before saving.');
    await expect(page.getByRole('combobox', {name: 'Select Namespace'}).last()).toBeFocused();
    await page.getByRole('button', {name: 'Close', exact: true}).click();

    await page.getByRole('link', {name: 'User', exact: true}).click();
    const currentUserRow = page.getByRole('row').filter({hasText: 'admin'}).first();
    await expect(currentUserRow.getByRole('button', {name: 'Roles for admin'})).toHaveCount(0);
    await expect(currentUserRow.getByRole('cell').nth(1).getByText('admin', {exact: true})).toBeVisible();
    await expect(currentUserRow.getByRole('button', {name: 'Delete'})).toBeDisabled();
    api.failNext = {method: 'POST', path: /\/users\/failing-user$/};
    await page.getByRole('button', {name: 'Add User'}).click();
    await page.getByRole('textbox', {name: 'Username'}).fill('failing-user');
    await page.getByRole('textbox', {name: 'Password'}).fill('secret');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('Failed to add user')).toBeVisible();
    await expect(page.getByRole('dialog', {name: 'Add User'})).toBeVisible();
    expect(api.requests.some(request => request.method === 'PATCH' && request.path.endsWith('/users/failing-user/role'))).toBe(false);
    browserErrors.set(page, []);

    await page.getByRole('textbox', {name: 'Username'}).fill('existing-user');
    await page.getByRole('button', {name: 'Submit', exact: true}).click();
    await expect(page.getByText('User already exists.')).toBeVisible();
    expect(api.requests.some(request => request.method === 'PATCH' && request.path.endsWith('/users/existing-user/role'))).toBe(false);
});

test('mobile layout keeps navigation and dashboard controls usable', async ({page}) => {
    await page.setViewportSize({width: 390, height: 844});
    await login(page);

    await expect(page.getByRole('button', {name: 'Open navigation'})).toBeVisible();
    await expect(page.getByRole('heading', {name: 'Dashboard'})).toBeVisible();
    await expect(page.getByText('Healthy Services')).toBeVisible();
    const healthyCard = await page.getByText('Healthy Services').evaluate(element => element.parentElement?.parentElement?.getBoundingClientRect().toJSON());
    const instancesCard = await page.getByText('Instances', {exact: true}).evaluate(element => element.parentElement?.parentElement?.getBoundingClientRect().toJSON());
    expect(healthyCard?.y).toBe(instancesCard?.y);
    const topology = page.locator('[data-slot="card"]').filter({hasText: 'Service Topology'}).locator('[data-slot="card-content"]');
    expect((await topology.boundingBox())?.height).toBeLessThanOrEqual(400);
    await page.getByRole('button', {name: 'Open navigation'}).click();
    await expect(page.getByRole('button', {name: 'Close navigation'})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Configuration'})).toBeVisible();
    await page.getByRole('button', {name: 'Close navigation'}).click();
    await expect(page.getByRole('button', {name: 'Open navigation'})).toBeVisible();

    await page.getByRole('button', {name: 'Open navigation'}).click();
    await page.getByRole('link', {name: 'Service', exact: true}).click();
    const addInstance = page.getByRole('button', {name: 'Add instance'}).first();
    const deleteService = page.getByRole('button', {name: 'Delete service'}).first();
    await expect(addInstance).toBeInViewport();
    await expect(deleteService).toBeInViewport();
    expect((await addInstance.boundingBox())?.height).toBeGreaterThanOrEqual(40);
    expect((await deleteService.boundingBox())?.height).toBeGreaterThanOrEqual(40);

    await addInstance.click();
    await expect(page.getByRole('dialog', {name: 'Add [api-gateway] Instance'})).toBeVisible();
    await expect(page.getByRole('button', {name: 'Submit', exact: true})).toBeInViewport();
    await page.getByRole('button', {name: 'Close', exact: true}).click();

    const horizontalOverflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
    expect(horizontalOverflow).toBe(false);
});
