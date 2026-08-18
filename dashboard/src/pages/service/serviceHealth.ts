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

import type {ServiceInstance} from '../../generated';

export type InstanceHealth = 'Healthy' | 'Expiring soon' | 'Expired' | 'Unhealthy';

export function getInstanceHealth(instance: ServiceInstance, now = Date.now()): InstanceHealth {
    const reportedStatus = instance.metadata.instance_status?.toUpperCase();
    if (reportedStatus && reportedStatus !== 'UP') return 'Unhealthy';
    if (instance.isExpired || (instance.isEphemeral && instance.ttlAt * 1000 <= now)) return 'Expired';
    if (instance.isEphemeral && instance.ttlAt * 1000 - now <= 5 * 60 * 1000) return 'Expiring soon';
    return 'Healthy';
}
