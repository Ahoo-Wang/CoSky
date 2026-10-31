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

package me.ahoo.cosky.store

import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.validation.Limits
import kotlin.time.Duration

/**
 * A position on a namespace's timeline. Revisions compare only within one [epoch] (§6.4).
 */
data class Position(val epoch: String, val revision: Long)

enum class NamespaceState { ACTIVE, TERMINATING }

data class ObjectVersion(val key: ResourceKey, val revision: Long)

enum class ChangeType { PUT, DELETE }

/**
 * For [ChangeType.DELETE], [resource] holds the spec before deletion; its revision, updated_at and updated_by describe
 * the delete.
 */
data class Change(val type: ChangeType, val resource: Resource)

/**
 * The changes of one revision, which take effect together.
 */
data class ChangeBatch(val revision: Long, val changes: List<Change>)

data class Lease(val id: Long, val ttl: Duration)

/**
 * @param attached the Instances on the lease, which a client compares with what it registered (design D31).
 */
data class LeaseRenewal(val ttl: Duration, val attached: Int)

data class LeaseStats(val total: Int, val expired: Int)

sealed interface ApplyResult {
    /**
     * @param changed mutations that took effect; 0 means nothing changed and [revision] is the current one.
     */
    data class Applied(val revision: Long, val changed: Int) : ApplyResult

    /**
     * @param currentRevision null when the object does not exist.
     */
    data class Conflict(val index: Int, val currentRevision: Long?) : ApplyResult

    data class LeaseNotFound(val index: Int, val lease: Long) : ApplyResult

    data object NamespaceNotActive : ApplyResult

    /**
     * The batch exceeds [Limits.maxBatchOperations] or [Limits.maxBatchBytes].
     */
    data object BatchTooLarge : ApplyResult
}

sealed interface GrantResult {
    data class Granted(val lease: Lease) : GrantResult

    data object NamespaceNotActive : GrantResult
}

enum class TerminateResult { TERMINATED, NOT_EMPTY, NOT_FOUND }

/**
 * @param earliest the oldest position that can still be resumed from; the caller must load everything again.
 */
class RevisionGoneException(val earliest: Long) : RuntimeException("Revisions before $earliest are gone.")

/**
 * The position is not on the current timeline: writes were lost, or the namespace was recreated (§6.4). The caller
 * must load everything again from [current].
 */
class TimelineDivergedException(val current: Position) :
    RuntimeException("The timeline diverged; current position is $current.")

/**
 * @param logSize entries of the change log to retain, trimmed by whole revisions.
 */
data class StoreOptions(val logSize: Int = DEFAULT_LOG_SIZE, val limits: Limits = Limits.DEFAULT) {
    companion object {
        const val DEFAULT_LOG_SIZE = 10_000
    }
}
