import {useQuery} from "@ahoo-wang/fetcher-react";
import {namespaceApiClient} from "../services/clients.ts";

export interface UseNamespacesReturn {
    namespaces: string[],
    loading: boolean,
    error?: unknown,
    refresh: () => void
}

export function useNamespaces(): UseNamespacesReturn {
    const {result: namespaces = [], loading, error, execute} = useQuery<null, string[]>({
        initialQuery: null,
        execute: () => {
            return namespaceApiClient.getNamespaces()
        }
    })
    return {namespaces, loading, error, refresh: execute}
}
