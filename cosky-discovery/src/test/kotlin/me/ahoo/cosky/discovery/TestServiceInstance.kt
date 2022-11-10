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
package me.ahoo.cosky.discovery

import me.ahoo.cosid.test.MockIdGenerator
import reactor.kotlin.test.test
import java.util.concurrent.ThreadLocalRandom

/**
 * @author ahoo wang
 */
object TestServiceInstance {
    @JvmStatic
    fun randomInstance(): ServiceInstance {
        return createInstance(MockIdGenerator.INSTANCE.generateAsString())
    }

    @JvmStatic
    fun createInstance(serviceId: String): ServiceInstance {
        val instance = Instance.asInstance(serviceId, "http", "127.0.0.1", ThreadLocalRandom.current().nextInt(65535))
        return ServiceInstance(instance, metadata = mapOf("from" to "test"))
    }

    @JvmStatic
    fun randomFixedInstance(): ServiceInstance {
        return randomInstance().copy(isEphemeral = false)
    }

    @JvmStatic
    fun registerRandomInstanceAndTestThenDeregister(
        namespace: String,
        serviceRegistry: ServiceRegistry,
        doTest: (ServiceInstance) -> Unit
    ) {
        val randomInstance = randomInstance()
        serviceRegistry.register(namespace, randomInstance).test().expectNext(true).verifyComplete()
        doTest(randomInstance)
        serviceRegistry.deregister(namespace, randomInstance).test().expectNext(true).verifyComplete()
    }
}
