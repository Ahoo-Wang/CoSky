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

import {ExchangeError} from '@ahoo-wang/fetcher';
import type {ParameterRequest} from '@ahoo-wang/fetcher-decorator';
import {ConfigFormat, ErrorReason, Kind, SubjectType, Verb} from '@/generated';
import type {
    ConfigImportResponse,
    CreateTokenResponse,
    DeleteResponse,
    HistoryResponse,
    ListResponse,
    Problem,
    PutResponse,
    Resource,
} from '@/generated';
import {
    configsClient,
    instancesClient,
    namespacesClient,
    roleBindingsClient,
    rolesClient,
    serviceAccountsClient,
} from '@/services/clients';
import {minRevisionOf, readUpTo, timedOutOn, wroteUpTo} from '@/services/revisions';

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

const GATEWAY_TIMEOUT = 504;

/** Whether the server says the namespace did not reach the revision a read asked for in time. */
async function minRevisionTimedOut(error: unknown): Promise<boolean> {
    const response = error instanceof ExchangeError ? error.exchange.response : undefined;
    if (response?.status !== GATEWAY_TIMEOUT) {
        return false;
    }
    try {
        const problem = await response.clone().json() as Partial<Problem>;
        return problem.reason === ErrorReason.ERROR_REASON_MIN_REVISION_TIMEOUT;
    } catch {
        return false;
    }
}

/**
 * Reads [namespace] no older than this page has seen it, by its watch and by its own writes. Should that revision not
 * come in time, the read is made again without it: on another timeline than the page saw it on, as after a failover,
 * it is forgotten and that answer counts; on the same one it is only late, and the read fails as it did.
 *
 * @param epochOf the timeline of an answer, if it says.
 */
async function readAtLeast<T>(
    namespace: string,
    read: (minRevision: string | undefined) => Promise<T>,
    epochOf: (answer: T) => string | undefined,
): Promise<T> {
    const minRevision = minRevisionOf(namespace);
    try {
        return await read(minRevision);
    } catch (error) {
        if (!minRevision || !await minRevisionTimedOut(error)) {
            throw error;
        }
        const answer = await read(undefined);
        if (!timedOutOn(namespace, epochOf(answer))) {
            throw error;
        }
        return answer;
    }
}

/** One page of a list: `minRevision` for the first, `pageToken` for the others. */
type ListPage = (
    minRevision: string | undefined,
    pageToken: string | undefined,
    request: ParameterRequest,
) => Promise<ListResponse>;

/**
 * Reads every page of a list in [namespace]: a list is split by bytes, so that a page can hold few objects (design D23).
 */
async function listAll<T>(
    namespace: string,
    list: ListPage,
    toResource: (resource: WireResource) => T,
    abortController?: AbortController,
): Promise<T[]> {
    const listPage = async (minRevision: string | undefined, pageToken: string | undefined) =>
        await list(minRevision, pageToken, {abortController}) as Wire<ListResponse>;
    // Later pages read where the first one did.
    let page = await readAtLeast(namespace, minRevision => listPage(minRevision, undefined), first => first.epoch);
    readUpTo(namespace, page.revision, page.epoch);
    const items: T[] = [];
    for (;;) {
        items.push(...(page.items ?? []).map(item => toResource(item as WireResource)));
        if (!page.nextPageToken) {
            return items;
        }
        page = await listPage(undefined, page.nextPageToken);
    }
}

/** The revision a write or a delete started from, as `If-Match` takes it: someone else's change since fails it. */
const ifMatch = (revision?: string): string | undefined => revision ? `"${revision}"` : undefined;

/**
 * Compare-and-set against the revision an edit started from, or create-only without one: `If-Match` and
 * `If-None-Match`, as the generated clients take them.
 */
const writeGuard = (revision?: string): [string | undefined, '*' | undefined] =>
    revision ? [ifMatch(revision), undefined] : [undefined, '*'];

/**
 * A write to [namespace]: what this page reads there from now on asks for no less than its revision, so that a replica
 * that is behind cannot answer with what was there before.
 */
