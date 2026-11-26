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

package me.ahoo.cosky.crossversion

import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.Rule
import me.ahoo.cosky.api.v1.Subject
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.namespaceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.v1.roleBindingSpec
import me.ahoo.cosky.api.v1.roleSpec
import me.ahoo.cosky.api.v1.serviceAccountSpec
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.cosky.client.CoSkyClientOptions
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Where the global kinds live. */
const val SYSTEM = "_system"

/** The shortest lease the protocol allows, so that expiry takes seconds. */
val LEASE_TTL: Duration = 5.seconds

/** Longer than the reaper waits on an expired lease: its TTL and a third (design D32), and a tick or two. */
val PAST_REAPING: Duration = 8.seconds

private val virtualThreads = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()

/**
 * Runs a test's coroutines on virtual threads, within [timeout].
 */
fun scenario(timeout: Duration = 3.minutes, block: suspend CoroutineScope.() -> Unit): Unit =
    runBlocking(virtualThreads) { withTimeout(timeout) { block() } }

/**
 * Polls [condition] until it holds, failing with what it last threw once [timeout] passed.
 */
suspend fun eventually(timeout: Duration = 1.minutes, condition: suspend () -> Boolean) {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    var failure: Throwable? = null
    while (true) {
        val result = runCatching { condition() }
        if (result.getOrDefault(false)) {
            return
        }
        failure = result.exceptionOrNull() ?: failure
        if (deadline.hasPassedNow()) {
            throw AssertionError("Still not so after $timeout.", failure)
        }
        delay(100.milliseconds)
    }
}

/** The status a call failed with, or null when it did not. */
suspend fun statusOf(call: suspend () -> Unit): Status.Code? = try {
    call()
    null
} catch (e: StatusException) {
    e.status.code
}

/**
 * A client of the release, on [servers] in turn: an application that knows every replica.
 */
fun client(token: String, vararg servers: CoSkyServer): CoSkyClient = CoSkyClient(
    CoSkyClientOptions(servers.map { it.endpoint }) {
        this.token = token
        snapshotDir = null
        reconnectMin = 20.milliseconds
        reconnectMax = 500.milliseconds
        reRegisterJitter = Duration.ZERO
        leaseTtl = LEASE_TTL
    },
)

fun uniqueName(prefix: String): String = "$prefix-${UUID.randomUUID().toString().take(8)}"

fun namespace(name: String): Resource = resource {
    meta = objectMeta {
        namespace = SYSTEM
        this.name = name
    }
    this.namespace = namespaceSpec { description = "Written by one version, read by the other." }
}

fun config(namespace: String, name: String, content: String, format: ConfigFormat = ConfigFormat.CONFIG_FORMAT_TEXT) =
    resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = name
            labels["written-by"] = "cross-version-test"
        }
        config = configSpec {
            this.format = format
            this.content = content
        }
    }

fun endpoint(host: String, port: Int = 8080): Endpoint =
    Endpoint.newBuilder().setScheme("http").setHost(host).setPort(port).build()

fun instance(namespace: String, name: String, service: String, lease: Long = 0): Resource = resource {
    meta = objectMeta {
        this.namespace = namespace
        this.name = name
        this.lease = lease
    }
    instance = instanceSpec {
        this.service = service
        endpoint = endpoint("10.0.0.1")
        metadata["zone"] = "az1"
    }
}

fun serviceAccount(name: String): Resource = resource {
    meta = objectMeta {
        namespace = SYSTEM
        this.name = name
    }
    serviceAccount = serviceAccountSpec { description = "Bound through one version, used through the other." }
}

fun role(name: String, vararg rules: Rule): Resource = resource {
    meta = objectMeta {
        namespace = SYSTEM
        this.name = name
    }
    role = roleSpec { this.rules += rules.toList() }
}

fun rule(kind: Kind, namespace: String, vararg verbs: Verb): Rule =
    Rule.newBuilder().addKinds(kind).addNamespaces(namespace).addAllVerbs(verbs.toList()).build()

fun roleBinding(name: String, role: String, account: String, namespace: String): Resource = resource {
    meta = objectMeta {
        this.namespace = SYSTEM
        this.name = name
    }
    roleBinding = roleBindingSpec {
        this.role = role
        subjects += Subject.newBuilder().setType(Subject.Type.TYPE_SERVICE_ACCOUNT).setName(account).build()
        namespaces += namespace
    }
}

fun key(namespace: String, kind: Kind, name: String): ResourceKey =
    ResourceKey.newBuilder().setNamespace(namespace).setKind(kind).setName(name).build()

/** Every kind a namespace holds. */
val NAMESPACED_KINDS = listOf(Kind.KIND_CONFIG, Kind.KIND_INSTANCE)

/** Every kind `_system` holds. */
val GLOBAL_KINDS = listOf(Kind.KIND_NAMESPACE, Kind.KIND_SERVICE_ACCOUNT, Kind.KIND_ROLE, Kind.KIND_ROLE_BINDING)
