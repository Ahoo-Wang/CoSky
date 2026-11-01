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

package me.ahoo.cosky.server.adapter.rest

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import me.ahoo.cosky.api.v1.Resource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.CharacterCodingException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The zip format of Config import and export (design §4.5): each entry path is a Config name.
 */
internal object ConfigArchive {
    const val MAX_ENTRIES = 10_000
    private val SIGNATURE = "PK".encodeToByteArray()

    /**
     * Unpacks one entry at a time as [block] asks for it, so that a small archive cannot exhaust the memory, and a
     * caller who may not write gets no further than the first entry.
     *
     * @param maxEntryBytes what one entry may unpack to.
     * @param block gets name to content.
     */
    inline fun <T> read(archive: ByteArray, maxEntryBytes: Int, block: (Sequence<Pair<String, String>>) -> T): T {
        // ZipInputStream takes anything else for an archive without entries.
        if (!isZip(archive)) {
            throw ProtoJson.invalidBody("This is not a zip archive.")
        }
        return try {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                block(
                    generateSequence { zip.nextEntry }
                        .filterNot { it.isDirectory }
                        .mapIndexed { index, entry -> readEntry(zip, entry, index, maxEntryBytes) },
                )
            }
        } catch (@Suppress("SwallowedException") e: ZipException) {
            throw ProtoJson.invalidBody("This is not a zip archive: ${e.message}")
        }
    }

    fun isZip(archive: ByteArray): Boolean =
        archive.size >= SIGNATURE.size && SIGNATURE.indices.all { archive[it] == SIGNATURE[it] }

    fun readEntry(zip: ZipInputStream, entry: ZipEntry, index: Int, maxEntryBytes: Int): Pair<String, String> {
        val content = zip.readNBytes(maxEntryBytes + 1)
        val problem = when {
            index >= MAX_ENTRIES -> "An archive holds at most $MAX_ENTRIES Configs."
            content.size > maxEntryBytes -> "${entry.name} is larger than $maxEntryBytes bytes."
            else -> return entry.name to textOf(entry, content)
        }
        throw ProtoJson.invalidBody(problem)
    }

    /**
     * Rather than replacing what is not UTF-8, which would lose it without anyone noticing.
     */
    private fun textOf(entry: ZipEntry, content: ByteArray): String = try {
        content.decodeToString(throwOnInvalidSequence = true)
    } catch (@Suppress("SwallowedException") e: CharacterCodingException) {
        throw ProtoJson.invalidBody("${entry.name} is not UTF-8 text.")
    }

    /**
     * Writes one entry at a time, so that a namespace of many Configs takes no more memory than its largest.
     */
    fun write(configs: Flow<Resource>): Flow<ByteArray> = flow {
        val buffer = ByteArrayOutputStream()
        val zip = ZipOutputStream(buffer)
        configs.collect { config ->
            zip.putNextEntry(ZipEntry(config.meta.name))
            config.config.contentBytes.writeTo(zip)
            zip.closeEntry()
            zip.flush()
            emit(buffer.toByteArray())
            buffer.reset()
        }
        zip.close()
        emit(buffer.toByteArray())
    }
}