async function wrote<T extends PutResponse | DeleteResponse>(namespace: string, write: Promise<T>): Promise<T> {
    const answer = await write;
    wroteUpTo(namespace, answer.revision);
    return answer;
}

/** proto3 JSON leaves out what is empty, which the generated types do not know. */
const body = (resource: WireResource) => resource as Resource;

/** Where Namespaces and the other global kinds live, and so where their changes are watched. */
export const SYSTEM_NAMESPACE = '_system';

export const namespaces = {
    list: (abortController?: AbortController) => listAll(
        SYSTEM_NAMESPACE,
        (minRevision, pageToken, request) =>
            namespacesClient.listNamespaces(undefined, minRevision, undefined, pageToken, request),
        namespaceOf,
        abortController,
    ),
    create: (name: string, description: string) =>
        wrote(SYSTEM_NAMESPACE, namespacesClient.putNamespace(name, body({namespace: {description}}), ...writeGuard())),
    /**
     * @param revision of what the user chose to delete: a change since then must not be deleted unseen.
     */
    delete: (name: string, revision?: string) =>
        wrote(SYSTEM_NAMESPACE, namespacesClient.deleteNamespace(name, ifMatch(revision))),
};

export const configs = {
    list: (namespace: string, abortController?: AbortController) => listAll(
        namespace,
        (minRevision, pageToken, request) =>
            configsClient.listConfigs(namespace, undefined, minRevision, undefined, pageToken, request),
        configOf,
        abortController,
    ),
    async get(namespace: string, name: string, abortController?: AbortController): Promise<ConfigResource> {
        return configOf(await readAtLeast(
            namespace,
            async minRevision =>
                await configsClient.getConfig(namespace, name, minRevision, {abortController}) as WireResource,
            // An object does not say which timeline it was read on.
            () => undefined,
        ));
    },
    /**
     * @param revision the revision the edit started from; a new Config has none.
     */
    put: (namespace: string, name: string, spec: {format: ConfigFormat; content: string}, revision?: string,
          labels?: Record<string, string>) => wrote(
        namespace,
        configsClient.putConfig(namespace, name, body({meta: {labels}, config: spec}), ...writeGuard(revision)),
    ),
    /**
     * @param revision of what the user chose to delete: a change since then must not be deleted unseen.
     */
    delete: (namespace: string, name: string, revision?: string) =>
        wrote(namespace, configsClient.deleteConfig(namespace, name, ifMatch(revision))),
    /**
     * Newest first; the first one is the current version.
     */
    async history(namespace: string, name: string, abortController?: AbortController): Promise<ConfigResource[]> {
        const versions: ConfigResource[] = [];
        let pageToken = '';
        do {
            const page = await configsClient.getConfigHistory(
                namespace,
                name,
                undefined,
                pageToken || undefined,
                {abortController},
            ) as Wire<HistoryResponse>;
            versions.push(...(page.revisions ?? []).map(item => configOf(item as WireResource)));
            pageToken = page.nextPageToken ?? '';
        } while (pageToken);
        return versions;
    },
    /**
     * @param currentRevision what the user compared it with, so that a later change is not overwritten unseen.
     */
    rollback: (namespace: string, name: string, toRevision: string, currentRevision?: string) =>
        wrote(namespace, configsClient.rollbackConfig(namespace, name, {toRevision}, ifMatch(currentRevision))),
    async importArchive(namespace: string, archive: File): Promise<ConfigImportResponse> {
        const imported = await configsClient.importConfigs(namespace, archive);
        (imported as Wire<ConfigImportResponse>).items?.forEach(item => wroteUpTo(namespace, item?.revision));
        return imported;
    },
    async exportArchive(namespace: string): Promise<Blob> {
        const response = await configsClient.exportConfigs(namespace);
        return response.blob();
    },
};

