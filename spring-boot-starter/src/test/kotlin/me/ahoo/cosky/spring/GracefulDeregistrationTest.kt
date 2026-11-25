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

package me.ahoo.cosky.spring

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.RouterFunctions
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * An application leaves before its web server stops taking requests, so that consumers stop sending them, while those
 * under way still finish. A graceful shutdown waits for them before the server stops: leaving only after it would keep
 * the instance listed all the while the server refuses connections, as during a rolling update.
 */
class GracefulDeregistrationTest {
    private val server = TestCoSkyServer()
    private var release: CompletableFuture<String>? = null
    private var closing: Thread? = null

    @EnableAutoConfiguration
    class Application {
        /** Counted down once a request is under way, which then waits for [release]. */
        val entered = CountDownLatch(1)
        val release = CompletableFuture<String>()

        @Bean
        fun slow(): RouterFunction<ServerResponse> = RouterFunctions.route()
            .GET("/slow") {
                entered.countDown()
                Mono.fromFuture(release).flatMap { ServerResponse.ok().bodyValue(it) }
            }
            .build()

        @Bean
        fun webServerStopping(): WebServerStopping = WebServerStopping()
    }

    /**
     * Stops right before the web server's graceful shutdown, which is where the server stops taking connections.
     */
    class WebServerStopping : SmartLifecycle {
        @Volatile
        private var running = false

        @Volatile
        var onStop: () -> Unit = {}

        override fun start() {
            running = true
        }

        override fun stop() {
            onStop()
            running = false
        }

        override fun isRunning(): Boolean = running

        override fun getPhase(): Int = WebServerApplicationContext.GRACEFUL_SHUTDOWN_PHASE + 1
    }

    @AfterEach
    fun stop() {
        // Whatever happened, the request under way ends, and with it the shutdown waiting for it.
        release?.complete("done")
        closing?.join(TimeUnit.SECONDS.toMillis(SHUTDOWN_SECONDS))
        server.close()
    }

    private suspend fun eventually(condition: () -> Boolean) {
        withTimeout(15.seconds) {
            while (!condition()) {
                delay(20.milliseconds)
            }
        }
    }

    @Test
    fun `an application leaves before its web server stops taking requests, and those under way finish`() =
        runBlocking<Unit> {
            val context = SpringApplicationBuilder(Application::class.java).run(
                "--spring.application.name=order",
                "--spring.grpc.server.enabled=false",
                "--spring.main.web-application-type=reactive",
                "--server.port=0",
                "--server.shutdown=graceful",
                "--spring.lifecycle.timeout-per-shutdown-phase=${SHUTDOWN_SECONDS}s",
                "--cosky.snapshot.enabled=false",
                "--cosky.endpoints=${server.endpoint}",
                "--cosky.namespace=${TestCoSkyServer.NAMESPACE}",
            )
            eventually { server.instances().isNotEmpty() }
            val application = context.getBean(Application::class.java)
            release = application.release
            val listedWhenServerStops = CompletableFuture<List<Resource>>()
            context.getBean(WebServerStopping::class.java).onStop = {
                listedWhenServerStops.complete(server.instances())
            }
            val port = checkNotNull(context.environment.getProperty("local.server.port", Int::class.java))
            val response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().sendAsync(
                HttpRequest.newBuilder(URI("http://localhost:$port/slow")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
            application.entered.await(SHUTDOWN_SECONDS, TimeUnit.SECONDS).assert().isTrue()

            closing = thread(name = "closing") { context.close() }

            listedWhenServerStops.get(SHUTDOWN_SECONDS, TimeUnit.SECONDS).assert().isEmpty()
            // The server is still there, finishing the request under way.
            response.isDone.assert().isFalse()
            checkNotNull(closing).isAlive.assert().isTrue()

            application.release.complete("done")
            response.get(SHUTDOWN_SECONDS, TimeUnit.SECONDS).body().assert().isEqualTo("done")
            checkNotNull(closing).join(TimeUnit.SECONDS.toMillis(SHUTDOWN_SECONDS))
            checkNotNull(closing).isAlive.assert().isFalse()
            server.instances().assert().isEmpty()
        }

    private companion object {
        const val SHUTDOWN_SECONDS = 20L
    }
}
