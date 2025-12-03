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

import { createContext, useContext, useState, useEffect, ReactNode } from 'react'
import { namespaceApi } from '../api'

interface NamespaceContextType {
  currentNamespace: string
  namespaces: string[]
  setCurrentNamespace: (namespace: string) => void
  refreshNamespaces: () => Promise<void>
}

const NamespaceContext = createContext<NamespaceContextType | undefined>(undefined)

export function NamespaceProvider({ children }: { children: ReactNode }) {
  const [currentNamespace, setCurrentNamespaceState] = useState<string>(() => {
    return localStorage.getItem('currentNamespace') || 'default'
  })
  const [namespaces, setNamespaces] = useState<string[]>(['default'])

  const refreshNamespaces = async () => {
    try {
      const response = await namespaceApi.getNamespaces()
      setNamespaces(response as string[])
    } catch (error) {
      console.error('Failed to fetch namespaces:', error)
    }
  }

  const handleSetNamespace = async (namespace: string) => {
    try {
      await namespaceApi.setCurrentContextNamespace(namespace)
      localStorage.setItem('currentNamespace', namespace)
      setCurrentNamespaceState(namespace)
    } catch (error) {
      console.error('Failed to set namespace:', error)
    }
  }

  useEffect(() => {
    refreshNamespaces()
  }, [])

  return (
    <NamespaceContext.Provider value={{ 
      currentNamespace, 
      namespaces,
      setCurrentNamespace: handleSetNamespace,
      refreshNamespaces
    }}>
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
