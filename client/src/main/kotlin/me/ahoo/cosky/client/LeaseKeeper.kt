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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.ahoo.cosky.api.error.CoSkyException
import me.ahoo.cosky.api.v1.DeleteRequest
import me.ahoo.cosky.api.v1.ErrorReason
import me.ahoo.cosky.api.v1.GrantRequest
import me.ahoo.cosky.api.v1.KeepAliveRequest
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
    private val registrations = ConcurrentHashMap<String, Resource>()
    private var renewal: Job? = null

    @Volatile
    private var lease = 0L

    /**
     * @throws StatusException when the server refuses it. A server out of reach is no reason to fail: the registration
     * is remembered, and goes out as soon as one answers (design §7.5).
     */
    suspend fun register(resource: Resource) {
        mutex.withLock {
            registrations[resource.meta.name] = resource
            try {
                if (lease == 0L) {
                    lease = grant()
                }
                put(resource)
            } catch (e: StatusException) {
                if (e.status.code !in OUT_OF_REACH) {
                    registrations.remove(resource.meta.name)
                    throw e
                }
                log.warn("Could not register {} yet, and keeps trying: {}", resource.meta.name, e.status)
            }
            if (renewal == null) {
                renewal = scope.launch { keepRenewing() }
            }
        }
    }

    suspend fun deregister(name: String) {
        mutex.withLock {
            // In this order: if the server cannot be reached the registration stays, and with it the renewals of an
            // Instance that is still there.
            resources().timed().delete(DeleteRequest.newBuilder().setKey(keyOf(name)).build())
            registrations.remove(name)
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

    private suspend fun revoke() {
        renewal?.cancel()
        renewal = null
        // None was ever granted if the server has been away since the first registration.
        if (lease != 0L) {
            leases().timed().revoke(RevokeRequest.newBuilder().setNamespace(namespace).setLease(lease).build())
            lease = 0
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
                log.warn("Renewing the lease of namespace {} failed: {}", namespace, e.status)
            }
            connection.rotate(channel)
            delay(backoff.next())
        }
    }

    private suspend fun renewOnStream(backoff: Backoff) {
        val renewals = flow {
            while (true) {
                // The first renewal goes out right after connecting, without waiting for the next period (design §6.2).
                emit(KeepAliveRequest.newBuilder().setNamespace(namespace).setLease(lease).build())
                delay(options.leaseTtl / RENEWALS_PER_TTL)
            }
        }
        leases().keepAlive(renewals).collect { response ->
            backoff.reset()
            when {
                response.lease != lease -> Unit
                response.ttlSeconds == 0 -> restore()
                response.attachedCount < registrations.size -> putBack()
            }
        }
    }

    /**
     * Grants a new lease and writes every registration again, after a little jitter so that a whole fleet losing its
     * leases does not arrive at once.
     */
    private suspend fun restore() {
        delay(options.reRegisterJitter * Random.nextDouble())
        mutex.withLock {
            lease = grant()
            registrations.values.forEach { put(it) }
        }
        log.info("Restored {} registrations in namespace {} with lease {}.", registrations.size, namespace, lease)
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
     * @return false if the Instance is there already, whatever it holds.
     */
    private suspend fun createIfMissing(resource: Resource): Boolean = try {
        put(resource, Validator.MUST_NOT_EXIST)
        true
    } catch (e: StatusException) {
        if (CoSkyException.from(e)?.reason != ErrorReason.ERROR_REASON_REVISION_CONFLICT) {
            throw e
        }
        false
    }

    private suspend fun grant(): Long = leases().timed().grant(
        GrantRequest.newBuilder()
            .setNamespace(namespace)
            .setTtlSeconds(options.leaseTtl.inWholeSeconds.toInt())
            .build(),
    ).lease

    private suspend fun put(resource: Resource, expectedRevision: Long = NO_CHECK) {
        try {
            resources().timed().put(putRequest(resource, expectedRevision))
        } catch (e: StatusException) {
            if (CoSkyException.from(e)?.reason != ErrorReason.ERROR_REASON_LEASE_NOT_FOUND) {
                throw e
            }
            lease = grant()
            resources().timed().put(putRequest(resource, expectedRevision))
        }
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

    private fun leases() = LeaseServiceGrpcKt.LeaseServiceCoroutineStub(connection.channel())

    private fun resources() = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(connection.channel())

    /**
     * For a single call: a server that accepts it and never answers must not hold up everything behind the mutex.
     */
    private fun <S : AbstractStub<S>> S.timed(): S =
        withDeadlineAfter(options.callTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    private companion object {
        val log = LoggerFactory.getLogger(LeaseKeeper::class.java)
        const val RENEWALS_PER_TTL = 3

        /** `expected_revision` meaning no check at all. */
        const val NO_CHECK = 0L
        val OUT_OF_REACH = setOf(Status.Code.UNAVAILABLE, Status.Code.DEADLINE_EXCEEDED)
    }
}
