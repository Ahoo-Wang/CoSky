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

package me.ahoo.cosky.store.redis

/**
 * The image these tests run on, which CI varies over the versions design D31 promises.
 */
internal val REDIS_IMAGE: String = System.getenv("COSKY_REDIS_IMAGE") ?: "redis:7.4-alpine"

/**
 * A shell command that starts a Redis Cluster node of [REDIS_IMAGE] in the background, with [settings] besides its own.
 */
internal fun clusterNode(port: Int, settings: String = ""): String =
    "redis-server --port $port --cluster-enabled yes --cluster-config-file n$port.conf --daemonize yes --save '' " +
        "--appendonly no $settings $TRUSTED_CLUSTER_BUS"

/**
 * Redis 8.12 refuses to run a cluster bus without TLS unless told that only trusted hosts reach it. Redis before 8.10
 * and Valkey refuse that setting, Redis 8.0 only once it has gone to the background, so the shell asks the version.
 */
private const val TRUSTED_CLUSTER_BUS = "\$(redis-server --version | awk '{ split(substr(\$3, 3), v, \".\"); " +
    "if (\$1 == \"Redis\" && (v[1] > 8 || (v[1] == 8 && v[2] >= 12))) " +
    "print \"--cluster-bus-port-protected-mode no\" }')"
