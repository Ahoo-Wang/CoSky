import type {UseNamespacesReturn} from "../../hooks/useNamespaces.ts";
import {createContext, useContext} from "react";

export type NamespacesContextType = UseNamespacesReturn

export const NamespacesContext = createContext<NamespacesContextType | undefined>(undefined);

export function useNamespacesContext() {
    const context = useContext(NamespacesContext);
    if (context === undefined) {
        throw new Error("useNamespacesContext must be used within a NamespacesProvider");
    }
    return context;
}