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

const ILLEGAL_NAME_CHARACTER = /[^A-Za-z0-9._-]/g;
const MAX_NAME_LENGTH = 253;

/**
 * As the client names an instance (`NamingRules.defaultInstanceName`), so that one endpoint has one name.
 */
export const defaultInstanceName = (service: string, host: string, port: string | number) =>
    `${service}-${host}-${port}`.replace(ILLEGAL_NAME_CHARACTER, '-').slice(0, MAX_NAME_LENGTH);
