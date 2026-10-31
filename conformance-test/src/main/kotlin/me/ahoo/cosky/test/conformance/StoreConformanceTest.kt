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

package me.ahoo.cosky.test.conformance

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.copy
import me.ahoo.cosky.api.v1.endpoint
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.resourceKey
import me.ahoo.cosky.api.validation.Limits
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.ChangeType
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.LeaseStats
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.ObjectVersion
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.RevisionGoneException
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.cosky.store.key
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What every [Store] must do (design §5, §6.2, §6.4, §7.1, §7.2). An implementation extends this class and supplies a
 * store; both the in-memory and the Redis store run the same cases.
 *
 * Expiry runs on real time, since a store may keep its own clock (Redis `TIME`). Each case uses its own namespace, so
 * stores may share a backend.
 */
@Suppress("LargeClass") // One contract, one suite: implementations extend a single class.
abstract class StoreConformanceTest {
    protected abstract fun newStore(options: StoreOptions): Store

    /**
     * Forgets the writes after [revision] and keeps the epoch, as a failover to a lagging replica does.
     */
    protected abstract suspend fun loseWritesAfter(store: Store, namespace: String, revision: Long)

    private val namespace = "conformance-${UUID.randomUUID()}"

    // --- apply: compare-and-set ---

    @Test
    fun `no check creates and overwrites`() = scenario { store ->
        store.put(config(content = "1")).assert().isEqualTo(ApplyResult.Applied(1, 1))
        store.put(config(content = "2")).assert().isEqualTo(ApplyResult.Applied(2, 1))
    }

    @Test
    fun `must not exist only creates`() = scenario { store ->
        store.put(config(content = "1"), Mutation.MUST_NOT_EXIST).assert().isEqualTo(ApplyResult.Applied(1, 1))
        store.put(config(content = "2"), Mutation.MUST_NOT_EXIST).assert().isEqualTo(ApplyResult.Conflict(0, 1))
    }

    @Test
    fun `compare and set needs the current revision`() = scenario { store ->
        store.put(config(content = "1"))
        store.put(config(content = "2"), 5).assert().isEqualTo(ApplyResult.Conflict(0, 1))
        store.put(config(content = "2"), 1).assert().isEqualTo(ApplyResult.Applied(2, 1))
    }

    @Test
    fun `compare and set on a missing object reports no current revision`() = scenario { store ->
        store.put(config(), 5).assert().isEqualTo(ApplyResult.Conflict(0, null))
    }

    @Test
    fun `delete honors the expected revision`() = scenario { store ->
        store.put(config())
        store.apply(namespace, listOf(Mutation.Delete(configKey(), 9)), ACTOR)
            .assert().isEqualTo(ApplyResult.Conflict(0, 1))
        store.apply(namespace, listOf(Mutation.Delete(configKey(), 1)), ACTOR)
            .assert().isEqualTo(ApplyResult.Applied(2, 1))
    }

    // --- apply: batches ---

    @Test
    fun `a batch shares one revision`() = scenario { store ->
        store.apply(namespace, listOf(Mutation.Put(config("a")), Mutation.Put(config("b"))), ACTOR)
            .assert().isEqualTo(ApplyResult.Applied(1, 2))
        store.readAll(namespace).toList().map { it.meta.revision }.assert().containsExactly(1, 1)
    }

    @Test
    fun `a failed mutation fails the whole batch`() = scenario { store ->
        store.put(config("taken"))
        store.apply(
            namespace,
            listOf(Mutation.Put(config("free")), Mutation.Put(config("taken"), Mutation.MUST_NOT_EXIST)),
            ACTOR,
        ).assert().isEqualTo(ApplyResult.Conflict(1, 1))
        store.readAll(namespace).toList().map { it.meta.name }.assert().containsExactly("taken")
        position(store).revision.assert().isEqualTo(1)
    }

    @Test
    fun `a batch counts only what changed`() = scenario { store ->
        store.put(config("same"))
        store.apply(namespace, listOf(Mutation.Put(config("same")), Mutation.Put(config("new"))), ACTOR)
            .assert().isEqualTo(ApplyResult.Applied(2, 1))
    }

    @Test
    fun `a batch over the operation limit is rejected`() = scenario(limits(maxBatchOperations = 2)) { store ->
        store.apply(namespace, List(3) { Mutation.Put(config("c$it")) }, ACTOR)
            .assert().isEqualTo(ApplyResult.BatchTooLarge)
    }

