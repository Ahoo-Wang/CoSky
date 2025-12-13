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

import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { notification } from 'antd';

const NAMESPACE_KEY = 'cosky:ns:current';
const SYSTEM_NAMESPACE = 'cosky-{system}';
const SYSTEM_NAMESPACES = new Set(['cosky-{default}', SYSTEM_NAMESPACE]);

interface NamespaceContextType {
  currentNamespace: string;
  setCurrent: (namespace: string) => void;
  ensureCurrentNamespace: () => string;
  isSystem: (namespace: string) => boolean;
  reset: () => void;
}

const NamespaceContext = createContext<NamespaceContextType | undefined>(undefined);

export const NamespaceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [currentNamespace, setCurrentNamespace] = useState<string>(() => {
    const stored = localStorage.getItem(NAMESPACE_KEY);
    return stored || SYSTEM_NAMESPACE;
  });

  useEffect(() => {
    if (!localStorage.getItem(NAMESPACE_KEY)) {
      localStorage.setItem(NAMESPACE_KEY, SYSTEM_NAMESPACE);
    }
  }, []);

  const setCurrent = useCallback((namespace: string) => {
    localStorage.setItem(NAMESPACE_KEY, namespace);
    setCurrentNamespace(namespace);
  }, []);

  const reset = useCallback(() => {
    setCurrent(SYSTEM_NAMESPACE);
  }, [setCurrent]);

  const ensureCurrentNamespace = useCallback((): string => {
    const current = localStorage.getItem(NAMESPACE_KEY);
    if (current) {
      return current;
    }
    notification.error({
      message: 'Namespace Context ERROR',
      description: 'Please Select a namespace.',
    });
    throw new Error('Please Select a namespace.');
  }, []);

  const isSystem = useCallback((namespace: string): boolean => {
    return SYSTEM_NAMESPACES.has(namespace);
  }, []);

  const value: NamespaceContextType = {
    currentNamespace,
    setCurrent,
    ensureCurrentNamespace,
    isSystem,
    reset,
  };

  return <NamespaceContext.Provider value={value}>{children}</NamespaceContext.Provider>;
};

export const useNamespace = (): NamespaceContextType => {
  const context = useContext(NamespaceContext);
  if (!context) {
    throw new Error('useNamespace must be used within a NamespaceProvider');
  }
  return context;
};
