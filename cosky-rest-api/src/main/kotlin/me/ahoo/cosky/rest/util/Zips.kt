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
package me.ahoo.cosky.rest.util

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.isHidden
import kotlin.io.path.name

/**
 * Zip tool.
 *
 * @author ahoo wang
 */
object Zips {
    @JvmStatic
    fun zip(items: List<ZipItem>): ByteArray {
        ByteArrayOutputStream().use { byteArrayOutputStream ->
            ZipOutputStream(byteArrayOutputStream).use { zipOutputStream ->
                for (item in items) {
                    zipOutputStream.putNextEntry(ZipEntry(item.name))
                    zipOutputStream.write(item.data.toByteArray(StandardCharsets.UTF_8))
                }
                zipOutputStream.flush()
                zipOutputStream.finish()
                return byteArrayOutputStream.toByteArray()
            }
        }
    }

    fun unzip(zipSource: ByteArray, maxUnzippedSize: Long = Long.MAX_VALUE): List<ZipItem> {
        return unzip(ByteArrayInputStream(zipSource), maxUnzippedSize)
    }

    @JvmStatic
    fun unzip(zipSource: InputStream, maxUnzippedSize: Long = Long.MAX_VALUE): List<ZipItem> {
        val items: MutableList<ZipItem> = ArrayList()
        var unzippedSize = 0L
        ZipInputStream(zipSource).use { zipInputStream ->
            generateSequence { zipInputStream.nextEntry }
                .filterNot { it.isDirectory || Paths.get(it.name).isHidden() }
                .forEach { entry ->
                    val data = ByteArrayOutputStream()
                    unzippedSize = zipInputStream.readEntry(data, unzippedSize, maxUnzippedSize)
                    items.add(ZipItem.of(Paths.get(entry.name).fileName.name, data.toString(StandardCharsets.UTF_8)))
                }
        }
        return items
    }

    /**
     * Copies the current entry to [out].
     *
     * @return the total unzipped size, which must stay within [maxUnzippedSize].
     */
    private fun ZipInputStream.readEntry(out: ByteArrayOutputStream, unzippedSize: Long, maxUnzippedSize: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = unzippedSize
        var read = read(buffer)
        while (read != -1) {
            total += read
            require(total <= maxUnzippedSize) {
                "Unzipped size exceeds the limit of [$maxUnzippedSize] bytes."
            }
            out.write(buffer, 0, read)
            read = read(buffer)
        }
        return total
    }

    class ZipItem(val name: String, val data: String) {

        companion object {
            @JvmStatic
            fun of(name: String, data: String): ZipItem {
                return ZipItem(name, data)
            }
        }

        override fun toString(): String {
            return "ZipItem(name='$name')"
        }
    }
}
