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
package me.ahoo.cosky.examples.service.consumer

import me.ahoo.cosky.examples.service.provider.client.HelloClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.cloud.openfeign.EnableFeignClients

/**
 * Consumer Server.
 *
 * @author ahoo wang
 */
@SpringBootApplication
@EnableFeignClients(basePackages = ["me.ahoo.cosky.examples.service.provider.client"])
class ConsumerServer : CommandLineRunner {
    @Autowired
    private lateinit var helloClient: HelloClient

    @Throws(Exception::class)
    override fun run(vararg args: String) {
        val rpcResponse = helloClient.hi("consumer")
        log.warn(rpcResponse)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ConsumerServer::class.java)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(ConsumerServer::class.java, *args)
}