    @Test
    fun `a batch over the byte limit is rejected`() = scenario(limits(maxBatchBytes = 10)) { store ->
        store.put(config(content = "a".repeat(100))).assert().isEqualTo(ApplyResult.BatchTooLarge)
    }

    @Test
    fun `a batch must not change an object twice`() = scenario { store ->
        thrownBy<IllegalArgumentException> {
            store.apply(namespace, listOf(Mutation.Put(config()), Mutation.Delete(configKey())), ACTOR)
        }
    }

    @Test
    fun `a batch must stay in its namespace`() = scenario { store ->
        thrownBy<IllegalArgumentException> {
            store.apply("other", listOf(Mutation.Put(config())), ACTOR)
        }
    }

    // --- apply: nothing changed ---

    @Test
    fun `writing the same content creates no revision`() = scenario { store ->
        store.put(config())
        store.put(config()).assert().isEqualTo(ApplyResult.Applied(1, 0))
        position(store).revision.assert().isEqualTo(1)
    }

    @Test
    fun `label order does not count as a change`() = scenario { store ->
        store.put(config(labels = listOf("a" to "1", "b" to "2")))
        store.put(config(labels = listOf("b" to "2", "a" to "1"))).assert().isEqualTo(ApplyResult.Applied(1, 0))
    }

    @Test
    fun `read-only fields do not count as a change`() = scenario { store ->
        store.put(config())
        val tampered = config().copy {
            meta = meta.copy {
                revision = 99
                updatedBy = "someone else"
            }
        }
        store.put(tampered).assert().isEqualTo(ApplyResult.Applied(1, 0))
    }

    @Test
    fun `deleting a missing object changes nothing`() = scenario { store ->
        store.apply(namespace, listOf(Mutation.Delete(configKey())), ACTOR)
            .assert().isEqualTo(ApplyResult.Applied(0, 0))
    }

    // --- metadata ---

    @Test
    fun `the store fills the read-only metadata`() = scenario { store ->
        val tampered = config().copy {
            meta = meta.copy {
                revision = 99
                createRevision = 99
                updatedBy = "someone else"
            }
        }
        store.put(tampered)
        val stored = store.readAll(namespace).toList().single().meta
        stored.revision.assert().isEqualTo(1)
        stored.createRevision.assert().isEqualTo(1)
        stored.updatedBy.assert().isEqualTo(ACTOR)
        stored.hasCreatedAt().assert().isTrue()
        stored.updatedAt.assert().isEqualTo(stored.createdAt)
    }

    @Test
    fun `an update keeps the creation metadata`() = scenario { store ->
        store.put(config(content = "1"))
        val created = store.readAll(namespace).toList().single().meta
        delay(5.milliseconds)
        store.put(config(content = "2"), actor = "another")
        val updated = store.readAll(namespace).toList().single().meta
        updated.revision.assert().isEqualTo(2)
        updated.createRevision.assert().isEqualTo(1)
        updated.createdAt.assert().isEqualTo(created.createdAt)
        updated.updatedAt.assert().isNotEqualTo(created.updatedAt)
        updated.updatedBy.assert().isEqualTo("another")
    }

    @Test
    fun `recreating an object restarts its creation metadata`() = scenario { store ->
        store.put(config())
        store.apply(namespace, listOf(Mutation.Delete(configKey())), ACTOR)
        store.put(config())
        store.readAll(namespace).toList().single().meta.createRevision.assert().isEqualTo(3)
    }

    @Test
    fun `versions list every object with its revision`() = scenario { store ->
        store.put(config("a"))
        store.put(config("b"))
        store.versions(namespace).toList().assert().containsExactlyInAnyOrder(
            ObjectVersion(configKey("a"), 1),
            ObjectVersion(configKey("b"), 2),
        )
    }

    // --- changes ---

    @Test
    fun `changes replay revisions in order`() = scenario { store ->
        val start = position(store)
        store.put(config("a"))
        store.put(config("b"))
        val batches = store.changes(namespace, start).take(2).toList()
        batches.map { it.revision }.assert().containsExactly(1, 2)
        batches.map { batch -> batch.changes.single().resource.meta.name }.assert().containsExactly("a", "b")
    }

