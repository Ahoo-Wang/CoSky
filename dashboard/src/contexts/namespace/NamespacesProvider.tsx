import type {ReactNode} from "react";
import {useNamespaces} from "../../hooks/useNamespaces.ts";
import {NamespacesContext} from "./NamespacesContext.tsx";

export function NamespacesProvider({children}: { children: ReactNode }) {
    const namespacesReturn = useNamespaces();
    return (
        <NamespacesContext.Provider value={namespacesReturn}>
            {children}
        </NamespacesContext.Provider>
    )
}