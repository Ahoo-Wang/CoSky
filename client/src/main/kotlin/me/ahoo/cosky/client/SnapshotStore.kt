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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.ListResponse
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Views on disk, so that a process restarting on the same disk can serve before it reaches a server (design §7.5).
 *
 * Snapshots are not encrypted, since a client has nowhere safe to keep a key: directories are `0700`, files `0600`, and
 * Config contents can stay off the disk altogether. Failing to write one is never fatal.
 */
internal class SnapshotStore(private val options: CoSkyClientOptions, private val scope: CoroutineScope) {
    private val root: Path? = options.snapshotDir?.resolve(serverKey(options))
    private val pending = ConcurrentHashMap<WatchKey, Job>()

    fun load(key: WatchKey): ListResponse? {
        val path = pathOf(key) ?: return null
        return try {
            if (Files.exists(path)) Files.newInputStream(path).use { ListResponse.parseFrom(it) } else null
        } catch (e: IOException) {
            log.warn("Ignoring the unreadable snapshot {}.", path, e)
            null
        }
    }

    /**
     * Writes once the view has been quiet for a moment, so a burst of changes costs one write.
     */
    fun schedule(key: WatchKey, read: () -> ListResponse) {
        if (pathOf(key) == null) {
            return
        }
        pending.put(
            key,
            scope.launch {
                delay(options.snapshotDebounce)
                write(key, read())
            },
        )?.cancel()
    }

    private fun write(key: WatchKey, snapshot: ListResponse) {
        val path = pathOf(key) ?: return
        try {
            createPrivateDirectories(path.parent)
            val temporary = Files.createTempFile(path.parent, path.fileName.toString(), ".tmp", OWNER_ONLY_FILE)
            Files.newOutputStream(temporary).use { snapshot.writeTo(it) }
            move(temporary, path)
        } catch (e: IOException) {
            log.warn("Could not write the snapshot {}.", path, e)
        }
    }

    private fun move(from: Path, to: Path) {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun createPrivateDirectories(directory: Path) {
        if (!Files.exists(directory)) {
            directory.parent?.let { createPrivateDirectories(it) }
            Files.createDirectory(directory, OWNER_ONLY_DIRECTORY)
        }
    }

    private fun pathOf(key: WatchKey): Path? {
        if (!options.snapshotIncludeConfig && key.kind == Kind.KIND_CONFIG) {
            return null
        }
        val filter = listOf(key.name, key.service).filter { it.isNotEmpty() }.joinToString("-")
        val kind = key.kind.name.removePrefix("KIND_").lowercase()
        val file = if (filter.isEmpty()) kind else "$kind-${filter.replace("/", "%2F")}"
        return root?.resolve(key.namespace)?.resolve("$file.pb")
    }

    private companion object {
        val log = LoggerFactory.getLogger(SnapshotStore::class.java)
        val OWNER_ONLY_DIRECTORY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
        val OWNER_ONLY_FILE = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        const val KEY_LENGTH = 16

        /**
         * Keeps servers and identities apart: processes on one host must not read each other's views. Only the token's
         * id goes in, never its secret.
         */
        fun serverKey(options: CoSkyClientOptions): String {
            val tokenId = options.token?.split("_")?.getOrNull(1).orEmpty()
            val source = (options.endpoints.sorted() + tokenId).joinToString("\n")
            return MessageDigest.getInstance("SHA-256").digest(source.encodeToByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(KEY_LENGTH)
        }
    }
}
