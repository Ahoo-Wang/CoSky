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

import {lazy, Suspense} from "react";
import {Navigate, Route, Routes} from "react-router-dom";
import {AuthenticatedLayout} from "@/components/layout/AuthenticatedLayout";
import {ProtectedRoute} from "@/components/security/ProtectedRoute";
import {Skeleton} from "@/components/ui/skeleton";
import {DrawerProvider} from "@/contexts/DrawerProvider";
import {CurrentNamespaceProvider} from "@/contexts/namespace/CurrentNamespaceProvider";
import {NamespacesProvider} from "@/contexts/namespace/NamespacesProvider";

const LoginPage = lazy(() => import("@/pages/login/LoginPage").then(module => ({default: module.LoginPage})));
const DashboardPage = lazy(() => import("@/pages/dashboard/DashboardPage").then(module => ({default: module.DashboardPage})));
const ConfigPage = lazy(() => import("@/pages/config/ConfigPage").then(module => ({default: module.ConfigPage})));
const ServicePage = lazy(() => import("@/pages/service/ServicePage").then(module => ({default: module.ServicePage})));
const NamespacePage = lazy(() => import("@/pages/namespace/NamespacePage").then(module => ({default: module.NamespacePage})));
const ServiceAccountPage = lazy(() => import("@/pages/account/ServiceAccountPage").then(module => ({default: module.ServiceAccountPage})));
const RolePage = lazy(() => import("@/pages/role/RolePage").then(module => ({default: module.RolePage})));
const RoleBindingPage = lazy(() => import("@/pages/binding/RoleBindingPage").then(module => ({default: module.RoleBindingPage})));

export function AppRoutes() {
    return (
        <Suspense fallback={<div className="space-y-3 p-8"><Skeleton className="h-9 w-56"/><Skeleton className="h-64 w-full"/></div>}>
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route
                    path="/"
                    element={
                        <ProtectedRoute>
                            <NamespacesProvider>
                                <DrawerProvider>
                                    <CurrentNamespaceProvider>
                                        <AuthenticatedLayout/>
                                    </CurrentNamespaceProvider>
                                </DrawerProvider>
                            </NamespacesProvider>
                        </ProtectedRoute>
                    }
                >
                    <Route index element={<Navigate to="/home" replace/>}/>
                    <Route path="home" element={<DashboardPage/>}/>
                    <Route path="config" element={<ConfigPage/>}/>
                    <Route path="service" element={<ServicePage/>}/>
                    <Route path="namespace" element={<NamespacePage/>}/>
                    <Route path="service-account" element={<ServiceAccountPage/>}/>
                    <Route path="role" element={<RolePage/>}/>
                    <Route path="role-binding" element={<RoleBindingPage/>}/>
                </Route>
            </Routes>
        </Suspense>
    );
}