    @Test
    fun `changes resume after a position`() = scenario { store ->
        val start = position(store)
        store.put(config("a"))
        store.put(config("b"))
        store.changes(namespace, start.copy(revision = 1)).first().revision.assert().isEqualTo(2)
    }

    @Test
    fun `changes keep a revision together`() = scenario { store ->
        val start = position(store)
        store.apply(namespace, listOf(Mutation.Put(config("a")), Mutation.Put(config("b"))), ACTOR)
        store.changes(namespace, start).first().changes.map { it.resource.meta.name }
            .assert().containsExactly("a", "b")
    }

    @Test
    fun `changes follow new revisions`() = scenario { store ->
        val start = position(store)
        val following = async { store.changes(namespace, start).take(2).toList() }
        store.put(config("a"))
        store.put(config("b"))
        following.await().map { it.revision }.assert().containsExactly(1, 2)
    }

    @Test
    fun `a delete event holds the last spec and describes the delete`() = scenario { store ->
        store.put(config(content = "last"))
        val start = position(store)
        store.apply(namespace, listOf(Mutation.Delete(configKey())), "deleter")
        val change = store.changes(namespace, start).first().changes.single()
        change.type.assert().isEqualTo(ChangeType.DELETE)
        change.resource.config.content.assert().isEqualTo("last")
        change.resource.meta.revision.assert().isEqualTo(2)
        change.resource.meta.createRevision.assert().isEqualTo(1)
        change.resource.meta.updatedBy.assert().isEqualTo("deleter")
    }

    @Test
    fun `a put event holds the complete metadata`() = scenario { store ->
        val start = position(store)
        store.put(config())
        val change = store.changes(namespace, start).first().changes.single()
        change.type.assert().isEqualTo(ChangeType.PUT)
        change.resource.assert().isEqualTo(store.readAll(namespace).toList().single())
    }

    @Test
    fun `a long replay keeps every revision whole`() = scenario { store ->
        val start = position(store)
        repeat(400) { batch ->
            val mutations = List(3) { Mutation.Put(config("c$it", content = "$batch")) }
            store.apply(namespace, mutations, ACTOR)
        }
        val batches = store.changes(namespace, start).take(400).toList()
        batches.map { it.revision }.assert().isEqualTo((1L..400L).toList())
        batches.map { it.changes.size }.toSet().assert().containsExactly(3)
    }

    @Test
    fun `recent changes are whole revisions up to a revision, oldest first`() = scenario { store ->
        store.apply(namespace, List(2) { Mutation.Put(config("c$it")) }, ACTOR)
        store.put(config("d"))
        store.put(config("e"))
        val recent = store.recentChanges(namespace, 2, 100)
        recent.map { it.revision }.assert().containsExactly(1L, 2L)
        recent.map { it.changes.size }.assert().containsExactly(2, 1)
    }

    @Test
    fun `recent changes stay within the limit without cutting a revision`() = scenario { store ->
        repeat(2) { batch ->
            store.apply(namespace, List(2) { Mutation.Put(config("c$batch$it")) }, ACTOR)
        }
        store.recentChanges(namespace, 2, 3).map { it.revision }.assert().containsExactly(2L)
        store.recentChanges(namespace, 2, 4).map { it.revision }.assert().containsExactly(1L, 2L)
        store.recentChanges(namespace, 2, 1).assert().isEmpty()
    }

    @Test
    fun `recent changes survive a trimmed log`() = scenario(StoreOptions(logSize = 10)) { store ->
        repeat(300) { batch ->
            store.apply(namespace, List(3) { Mutation.Put(config("c$it", content = "$batch")) }, ACTOR)
        }
        val current = position(store)
        val recent = store.recentChanges(namespace, current.revision, 1000)
        recent.map { it.changes.size }.toSet().assert().containsExactly(3)
        recent.last().revision.assert().isEqualTo(current.revision)
        // They can seed a window: a client may resume from just before the oldest of them.
        store.changes(namespace, current.copy(revision = recent.first().revision - 1)).first().revision
            .assert().isEqualTo(recent.first().revision)
    }

    @Test
    fun `recent changes of a namespace that does not exist are empty`() = scenario { store ->
        store.recentChanges("missing", 10, 10).assert().isEmpty()
    }

