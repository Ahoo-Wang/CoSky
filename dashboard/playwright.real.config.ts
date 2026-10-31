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

/**
 * The Dashboard as a server hosts it, against that server. Start one with auth on and a bootstrap token:
 * COSKY_REAL_URL and COSKY_REAL_TOKEN say where it is.
 */
export default defineConfig({
    testDir: './tests/ui',
    testMatch: 'real-backend.spec.ts',
    workers: 1,
    retries: process.env.CI ? 1 : 0,
    reporter: 'list',
    timeout: 60_000,
    expect: {
        timeout: 10_000,
    },
    use: {
        baseURL: process.env.COSKY_REAL_URL ?? 'http://localhost:8080',
        channel: process.env.CI ? undefined : 'chrome',
        viewport: {width: 1440, height: 1024},
        serviceWorkers: 'block',
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
    },
});
