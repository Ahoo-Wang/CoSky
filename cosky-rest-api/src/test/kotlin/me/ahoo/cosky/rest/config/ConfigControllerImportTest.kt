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
package me.ahoo.cosky.rest.config

import io.mockk.every
import io.mockk.mockk
import me.ahoo.cosky.config.redis.RedisConfigService
import me.ahoo.cosky.rest.util.Zips
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.codec.multipart.FilePart
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.test.test

class ConfigControllerImportTest {
    private val configService = mockk<RedisConfigService> {
        every { setConfig(any(), any(), any()) } returns Mono.just(true)
    }
    private val controller = ConfigController(configService)

    private fun filePart(bytes: ByteArray, chunkSize: Int): FilePart {
        val chunks: List<DataBuffer> = bytes.toList().chunked(chunkSize).map {
            DefaultDataBufferFactory.sharedInstance.wrap(it.toByteArray())
        }
        return mockk {
            every { filename() } returns "configs.zip"
            every { content() } returns Flux.fromIterable(chunks)
        }
    }

    @Test
    fun importZipSplitIntoMultipleBuffers() {
        val items = (1..20).map { Zips.ZipItem.of("config-$it.yaml", "key: ${"v".repeat(1024)}-$it") }
        val zipBytes = Zips.zip(items)

        controller.importZip("ns", "overwrite", Mono.just(filePart(zipBytes, chunkSize = 256)))
            .test()
            .expectNext(ImportResponse(total = 20, succeeded = 20))
            .verifyComplete()
    }

    @Test
    fun importZipRejectsOversizedUpload() {
        val zipBytes = ByteArray(ConfigController.MAX_IMPORT_ZIP_SIZE + 1)

        controller.importZip("ns", "overwrite", Mono.just(filePart(zipBytes, chunkSize = 64 * 1024)))
            .test()
            .expectError(IllegalArgumentException::class.java)
            .verify()
    }
}
