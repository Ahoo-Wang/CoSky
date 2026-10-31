/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {ConfigFormat, Kind, SubjectType, Verb} from '@/generated';
import type {CreateTokenRequest, CreateTokenResponse, ListResponse, Resource} from '@/generated';
import {
    configsClient,
    instancesClient,
    namespacesClient,
    roleBindingsClient,
    rolesClient,
    serviceAccountsClient,
} from '@/services/clients';

/**
 * A resource as it arrives. proto3 JSON leaves out whatever is empty, and only one spec of a resource is ever set,
 * while the generated types call every field required. Everything past this file works on the complete models below.
 */
type Wire<T> = T extends object ? {[K in keyof T]?: Wire<T[K]>} : T;
type WireResource = Wire<Resource>;

export {ConfigFormat, Kind, SubjectType, Verb};

export interface ResourceInfo {
    name: string;
    revision: string;
    labels: Record<string, string>;
    updatedAt?: string;
    updatedBy?: string;
}

export interface NamespaceResource extends ResourceInfo {
    description: string;
}

export interface ConfigResource extends ResourceInfo {
    format: ConfigFormat;
    content: string;
    sha256: string;
}

export interface InstanceSpec {
    service: string;
    scheme: string;
    host: string;
    port: number;
    weight: number;
    metadata: Record<string, string>;
}

export interface InstanceResource extends ResourceInfo, InstanceSpec {
    /** The lease that keeps it alive; `0` when nothing has to renew it. */
    lease: string;
}

export interface TokenInfo {
    id: string;
    expiresAt?: string;
}

export interface ServiceAccountResource extends ResourceInfo {
    description: string;
    tokens: TokenInfo[];
}

export interface RoleRule {
    kinds: Kind[];
    namespaces: string[];
    verbs: Verb[];
    names: string[];
}

export interface RoleResource extends ResourceInfo {
    description: string;
    rules: RoleRule[];
}

export interface RoleBindingSubject {
    type: SubjectType;
    name: string;
}

export interface RoleBindingResource extends ResourceInfo {
    role: string;
    subjects: RoleBindingSubject[];
    namespaces: string[];
}

const NO_LEASE = '0';
const DEFAULT_WEIGHT = 1;

const infoOf = ({meta}: WireResource): ResourceInfo => ({
    name: meta?.name ?? '',
    revision: meta?.revision ?? '0',
    labels: (meta?.labels ?? {}) as Record<string, string>,
    updatedAt: meta?.updatedAt,
    updatedBy: meta?.updatedBy,
});

const namespaceOf = (resource: WireResource): NamespaceResource => ({
    ...infoOf(resource),
    description: resource.namespace?.description ?? '',
});

const configOf = (resource: WireResource): ConfigResource => ({
    ...infoOf(resource),
    format: resource.config?.format ?? ConfigFormat.CONFIG_FORMAT_TEXT,
    content: resource.config?.content ?? '',
    sha256: resource.config?.sha256 ?? '',
});

const instanceOf = (resource: WireResource): InstanceResource => ({
    ...infoOf(resource),
    service: resource.instance?.service ?? '',
    scheme: resource.instance?.endpoint?.scheme ?? '',
    host: resource.instance?.endpoint?.host ?? '',
    port: resource.instance?.endpoint?.port ?? 0,
    weight: resource.instance?.weight ?? DEFAULT_WEIGHT,
    metadata: (resource.instance?.metadata ?? {}) as Record<string, string>,
    lease: resource.meta?.lease ?? NO_LEASE,
});

const serviceAccountOf = (resource: WireResource): ServiceAccountResource => ({
    ...infoOf(resource),
    description: resource.serviceAccount?.description ?? '',
    tokens: (resource.serviceAccount?.tokens ?? []).map(token => ({
        id: token?.id ?? '',
        expiresAt: token?.expiresAt,
    })),
});

const roleOf = (resource: WireResource): RoleResource => ({
    ...infoOf(resource),
    description: resource.role?.description ?? '',
    rules: (resource.role?.rules ?? []).map(rule => ({
        kinds: (rule?.kinds ?? []) as Kind[],
        namespaces: (rule?.namespaces ?? []) as string[],
        verbs: (rule?.verbs ?? []) as Verb[],
        names: (rule?.names ?? []) as string[],
    })),
});

const roleBindingOf = (resource: WireResource): RoleBindingResource => ({
    ...infoOf(resource),
    role: resource.roleBinding?.role ?? '',
    subjects: (resource.roleBinding?.subjects ?? []).map(subject => ({
        type: subject?.type ?? SubjectType.TYPE_UNSPECIFIED,
        name: subject?.name ?? '',
    })),
    namespaces: (resource.roleBinding?.namespaces ?? []) as string[],
});

interface PageOptions {
    abortController?: AbortController;
    query?: Record<string, string>;
}

/**
 * Reads every page: a list is split by bytes, so that a page can hold few objects (design D23).
 */
async function listAll<T>(
    list: (request: object) => Promise<Response>,
    path: Record<string, string>,
    toResource: (resource: WireResource) => T,
    {abortController, query}: PageOptions = {},
): Promise<T[]> {
    const items: T[] = [];
    let pageToken = '';
    do {
        const response = await list({
            urlParams: {path, query: {...query, ...(pageToken ? {pageToken} : {})}},
            abortController,
        });
        const page = await response.json() as Wire<ListResponse>;
        items.push(...(page.items ?? []).map(item => toResource(item as WireResource)));
        pageToken = page.nextPageToken ?? '';
    } while (pageToken);
    return items;
}

/**
 * Compare-and-set against the revision an edit started from, or create-only without one.
 */
