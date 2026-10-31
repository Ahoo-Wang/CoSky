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

const TOKEN_KEY = 'cosky.token';

const listeners = new Set<() => void>();

const changed = () => listeners.forEach(listener => listener());

/**
 * The token of this tab: a ServiceAccount token or an OIDC ID token. It ends with the tab, and the server decides
 * whether it is still good.
 */
export const session = {
    token(): string | null {
        return sessionStorage.getItem(TOKEN_KEY);
    },
    signIn(token: string) {
        sessionStorage.setItem(TOKEN_KEY, token);
        changed();
    },
    signOut() {
        sessionStorage.removeItem(TOKEN_KEY);
        changed();
    },
    subscribe(listener: () => void): () => void {
        listeners.add(listener);
        return () => listeners.delete(listener);
    },
};
