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

import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ConfigProvider } from 'antd'
import AuthenticatedLayout from './components/AuthenticatedLayout'
import Login from './pages/Login'
import Home from './pages/Home'
import Topology from './pages/Topology'
import Config from './pages/Config'
import Service from './pages/Service'
import Namespace from './pages/Namespace'
import User from './pages/User'
import Role from './pages/Role'
import AuditLog from './pages/AuditLog'
import { AuthProvider } from './contexts/AuthContext'
import { NamespaceProvider } from './contexts/NamespaceContext'

function App() {
  return (
    <ConfigProvider>
      <AuthProvider>
        <NamespaceProvider>
          <BrowserRouter basename="/dashboard">
            <Routes>
              <Route path="/login" element={<Login />} />
              <Route path="/" element={<AuthenticatedLayout />}>
                <Route index element={<Navigate to="/home" replace />} />
                <Route path="home" element={<Home />} />
                <Route path="topology" element={<Topology />} />
                <Route path="config" element={<Config />} />
                <Route path="service" element={<Service />} />
                <Route path="namespace" element={<Namespace />} />
                <Route path="user" element={<User />} />
                <Route path="role" element={<Role />} />
                <Route path="audit-log" element={<AuditLog />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </NamespaceProvider>
      </AuthProvider>
    </ConfigProvider>
  )
}

export default App