    @Test
    fun `a position outside the window is gone`() = scenario(StoreOptions(logSize = 10)) { store ->
        val start = position(store)
        repeat(300) { store.put(config(content = "$it")) }
        val gone = thrownBy<RevisionGoneException> { store.changes(namespace, start).first() }
        gone.earliest.assert().isGreaterThan(0)
        store.changes(namespace, start.copy(revision = gone.earliest)).first().revision
            .assert().isEqualTo(gone.earliest + 1)
    }

    @Test
    fun `the current position is never gone`() = scenario(StoreOptions(logSize = 10)) { store ->
        repeat(300) { store.put(config(content = "$it")) }
        val current = position(store)
        val following = async { store.changes(namespace, current).first() }
        store.put(config(content = "next"))
        following.await().revision.assert().isEqualTo(current.revision + 1)
    }

    @Test
    fun `changes complete when the namespace stops being active`() = scenario { store ->
        val start = position(store)
        val following = async { store.changes(namespace, start).toList() }
        store.terminateNamespace(namespace)
        following.await().assert().isEmpty()
    }

    // --- timeline ---

    @Test
    fun `another epoch is not on the timeline`() = scenario { store ->
        val current = position(store)
        val diverged = thrownBy<TimelineDivergedException> {
            store.changes(namespace, current.copy(epoch = "another")).first()
        }
        diverged.current.assert().isEqualTo(current)
    }

    @Test
    fun `a revision from the future is not on the timeline`() = scenario { store ->
        val current = position(store)
        val diverged = thrownBy<TimelineDivergedException> {
            store.changes(namespace, current.copy(revision = 5)).first()
        }
        diverged.current.epoch.assert().isNotEqualTo(current.epoch)
    }

    @Test
    fun `a follower detects revisions that went back`() = scenario { store ->
        val start = position(store)
        store.put(config(content = "1"))
        store.put(config(content = "2"))
        val diverged = follow(store, start, caughtUpTo = 2) {
            loseWritesAfter(store, namespace, 1)
        }
        diverged.current.revision.assert().isEqualTo(1)
        diverged.current.epoch.assert().isNotEqualTo(start.epoch)
        position(store).assert().isEqualTo(diverged.current)
    }

    @Test
    fun `a follower detects revisions that went back and grew again`() = scenario { store ->
        val start = position(store)
        store.put(config(content = "1"))
        store.put(config(content = "2"))
        val diverged = follow(store, start, caughtUpTo = 2) {
            loseWritesAfter(store, namespace, 1)
            store.put(config(content = "other 2"))
            store.put(config(content = "other 3"))
        }
        diverged.current.epoch.assert().isNotEqualTo(start.epoch)
    }

    @Test
    fun `the old epoch stays invalid after a fork`() = scenario { store ->
        val start = position(store)
        store.put(config(content = "1"))
        store.put(config(content = "2"))
        val first = follow(store, start, caughtUpTo = 2) {
            loseWritesAfter(store, namespace, 1)
        }
        val second = thrownBy<TimelineDivergedException> { store.changes(namespace, start).first() }
        second.current.epoch.assert().isEqualTo(first.current.epoch)
    }

    @Test
    fun `recreating a namespace starts a new timeline`() = scenario { store ->
        store.put(config())
        val old = position(store)
        store.apply(namespace, listOf(Mutation.Delete(configKey())), ACTOR)
        store.terminateNamespace(namespace).assert().isEqualTo(TerminateResult.TERMINATED)
        store.dropNamespace(namespace)
        val recreated = store.createNamespace(namespace)
        recreated.revision.assert().isEqualTo(0)
        recreated.epoch.assert().isNotEqualTo(old.epoch)
        val diverged = thrownBy<TimelineDivergedException> { store.changes(namespace, old).first() }
        diverged.current.assert().isEqualTo(recreated)
    }

    // --- history ---

    @Test
    fun `history lists revisions newest first`() = scenario { store ->
        store.put(config(content = "1"))
        store.put(config(content = "2"))
        store.history(configKey(), 0).map { it.config.content }.assert().containsExactly("2", "1")
    }

    @Test
    fun `history holds the complete metadata`() = scenario { store ->
        store.put(config(), actor = "writer")
        val meta = store.history(configKey(), 0).single().meta
        meta.revision.assert().isEqualTo(1)
        meta.updatedBy.assert().isEqualTo("writer")
        meta.hasCreatedAt().assert().isTrue()
    }

