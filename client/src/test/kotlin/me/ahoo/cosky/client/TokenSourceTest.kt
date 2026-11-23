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

import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

class TokenSourceTest {
    @TempDir
    lateinit var dir: Path

    private val file: Path
        get() = dir.resolve("token")

    /** Writes [content] as a change the source can tell, whatever the file system's clock resolution. */
    private fun write(content: String, at: Long) {
        Files.writeString(file, content)
        Files.setLastModifiedTime(file, FileTime.fromMillis(at))
    }

    @Test
    fun `a token that is set is the one sent`() {
        TokenSource("cosky_id_secret", null).current().assert().isEqualTo("cosky_id_secret")
        TokenSource(null, null).current().assert().isNull()
    }

    @Test
    fun `a file's token is read without its line break, and again once it changes`() {
        val source = TokenSource(null, file)
        write("cosky_first_secret\n", at = 1_000_000)
        source.current().assert().isEqualTo("cosky_first_secret")

        write("cosky_second_secret", at = 2_000_000)

        source.current().assert().isEqualTo("cosky_second_secret")
    }

    @Test
    fun `a file gone for a moment or empty leaves the last token`() {
        val source = TokenSource(null, file)
        write("cosky_first_secret", at = 1_000_000)
        source.current().assert().isEqualTo("cosky_first_secret")

        write("", at = 2_000_000)
        source.current().assert().isEqualTo("cosky_first_secret")
        Files.delete(file)
        source.current().assert().isEqualTo("cosky_first_secret")
    }

    @Test
    fun `no file yet is no token yet`() {
        TokenSource(null, file).current().assert().isNull()
    }
}
