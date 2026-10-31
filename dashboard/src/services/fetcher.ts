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

import {fetcher} from '@ahoo-wang/fetcher';
import {session} from '@/security/session';

const UNAUTHORIZED = 401;

fetcher.timeout = 1000 * 60 * 2;

fetcher.interceptors.request.use({
    name: 'cosky-token',
    order: 0,
    intercept(exchange) {
        const token = session.token();
        if (token) {
            exchange.ensureRequestHeaders()['Authorization'] = `Bearer ${token}`;
        }
    },
});

// A token the server stopped accepting is of no use. Dropping it sends the page back to sign in.
fetcher.interceptors.error.use({
    name: 'cosky-expired-token',
    order: 0,
    intercept(exchange) {
        if (exchange.response?.status === UNAUTHORIZED && session.token()) {
            session.signOut();
        }
    },
});
