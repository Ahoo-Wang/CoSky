import {useQuery} from "@ahoo-wang/fetcher-react";
import {roleApiClient} from "../services/clients.ts";
import {RoleDto} from "../generated";

export function useRoles() {
    const {result: roles = [], loading, error, execute: load} = useQuery<null, RoleDto[]>({
        initialQuery: null,
        execute: (_, __, abortController) => {
            return roleApiClient.allRole({abortController});
        },
    });
    return {roles, loading, error, load}
}