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
    fun `a blank token, as an unset variable gives, is none, and may stand beside a token file`() {
        TokenSource(CoSkyClientOptions(listOf("a:1")) { token = " " }).current().assert().isNull()
        CoSkyClientOptions(listOf("a:1")) { token = "cosky_id_secret\n" }.token.assert().isEqualTo("cosky_id_secret")

        write("cosky_file_secret", at = 1_000_000)
        val options = CoSkyClientOptions(listOf("a:1")) {
            token = ""
            tokenFile = file
        }
        options.token.assert().isNull()
        TokenSource(options).current().assert().isEqualTo("cosky_file_secret")
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
    fun `a file that cannot be read leaves the last token`() {
        val source = TokenSource(null, file)
        write("cosky_first_secret", at = 1_000_000)
        source.current().assert().isEqualTo("cosky_first_secret")

        // Something that has a modification time but no content to read.
        Files.delete(file)
        Files.createDirectory(file)
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000))

        source.current().assert().isEqualTo("cosky_first_secret")
        TokenSource(null, file).current().assert().isNull()
    }

    @Test
    fun `no file yet is no token yet`() {
        TokenSource(null, file).current().assert().isNull()
    }
}
