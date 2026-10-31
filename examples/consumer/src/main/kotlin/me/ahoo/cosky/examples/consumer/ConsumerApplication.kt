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

package me.ahoo.cosky.examples.consumer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cloud.client.loadbalancer.LoadBalanced
import org.springframework.context.annotation.Bean
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@SpringBootApplication
class ConsumerApplication {
    /**
     * `@LoadBalanced` resolves the host of a URL as a service name, from the instances CoSky knows of.
     */
    @Bean
    @LoadBalanced
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()
}

@RestController
class CallController(builder: WebClient.Builder) {
    private val provider = builder.baseUrl("http://provider").build()

    @GetMapping("/call/{name}")
    suspend fun call(@PathVariable name: String): String =
        provider.get().uri("/hello/{name}", name).retrieve().awaitBody()
}

fun main(args: Array<String>) {
    runApplication<ConsumerApplication>(*args)
}
