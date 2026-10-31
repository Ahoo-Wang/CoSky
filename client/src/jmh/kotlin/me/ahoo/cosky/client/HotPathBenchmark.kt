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

import kotlinx.coroutines.flow.MutableStateFlow
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.Endpoint
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.instanceSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.resource
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State

/**
 * The client's hot paths (design §7.10): picking an instance and reading a Config must neither suspend nor allocate.
 */
@State(Scope.Benchmark)
open class HotPathBenchmark {
    @Param("10", "1000")
    var instances: Int = 0

    private lateinit var balancer: WeightedRandomLoadBalancer
    private lateinit var config: MutableStateFlow<Resource?>

    @Setup
    fun setup() {
        val view = List(instances) { index ->
            resource {
                meta = objectMeta {
                    namespace = "benchmark"
                    name = "order-$index"
                }
                instance = instanceSpec {
                    service = "order"
                    weight = 1 + index % 3
                    endpoint = Endpoint.newBuilder().setScheme("http").setHost("10.0.0.1").setPort(8080).build()
                }
            }
        }
        balancer = WeightedRandomLoadBalancer(MutableStateFlow(view))
        config = MutableStateFlow(
            resource {
                meta = objectMeta {
                    namespace = "benchmark"
                    name = "application.yaml"
                }
                this.config = configSpec {
                    format = ConfigFormat.CONFIG_FORMAT_YAML
                    content = "a: 1"
                }
            },
        )
    }

    @Benchmark
    fun choose(): Resource? = balancer.choose()

    @Benchmark
    fun readConfig(): Resource? = config.value
}
