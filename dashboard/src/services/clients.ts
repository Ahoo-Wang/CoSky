import {
    AuditLogApiClient,
    AuthenticateApiClient,
    NamespaceApiClient,
    RoleApiClient,
    StatApiClient,
    UserApiClient
} from "../generated";

export const authenticateApiClient = new AuthenticateApiClient();

export const userApiClient = new UserApiClient();

export const namespaceApiClient = new NamespaceApiClient();

export const auditLogApiClient = new AuditLogApiClient();

export const stateApiClient = new StatApiClient();

export const roleApiClient = new RoleApiClient();