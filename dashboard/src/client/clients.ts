import {AuthenticateApiClient, NamespaceApiClient, UserApiClient} from "../generated";

export const authenticateApiClient = new AuthenticateApiClient();

export const userApiClient = new UserApiClient();

export const namespaceApiClient = new NamespaceApiClient();