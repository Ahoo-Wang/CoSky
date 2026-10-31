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

import {
    ConfigsApiClient,
    IdentityApiClient,
    InstancesApiClient,
    NamespacesApiClient,
    RoleBindingsApiClient,
    RolesApiClient,
    ServiceAccountsApiClient,
    ViewsApiClient,
    WatchApiClient,
} from '@/generated';

export const configsClient = new ConfigsApiClient();
export const identityClient = new IdentityApiClient();
export const instancesClient = new InstancesApiClient();
export const namespacesClient = new NamespacesApiClient();
export const roleBindingsClient = new RoleBindingsApiClient();
export const rolesClient = new RolesApiClient();
export const serviceAccountsClient = new ServiceAccountsApiClient();
export const viewsClient = new ViewsApiClient();
export const watchClient = new WatchApiClient();
