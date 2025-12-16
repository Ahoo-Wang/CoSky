import {
    AuditLogApiClient,
    AuthenticateApiClient, ConfigApiClient,
    NamespaceApiClient,
    RoleApiClient,
    ServiceApiClient,
    StatApiClient,
    UserApiClient
} from "../generated";

export const authenticateApiClient = new AuthenticateApiClient();

export const userApiClient = new UserApiClient();

export const namespaceApiClient = new NamespaceApiClient();

export const auditLogApiClient = new AuditLogApiClient();

export const statApiClient = new StatApiClient();

export const roleApiClient = new RoleApiClient();

export const serviceApiClient = new ServiceApiClient();

export const configApiClient = new ConfigApiClient();