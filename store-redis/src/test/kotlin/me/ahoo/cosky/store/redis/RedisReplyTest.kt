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

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.ahoo.cosky.store.StoreUnavailableException
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.cancellation.CancellationException

class RedisReplyTest {
    @Test
    fun `a command the driver cancels is the store being unavailable`() = runBlocking<Unit> {
        val cancelled = CompletableFuture<String>().apply { cancel(false) }

        runCatching { cancelled.reply() }.exceptionOrNull()
            .assert().isInstanceOf(StoreUnavailableException::class.java)
    }

    @Test
    fun `the caller being cancelled stays cancellation`() = runBlocking<Unit> {
        var thrown: Throwable? = null
        val waiting = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                CompletableFuture<String>().reply()
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }

        waiting.cancelAndJoin()

        thrown.assert().isInstanceOf(CancellationException::class.java)
            .isNotInstanceOf(StoreUnavailableException::class.java)
    }
}
