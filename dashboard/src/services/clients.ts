import {
    AuditLogApiClient,
    AuthenticateApiClient, ConfigApiClient,
    NamespaceApiClient,
    RoleApiClient,
    ServiceApiClient,
    StatApiClient,
    UserApiClient
} from "../generated";
import {createExecuteApiHooks} from "@ahoo-wang/fetcher-react";
import {ExchangeError} from "@ahoo-wang/fetcher";

export const authenticateApiClient = new AuthenticateApiClient();
export const authenticateApiHooks = createExecuteApiHooks<AuthenticateApiClient, ExchangeError>({api: authenticateApiClient})

export const userApiClient = new UserApiClient();

export const namespaceApiClient = new NamespaceApiClient();

export const auditLogApiClient = new AuditLogApiClient();

export const statApiClient = new StatApiClient();

export const roleApiClient = new RoleApiClient();

export const serviceApiClient = new ServiceApiClient();

export const configApiClient = new ConfigApiClient();