    @Test
    fun `identical content keeps its own history entry`() = scenario { store ->
        listOf("a", "b", "a").forEach { store.put(config(content = it)) }
        store.history(configKey(), 0).map { it.meta.revision to it.config.content }
            .assert().containsExactly(3L to "a", 2L to "b", 1L to "a")
    }

    @Test
    fun `history is cut to its size`() = scenario(limits(configHistorySize = 3)) { store ->
        repeat(5) { store.put(config(content = "$it")) }
        store.history(configKey(), 0).map { it.config.content }.assert().containsExactly("4", "3", "2")
    }

    @Test
    fun `history honors the limit`() = scenario { store ->
        repeat(3) { store.put(config(content = "$it")) }
        store.history(configKey(), 2).map { it.config.content }.assert().containsExactly("2", "1")
    }

    @Test
    fun `history outlives the config`() = scenario { store ->
        store.put(config(content = "kept"))
        store.apply(namespace, listOf(Mutation.Delete(configKey())), ACTOR)
        store.history(configKey(), 0).single().config.content.assert().isEqualTo("kept")
    }

    @Test
    fun `only configs have history`() = scenario { store ->
        store.put(instance("order-1"))
        store.history(instanceKey("order-1"), 0).assert().isEmpty()
    }

    // --- leases ---

    @Test
    fun `granted leases are distinct and remember their owner`() = scenario { store ->
        val first = store.grant(owner = "a")
        val second = store.grant(owner = "b")
        first.assert().isNotEqualTo(second)
        store.leaseOwners(namespace, listOf(first, second, MISSING_LEASE))
            .assert().isEqualTo(mapOf(first to "a", second to "b", MISSING_LEASE to null))
    }

    @Test
    fun `keep-alive renews by the lease's own ttl`() = scenario { store ->
        val lease = store.grant()
        store.keepAlive(namespace, listOf(lease, MISSING_LEASE))
            .assert().isEqualTo(mapOf(lease to LONG_TTL, MISSING_LEASE to null))
    }

    @Test
    fun `keep-alive without leases does nothing`() = scenario { store ->
        store.keepAlive(namespace, emptyList()).assert().isEmpty()
    }

    @Test
    fun `keep-alive creates no revision`() = scenario { store ->
        val lease = store.grant()
        store.keepAlive(namespace, listOf(lease))
        position(store).revision.assert().isEqualTo(0)
    }

    @Test
    fun `an expired lease can renew until it is reaped`() = scenario { store ->
        val lease = store.grant(SHORT_TTL)
        delay(EXPIRY_WAIT)
        store.expiredLeases(namespace, 10).assert().containsExactly(lease)
        store.keepAlive(namespace, listOf(lease)).assert().isEqualTo(mapOf(lease to SHORT_TTL))
        store.expiredLeases(namespace, 10).assert().isEmpty()
        store.expireLease(namespace, lease, ACTOR, force = false).assert().isFalse()
    }

    @Test
    fun `a put needs a lease that exists and has not expired`() = scenario { store ->
        store.put(instance("order-1", lease = MISSING_LEASE))
            .assert().isEqualTo(ApplyResult.LeaseNotFound(0, MISSING_LEASE))
        val expired = store.grant(SHORT_TTL)
        delay(EXPIRY_WAIT)
        store.put(instance("order-1", lease = expired)).assert().isEqualTo(ApplyResult.LeaseNotFound(0, expired))
    }

    @Test
    fun `a lease that has not expired is not reaped`() = scenario { store ->
        val lease = store.grant()
        store.put(instance("order-1", lease = lease))
        store.expireLease(namespace, lease, ACTOR, force = false).assert().isFalse()
        store.readAll(namespace).toList().single().meta.name.assert().isEqualTo("order-1")
    }

