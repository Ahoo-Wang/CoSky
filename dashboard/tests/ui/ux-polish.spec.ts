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

test('collapsed sidebar drops the inert Security group toggle and keeps icons distinguishable', async ({page}) => {
    await login(page);
    await page.getByRole('button', {name: 'Collapse navigation'}).click();

    // The Security collapsible renders no content when collapsed — its trigger must not stay behind as a dead button.
    await expect(page.getByRole('button', {name: 'Security', exact: true})).toHaveCount(0);
    await expect(page.getByRole('link', {name: 'User', exact: true})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Role', exact: true})).toBeVisible();
    await expect(page.getByRole('link', {name: 'Audit Log', exact: true})).toBeVisible();

    // Configuration/Audit Log and Security/Role must not share identical icons in the icon-only rail.
    const iconOf = (name: string) =>
        page.getByRole('link', {name, exact: true}).locator('svg').first().evaluate(element => element.innerHTML);
    expect(await iconOf('Configuration')).not.toBe(await iconOf('Audit Log'));
});

test('definition list wraps long unbroken values instead of clipping them', async ({page}) => {
    await login(page);
    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    await page.getByRole('button', {name: 'Expand row'}).first().click();
    await page.getByRole('table').nth(1).getByRole('button', {name: 'Diff', exact: true}).first().click();

    const differ = page.getByRole('dialog', {name: 'Config Version Differ'});
    // The mock serves a realistic 64-char hash — a single unbroken word that must
    // wrap within the definition cell instead of overflowing the overflow-hidden list.
    const hashCell = differ.getByText(/^[0-9a-f]{64}$/);
    const metrics = await hashCell.evaluate(element => {
        const cellRect = element.getBoundingClientRect();
        const listRect = element.closest('dl')!.getBoundingClientRect();
        return {cellRight: cellRect.right, listRight: listRect.right};
    });
    expect(metrics.cellRight).toBeLessThanOrEqual(metrics.listRight + 1);
});

test('command palette shortcuts stay inert inside the native topology dialog', async ({page}) => {
    await login(page);
    const search = page.getByRole('combobox', {name: 'Search navigation'});
    await expect(search).toBeVisible();

    await page.getByRole('button', {name: 'Open topology fullscreen'}).click();
    const dialog = page.getByRole('dialog', {name: 'Service Topology'});
    await expect(dialog).toBeVisible();

    const paletteState = () => page.evaluate(() => {
        const searchInput = document.querySelector('input[aria-label="Search navigation"]');
        return {
            ariaExpanded: searchInput?.getAttribute('aria-expanded'),
            searchFocused: document.activeElement === searchInput,
        };
    });

    // Focus a non-input control inside the native <dialog>, then try both shortcuts.
    await page.getByRole('button', {name: 'Close topology fullscreen'}).focus();
    await page.keyboard.press('/');
    await page.keyboard.press('Control+k');

    const state = await paletteState();
    expect(state.ariaExpanded).toBe('false');
    expect(state.searchFocused).toBe(false);
});

test('command palette shortcuts do not steal focus from open dialogs', async ({page}) => {
    await login(page);
    const search = page.getByRole('combobox', {name: 'Search navigation'});
    await expect(search).toBeVisible();

    await page.getByRole('link', {name: 'Configuration', exact: true}).click();
    await page.getByRole('button', {name: 'Add', exact: true}).click();
    const dialog = page.getByRole('dialog', {name: 'Add Config'});
    await expect(dialog).toBeVisible();

    // Elements outside an open dialog are aria-hidden, so assert via DOM attributes instead of role locators.
    const paletteState = () => page.evaluate(() => {
        const searchInput = document.querySelector('input[aria-label="Search navigation"]');
        return {
            ariaExpanded: searchInput?.getAttribute('aria-expanded'),
            searchFocused: document.activeElement === searchInput,
            activeElementId: document.activeElement?.id ?? null,
        };
    });

    // Ctrl+K while typing in a dialog field must not move focus to the header search.
    await page.locator('#config-file-name').click();
    await page.keyboard.press('Control+k');
    let state = await paletteState();
    expect(state.searchFocused).toBe(false);
    expect(state.ariaExpanded).toBe('false');
    expect(state.activeElementId).toBe('config-file-name');

    // '/' with focus on a non-input control inside a dialog must not open the palette behind the modal.
    const closeButton = dialog.getByRole('button', {name: 'Close', exact: true});
    await closeButton.focus();
    await page.keyboard.press('/');
    state = await paletteState();
    expect(state.ariaExpanded).toBe('false');
    expect(await closeButton.evaluate(element => document.activeElement === element)).toBe(true);
});

test('audit event details keep the timestamp on a single line', async ({page}) => {
    await login(page);
    await page.getByRole('button', {name: 'Security', exact: true}).click();
    await page.getByRole('link', {name: 'Audit Log', exact: true}).click();
    await page.getByRole('button', {name: 'Details'}).first().click();

    const dialog = page.getByRole('dialog', {name: 'Audit Event Details'});
    // Seconds precision matches the audit table and always fits the details cell on one line.
    const timestamp = dialog.getByText(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/);
    const metrics = await timestamp.evaluate(element => {
        const style = getComputedStyle(element);
        return {
            wordBreak: style.wordBreak,
            height: element.getBoundingClientRect().height,
            lineHeight: parseFloat(style.lineHeight),
        };
    });
    expect(metrics.wordBreak).not.toBe('break-all');
    expect(metrics.height).toBeLessThanOrEqual(metrics.lineHeight + 2);
});

test('topology hides the minimap on small screens', async ({page}) => {
    await login(page);
    await expect(page.locator('.react-flow__minimap')).toBeVisible();
    await page.setViewportSize({width: 390, height: 844});
    await expect(page.locator('.react-flow__minimap')).toBeHidden();
});

test('command palette supports Ctrl+K and Escape clears the query', async ({page}) => {
    await login(page);
    const search = page.getByRole('combobox', {name: 'Search navigation'});
    // Let the layout effects settle before relying on the global shortcut listener.
    await expect(search).toBeVisible();

    await page.keyboard.press('Control+k');
    await expect(search).toBeFocused();
    await expect(page.getByRole('listbox', {name: 'Navigation results'})).toBeVisible();

    await search.fill('serv');
    await search.press('Escape');
    await expect(page.getByRole('listbox', {name: 'Navigation results'})).toBeHidden();
    await expect(search).toHaveValue('');

    // Reopening starts from a clean slate instead of the stale query.
    await page.keyboard.press('/');
    await expect(search).toBeFocused();
    await expect(search).toHaveValue('');
});

test('service views use scheme naming, HTTP option labels, and input placeholders', async ({page}) => {
    await login(page);
    await page.getByRole('link', {name: 'Service', exact: true}).click();

    await page.getByRole('button', {name: 'Expand row'}).first().click();
    const instanceTable = page.getByRole('table').nth(1);
    await expect(instanceTable.getByRole('columnheader', {name: 'Scheme', exact: true})).toBeVisible();

    await page.getByRole('button', {name: 'Add instance', exact: true}).first().click();
    const dialog = page.getByRole('dialog', {name: 'Add [api-gateway] Instance'});
    await dialog.getByRole('combobox', {name: 'Scheme'}).click();
    await expect(page.getByRole('option', {name: 'HTTP', exact: true})).toBeVisible();
    await expect(page.getByRole('option', {name: 'HTTPS', exact: true})).toBeVisible();
    await page.keyboard.press('Escape');

    await expect(dialog.getByRole('textbox', {name: 'Host'})).toHaveAttribute('placeholder', /./);
    await expect(dialog.getByRole('spinbutton', {name: 'Port'})).toHaveAttribute('placeholder', /./);
});
