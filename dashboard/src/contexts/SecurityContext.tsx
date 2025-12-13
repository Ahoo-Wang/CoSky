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

import React, { createContext, useContext, useCallback } from 'react';
import { tokenStorage } from '../client/tokenStorage';
import { authenticateApiClient } from '../client/clients';

export interface TokenPayload {
  jti: string;
  sub: string;
  roles: string[];
  iat: number;
  exp: number;
}

const USER_UNAUTHORIZED: TokenPayload = {
  jti: "",
  sub: "UNAUTHORIZED",
  roles: [],
  iat: 0,
  exp: 0
};

interface SecurityContextType {
  authenticated: () => boolean;
  getCurrentUser: () => TokenPayload;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => void;
}

const SecurityContext = createContext<SecurityContextType | undefined>(undefined);

export const SecurityProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const parseToken = useCallback((token: string): TokenPayload => {
    const tokenSplit = token.split(".");
    if (tokenSplit.length !== 3) {
      throw Error(`token format error:[${token}]`);
    }
    const payloadStr = atob(tokenSplit[1]);
    return JSON.parse(payloadStr);
  }, []);

  const getCurrentTimeOfSecond = useCallback(() => {
    return Date.now() / 1000;
  }, []);

  const isValidity = useCallback((token: string) => {
    const tokenExp = parseToken(token).exp;
    return tokenExp > getCurrentTimeOfSecond();
  }, [parseToken, getCurrentTimeOfSecond]);

  const authenticated = useCallback(() => {
    const token = tokenStorage.get();
    if (!token || !token.access) {
      return false;
    }
    return isValidity(token.access.token);
  }, [isValidity]);

  const getCurrentUser = useCallback((): TokenPayload => {
    const token = tokenStorage.get();
    if (token && token.access) {
      return parseToken(token.access.token);
    }
    return USER_UNAUTHORIZED;
  }, [parseToken]);

  const signIn = useCallback(async (username: string, password: string) => {
    const response = await authenticateApiClient.login(username, { body: { password } });
    // Convert CompositeToken to JwtCompositeToken format expected by tokenStorage
    const jwtToken = {
      access: {
        token: response.accessToken,
        payload: parseToken(response.accessToken),
      },
      refresh: {
        token: response.refreshToken,
        payload: parseToken(response.refreshToken),
      },
    };
    (tokenStorage as any).tokenSubject.next(jwtToken);
    window.location.href = '/home';
  }, [parseToken]);

  const signOut = useCallback(() => {
    (tokenStorage as any).tokenSubject.next(null);
    window.location.href = '/login';
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
