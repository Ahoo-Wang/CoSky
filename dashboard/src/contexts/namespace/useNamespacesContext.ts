import {useContext} from "react";
import {NamespacesContext} from "./NamespacesContext.tsx";

export function useNamespacesContext() {
    const context = useContext(NamespacesContext);
    if (context === undefined) {
        throw new Error("useNamespacesContext must be used within a NamespacesProvider");
    }
    return context;
}