import {Navigate, Route, Routes, useNavigate} from "react-router-dom";
import {SecurityProvider} from "@ahoo-wang/fetcher-react";
import {tokenStorage} from "./security/tokenStorage.ts";
import {CurrentNamespaceProvider} from "./contexts/namespace/CurrentNamespaceContext.tsx";
import {DrawerProvider} from "./contexts/DrawerContext.tsx";
import {LoginPage} from "./pages/login/LoginPage.tsx";
import {AuthenticatedLayout} from "./components/layout/AuthenticatedLayout.tsx";
import {DashboardPage} from "./pages/dashboard/DashboardPage.tsx";
import {ConfigPage} from "./pages/config/ConfigPage.tsx";
import {ServicePage} from "./pages/service/ServicePage.tsx";
import {NamespacePage} from "./pages/namespace/NamespacePage.tsx";
import {UserPage} from "./pages/user/UserPage.tsx";
import {RolePage} from "./pages/role/RolePage.tsx";
import {AuditLogPage} from "./pages/audit/AuditLogPage.tsx";
import {ProtectedRoute} from "./components/security/ProtectedRoute.tsx";
import {NamespacesProvider} from "./contexts/namespace/NamespacesContext.tsx";

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
            <Routes>
                <Route path="/login" element={<LoginPage/>}/>
                <Route
                    path="/"
                    element={
                        <DrawerProvider>
                            <NamespacesProvider>
                                <CurrentNamespaceProvider>
                                    <ProtectedRoute>
                                        <AuthenticatedLayout/>
                                    </ProtectedRoute>
                                </CurrentNamespaceProvider>
                            </NamespacesProvider>
                        </DrawerProvider>
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

        </SecurityProvider>
    );
}