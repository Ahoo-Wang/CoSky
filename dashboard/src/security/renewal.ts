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

import {canRenew, idTokenClaims, renewIdToken} from './oidc';
import {session} from './session';

const MARGIN_SECONDS = 60;
const LIFETIME_SHARE = 3;
const RETRY_MILLISECONDS = 15_000;

/**
 * When to renew a token: a minute before it expires, or sooner if it does not last much longer than that.
 */
export function renewAt(issuedAt: number, expiresAt: number): number {
    return (expiresAt - Math.min(MARGIN_SECONDS, (expiresAt - issuedAt) / LIFETIME_SHARE)) * 1000;
}

let renewing: Promise<boolean> | null = null;

function renew(): Promise<boolean> {
    renewing ??= renewIdToken()
        .then(token => {
            if (token) {
                session.renew(token);
            }
            return !!token;
        })
        .catch(() => false)
        .finally(() => {
            renewing = null;
        });
    return renewing;
}

/**
 * The token for a request. A timer does not run while a computer sleeps, so one may have expired without being renewed.
 */
export async function currentToken(): Promise<string | null> {
    const token = session.token();
    const expiresAt = idTokenClaims(token)?.exp;
    if (typeof expiresAt === 'number' && expiresAt * 1000 <= Date.now() && canRenew()) {
        await renew();
        return session.token();
    }
    return token;
}

/**
 * Keeps the ID token of this tab from expiring, as long as the provider goes along: some keep them for 5 minutes.
 *
 * @return what stops it.
 */
export function startRenewing(): () => void {
    let timer: ReturnType<typeof setTimeout> | undefined;

    const schedule = () => {
        clearTimeout(timer);
        const claims = idTokenClaims(session.token());
        if (!claims || !canRenew()) {
            return;
        }
        const expiresAt = claims.exp as number;
        const issuedAt = (claims.iat as number | undefined) ?? Date.now() / 1000;
        timer = setTimeout(async () => {
            if (await renew()) {
                schedule();
            } else {
                timer = setTimeout(schedule, RETRY_MILLISECONDS);
            }
        }, Math.max(renewAt(issuedAt, expiresAt) - Date.now(), 0));
    };

    const stop = session.subscribe(schedule);
    schedule();
    return () => {
        stop();
        clearTimeout(timer);
    };
}
