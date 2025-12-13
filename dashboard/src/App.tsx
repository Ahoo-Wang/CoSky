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
import {SecurityProvider} from './contexts/SecurityContext';
import {NamespaceProvider} from './contexts/NamespaceContext';
import {ProtectedRoute} from './components/common/ProtectedRoute';
import {LoginPage} from './components/pages/LoginPage';
import {AuthenticatedLayout} from './components/layout/AuthenticatedLayout';
import {DashboardPage} from './components/pages/DashboardPage';
import {TopologyPage} from './components/pages/TopologyPage';
import {ConfigPage} from './components/pages/ConfigPage';
import {ServicePage} from './components/pages/ServicePage';
import {NamespacePage} from './components/pages/NamespacePage';
import {UserPage} from './components/pages/UserPage';
import {RolePage} from './components/pages/RolePage';
import {AuditLogPage} from './components/pages/AuditLogPage';
import './client/fetcher'

function App() {
    return (
        <ConfigProvider>
            <SecurityProvider>
                <NamespaceProvider>
                    <BrowserRouter>
                        <Routes>
                            <Route path="/login" element={<LoginPage/>}/>
                            <Route
                                path="/"
                                element={
                                    <ProtectedRoute>
                                        <AuthenticatedLayout/>
                                    </ProtectedRoute>
                                }
                            >
                                <Route index element={<Navigate to="/home" replace/>}/>
                                <Route path="home" element={<DashboardPage/>}/>
                                <Route path="topology" element={<TopologyPage/>}/>
                                <Route path="config" element={<ConfigPage/>}/>
                                <Route path="service" element={<ServicePage/>}/>
                                <Route path="namespace" element={<NamespacePage/>}/>
                                <Route path="user" element={<UserPage/>}/>
                                <Route path="role" element={<RolePage/>}/>
                                <Route path="audit-log" element={<AuditLogPage/>}/>
                            </Route>
                        </Routes>
                    </BrowserRouter>
                </NamespaceProvider>
            </SecurityProvider>
        </ConfigProvider>
    );
}

export default App;
