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

package me.ahoo.cosky.server

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.net.Socket

/**
 * A kept-alive HTTP connection is read again after every request, including one that waited for the request before it.
 *
 * Reactor Netty queues a request that arrives while the one before it is still being answered. Up to 1.3.7, when the
 * queued request was then answered at once, on the event loop, nothing read the connection again: the next request
 * stayed in the socket until the client gave up. The REST API answers from virtual threads and the Dashboard's files
 * on the event loop, so a browser or a proxy that reuses connections could meet it, as DashboardHostingTest did.
 */
class KeepAliveTest {
    @Test
    fun `a request queued behind another and answered at once leaves the connection read`() {
        RunningServer(mapOf("cosky.store.type" to CoSkyProperties.MEMORY)).use { server ->
            Socket("localhost", server.httpPort).use { socket ->
                socket.soTimeout = READ_TIMEOUT_MILLIS
                val output = socket.getOutputStream()
                val input = BufferedInputStream(socket.getInputStream())

                // In one write, so the file is queued while a virtual thread answers the first request.
                output.write((get("/v1/auth/config") + get("/assets/app.js")).toByteArray(Charsets.US_ASCII))
                input.readStatus().assert().isEqualTo(OK)
                input.readStatus().assert().isEqualTo(OK)

                output.write(get("/v1/auth/config").toByteArray(Charsets.US_ASCII))
                input.readStatus().assert().isEqualTo(OK)
            }
        }
    }

    private fun get(path: String): String = "GET $path HTTP/1.1\r\nHost: localhost\r\n\r\n"

    /**
     * Reads one response, which has a Content-Length, and returns its status code.
     */
    private fun InputStream.readStatus(): Int {
        val status = readLine().split(' ')[1].toInt()
        var length = 0
        generateSequence { readLine() }.takeWhile { it.isNotEmpty() }.forEach { header ->
            if (header.startsWith("content-length:", ignoreCase = true)) {
                length = header.substringAfter(':').trim().toInt()
            }
        }
        readNBytes(length)
        return status
    }

    private fun InputStream.readLine(): String {
        val line = ByteArrayOutputStream()
        while (true) {
            when (val byte = read()) {
                -1 -> throw EOFException("The server closed the connection.")
                '\n'.code -> return line.toString(Charsets.US_ASCII).removeSuffix("\r")
                else -> line.write(byte)
            }
        }
    }

    private companion object {
        const val OK = 200

        // Generous: a connection that is not read again never answers, however long the wait.
        const val READ_TIMEOUT_MILLIS = 10_000
    }
}
