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
import type {ServiceInstance} from '../../src/generated';
import {getInstanceHealth} from '../../src/pages/service/serviceHealth.ts';

const now = Date.UTC(2026, 7, 18, 1, 0, 0);
const instance = (overrides: Partial<ServiceInstance> = {}): ServiceInstance => ({
    isEphemeral: true,
    metadata: {instance_status: 'UP'},
    weight: 1,
    ttlAt: Math.floor(now / 1000) + 3600,
    isExpired: false,
    host: '127.0.0.1',
    port: 8080,
    schema: 'http',
    instanceId: 'instance-1',
    serviceId: 'service-1',
    uri: 'http://127.0.0.1:8080',
    secure: false,
    ...overrides,
});

test('derives actionable instance health from status and TTL', () => {
    expect(getInstanceHealth(instance(), now)).toBe('Healthy');
    expect(getInstanceHealth(instance({ttlAt: Math.floor(now / 1000) + 60}), now)).toBe('Expiring soon');
    expect(getInstanceHealth(instance({isExpired: true}), now)).toBe('Expired');
    expect(getInstanceHealth(instance({metadata: {instance_status: 'DOWN'}}), now)).toBe('Unhealthy');
});

test('does not expire persistent instances from TTL alone', () => {
    expect(getInstanceHealth(instance({isEphemeral: false, ttlAt: 0}), now)).toBe('Healthy');
});
