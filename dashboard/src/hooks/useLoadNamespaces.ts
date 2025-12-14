import {useExecutePromise} from "@ahoo-wang/fetcher-react";
import {useEffect} from "react";
import {namespaceApiClient} from "../client/clients.ts";

export function useLoadNamespaces() {
    const {result: namespaces = [], loading, error, execute} = useExecutePromise<string[]>()
    const load = () => execute(
        () => namespaceApiClient.getNamespaces()
    )
    useEffect(() => {
        load()
    }, []);
    return {namespaces, loading, error, load}
}