const writeGuard = (revision?: string): Record<string, string> =>
    revision ? {'If-Match': `"${revision}"`} : {'If-None-Match': '*'};

const putRequest = (path: Record<string, string>, body: WireResource, revision?: string) => ({
    urlParams: {path},
    headers: {'Content-Type': 'application/json', ...writeGuard(revision)},
    body: body as Resource,
});

export const namespaces = {
    list: (abortController?: AbortController) =>
        listAll(request => namespacesClient.listNamespaces(request), {}, namespaceOf, {abortController}),
    create: (name: string, description: string) =>
        namespacesClient.putNamespace(putRequest({name}, {namespace: {description}})),
    delete: (name: string) => namespacesClient.deleteNamespace({urlParams: {path: {name}}}),
};

export const configs = {
    list: (namespace: string, abortController?: AbortController) =>
        listAll(request => configsClient.listConfigs(request), {namespace}, configOf, {abortController}),
    async get(namespace: string, name: string, abortController?: AbortController): Promise<ConfigResource> {
        const response = await configsClient.getConfig({urlParams: {path: {namespace, name}}, abortController});
        return configOf(await response.json() as WireResource);
    },
    /**
     * @param revision the revision the edit started from; a new Config has none.
     */
    put: (namespace: string, name: string, spec: {format: ConfigFormat; content: string}, revision?: string,
          labels?: Record<string, string>) =>
        configsClient.putConfig(putRequest({namespace, name}, {meta: {labels}, config: spec}, revision)),
    delete: (namespace: string, name: string) => configsClient.deleteConfig({urlParams: {path: {namespace, name}}}),
    /**
     * Newest first; the first one is the current version.
     */
    async history(namespace: string, name: string, abortController?: AbortController): Promise<ConfigResource[]> {
        const versions: ConfigResource[] = [];
        let pageToken = '';
        do {
            const page = await configsClient.getConfigHistory({
                urlParams: {path: {namespace, name}, query: pageToken ? {pageToken} : {}},
                abortController,
            }) as Wire<{revisions: Resource[]; nextPageToken: string}>;
            versions.push(...(page.revisions ?? []).map(item => configOf(item as WireResource)));
            pageToken = page.nextPageToken ?? '';
        } while (pageToken);
        return versions;
    },
    rollback: (namespace: string, name: string, toRevision: string) => configsClient.rollbackConfig({
        urlParams: {path: {namespace, name}},
        headers: {'Content-Type': 'application/json'},
        body: {toRevision},
    }),
    importArchive: (namespace: string, archive: File) => configsClient.importConfigs({
        urlParams: {path: {namespace}},
        body: archive,
    }),
    async exportArchive(namespace: string): Promise<Blob> {
        const response = await configsClient.exportConfigs({urlParams: {path: {namespace}}});
        return response.blob();
    },
};

export const instances = {
    list: (namespace: string, service: string, abortController?: AbortController) => listAll(
        request => instancesClient.listInstances(request),
        {namespace},
        instanceOf,
        {abortController, query: {service}},
    ),
    /**
     * @param existing what it replaces, whose revision, labels and lease carry over.
     */
    put: (namespace: string, name: string, spec: InstanceSpec, existing?: InstanceResource) =>
        instancesClient.putInstance(putRequest(
            {namespace, name},
            {
                meta: {labels: existing?.labels, lease: existing?.lease},
                instance: {
                    service: spec.service,
                    endpoint: {scheme: spec.scheme, host: spec.host, port: spec.port},
                    weight: spec.weight,
                    metadata: spec.metadata,
                },
            },
            existing?.revision,
        )),
    delete: (namespace: string, name: string) =>
        instancesClient.deleteInstance({urlParams: {path: {namespace, name}}}),
};

export const serviceAccounts = {
    list: (abortController?: AbortController) => listAll(
        request => serviceAccountsClient.listServiceAccounts(request),
        {},
        serviceAccountOf,
        {abortController},
    ),
    create: (name: string, description: string) => serviceAccountsClient.putServiceAccount(
        putRequest({name}, {serviceAccount: {description}}),
    ),
    delete: (name: string) => serviceAccountsClient.deleteServiceAccount({urlParams: {path: {name}}}),
    /**
     * @return the plaintext, which the server never shows again.
     */
    async createToken(name: string): Promise<string> {
        const created = await serviceAccountsClient.createToken({
            urlParams: {path: {name}},
            headers: {'Content-Type': 'application/json'},
            body: {} as CreateTokenRequest,
        }) as Wire<CreateTokenResponse>;
        return created.token ?? '';
    },
    revokeToken: (name: string, tokenId: string) =>
        serviceAccountsClient.revokeToken({urlParams: {path: {name, tokenId}}}),
};

export const roles = {
    list: (abortController?: AbortController) =>
        listAll(request => rolesClient.listRoles(request), {}, roleOf, {abortController}),
    put: (name: string, spec: {description: string; rules: RoleRule[]}, revision?: string) =>
        rolesClient.putRole(putRequest({name}, {role: spec}, revision)),
    delete: (name: string) => rolesClient.deleteRole({urlParams: {path: {name}}}),
};

export const roleBindings = {
    list: (abortController?: AbortController) =>
        listAll(request => roleBindingsClient.listRoleBindings(request), {}, roleBindingOf, {abortController}),
    put: (name: string, spec: {role: string; subjects: RoleBindingSubject[]; namespaces: string[]}, revision?: string) =>
        roleBindingsClient.putRoleBinding(putRequest({name}, {roleBinding: spec}, revision)),
    delete: (name: string) => roleBindingsClient.deleteRoleBinding({urlParams: {path: {name}}}),
};
