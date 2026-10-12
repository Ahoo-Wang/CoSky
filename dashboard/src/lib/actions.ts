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

import {ExchangeError} from '@ahoo-wang/fetcher';
import {toast} from 'sonner';
import type {ErrorResponse} from '@/generated';

/**
 * The reason the REST API gave for a failed request (`{"code", "msg"}`), when there is one.
 */
export async function errorReason(error: unknown): Promise<string | undefined> {
    if (!(error instanceof ExchangeError)) return undefined;
    try {
        const response = await error.exchange.requiredResponse.json<ErrorResponse>();
        return response.msg || undefined;
    } catch {
        return undefined;
    }
}

/**
 * The message to show for a failed operation: the summary, followed by the server's reason when it gave one.
 */
export async function failureMessage(summary: string, error: unknown): Promise<string> {
    const reason = await errorReason(error);
    return reason ? `${summary}: ${reason}` : summary;
}

export interface ActionMessages {
    success: string;
    failure: string;
}

/**
 * Runs an operation and reports its outcome in a toast.
 *
 * @return whether the operation succeeded.
 */
export async function runAction(operation: () => Promise<unknown>, messages: ActionMessages): Promise<boolean> {
    try {
        await operation();
        toast.success(messages.success);
        return true;
    } catch (error) {
        toast.error(await failureMessage(messages.failure, error));
        return false;
    }
}
