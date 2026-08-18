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

import {defineConfig} from '@playwright/test';

const baseURL = 'http://127.0.0.1:4175';
const apiBaseURL = process.env.COSKY_REAL_API_URL ?? `${baseURL}/`;

export default defineConfig({
    testDir: './tests/ui',
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
    reporter: [
        ['list'],
        ['./tests/ui/coverage-reporter.ts'],
    ],
    timeout: 30_000,
    expect: {
        timeout: 5_000,
    },
    use: {
        baseURL,
        channel: process.env.CI ? undefined : 'chrome',
        viewport: {width: 1440, height: 1024},
        serviceWorkers: 'block',
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
    },
    webServer: {
        command: 'pnpm dev --host 127.0.0.1 --port 4175 --strictPort',
        url: baseURL,
        reuseExistingServer: false,
        timeout: 120_000,
        env: {
            VITE_API_BASE_URL: apiBaseURL,
        },
    },
});
