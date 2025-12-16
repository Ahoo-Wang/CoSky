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

import {BrowserRouter, Routes, Route, Navigate} from 'react-router-dom';
import {ConfigProvider} from 'antd';
import {SecurityProvider} from '@ahoo-wang/fetcher-react'
import {NamespaceProvider} from './contexts/NamespaceContext.tsx';
import {DrawerProvider} from './contexts/DrawerContext';
import {LoginPage} from './pages/login/LoginPage.tsx';
import {AuthenticatedLayout} from './components/layout/AuthenticatedLayout';
import {DashboardPage} from './pages/dashboard/DashboardPage.tsx';
import {ConfigPage} from './pages/config/ConfigPage.tsx';
import {ServicePage} from './pages/service/ServicePage.tsx';
import {NamespacePage} from './pages/namespace/NamespacePage.tsx';
import {UserPage} from './pages/user/UserPage.tsx';
import {RolePage} from './pages/role/RolePage.tsx';
import {AuditLogPage} from './pages/audit/AuditLogPage.tsx';
import './services/fetcher'
import {tokenStorage} from "./security/tokenStorage.ts";
import ErrorBoundary from "antd/es/alert/ErrorBoundary";

function App() {
    return (
        <ConfigProvider>
            <ErrorBoundary>
                <SecurityProvider tokenStorage={tokenStorage}
                                  onSignIn={() => {
                                      window.location.href = '/home';
                                  }}
                                  onSignOut={() => {
                                      window.location.href = '/login';
                                  }}
                >
                    <NamespaceProvider>
                        <DrawerProvider>
                            <BrowserRouter>
                                <Routes>
                                    <Route path="/login" element={<LoginPage/>}/>
                                    <Route
                                        path="/"
                                        element={
                                            <AuthenticatedLayout/>
                                        }
                                    >
                                        <Route index element={<Navigate to="/home" replace/>}/>
                                        <Route path="home" element={<DashboardPage/>}/>
                                        <Route path="config" element={<ConfigPage/>}/>
                                        <Route path="service" element={<ServicePage/>}/>
                                        <Route path="namespace" element={<NamespacePage/>}/>
                                        <Route path="user" element={<UserPage/>}/>
                                        <Route path="role" element={<RolePage/>}/>
                                        <Route path="audit-log" element={<AuditLogPage/>}/>
                                    </Route>
                                </Routes>
                            </BrowserRouter>
                        </DrawerProvider>
                    </NamespaceProvider>
                </SecurityProvider>
            </ErrorBoundary>
        </ConfigProvider>
    );
}

export default App;
