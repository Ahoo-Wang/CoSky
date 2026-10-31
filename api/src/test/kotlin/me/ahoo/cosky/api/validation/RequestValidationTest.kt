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
package me.ahoo.cosky.api.validation

import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.ViolationCode
import me.ahoo.cosky.api.v1.WatchRequest
import me.ahoo.cosky.api.v1.WatchRequestKt
import me.ahoo.cosky.api.v1.copy
import me.ahoo.cosky.api.v1.deleteRequest
import me.ahoo.cosky.api.v1.getRequest
import me.ahoo.cosky.api.v1.grantRequest
import me.ahoo.cosky.api.v1.historyRequest
import me.ahoo.cosky.api.v1.keepAliveRequest
import me.ahoo.cosky.api.v1.listRequest
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.v1.resourceKey
import me.ahoo.cosky.api.v1.revokeRequest
import me.ahoo.cosky.api.v1.servicesRequest
import me.ahoo.cosky.api.v1.statsRequest
import me.ahoo.cosky.api.v1.topologyRequest
import me.ahoo.cosky.api.v1.watchRequest
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RequestValidationTest {
    private val validator = Validator()

    // --- ResourceKey ---

    @Test
    fun `valid keys of every kind`() {
        listOf(
            Fixtures.configKey,
            key(Fixtures.NAMESPACE, Kind.KIND_INSTANCE, "order-1"),
            key(Fixtures.SYSTEM, Kind.KIND_NAMESPACE, "prod"),
            key(Fixtures.SYSTEM, Kind.KIND_SERVICE_ACCOUNT, "admin"),
            key(Fixtures.SYSTEM, Kind.KIND_ROLE, "viewer"),
            key(Fixtures.SYSTEM, Kind.KIND_ROLE_BINDING, "admin"),
        ).forEach { validator.validate(it).assert().isEmpty() }
    }

    @Test
    fun `key kind is required and other checks wait for it`() {
        validator.validate(ResourceKey.getDefaultInstance()).codes()
            .assert().containsExactly("kind" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `unknown key kind is out of range`() {
        validator.validate(Fixtures.configKey.toBuilder().setKindValue(42).build()).codes()
            .assert().containsExactly("kind" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `key namespace and name follow the kind`() {
        validator.validate(key(Fixtures.NAMESPACE, Kind.KIND_ROLE, "")).codes().assert().containsExactly(
            "namespace" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
            "name" to ViolationCode.VIOLATION_CODE_REQUIRED,
        )
        validator.validate(key(Fixtures.SYSTEM, Kind.KIND_CONFIG, "a b")).codes().assert().containsExactly(
            "namespace" to ViolationCode.VIOLATION_CODE_RESERVED_NAME,
            "name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
        )
    }

    // --- Get ---

    @Test
    fun `get requires a key`() {
        validator.validate(
            getRequest {}
        ).codes().assert().containsExactly("key" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `get prefixes key fields and checks min revision`() {
        validator.validate(getRequest { key = Fixtures.configKey }).assert().isEmpty()
        validator.validate(
            getRequest {
                key = Fixtures.configKey.copy { name = "" }
                minRevision = -1
            },
        ).codes().assert().containsExactly(
            "key.name" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "min_revision" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    // --- List ---

    @Test
    fun `list of instances with every filter`() {
        validator.validate(
            listRequest {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_INSTANCE
                labelSelector["zone"] = "az1"
                service = "order"
                minRevision = 10
                pageSize = 100
                pageToken = "token"
            },
        ).assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 500, 1000])
    fun `list accepts the default and the page size range`(size: Int) {
        validator.validate(
            listRequest {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                pageSize = size
            },
        ).assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 1001])
    fun `list rejects page sizes outside the range`(size: Int) {
        validator.validate(
            listRequest {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                pageSize = size
            },
        ).codes().assert().containsExactly("page_size" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `list page token is at most 1024 characters`() {
        validator.validate(
            listRequest {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                pageToken = "t".repeat(1024)
            },
        ).assert().isEmpty()
        validator.validate(
            listRequest {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                pageToken = "t".repeat(1025)
            },
        ).codes().assert().containsExactly("page_token" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @Test
    fun `list of a global kind uses the system namespace`() {
        validator.validate(
            listRequest {
                namespace = Fixtures.SYSTEM
                kind = Kind.KIND_ROLE
            },
        ).assert().isEmpty()
    }

    @Test
    fun `list requires a kind`() {
        validator.validate(listRequest { namespace = "Bad" }).codes()
            .assert().containsExactly("kind" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `list checks namespace, selector, service filter and revision`() {
        validator.validate(
            listRequest {
                namespace = ""
                kind = Kind.KIND_CONFIG
                labelSelector["k".repeat(64)] = "v"
                service = "order"
                minRevision = -1
            },
        ).codes().assert().containsExactly(
            "namespace" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "label_selector[\"${"k".repeat(64)}\"]" to ViolationCode.VIOLATION_CODE_TOO_LONG,
            "service" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
            "min_revision" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    @Test
    fun `list service filter follows the service rule`() {
        validator.validate(
            listRequest {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_INSTANCE
                service = "Order"
            },
        ).codes().assert().containsExactly("service" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT)
    }

    // --- Put ---

    @Test
    fun `put requires a resource`() {
        validator.validate(
            putRequest {}
        ).codes().assert().containsExactly("resource" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 1, Long.MAX_VALUE])
    fun `put accepts every expected revision mode`(expected: Long) {
        validator.validate(
            putRequest {
                resource = Fixtures.instance
                expectedRevision = expected
            },
        ).assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(longs = [-2, Long.MIN_VALUE])
    fun `put rejects expected revisions below -1`(expected: Long) {
        validator.validate(
            putRequest {
                resource = Fixtures.config
                expectedRevision = expected
            },
        ).codes().assert().containsExactly("expected_revision" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `put prefixes resource fields`() {
        validator.validate(putRequest { resource = Fixtures.config.copy { meta = meta.copy { name = "" } } }).codes()
            .assert().containsExactly("resource.meta.name" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    // --- Delete ---

    @Test
    fun `delete requires a key and a non-negative expected revision`() {
        validator.validate(deleteRequest { key = Fixtures.configKey }).assert().isEmpty()
        validator.validate(
            deleteRequest {
                key = Fixtures.configKey
                expectedRevision = 3
            },
        ).assert().isEmpty()
        validator.validate(deleteRequest { expectedRevision = -1 }).codes().assert().containsExactly(
            "key" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "expected_revision" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    // --- History ---

    @Test
    fun `history is for configs only`() {
        validator.validate(historyRequest { key = Fixtures.configKey }).assert().isEmpty()
        validator.validate(historyRequest { key = key(Fixtures.NAMESPACE, Kind.KIND_INSTANCE, "order-1") }).codes()
            .assert().containsExactly("key.kind" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 20, 21, Int.MAX_VALUE])
    fun `history accepts any non-negative limit, the server clamps it`(limit: Int) {
        validator.validate(
            historyRequest {
                key = Fixtures.configKey
                this.limit = limit
            },
        ).assert().isEmpty()
    }

    @Test
    fun `history requires a key and a non-negative limit`() {
        validator.validate(historyRequest { limit = -1 }).codes().assert().containsExactly(
            "key" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "limit" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    @Test
    fun `history page token is at most 1024 characters`() {
        validator.validate(
            historyRequest {
                key = Fixtures.configKey
                pageToken = "t".repeat(1024)
            },
        ).assert().isEmpty()
        validator.validate(
            historyRequest {
                key = Fixtures.configKey
                pageToken = "t".repeat(1025)
            },
        ).codes().assert().containsExactly("page_token" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    @Test
    fun `history of a key without kind reports only the missing kind`() {
        validator.validate(historyRequest { key = ResourceKey.getDefaultInstance() }).codes()
            .assert().containsExactly("key.kind" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    // --- Watch ---

    @Test
    fun `watch requires create or cancel`() {
        validator.validate(WatchRequest.getDefaultInstance()).codes()
            .assert().containsExactly("request" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `watch cancel is always valid`() {
        validator.validate(watchRequest { cancel = WatchRequestKt.cancel { watchId = 1 } }).assert().isEmpty()
    }

    @Test
    fun `watch create of a whole kind or one object`() {
        validator.validate(
            create {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_INSTANCE
                service = "order"
                fromRevision = 5
                fromEpoch = "epoch"
            },
        ).assert().isEmpty()
        validator.validate(
            create {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                name = "application.yaml"
            },
        ).assert().isEmpty()
    }

    @Test
    fun `watch create checks every field`() {
        validator.validate(
            create {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                name = "a b"
                service = "order"
                fromRevision = -1
            },
        ).codes().assert().containsExactly(
            "create.name" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
            "create.service" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED,
            "create.from_revision" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    @Test
    fun `watch create resuming from a revision requires its epoch`() {
        validator.validate(
            create {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_CONFIG
                fromRevision = 5
            },
        ).codes().assert().containsExactly("create.from_epoch" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `watch create requires a kind`() {
        validator.validate(create { namespace = Fixtures.NAMESPACE }).codes()
            .assert().containsExactly("create.kind" to ViolationCode.VIOLATION_CODE_REQUIRED)
    }

    @Test
    fun `watch create namespace follows the kind`() {
        validator.validate(
            create {
                namespace = Fixtures.NAMESPACE
                kind = Kind.KIND_ROLE_BINDING
            },
        ).codes().assert().containsExactly("create.namespace" to ViolationCode.VIOLATION_CODE_NOT_ALLOWED)
    }

    // --- Leases ---

    @ParameterizedTest
    @ValueSource(ints = [0, 5, 30, 300])
    fun `grant accepts the default and the TTL range`(ttl: Int) {
        validator.validate(
            grantRequest {
                namespace = Fixtures.NAMESPACE
                ttlSeconds = ttl
            },
        ).assert().isEmpty()
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 1, 4, 301, Int.MAX_VALUE])
    fun `grant rejects TTLs outside the range`(ttl: Int) {
        validator.validate(
            grantRequest {
                namespace = Fixtures.NAMESPACE
                ttlSeconds = ttl
            },
        ).codes().assert().containsExactly("ttl_seconds" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `grant follows configured TTL bounds`() {
        val custom = Validator(Limits(minLeaseTtlSeconds = 1, defaultLeaseTtlSeconds = 10, maxLeaseTtlSeconds = 10))
        custom.validate(
            grantRequest {
                namespace = Fixtures.NAMESPACE
                ttlSeconds = 1
            },
        ).assert().isEmpty()
        custom.validate(
            grantRequest {
                namespace = Fixtures.NAMESPACE
                ttlSeconds = 11
            },
        ).codes().assert().containsExactly("ttl_seconds" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE)
    }

    @Test
    fun `leases live in user namespaces`() {
        validator.validate(grantRequest { namespace = Fixtures.SYSTEM }).codes()
            .assert().containsExactly("namespace" to ViolationCode.VIOLATION_CODE_RESERVED_NAME)
    }

    @Test
    fun `keep-alive and revoke need a namespace and a positive lease`() {
        validator.validate(
            keepAliveRequest {
                namespace = Fixtures.NAMESPACE
                lease = 1
            },
        ).assert().isEmpty()
        validator.validate(
            revokeRequest {
                namespace = Fixtures.NAMESPACE
                lease = Long.MAX_VALUE
            },
        ).assert().isEmpty()
        validator.validate(keepAliveRequest {}).codes().assert().containsExactly(
            "namespace" to ViolationCode.VIOLATION_CODE_REQUIRED,
            "lease" to ViolationCode.VIOLATION_CODE_REQUIRED,
        )
        validator.validate(
            revokeRequest {
                namespace = "Prod"
                lease = -1
            },
        ).codes().assert().containsExactly(
            "namespace" to ViolationCode.VIOLATION_CODE_INVALID_FORMAT,
            "lease" to ViolationCode.VIOLATION_CODE_OUT_OF_RANGE,
        )
    }

    // --- Views ---

    @Test
    fun `views need a user namespace`() {
        validator.validate(servicesRequest { namespace = Fixtures.NAMESPACE }).assert().isEmpty()
        validator.validate(topologyRequest { namespace = Fixtures.NAMESPACE }).assert().isEmpty()
        validator.validate(statsRequest { namespace = Fixtures.NAMESPACE }).assert().isEmpty()
        validator.validate(
            servicesRequest {}
        ).codes().assert().containsExactly("namespace" to ViolationCode.VIOLATION_CODE_REQUIRED)
        validator.validate(topologyRequest { namespace = Fixtures.SYSTEM }).codes()
            .assert().containsExactly("namespace" to ViolationCode.VIOLATION_CODE_RESERVED_NAME)
        validator.validate(statsRequest { namespace = "a".repeat(64) }).codes()
            .assert().containsExactly("namespace" to ViolationCode.VIOLATION_CODE_TOO_LONG)
    }

    private fun key(namespace: String, kind: Kind, name: String): ResourceKey = resourceKey {
        this.namespace = namespace
        this.kind = kind
        this.name = name
    }

    private fun create(block: WatchRequestKt.CreateKt.Dsl.() -> Unit): WatchRequest =
        watchRequest { create = WatchRequestKt.create(block) }
}
