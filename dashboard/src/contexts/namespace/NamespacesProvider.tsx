import type {ReactNode} from "react";
import {useNamespaces} from "@/hooks/useNamespaces";
import {NamespacesContext} from "@/contexts/namespace/NamespacesContext";

export function NamespacesProvider({children}: { children: ReactNode }) {
    const namespacesReturn = useNamespaces();
    return (
        <NamespacesContext.Provider value={namespacesReturn}>
            {children}
        </NamespacesContext.Provider>
    )
}