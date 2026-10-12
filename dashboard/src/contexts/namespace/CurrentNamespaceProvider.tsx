import React, {type ReactNode} from "react";
import {useKeyStorage} from "@ahoo-wang/fetcher-react";
import {currentNamespaceStorage} from "@/contexts/namespace/CurrentNamespaceStorage";
import {SYSTEM_NAMESPACE} from "@/pages/namespace/namespaces";
import type { CurrentNamespaceContextType} from "@/contexts/namespace/CurrentNamespaceContext";
import {CurrentNamespaceContext} from "@/contexts/namespace/CurrentNamespaceContext";

export const CurrentNamespaceProvider: React.FC<{ children: ReactNode }> = ({children}) => {
    const [currentNamespace, setCurrentNamespace] = useKeyStorage(currentNamespaceStorage, SYSTEM_NAMESPACE)

    const reset = () => {
        setCurrentNamespace(SYSTEM_NAMESPACE);
    };


    const value: CurrentNamespaceContextType = {
        currentNamespace: currentNamespace,
        setCurrent: setCurrentNamespace,
        reset,
    };

    return <CurrentNamespaceContext.Provider value={value}>{children}</CurrentNamespaceContext.Provider>;
};