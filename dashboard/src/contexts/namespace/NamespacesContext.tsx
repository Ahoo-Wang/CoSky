import {useNamespaces, UseNamespacesReturn} from "../../hooks/useNamespaces.ts";
import {createContext, ReactNode, useContext} from "react";

export type NamespacesContextType = UseNamespacesReturn

export const NamespacesContext = createContext<NamespacesContextType | undefined>(undefined);

export function NamespacesProvider({children}: { children: ReactNode }) {
    const namespacesReturn = useNamespaces();
    return (
        <NamespacesContext.Provider value={namespacesReturn}>
            {children}
        </NamespacesContext.Provider>
    )
}

export function useNamespacesContext() {
    const context = useContext(NamespacesContext);
    if (context === undefined) {
        throw new Error("useNamespacesContext must be used within a NamespacesProvider");
    }
    return context;
}