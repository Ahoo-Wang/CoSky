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

import { Fetcher, fetcherRegistrar } from '@ahoo-wang/fetcher'
import { CoSecConfigurer, TokenStorage } from '@ahoo-wang/fetcher-cosec'
import {
  AuthenticateApiClient,
  NamespaceApiClient,
  StatApiClient,
  ConfigApiClient,
  ServiceApiClient,
  UserApiClient,
  RoleApiClient,
  AuditLogApiClient
} from '../generated'

export const tokenStorage = new TokenStorage()

const coSecConfigurer = new CoSecConfigurer({
  appId: 'cosky-dashboard',
  tokenStorage
})

const fetcher = new Fetcher()
coSecConfigurer.applyTo(fetcher)
fetcherRegistrar.default = fetcher

export const authenticateApi = new AuthenticateApiClient()
export const namespaceApi = new NamespaceApiClient()
export const statApi = new StatApiClient()
export const configApi = new ConfigApiClient()
export const serviceApi = new ServiceApiClient()
export const userApi = new UserApiClient()
export const roleApi = new RoleApiClient()
export const auditLogApi = new AuditLogApiClient()
