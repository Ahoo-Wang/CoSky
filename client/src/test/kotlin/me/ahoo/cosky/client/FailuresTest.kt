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

import io.grpc.Status
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.net.ConnectException

class FailuresTest {
    @Test
    fun `a failure names what lies under gRPC's io exception`() {
        val failure = Status.UNAVAILABLE.withDescription("io exception")
            .withCause(ConnectException("Connection refused: localhost/127.0.0.1:9090")).asException()

        describe(failure).assert().isEqualTo("UNAVAILABLE: io exception (Connection refused: localhost/127.0.0.1:9090)")
    }

    @Test
    fun `a refusal says what the server said, once`() {
        val failure = Status.UNAUTHENTICATED.withDescription("A valid token is required.").asRuntimeException()

        describe(failure).assert().isEqualTo("UNAUTHENTICATED: A valid token is required.")
        describe(IllegalStateException("closed")).assert().isEqualTo("java.lang.IllegalStateException: closed")
    }
}
