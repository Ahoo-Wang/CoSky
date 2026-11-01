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

import {useSyncExternalStore} from 'react';
import type {ReactNode} from 'react';
import {Navigate} from 'react-router-dom';
import {ExchangeError} from '@ahoo-wang/fetcher';
import {useQuery} from '@ahoo-wang/fetcher-react';
import type {MeResponse} from '@/generated';
import {Button} from '@/components/ui/button';
import {Skeleton} from '@/components/ui/skeleton';
import {MeContext} from '@/contexts/MeContext';
import {session} from '@/security/session';
import {identityClient} from '@/services/clients';

const UNAUTHORIZED = 401;

/**
 * Lets through whoever the server accepts, which without auth is everyone (design D34).
 */
export function ProtectedRoute({children}: {children: ReactNode}) {
    // Not by the token: renewing that must not take down the page someone is working on.
    const signIns = useSyncExternalStore(session.subscribe, session.signIns);
    const {result: me, error, execute: retry} = useQuery<number, MeResponse>({
        query: signIns,
        execute: (_, __, abortController) => identityClient.getMe({abortController}),
    });
    if (error instanceof ExchangeError && error.exchange.response?.status === UNAUTHORIZED) {
        return <Navigate to="/login" replace/>;
    }
    if (error) {
        return (
            <div role="alert" className="space-y-4 p-8 text-sm">
                <p>The server cannot be reached.</p>
                <Button variant="outline" onClick={() => retry()}>Retry</Button>
            </div>
        );
    }
    if (!me) {
        return <div className="space-y-3 p-8"><Skeleton className="h-9 w-56"/><Skeleton className="h-64 w-full"/></div>;
    }
    return <MeContext.Provider value={me}>{children}</MeContext.Provider>;
}
