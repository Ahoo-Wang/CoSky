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
import java.io.EOFException
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

    /** How a zip archive's first entry begins, and how the record that ends every zip archive does. */
    private val LOCAL_HEADER = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)
    private val END_RECORD = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 5, 6)

    /** The end record without its comment, which closes the archive and says how long it is. */
    private const val END_RECORD_BYTES = 22
    private const val COMMENT_LENGTH_AT = 20
    private const val MAX_COMMENT_BYTES = 0xFFFF
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    /**
     * Unpacks one entry at a time as [block] asks for it, so that a small archive cannot exhaust the memory, and a
     * caller who may not write gets no further than the first entry.
     *
     * @param maxEntryBytes what one entry may unpack to.
     * @param block gets name to content.
     */
    inline fun <T> read(archive: ByteArray, maxEntryBytes: Int, block: (Sequence<Pair<String, String>>) -> T): T {
        // ZipInputStream takes anything else, an archive cut short included, for an archive without entries.
        if (!isZip(archive)) {
            throw ProtoJson.invalidBody("This is not a zip archive, or it was cut short.")
        }
        return ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            block(
                generateSequence { decoding { zip.nextEntry } }
                    .filterNot { it.isDirectory }
                    .mapIndexed { index, entry -> readEntry(zip, entry, index, maxEntryBytes) },
            )
        }
    }

    /**
     * Whether the archive begins as a zip archive does, and ends with the record that closes every one, an empty one
     * included. That record comes last, so an archive cut short has lost it.
     */
    fun isZip(archive: ByteArray): Boolean {
        if (!archive.startsWith(LOCAL_HEADER, 0) && !archive.startsWith(END_RECORD, 0)) {
            return false
        }
        val last = archive.size - END_RECORD_BYTES
        return (last downTo maxOf(0, last - MAX_COMMENT_BYTES)).any { start ->
            archive.startsWith(END_RECORD, start) &&
                start + END_RECORD_BYTES + archive.shortAt(start + COMMENT_LENGTH_AT) == archive.size
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray, at: Int): Boolean =
        at >= 0 && at + prefix.size <= size && prefix.indices.all { this[at + it] == prefix[it] }

    /** An unsigned little-endian short, as zip records hold their numbers. */
    private fun ByteArray.shortAt(at: Int): Int =
        (this[at].toInt() and BYTE_MASK) or ((this[at + 1].toInt() and BYTE_MASK) shl BITS_PER_BYTE)

    /**
     * Reads from the archive, where what does not decode is a mistake of the archive's, not the server's. Only around
     * the reading, so that what [read]'s block fails with keeps its own cause.
     */
    inline fun <T> decoding(read: () -> T): T = try {
        read()
    } catch (@Suppress("SwallowedException") e: ZipException) {
        throw ProtoJson.invalidBody("This is not a zip archive: ${e.message}")
    } catch (@Suppress("SwallowedException") e: EOFException) {
        throw ProtoJson.invalidBody("This zip archive was cut short.")
    }

    fun readEntry(zip: ZipInputStream, entry: ZipEntry, index: Int, maxEntryBytes: Int): Pair<String, String> {
        val content = decoding { zip.readNBytes(maxEntryBytes + 1) }
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