    @Test
    fun `an expired lease takes its instances in one revision`() = scenario { store ->
        val lease = store.grant(SHORT_TTL)
        store.apply(
            namespace,
            listOf(Mutation.Put(instance("order-1", lease = lease)), Mutation.Put(instance("order-2", lease = lease))),
            ACTOR,
        )
        val start = position(store)
        delay(EXPIRY_WAIT)
        store.expireLease(namespace, lease, "reaper", force = false).assert().isTrue()
        val batch = store.changes(namespace, start).first()
        batch.revision.assert().isEqualTo(start.revision + 1)
        batch.changes.map { it.type }.assert().containsOnly(ChangeType.DELETE)
        batch.changes.map { it.resource.meta.name }.assert().containsExactlyInAnyOrder("order-1", "order-2")
        batch.changes.map { it.resource.meta.updatedBy }.assert().containsOnly("reaper")
        store.readAll(namespace).toList().assert().isEmpty()
    }

    @Test
    fun `revoking removes a lease before it expires`() = scenario { store ->
        val lease = store.grant()
        store.put(instance("order-1", lease = lease))
        store.expireLease(namespace, lease, ACTOR, force = true).assert().isTrue()
        store.readAll(namespace).toList().assert().isEmpty()
        store.keepAlive(namespace, listOf(lease)).assert().isEqualTo(mapOf(lease to null))
        store.leaseOwners(namespace, listOf(lease)).assert().isEqualTo(mapOf(lease to null))
    }

    @Test
    fun `a lease without instances leaves no revision`() = scenario { store ->
        val lease = store.grant()
        store.expireLease(namespace, lease, ACTOR, force = true).assert().isTrue()
        position(store).revision.assert().isEqualTo(0)
    }

    @Test
    fun `expiring a missing lease does nothing`() = scenario { store ->
        store.expireLease(namespace, MISSING_LEASE, ACTOR, force = true).assert().isFalse()
    }

    @Test
    fun `an instance moved to another lease survives the old one`() = scenario { store ->
        val old = store.grant()
        val new = store.grant()
        store.put(instance("order-1", lease = old))
        store.put(instance("order-1", lease = new))
        store.expireLease(namespace, old, ACTOR, force = true).assert().isTrue()
        store.readAll(namespace).toList().single().meta.lease.assert().isEqualTo(new)
        store.expireLease(namespace, new, ACTOR, force = true).assert().isTrue()
        store.readAll(namespace).toList().assert().isEmpty()
    }

    @Test
    fun `an instance taken off its lease survives it`() = scenario { store ->
        val lease = store.grant()
        store.put(instance("order-1", lease = lease))
        store.put(instance("order-1"))
        store.expireLease(namespace, lease, ACTOR, force = true).assert().isTrue()
        store.readAll(namespace).toList().single().meta.name.assert().isEqualTo("order-1")
    }

    @Test
    fun `a deleted instance is not deleted again with its lease`() = scenario { store ->
        val lease = store.grant()
        store.put(instance("order-1", lease = lease))
        store.apply(namespace, listOf(Mutation.Delete(instanceKey("order-1"))), ACTOR)
        val revision = position(store).revision
        store.expireLease(namespace, lease, ACTOR, force = true).assert().isTrue()
        position(store).revision.assert().isEqualTo(revision)
    }

    @Test
    fun `expired leases come longest expired first, up to the limit`() = scenario { store ->
        val first = store.grant(SHORT_TTL)
        delay(5.milliseconds)
        val second = store.grant(SHORT_TTL)
        store.grant()
        delay(EXPIRY_WAIT)
        store.expiredLeases(namespace, 10).assert().containsExactly(first, second)
        store.expiredLeases(namespace, 1).assert().containsExactly(first)
    }

    @Test
    fun `lease stats count the total and the expired`() = scenario { store ->
        store.grant(SHORT_TTL)
        store.grant()
        delay(EXPIRY_WAIT)
        store.leaseStats(namespace).assert().isEqualTo(LeaseStats(2, 1))
    }

    // --- namespace lifecycle ---

    @Test
    fun `a namespace that was never created rejects writes and has no position`() = scenario { store ->
        val absent = "absent-${UUID.randomUUID()}"
        store.namespaceState(absent).assert().isNull()
        store.position(absent).assert().isNull()
        store.apply(absent, emptyList(), ACTOR).assert().isEqualTo(ApplyResult.NamespaceNotActive)
        store.grantLease(absent, LONG_TTL, ACTOR).assert().isEqualTo(GrantResult.NamespaceNotActive)
        store.terminateNamespace(absent).assert().isEqualTo(TerminateResult.NOT_FOUND)
    }

