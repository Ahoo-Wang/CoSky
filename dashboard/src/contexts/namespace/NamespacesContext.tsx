import type { UseNamespacesReturn} from "../../hooks/useNamespaces.ts";
import {useNamespaces} from "../../hooks/useNamespaces.ts";
import type { ReactNode} from "react";
import {createContext} from "react";

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