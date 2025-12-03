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

import { createContext, useContext, useState, ReactNode } from 'react'

interface NamespaceContextType {
  currentNamespace: string
  setCurrentNamespace: (namespace: string) => void
}

const NamespaceContext = createContext<NamespaceContextType | undefined>(undefined)

export function NamespaceProvider({ children }: { children: ReactNode }) {
  const [currentNamespace, setCurrentNamespace] = useState<string>(() => {
    return localStorage.getItem('currentNamespace') || 'default'
  })

  const handleSetNamespace = (namespace: string) => {
    localStorage.setItem('currentNamespace', namespace)
    setCurrentNamespace(namespace)
  }

  return (
    <NamespaceContext.Provider value={{ currentNamespace, setCurrentNamespace: handleSetNamespace }}>
      {children}
    </NamespaceContext.Provider>
  )
}

export function useNamespace() {
  const context = useContext(NamespaceContext)
  if (context === undefined) {
    throw new Error('useNamespace must be used within a NamespaceProvider')
  }
  return context
}