    @Test
    fun `creating an active namespace changes nothing`() = scenario { store ->
        store.put(config())
        val current = position(store)
        store.createNamespace(namespace).assert().isEqualTo(current)
        store.namespaceState(namespace).assert().isEqualTo(NamespaceState.ACTIVE)
    }

    @Test
    fun `a namespace with objects cannot terminate`() = scenario { store ->
        store.put(config())
        store.terminateNamespace(namespace).assert().isEqualTo(TerminateResult.NOT_EMPTY)
        store.namespaceState(namespace).assert().isEqualTo(NamespaceState.ACTIVE)
        store.put(config("still writable")).assert().isEqualTo(ApplyResult.Applied(2, 1))
    }

    @Test
    fun `a terminating namespace rejects writes`() = scenario { store ->
        store.terminateNamespace(namespace).assert().isEqualTo(TerminateResult.TERMINATED)
        store.namespaceState(namespace).assert().isEqualTo(NamespaceState.TERMINATING)
        store.position(namespace).assert().isNull()
        store.put(config()).assert().isEqualTo(ApplyResult.NamespaceNotActive)
        store.grantLease(namespace, LONG_TTL, ACTOR).assert().isEqualTo(GrantResult.NamespaceNotActive)
    }

    @Test
    fun `terminating twice is fine`() = scenario { store ->
        store.terminateNamespace(namespace)
        store.terminateNamespace(namespace).assert().isEqualTo(TerminateResult.TERMINATED)
    }

    @Test
    fun `an active namespace cannot be dropped`() = scenario { store ->
        thrownBy<IllegalStateException> { store.dropNamespace(namespace) }
    }

    @Test
    fun `dropping removes everything and can repeat`() = scenario { store ->
        store.put(config())
        val lease = store.grant()
        store.apply(namespace, listOf(Mutation.Delete(configKey())), ACTOR)
        store.terminateNamespace(namespace)
        store.dropNamespace(namespace)
        store.dropNamespace(namespace)
        store.namespaceState(namespace).assert().isEqualTo(NamespaceState.TERMINATING)
        store.history(configKey(), 0).assert().isEmpty()
        store.keepAlive(namespace, listOf(lease)).assert().isEqualTo(mapOf(lease to null))
        store.readAll(namespace).toList().assert().isEmpty()
    }

    @Test
    fun `dropping a namespace that was never created does nothing`() = scenario { store ->
        val absent = "absent-${UUID.randomUUID()}"
        store.dropNamespace(absent)
        store.namespaceState(absent).assert().isNull()
    }

    @Test
    fun `a late write does not revive a dropped namespace`() = scenario { store ->
        store.terminateNamespace(namespace)
        store.dropNamespace(namespace)
        store.put(config()).assert().isEqualTo(ApplyResult.NamespaceNotActive)
        store.namespaceState(namespace).assert().isEqualTo(NamespaceState.TERMINATING)
    }

    @Test
    fun `creating over a terminating namespace discards what remains`() = scenario { store ->
        store.put(config())
        store.apply(namespace, listOf(Mutation.Delete(configKey())), ACTOR)
        val lease = store.grant()
        store.terminateNamespace(namespace)
        store.createNamespace(namespace).revision.assert().isEqualTo(0)
        store.history(configKey(), 0).assert().isEmpty()
        store.keepAlive(namespace, listOf(lease)).assert().isEqualTo(mapOf(lease to null))
    }

    // --- locks ---

    @Test
    fun `a lock excludes other owners`() = scenario { store ->
        val lock = "lock-$namespace"
        store.tryLock(lock, "a", LONG_TTL).assert().isTrue()
        store.tryLock(lock, "b", LONG_TTL).assert().isFalse()
    }

    @Test
    fun `the same owner renews a lock`() = scenario { store ->
        val lock = "lock-$namespace"
        store.tryLock(lock, "a", SHORT_TTL).assert().isTrue()
        store.tryLock(lock, "a", LONG_TTL).assert().isTrue()
        delay(EXPIRY_WAIT)
        store.tryLock(lock, "b", LONG_TTL).assert().isFalse()
    }

    @Test
    fun `an expired lock is free`() = scenario { store ->
        val lock = "lock-$namespace"
        store.tryLock(lock, "a", SHORT_TTL)
        delay(EXPIRY_WAIT)
        store.tryLock(lock, "b", LONG_TTL).assert().isTrue()
    }

