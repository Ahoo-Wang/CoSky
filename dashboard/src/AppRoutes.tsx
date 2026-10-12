import {Navigate, Route, Routes, useNavigate} from "react-router-dom";
import {lazy, Suspense} from "react";
import {SecurityProvider} from "@ahoo-wang/fetcher-react";
import {tokenStorage} from "@/security/tokenStorage";
import {AuthenticatedLayout} from "@/components/layout/AuthenticatedLayout";
import {ProtectedRoute} from "@/components/security/ProtectedRoute";
import {CurrentNamespaceProvider} from "@/contexts/namespace/CurrentNamespaceProvider";
import {DrawerProvider} from "@/contexts/DrawerProvider";
import {NamespacesProvider} from "@/contexts/namespace/NamespacesProvider";
import {Skeleton} from "@/components/ui/skeleton";

const LoginPage = lazy(() => import("@/pages/login/LoginPage").then(module => ({default: module.LoginPage})));
const DashboardPage = lazy(() => import("@/pages/dashboard/DashboardPage").then(module => ({default: module.DashboardPage})));
const ConfigPage = lazy(() => import("@/pages/config/ConfigPage").then(module => ({default: module.ConfigPage})));
const ServicePage = lazy(() => import("@/pages/service/ServicePage").then(module => ({default: module.ServicePage})));
const NamespacePage = lazy(() => import("@/pages/namespace/NamespacePage").then(module => ({default: module.NamespacePage})));
const UserPage = lazy(() => import("@/pages/user/UserPage").then(module => ({default: module.UserPage})));
const RolePage = lazy(() => import("@/pages/role/RolePage").then(module => ({default: module.RolePage})));
const AuditLogPage = lazy(() => import("@/pages/audit/AuditLogPage").then(module => ({default: module.AuditLogPage})));

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
            <Suspense fallback={<div className="space-y-3 p-8"><Skeleton className="h-9 w-56"/><Skeleton className="h-64 w-full"/></div>}>
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
