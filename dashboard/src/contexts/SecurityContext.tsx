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

import React, {createContext, useContext, useCallback} from 'react';
import {tokenStorage} from '../client/tokenStorage';
import {authenticateApiClient} from '../client/clients';
import {JwtPayload} from "@ahoo-wang/fetcher-cosec";

const USER_UNAUTHORIZED: JwtPayload = {
    jti: "",
    sub: "UNAUTHORIZED",
    roles: [],
    iat: 0,
    exp: 0
};

interface SecurityContextType {
    authenticated: () => boolean;
    getCurrentUser: () => JwtPayload;
    signIn: (username: string, password: string) => Promise<void>;
    signOut: () => void;
}

const SecurityContext = createContext<SecurityContextType | undefined>(undefined);

export const SecurityProvider: React.FC<{ children: React.ReactNode }> = ({children}) => {
    const signIn = useCallback(async (username: string, password: string) => {
        const responseToken = await authenticateApiClient.login(username, {body: {password}});
        tokenStorage.setCompositeToken(responseToken)
        // Use window.location for navigation to ensure proper authentication state reset
        window.location.href = '/home';
    }, []);

    const signOut = useCallback(() => {
        tokenStorage.remove()
        window.location.href = '/login';
    }, []);
    const authenticated = useCallback(() => {
        const token = tokenStorage.get();
        if (!token || !token.access) {
            return false;
        }
        return !token.access.isExpired
    }, []);
    const getCurrentUser = useCallback((): JwtPayload => {
        const token = tokenStorage.get();
        if (token && token.access) {
            return token.access.payload!!
        }
        return USER_UNAUTHORIZED;
    }, []);
    const value: SecurityContextType = {
        authenticated,
        getCurrentUser,
        signIn,
        signOut,
    };

    return <SecurityContext.Provider value={value}>{children}</SecurityContext.Provider>;
};

export const useSecurity = (): SecurityContextType => {
    const context = useContext(SecurityContext);
    if (!context) {
        throw new Error('useSecurity must be used within a SecurityProvider');
    }
    return context;
};
