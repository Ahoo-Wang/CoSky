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
 * How far this page has seen each namespace, by its watch, by its reads and by its own writes (design §6.1), on the
 * timeline of one epoch (design §6.4): revisions compare only within an epoch, and a new one may start lower.
 *
 * - A read asks for no less than the watch has seen and the page has written, so that a replica that is behind cannot
 *   answer with less than the page knows has changed.
 * - A bookmark has the page read again while any read since the watch last reported a change is behind it: one read
 *   that reached a replica further on must not vouch for another that did not.
 * - A snapshot of another epoch starts over from its revision, and forgets the reads and writes of the old timeline. A
 *   read or an event that says it belongs to another epoch than the latest snapshot counts for nothing. Before any
 *   snapshot, the first read that says its epoch tells the timeline.
 * - A read that timed out waiting for a revision it asked for forgets that revision when one that did not ask was
 *   answered on another timeline, or on one it cannot tell (see [timedOutOn]).
 */
interface Position {
    epoch?: string;
    watched?: bigint;
    /** The latest of this page's own writes. */
    written?: bigint;
    /** The lowest revision the reads since the last reported change were answered at. */
    read?: bigint;
}

const positions = new Map<string, Position>();

const positionOf = (namespace: string): Position => {
    let position = positions.get(namespace);
    if (!position) {
        position = {};
        positions.set(namespace, position);
    }
    return position;
};

const ofAnotherEpoch = (position: Position, epoch: string | undefined) =>
    epoch !== undefined && position.epoch !== undefined && epoch !== position.epoch;

/**
 * A watch of the namespace reached this revision, on the epoch of its snapshot. [snapshot] says whether it is that
 * snapshot, which starts the namespace over on a new epoch.
 */
export const watchedUpTo = (namespace: string, revision: string | undefined, epoch?: string, snapshot = false) => {
    const position = positionOf(namespace);
    if (snapshot && epoch !== undefined && epoch !== position.epoch) {
        positions.set(namespace, {epoch, watched: revision ? BigInt(revision) : undefined});
        return;
    }
    if (!revision || ofAnotherEpoch(position, epoch)) {
        return;
    }
    if ((position.watched ?? -1n) < BigInt(revision)) {
        position.watched = BigInt(revision);
    }
};

/** The page reads again for a change the watch reported: the reads from here on are what counts. */
export const readingAgain = (namespace: string) => {
    positionOf(namespace).read = undefined;
};

/** A read of the namespace was answered as of this revision, on this epoch if the answer says so. */
export const readUpTo = (namespace: string, revision: string | undefined, epoch?: string) => {
    const position = positionOf(namespace);
    if (!revision || ofAnotherEpoch(position, epoch)) {
        return;
    }
    if (position.epoch === undefined) {
        position.epoch = epoch;
    }
    const answered = BigInt(revision);
    if (position.read === undefined || answered < position.read) {
        position.read = answered;
    }
};

/** This page wrote to the namespace, as of this revision: what it reads there from now on must not be older. */
export const wroteUpTo = (namespace: string, revision: string | undefined) => {
    if (!revision) {
        return;
    }
    const position = positionOf(namespace);
    if ((position.written ?? -1n) < BigInt(revision)) {
        position.written = BigInt(revision);
    }
};

/**
 * A read that asked for the least revision of the namespace timed out waiting for it (MIN_REVISION_TIMEOUT), and one
 * that asked for none was answered on [epoch], if it says so. On the timeline the page saw that revision on, it is
 * only late, and is still asked for. On another, or when that cannot be told, it may not come for long, as after a
 * failover started a new timeline lower: it is forgotten, with all else the page knew of the old timeline.
 *
 * @return whether it was forgotten, so that the answer counts.
 */
export const timedOutOn = (namespace: string, epoch: string | undefined): boolean => {
    if (epoch !== undefined && positions.get(namespace)?.epoch === epoch) {
        return false;
    }
    positions.set(namespace, {epoch});
    return true;
};

/** The least revision a read of the namespace asks for, once a watch has seen one or the page has written one. */
export const minRevisionOf = (namespace: string): string | undefined => {
    const {watched, written} = positions.get(namespace) ?? {};
    const least = watched === undefined || (written !== undefined && written > watched) ? written : watched;
    return least?.toString();
};

/** Whether a read since the last reported change is behind the revision, or none has been answered yet. */
export const readsBehind = (namespace: string, revision: string): boolean =>
    (positions.get(namespace)?.read ?? -1n) < BigInt(revision);
