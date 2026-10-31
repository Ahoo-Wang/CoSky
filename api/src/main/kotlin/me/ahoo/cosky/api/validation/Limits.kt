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

/**
 * Resource limits (design §7.1) and lease TTL bounds (§4.3, §7.6). The values are defaults; a server may configure
 * its own.
 *
 * Lengths of values and descriptions count Unicode code points (names and keys are ASCII);
 * [maxConfigContentBytes] counts UTF-8 bytes.
 */
data class Limits(
    val maxConfigContentBytes: Int = MIB,
    val maxMetadataEntries: Int = 64,
    val maxMetadataKeyLength: Int = 63,
    val maxMetadataValueLength: Int = 1024,
    val maxLabels: Int = 32,
    val maxLabelKeyLength: Int = 63,
    val maxLabelValueLength: Int = 253,
    val maxTokens: Int = 2,
    /** `description` of NamespaceSpec, ServiceAccountSpec and RoleSpec. */
    val maxDescriptionLength: Int = 1024,
    val defaultLeaseTtlSeconds: Int = 30,
    val minLeaseTtlSeconds: Int = 5,
    val maxLeaseTtlSeconds: Int = 300,
    /** `names` of one Rule. */
    val maxRuleNames: Int = 64,
    val defaultPageSize: Int = 500,
    val maxPageSize: Int = 1000,
    val maxPageTokenLength: Int = 1024,
    /** Resources in one List page, Snapshot chunk or Events message (§4.6); enforced by the server. */
    val maxMessageBytes: Int = 2 * MIB,
    /** Enforced by the store's batch apply, not by request validation. */
    val maxBatchOperations: Int = 100,
    /** Enforced by the store's batch apply, not by request validation. */
    val maxBatchBytes: Int = DEFAULT_MAX_BATCH_BYTES,
    /** Soft limit: exceeding it raises an alert but writes are not rejected. */
    val namespaceObjectSoftLimit: Int = 100_000,
    /** Config revisions kept in history; enforced by the store. */
    val configHistorySize: Int = 20,
) {
    init {
        require(minLeaseTtlSeconds in 1..maxLeaseTtlSeconds) {
            "minLeaseTtlSeconds must be within [1, maxLeaseTtlSeconds]."
        }
        require(defaultLeaseTtlSeconds in minLeaseTtlSeconds..maxLeaseTtlSeconds) {
            "defaultLeaseTtlSeconds must be within [minLeaseTtlSeconds, maxLeaseTtlSeconds]."
        }
        require(defaultPageSize in 1..maxPageSize) {
            "defaultPageSize must be within [1, maxPageSize]."
        }
        require(maxMessageBytes > maxConfigContentBytes) {
            "maxMessageBytes must hold the largest Config."
        }
    }

    companion object {
        val DEFAULT = Limits()
    }
}

private const val MIB = 1024 * 1024
private const val DEFAULT_MAX_BATCH_BYTES = 8 * MIB
