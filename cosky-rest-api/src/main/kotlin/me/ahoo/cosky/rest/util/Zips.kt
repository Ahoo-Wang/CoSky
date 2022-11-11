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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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

    fun unzip(zipSource: ByteArray?): List<ZipItem> {
        return unzip(ByteArrayInputStream(zipSource))
    }

    @JvmStatic
    fun unzip(zipSource: InputStream?): List<ZipItem> {
        val items: MutableList<ZipItem> = ArrayList()
        ZipInputStream(zipSource).use { zipInputStream ->
            var entry: ZipEntry
            while (zipInputStream.nextEntry.also { entry = it } != null) {
                if (entry.isDirectory) {
                    continue
                }
                ByteArrayOutputStream().use { itemOutputStream ->
                    val buffer = ByteArray(1024)
                    var offset: Int
                    while (zipInputStream.read(buffer).also { offset = it } != -1) {
                        itemOutputStream.write(buffer, 0, offset)
                    }
                    val entryName = entry.name
                    items.add(ZipItem.of(entryName, itemOutputStream.toString("UTF-8")))
                }
            }
            return items
        }
    }

    class ZipItem(val name: String, val data: String) {

        companion object {
            @JvmStatic
            fun of(name: String, data: String): ZipItem {
                return ZipItem(name, data)
            }
        }
    }
}