    @Test
    fun `only the owner unlocks`() = scenario { store ->
        val lock = "lock-$namespace"
        store.tryLock(lock, "a", LONG_TTL)
        store.unlock(lock, "b")
        store.tryLock(lock, "b", LONG_TTL).assert().isFalse()
        store.unlock(lock, "a")
        store.tryLock(lock, "b", LONG_TTL).assert().isTrue()
    }

    // --- helpers ---

    private fun scenario(
        options: StoreOptions = StoreOptions(),
        block: suspend CoroutineScope.(Store) -> Unit,
    ): Unit = runBlocking {
        withTimeout(SCENARIO_TIMEOUT) {
            val store = newStore(options)
            store.createNamespace(namespace)
            block(store)
        }
    }

    private fun limits(
        maxBatchOperations: Int = Limits.DEFAULT.maxBatchOperations,
        maxBatchBytes: Int = Limits.DEFAULT.maxBatchBytes,
        configHistorySize: Int = Limits.DEFAULT.configHistorySize,
    ): StoreOptions = StoreOptions(
        limits = Limits(
            maxBatchOperations = maxBatchOperations,
            maxBatchBytes = maxBatchBytes,
            configHistorySize = configHistorySize,
        ),
    )

    private suspend fun position(store: Store): Position = checkNotNull(store.position(namespace))

    /**
     * Follows from [start] until [caughtUpTo], runs [disturb], and returns how the follower failed.
     */
    private suspend fun CoroutineScope.follow(
        store: Store,
        start: Position,
        caughtUpTo: Long,
        disturb: suspend () -> Unit,
    ): TimelineDivergedException {
        val caughtUp = CompletableDeferred<Unit>()
        val follower = async {
            thrownBy<TimelineDivergedException> {
                store.changes(namespace, start).collect {
                    if (it.revision == caughtUpTo) {
                        caughtUp.complete(Unit)
                    }
                }
            }
        }
        caughtUp.await()
        disturb()
        return follower.await()
    }

    private suspend inline fun <reified T : Throwable> thrownBy(crossinline block: suspend () -> Unit): T {
        val failure = runCatching { block() }.exceptionOrNull()
        failure.assert().isInstanceOf(T::class.java)
        return failure as T
    }

    private suspend fun Store.put(
        resource: Resource,
        expectedRevision: Long = Mutation.NO_CHECK,
        actor: String = ACTOR,
    ): ApplyResult = apply(namespace, listOf(Mutation.Put(resource, expectedRevision)), actor)

    private suspend fun Store.grant(ttl: Duration = LONG_TTL, owner: String = ACTOR): Long =
        (grantLease(namespace, ttl, owner) as GrantResult.Granted).lease.id

    private fun config(
        name: String = CONFIG_NAME,
        content: String = "a: 1",
        labels: List<Pair<String, String>> = emptyList(),
    ): Resource {
        val ns = namespace
        return resource {
            meta = objectMeta {
                namespace = ns
                this.name = name
                labels.forEach { (key, value) -> this.labels[key] = value }
            }
            config = configSpec {
                format = ConfigFormat.CONFIG_FORMAT_YAML
                this.content = content
            }
        }
    }

    private fun instance(name: String, lease: Long = 0): Resource {
        val ns = namespace
        return resource {
            meta = objectMeta {
                namespace = ns
                this.name = name
                this.lease = lease
            }
            instance = instanceSpec {
                service = "order"
                endpoint = endpoint {
                    scheme = "http"
                    host = "10.0.0.1"
                    port = 8080
                }
            }
        }
    }

    private fun configKey(name: String = CONFIG_NAME): ResourceKey = key(Kind.KIND_CONFIG, name)

    private fun instanceKey(name: String): ResourceKey = instance(name).key

    private fun key(kind: Kind, name: String): ResourceKey {
        val ns = namespace
        return resourceKey {
            namespace = ns
            this.kind = kind
            this.name = name
        }
    }

    private companion object {
        const val ACTOR = "conformance"
        const val CONFIG_NAME = "application.yaml"
        const val MISSING_LEASE = 9_000_000L
        val SHORT_TTL = 50.milliseconds
        val LONG_TTL = 1.minutes
        val EXPIRY_WAIT = 150.milliseconds
        val SCENARIO_TIMEOUT = 30.seconds
    }
}
