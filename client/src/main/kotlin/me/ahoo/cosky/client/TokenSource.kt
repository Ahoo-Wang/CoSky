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

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

/**
 * The token a call carries: the one set, or what a file holds, read again once the file changes. A token rotated on
 * disk then goes out from the next call on, and a stream the server ends for the old one opens again with the new one,
 * with no restart.
 *
 * A file that is gone for a moment, or empty, as while a mounted secret is swapped, leaves the last token in use.
 */
internal class TokenSource(private val token: String?, private val file: Path?) {
    private class Read(val modified: FileTime, val value: String)

    @Volatile
    private var last: Read? = null

    constructor(options: CoSkyClientOptions) : this(options.token, options.tokenFile)

    fun current(): String? {
        val file = file ?: return token
        val known = last
        val modified = try {
            Files.getLastModifiedTime(file)
        } catch (e: IOException) {
            log.debug("Cannot read the token file {}; keeping the last token: {}", file, e.message)
            return known?.value
        }
        if (known != null && known.modified == modified) {
            return known.value
        }
        val value = try {
            Files.readString(file).trim()
        } catch (e: IOException) {
            log.debug("Cannot read the token file {}; keeping the last token: {}", file, e.message)
            return known?.value
        }
        if (value.isEmpty()) {
            return known?.value
        }
        last = Read(modified, value)
        return value
    }

    private companion object {
        val log = LoggerFactory.getLogger(TokenSource::class.java)
    }
}
