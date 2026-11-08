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

/**
 * How far this page has seen each namespace's revision, by its watch and by its reads (design §6.1). A read asks for no
 * less than the watch has seen, so that a replica that is behind cannot answer with less than the page knows has
 * changed; and a bookmark of a revision no read has reached yet has the page read again.
 */
const watched = new Map<string, bigint>();
const read = new Map<string, bigint>();

const raise = (seen: Map<string, bigint>, namespace: string, revision: string | undefined) => {
    if (revision && (seen.get(namespace) ?? -1n) < BigInt(revision)) {
        seen.set(namespace, BigInt(revision));
    }
};

/** A watch of the namespace reached this revision. */
export const watchedUpTo = (namespace: string, revision: string | undefined) => raise(watched, namespace, revision);

/** A read of the namespace was answered as of this revision. */
export const readUpTo = (namespace: string, revision: string | undefined) => raise(read, namespace, revision);

/** The least revision a read of the namespace asks for, once a watch has seen one. */
export const minRevisionOf = (namespace: string): string | undefined => watched.get(namespace)?.toString();

/** Whether no read of the namespace has reached the revision yet. */
export const readsBehind = (namespace: string, revision: string): boolean =>
    (read.get(namespace) ?? -1n) < BigInt(revision);
