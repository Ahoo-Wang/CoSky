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

import io.grpc.StatusException
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
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
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

    suspend fun register(resource: Resource) {
        mutex.withLock {
            if (lease == 0L) {
                lease = grant()
            }
            registrations[resource.meta.name] = resource
            put(resource)
            if (renewal == null) {
                renewal = scope.launch { keepRenewing() }
            }
        }
    }

    suspend fun deregister(name: String) {
        mutex.withLock {
            // In this order: if the server cannot be reached the registration stays, and with it the renewals of an
            // Instance that is still there.
            resources().delete(DeleteRequest.newBuilder().setKey(keyOf(name)).build())
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
        leases().revoke(RevokeRequest.newBuilder().setNamespace(namespace).setLease(lease).build())
        lease = 0
    }

    private suspend fun keepRenewing() {
        val backoff = Backoff(options.reconnectMin, options.reconnectMax)
        while (true) {
            val channel = connection.channel()
            try {
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
     * A failover can lose an Instance while the older lease survives, and renewing keeps succeeding (design D31). So
     * this also runs for a registration still on its way, which is harmless: writing the same content changes nothing.
     */
    private suspend fun putBack() {
        delay(options.reRegisterJitter * Random.nextDouble())
        mutex.withLock {
            registrations.values.forEach { put(it) }
        }
        log.warn("Put back the registrations in namespace {}, since lease {} had lost some.", namespace, lease)
    }

    private suspend fun grant(): Long = leases().grant(
        GrantRequest.newBuilder()
            .setNamespace(namespace)
            .setTtlSeconds(options.leaseTtl.inWholeSeconds.toInt())
            .build(),
    ).lease

    private suspend fun put(resource: Resource) {
        try {
            resources().put(putRequest(resource))
        } catch (e: StatusException) {
            if (CoSkyException.from(e)?.reason != ErrorReason.ERROR_REASON_LEASE_NOT_FOUND) {
                throw e
            }
            lease = grant()
            resources().put(putRequest(resource))
        }
    }

    private fun putRequest(resource: Resource): PutRequest = PutRequest.newBuilder()
        .setResource(resource.toBuilder().apply { metaBuilder.lease = lease })
        .build()

    private fun keyOf(name: String): ResourceKey = ResourceKey.newBuilder()
        .setNamespace(namespace)
        .setKind(Kind.KIND_INSTANCE)
        .setName(name)
        .build()

    private fun leases() = LeaseServiceGrpcKt.LeaseServiceCoroutineStub(connection.channel())

    private fun resources() = ResourceServiceGrpcKt.ResourceServiceCoroutineStub(connection.channel())

    private companion object {
        val log = LoggerFactory.getLogger(LeaseKeeper::class.java)
        const val RENEWALS_PER_TTL = 3
    }
}
