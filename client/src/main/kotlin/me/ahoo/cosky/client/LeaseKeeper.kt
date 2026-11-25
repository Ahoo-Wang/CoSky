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

package me.ahoo.cosky.client

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.stub.AbstractStub
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.GetRequest
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.KeepAliveRequest
import me.ahoo.cosky.api.v1.KeepAliveResponse
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.LeaseServiceGrpcKt
import me.ahoo.cosky.api.v1.PutRequest
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ResourceServiceGrpcKt
import me.ahoo.cosky.api.v1.RevokeRequest
import me.ahoo.cosky.api.validation.Validator
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource
import io.grpc.Channel as GrpcChannel

/**
 * Keeps one lease per namespace alive and remembers every registration, so that they can be restored when the lease is
 * lost (design §6.2, §7.5).
 *
 * Only `ttl_seconds = 0` or `LEASE_NOT_FOUND` mean the lease is lost. A failing stream does not: the lease may well
 * outlive the outage, so the registrations stay and renewal resumes as soon as a server answers.
 */
internal class LeaseKeeper(
    private val namespace: String,
    private val connection: Connection,
    private val options: CoSkyClientOptions,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Every registration as last made: what to restore should the lease be lost. */
    private val registrations = ConcurrentHashMap<String, Resource>()

    /** What the server last accepted under each name: what a registration it refuses later gives way to. */
    private val confirmed = ConcurrentHashMap<String, Resource>()

    /**
     * Names whose latest registration was made while no server answered, so it has not reached one, or not for certain,
     * and goes out as soon as one answers. Comparing it with what was confirmed would not do: it may hold just that,
     * and still have to go out, since an operator may have changed the Instance in between.
     */
    private val pending = ConcurrentHashMap.newKeySet<String>()

    /**
     * Registrations of new names cancelled on their way, which the server took for all the caller knows. The Instance
     * would live on with the lease the other registrations renew, and nobody would hold a handle to close it, so each
     * is withdrawn unless its name is registered again first.
     */
    private val withdrawals = ConcurrentHashMap<String, Withdrawal>()

    /**
     * The handle of each name's latest registration, the only one that closes it: one closed already, or replaced by a
     * later registration of the name, would otherwise delete what that one registered.
     */
    private val owners = ConcurrentHashMap<String, Registration>()

    private var renewal: Job? = null

    @Volatile
    private var lease = 0L

    /**
     * Returns once the client holds the registration. A server out of reach is no reason to fail: the registration is
     * remembered, and goes out as soon as one answers (design §7.5).
     *
     * @return the handle that closes it, until the name is registered again.
     * @throws StatusException when the server refuses it. Then, as when the call is cancelled, the client holds what it
     * held before: the caller has no [Registration] to close, so nothing may stay behind that would be put again.
     */
    suspend fun register(resource: Resource): Registration =
        mutex.withLock {
            val name = resource.meta.name
            val previous = registrations.put(name, resource)
            withdrawals.remove(name)
            // One channel for the whole attempt, so that a failure names where it happened, even if a stream has
            // moved the connection on meanwhile.
            val channel = connection.channel()
            try {
                if (lease == 0L) {
                    lease = grant(channel)
                }
                put(resource, channel = channel)
                confirmed[name] = resource
                pending.remove(name)
            } catch (e: CancellationException) {
                giveWay(name, previous)
                if (previous == null) {
                    withdrawLater(resource)
                }
                throw e
            } catch (e: StatusException) {
                if (e.status.code !in OUT_OF_REACH) {
                    // The server keeps what an earlier registration of this name put there, so the client keeps it
                    // too: it is what to restore should the lease be lost.
                    giveWay(name, previous)
                    throw e
                }
                log.warn(
                    "Could not register {} at {} yet, and keeps trying: {}",
                    name,
                    channel.authority(),
                    describe(e),
                )
                pending.add(name)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // A call can fail without a status, as one does that starts just as an in-process server stops. That
                // is no refusal, and whether the server got it is not certain, so it goes out again once one answers.
                log.warn("Could not register {} yet, and keeps trying.", name, e)
                pending.add(name)
            }
            if (renewal == null) {
                renewal = scope.launch { keepRenewing() }
            }
            Registration(name, this).also { owners[name] = it }
        }

    /**
     * Deletes the Instance of [registration], unless it was closed already or its name registered again since: then
     * this does nothing. One no server has accepted yet is not deleted by its name, which may be another's: whatever
     * of it landed after all is withdrawn from this client's lease instead.
     *
     * Throws when the Instance cannot be deleted, and then leaves the registration as it was: its renewals go on, and
     * closing it can be tried again. Once the Instance is deleted nothing fails it: revoking the lease after the last
     * one is best effort.
     */
    suspend fun deregister(registration: Registration) {
        mutex.withLock {
            val name = registration.instanceName
            if (owners[name] !== registration) {
                return
            }
            if (confirmed.containsKey(name)) {
                // In this order: if the server cannot be reached the registration stays, and with it the renewals of
                // an Instance that is still there.
                resources().timed().delete(DeleteRequest.newBuilder().setKey(keyOf(name)).build())
            } else {
                // Not yet out, or out without an answer: the Instance of that name, if any, is someone else's, unless
                // a Put landed after all, which only this client's lease on it would show.
                registrations[name]?.let { withdrawLater(it) }
            }
            registrations.remove(name)
            confirmed.remove(name)
            pending.remove(name)
            owners.remove(name)
            if (registrations.isEmpty()) {
                revoke()
            }
        }
    }

    /**
     * Revoking removes the Instances at once; if it cannot be done in time the server expires the lease.
     */
    suspend fun close() {
        mutex.withLock {
            if (lease != 0L) {
                revoke()
            }
        }
    }

    /**
     * Best effort: a lease that cannot be revoked is no longer renewed, so the server expires it by itself.
     */
    private suspend fun revoke() {
        renewal?.cancel()
        renewal = null
        // None was ever granted if the server has been away since the first registration.
        if (lease == 0L) {
            return
        }
        try {
            leases().timed().revoke(RevokeRequest.newBuilder().setNamespace(namespace).setLease(lease).build())
            lease = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: StatusException) {
            log.warn("Could not revoke lease {} of namespace {}, so it will expire: {}", lease, namespace, e.status)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            log.warn("Could not revoke lease {} of namespace {}, so it will expire.", lease, namespace, e)
        }
    }

    private suspend fun keepRenewing() {
        val backoff = Backoff(options.reconnectMin, options.reconnectMax)
        while (true) {
            val channel = connection.channel()
            try {
                if (lease == 0L) {
                    restore()
                }
                renewOnStream(backoff)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StatusException) {
                log.warn(
                    "Renewing the lease of namespace {} at {} failed: {}",
                    namespace,
                    channel.authority(),
                    describe(e),
                )
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // A call can fail without a status, as one does that starts just as an in-process server stops.
                // Letting it end this loop would end the renewals for good, and every registration with its lease.
                log.warn("Renewing the lease of namespace {} failed.", namespace, e)
            }
            connection.rotate(channel)
            delay(backoff.next())
        }
    }

    private suspend fun renewOnStream(backoff: Backoff): Unit = coroutineScope {
        val renewals = flow {
            while (true) {
                // The first renewal goes out right after connecting, without waiting for the next period (design §6.2).
                emit(KeepAliveRequest.newBuilder().setNamespace(namespace).setLease(lease).build())
                delay(options.leaseTtl / RENEWALS_PER_TTL)
            }
        }
        val responses = Channel<KeepAliveResponse>()
        launch {
            leases().keepAlive(renewals).collect { responses.send(it) }
            responses.close()
        }
        val silence = silenceOf(options.leaseTtl, options.callTimeout)
        while (true) {
            val response = withTimeoutOrNull(silence) { responses.receiveCatching() }
                ?: throw StatusException(Status.DEADLINE_EXCEEDED.withDescription("No renewal answered in $silence."))
            val answered = response.getOrNull() ?: break
            backoff.reset()
            when {
                answered.lease != lease -> Unit
                answered.ttlSeconds == 0 -> restore()
                else -> catchUp(answered.attachedCount)
            }
        }
    }

    /**
     * Brings a lease that is still there up to date. The count may predate what was just sent, which is harmless:
     * putting back creates only what is missing.
     */
    private suspend fun catchUp(attachedCount: Int) {
        withdraw()
        putUnconfirmed()
        if (attachedCount < registrations.size) {
            putBack()
        }
    }

    /**
     * Grants a new lease and writes every registration again, after a little jitter so that a whole fleet losing its
     * leases does not arrive at once.
     */
    private suspend fun restore() {
        delay(options.reRegisterJitter * Random.nextDouble())
        val restored = mutex.withLock {
            lease = grant()
            // One refused gives way to the registration it would have replaced, which the next renewal finds missing.
            registrations.toList().count { (name, resource) -> send(name, resource) }
        }
        log.info("Restored {} registrations in namespace {} with lease {}.", restored, namespace, lease)
    }

    /**
     * Sends what the server has not confirmed. Mostly there is nothing, which needs no lock to see.
     *
     * Unlike putting back, this replaces what the server holds: it is a registration the application made later, which
     * the server would have taken then had it answered.
     */
    private suspend fun putUnconfirmed() {
        if (pending.isEmpty()) {
            return
        }
        mutex.withLock {
            pending.toList().forEach { name -> registrations[name]?.let { send(name, it) } }
        }
    }

    /**
     * Puts a registration and remembers that the server has it. One the server refuses, having never accepted it, gives
     * way to the registration it would have replaced, as in [register], rather than being sent again and again. Where
     * there was none, its handle closes nothing: whatever holds the name now is not this client's.
     *
     * One it accepted before and refuses now, as when its service may no longer be written, stays, and goes again with
     * the next renewal: the refusal may pass, and what is pending still has to go out. It must not hold up the others:
     * they are what this is restoring.
     *
     * @return whether the server took it.
     */
    private suspend fun send(name: String, resource: Resource): Boolean = try {
        put(resource)
        confirmed[name] = resource
        pending.remove(name)
        true
    } catch (e: StatusException) {
        if (e.status.code in OUT_OF_REACH) {
            throw e
        }
        val previous = confirmed[name]
        if (previous == resource) {
            log.warn("The server refused {}, which it had accepted before: {}", name, describe(e))
        } else {
            log.error("The server refused {}, registered while it was out of reach: {}", name, e.status)
            giveWay(name, previous)
            pending.remove(name)
            if (previous == null) {
                owners.remove(name)
            }
        }
        false
    }

    /**
     * Deletes what cancelled registrations may have created: only an Instance still on the lease it was sent on, and at
     * the revision the store holds, so that one written since, by this client or anyone, stays. One that cannot be
     * settled now is tried again with the next renewal.
     */
    private suspend fun withdraw() {
        if (withdrawals.isEmpty()) {
            return
        }
        mutex.withLock {
            withdrawals.toList().forEach { (name, withdrawal) ->
                val lease = withdrawal.sent.meta.lease
                val done = registrations.containsKey(name) ||
                    // Cancelled before a lease was granted, so before anything was sent.
                    lease == 0L ||
                    // On a lease no longer renewed, which the reaper removes with the Instances on it.
                    lease != this.lease ||
                    tryWithdraw(withdrawal)
                if (done) {
                    withdrawals.remove(name, withdrawal)
                }
            }
        }
    }

    /**
     * Asks the store, not a replica's view, which may not have the write yet: a Delete at a revision nothing has
     * answers with the revision the Instance is at, 0 when there is none, and changes nothing. Then the Instance is
     * read as of that revision, to see whose it is.
     *
     * @return whether nothing is left to withdraw: deleted, never there, or no longer this client's.
     */
    private suspend fun tryWithdraw(withdrawal: Withdrawal): Boolean = try {
        val key = keyOf(withdrawal.sent.meta.name)
        val revision = storedRevision(key)
        when {
            // Absent for good only once the cancelled Put can no longer land.
            revision == 0L -> withdrawal.settled.hasPassedNow()
            else -> {
                val current = resources().timed()
                    .get(GetRequest.newBuilder().setKey(key).setMinRevision(revision).build())
                if (current.meta.revision == revision && current.meta.lease == withdrawal.sent.meta.lease) {
                    resources().timed()
                        .delete(DeleteRequest.newBuilder().setKey(key).setExpectedRevision(revision).build())
                    log.info("Withdrew {}, whose registration was cancelled after the server took it.", key.name)
                }
                true
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: StatusException) {
        when (CoSkyException.from(e)?.reason) {
            // Changed, or deleted, since the store answered: by someone who owns it now.
            ErrorReason.ERROR_REASON_REVISION_CONFLICT, ErrorReason.ERROR_REASON_RESOURCE_NOT_FOUND -> true
            else -> {
                log.warn("Could not withdraw {} yet: {}", withdrawal.sent.meta.name, describe(e))
                e.status.code !in OUT_OF_REACH
            }
        }
    }

    /**
     * The revision the store holds [key] at, 0 if none, from the conflict of a Delete at a revision that cannot match.
     */
    private suspend fun storedRevision(key: ResourceKey): Long {
        try {
            resources().timed().delete(DeleteRequest.newBuilder().setKey(key).setExpectedRevision(UNMATCHABLE).build())
        } catch (e: StatusException) {
            val conflict = CoSkyException.from(e)
            if (conflict?.reason == ErrorReason.ERROR_REASON_REVISION_CONFLICT) {
                return conflict.detail.revisionConflict.currentRevision
            }
            throw e
        }
        error("A Delete of ${key.name} applied at revision $UNMATCHABLE.")
    }

    /**
     * Withdraws what [resource] may have created on the current lease, unless its name is registered again first: a Put
     * that did not answer may still land.
     */
    private fun withdrawLater(resource: Resource) {
        withdrawals[resource.meta.name] = Withdrawal(
            resource.toBuilder().apply { metaBuilder.lease = lease }.build(),
            TimeSource.Monotonic.markNow() + options.callTimeout,
        )
        scope.launch { withdraw() }
    }

    /** Takes back a registration of [name], so that the one it would have replaced, if any, is what stays. */
    private fun giveWay(name: String, previous: Resource?) {
        if (previous == null) registrations.remove(name) else registrations[name] = previous
    }

    /**
     * A failover can lose an Instance while the older lease survives, and renewing keeps succeeding (design D31).
     *
     * Only the missing ones are created again. The others may hold what an operator has changed since, such as a weight
     * of 0 that drains one (design §6.2), which putting the registration again would undo. Creating also leaves alone
     * an Instance created meanwhile, and a registration that was still on its way when the server counted.
     */
    private suspend fun putBack() {
        delay(options.reRegisterJitter * Random.nextDouble())
        val created = mutex.withLock {
            registrations.values.count { createIfMissing(it) }
        }
        if (created > 0) {
            log.warn("Put back {} registrations in namespace {}, which lease {} had lost.", created, namespace, lease)
        }
    }

    /**
     * @return false if the Instance is there already, whatever it holds, or the server refuses it now: the others are
     * put back all the same, and it is tried again with the next renewal that finds it missing. Not every Instance of
     * the name comes back as a conflict: one of another service, or of a service this one may no longer write, is
     * refused before the store is asked.
     */
    private suspend fun createIfMissing(resource: Resource): Boolean = try {
        put(resource, Validator.MUST_NOT_EXIST)
        true
    } catch (e: StatusException) {
        when {
            CoSkyException.from(e)?.reason == ErrorReason.ERROR_REASON_REVISION_CONFLICT -> false
            e.status.code in OUT_OF_REACH -> throw e
            else -> {
                log.warn("The server refused to put back {}: {}", resource.meta.name, describe(e))
                false
            }
        }
    }

    private suspend fun grant(channel: GrpcChannel = connection.channel()): Long = leases(channel).timed().grant(
        GrantRequest.newBuilder()
            .setNamespace(namespace)
            .setTtlSeconds(options.leaseTtl.inWholeSeconds.toInt())
            .build(),
    ).lease

    private suspend fun put(
        resource: Resource,
        expectedRevision: Long = NO_CHECK,
        channel: GrpcChannel = connection.channel(),
    ) {
        try {
            resources(channel).timed().put(putRequest(resource, expectedRevision))
        } catch (e: StatusException) {
            if (CoSkyException.from(e)?.reason != ErrorReason.ERROR_REASON_LEASE_NOT_FOUND) {
                throw e
            }
            replaceLease(channel, except = resource.meta.name)
            resources(channel).timed().put(putRequest(resource, expectedRevision))
        }
    }

    /**
     * The lease is lost, as when renewing finds it gone (design §7.5): a new one is granted, and every other
     * registration goes onto it now, before the reaper removes the old lease with them. One that cannot go now is sent
     * with the next renewal. Called with the lock held, by whoever put first.
     */
    private suspend fun replaceLease(channel: GrpcChannel, except: String) {
        val lost = lease
        lease = grant(channel)
        val others = registrations.filterKeys { it != except }
        pending += others.keys
        others.forEach { (name, resource) ->
            try {
                resources(channel).timed().put(putRequest(resource, NO_CHECK))
                confirmed[name] = resource
                pending.remove(name)
            } catch (e: CancellationException) {
                throw e
            } catch (e: StatusException) {
                log.warn("Could not move {} from lease {} onto {} yet: {}", name, lost, lease, describe(e))
            }
        }
        log.info("Lease {} of namespace {} was lost; {} is its replacement.", lost, namespace, lease)
    }

    private fun putRequest(resource: Resource, expectedRevision: Long): PutRequest = PutRequest.newBuilder()
        .setResource(resource.toBuilder().apply { metaBuilder.lease = lease })
        .setExpectedRevision(expectedRevision)
        .build()

    private fun keyOf(name: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(namespace)
        .setKind(Kind.KIND_INSTANCE)
        .setName(name)
        .build()

    private fun leases(channel: GrpcChannel = connection.channel()) =
        LeaseServiceGrpcKt.LeaseServiceCoroutineStub(channel)

    private fun resources(channel: GrpcChannel = connection.channel()) =
        ResourceServiceGrpcKt.ResourceServiceCoroutineStub(channel)

    /**
     * For a single call: a server that accepts it and never answers must not hold up everything behind the mutex.
     */
    private fun <S : AbstractStub<S>> S.timed(): S =
        withDeadlineAfter(options.callTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    internal companion object {
        private val log = LoggerFactory.getLogger(LeaseKeeper::class.java)
        private const val RENEWALS_PER_TTL = 3

        /**
         * How long a renewal stream may answer nothing before it counts as gone, whatever its transport says: every
         * renewal is answered, and waiting on would let the lease run out unnoticed. Only the wait counts, not handling
         * an answer.
         *
         * A period and a call's time, but never more than two periods. The server keeps a lease nobody renews for 4/3
         * of its TTL, and removes it with its Instances then (design D32); it renews one that has expired until then.
         * Giving up two periods after the last answer leaves a whole period, a third of the TTL and 5/3 s at the
         * least, to reconnect and renew before the lease even expires, and two before the reaper comes. An answer is
         * still waited for a period after it was due. The default TTL of 30 s and call timeout of 10 s give 20 s
         * either way; a short TTL would otherwise outwait the reaper, as 6 s does with 2 + 10.
         */
        fun silenceOf(leaseTtl: Duration, callTimeout: Duration): Duration =
            minOf(leaseTtl / RENEWALS_PER_TTL + callTimeout, leaseTtl * (RENEWALS_PER_TTL - 1) / RENEWALS_PER_TTL)

        /** `expected_revision` meaning no check at all. */
        private const val NO_CHECK = 0L

        /** An `expected_revision` beyond any a namespace reaches. */
        private const val UNMATCHABLE = Long.MAX_VALUE
        private val OUT_OF_REACH = setOf(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED)
    }
}

/**
 * A registration to withdraw, as sent: with the lease it went on, and when the Put can no longer land, its deadline.
 */
private class Withdrawal(val sent: Resource, val settled: TimeSource.Monotonic.ValueTimeMark)
