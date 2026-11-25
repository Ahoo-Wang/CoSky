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

/**
 * The address of the Dashboard given the base URL of its page: the directory the page is in, without its file name,
 * query or fragment. That is `https://example.com/` when the server hosts it, and `https://example.com/cosky/` behind
 * a reverse proxy that serves it under `/cosky/`. The API is under the same address, so the page works under any
 * prefix without being built again.
 */
export function dashboardBaseOf(documentBaseUri: string): string {
    return new URL('.', documentBaseUri).href;
}

/**
 * The address of this Dashboard: see [dashboardBaseOf].
 */
export function dashboardBase(): string {
    return dashboardBaseOf(document.baseURI);
}
