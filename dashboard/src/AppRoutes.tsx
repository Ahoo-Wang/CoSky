import {Navigate, Route, Routes, useNavigate} from "react-router-dom";
import {lazy, Suspense} from "react";
import {Spin} from "antd";
import {SecurityProvider} from "@ahoo-wang/fetcher-react";
import {tokenStorage} from "./security/tokenStorage.ts";
import {CurrentNamespaceProvider} from "./contexts/namespace/CurrentNamespaceContext.tsx";
import {DrawerProvider} from "./contexts/DrawerContext.tsx";
import {AuthenticatedLayout} from "./components/layout/AuthenticatedLayout.tsx";
import {ProtectedRoute} from "./components/security/ProtectedRoute.tsx";
import {NamespacesProvider} from "./contexts/namespace/NamespacesContext.tsx";

const LoginPage = lazy(() => import("./pages/login/LoginPage.tsx").then(module => ({default: module.LoginPage})));
const DashboardPage = lazy(() => import("./pages/dashboard/DashboardPage.tsx").then(module => ({default: module.DashboardPage})));
const ConfigPage = lazy(() => import("./pages/config/ConfigPage.tsx").then(module => ({default: module.ConfigPage})));
const ServicePage = lazy(() => import("./pages/service/ServicePage.tsx").then(module => ({default: module.ServicePage})));
const NamespacePage = lazy(() => import("./pages/namespace/NamespacePage.tsx").then(module => ({default: module.NamespacePage})));
const UserPage = lazy(() => import("./pages/user/UserPage.tsx").then(module => ({default: module.UserPage})));
const RolePage = lazy(() => import("./pages/role/RolePage.tsx").then(module => ({default: module.RolePage})));
const AuditLogPage = lazy(() => import("./pages/audit/AuditLogPage.tsx").then(module => ({default: module.AuditLogPage})));

export function AppRoutes() {
    const navigate = useNavigate();
    return (
        <SecurityProvider tokenStorage={tokenStorage}
                          onSignIn={() => {
                              navigate('/home')
                          }}
                          onSignOut={() => {
                              navigate('/login')
                          }}
        >
            <Suspense fallback={<Spin size="large"/>}>
                <Routes>
                    <Route path="/login" element={<LoginPage/>}/>
                    <Route
                        path="/"
                        element={
                            <NamespacesProvider>
                                <DrawerProvider>
                                    <CurrentNamespaceProvider>
                                        <ProtectedRoute>
                                            <AuthenticatedLayout/>
                                        </ProtectedRoute>
                                    </CurrentNamespaceProvider>
                                </DrawerProvider>
                            </NamespacesProvider>
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
            </Suspense>

        </SecurityProvider>
    );
}