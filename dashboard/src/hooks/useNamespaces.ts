import {useQuery} from "@ahoo-wang/fetcher-react";
import {namespaceApiClient} from "../services/clients.ts";

export function useNamespaces() {
    const {result: namespaces = [], loading, error, execute} = useQuery<number, string[]>({
        initialQuery: 0,
        execute: () => {
            return namespaceApiClient.getNamespaces()
        }
    })
    return {namespaces, loading, error, reload: execute}
}