export const instances = {
    list: (namespace: string, service: string, abortController?: AbortController) => listAll(
        namespace,
        (minRevision, pageToken, request) =>
            instancesClient.listInstances(namespace, undefined, service, minRevision, undefined, pageToken, request),
        instanceOf,
        abortController,
    ),
    /**
     * @param existing what it replaces, whose revision, labels and lease carry over.
     */
    put: (namespace: string, name: string, spec: InstanceSpec, existing?: InstanceResource) => wrote(
        namespace,
        instancesClient.putInstance(
            namespace,
            name,
            body({
                meta: {labels: existing?.labels, lease: existing?.lease},
                instance: {
                    service: spec.service,
                    endpoint: {scheme: spec.scheme, host: spec.host, port: spec.port},
                    weight: spec.weight,
                    metadata: spec.metadata,
                },
            }),
            ...writeGuard(existing?.revision),
        ),
    ),
    /**
     * @param revision of what the user chose to delete: a change since then must not be deleted unseen.
     */
    delete: (namespace: string, name: string, revision?: string) =>
        wrote(namespace, instancesClient.deleteInstance(namespace, name, ifMatch(revision))),
};

export const serviceAccounts = {
    list: (abortController?: AbortController) => listAll(
        SYSTEM_NAMESPACE,
        (minRevision, pageToken, request) =>
            serviceAccountsClient.listServiceAccounts(undefined, minRevision, undefined, pageToken, request),
        serviceAccountOf,
        abortController,
    ),
    create: (name: string, description: string) => wrote(
        SYSTEM_NAMESPACE,
        serviceAccountsClient.putServiceAccount(name, body({serviceAccount: {description}}), ...writeGuard()),
    ),
    /**
     * @param revision of what the user chose to delete: tokens added since then may be in use, and must not be deleted
     * unseen.
     */
    delete: (name: string, revision?: string) =>
        wrote(SYSTEM_NAMESPACE, serviceAccountsClient.deleteServiceAccount(name, ifMatch(revision))),
    /**
     * @return the plaintext, which the server never shows again.
     */
    async createToken(name: string): Promise<string> {
        const created = await serviceAccountsClient.createToken(name, {}) as Wire<CreateTokenResponse>;
        return created.token ?? '';
    },
    revokeToken: (name: string, tokenId: string) => serviceAccountsClient.revokeToken(name, tokenId),
};

export const roles = {
    list: (abortController?: AbortController) => listAll(
        SYSTEM_NAMESPACE,
        (minRevision, pageToken, request) => rolesClient.listRoles(undefined, minRevision, undefined, pageToken, request),
        roleOf,
        abortController,
    ),
    /**
     * @param existing what it replaces, whose revision and labels carry over.
     */
    put: (name: string, spec: {description: string; rules: RoleRule[]}, existing?: RoleResource) => wrote(
        SYSTEM_NAMESPACE,
        rolesClient.putRole(name, body({meta: {labels: existing?.labels}, role: spec}), ...writeGuard(existing?.revision)),
    ),
    /**
     * @param revision of what the user chose to delete: a change since then must not be deleted unseen.
     */
    delete: (name: string, revision?: string) => wrote(SYSTEM_NAMESPACE, rolesClient.deleteRole(name, ifMatch(revision))),
};

export const roleBindings = {
    list: (abortController?: AbortController) => listAll(
        SYSTEM_NAMESPACE,
        (minRevision, pageToken, request) =>
            roleBindingsClient.listRoleBindings(undefined, minRevision, undefined, pageToken, request),
        roleBindingOf,
        abortController,
    ),
    /**
     * @param existing what it replaces, whose revision and labels carry over.
     */
    put: (
        name: string,
        spec: {role: string; subjects: RoleBindingSubject[]; namespaces: string[]},
        existing?: RoleBindingResource,
    ) => wrote(SYSTEM_NAMESPACE, roleBindingsClient.putRoleBinding(
        name,
        body({meta: {labels: existing?.labels}, roleBinding: spec}),
        ...writeGuard(existing?.revision),
    )),
    /**
     * @param revision of what the user chose to delete: a change since then must not be deleted unseen.
     */
    delete: (name: string, revision?: string) =>
        wrote(SYSTEM_NAMESPACE, roleBindingsClient.deleteRoleBinding(name, ifMatch(revision))),
};
