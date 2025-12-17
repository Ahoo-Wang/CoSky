import {useQuery} from "@ahoo-wang/fetcher-react";
import {namespaceApiClient} from "../services/clients.ts";

export interface UseNamespacesReturn {
    namespaces: string[],
    loading: boolean,
    refresh: () => void
}

export function useNamespaces(): UseNamespacesReturn {
    const {result: namespaces = [], loading, execute} = useQuery<null, string[]>({
        initialQuery: null,
        execute: () => {
            return namespaceApiClient.getNamespaces()
        }
    })
    return {namespaces, loading, refresh: execute}
}