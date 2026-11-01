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

package me.ahoo.cosky.store.redis

import io.lettuce.core.ClientOptions
import io.lettuce.core.Limit
import io.lettuce.core.MapScanCursor
import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisException
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SocketOptions
import io.lettuce.core.StreamMessage
import io.lettuce.core.cluster.ClusterClientOptions
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.resource.EpollProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.ResourceKey
import me.ahoo.cosky.api.v1.TopologyEdge
import me.ahoo.cosky.store.ApplyResult
import me.ahoo.cosky.store.Change
import me.ahoo.cosky.store.ChangeBatch
import me.ahoo.cosky.store.ChangeType
import me.ahoo.cosky.store.GrantResult
import me.ahoo.cosky.store.Lease
import me.ahoo.cosky.store.LeaseRenewal
import me.ahoo.cosky.store.LeaseStats
import me.ahoo.cosky.store.Mutation
import me.ahoo.cosky.store.NamespaceState
import me.ahoo.cosky.store.ObjectVersion
import me.ahoo.cosky.store.Position
import me.ahoo.cosky.store.RevisionGoneException
import me.ahoo.cosky.store.Store
import me.ahoo.cosky.store.StoreOptions
import me.ahoo.cosky.store.TerminateResult
import me.ahoo.cosky.store.TimelineDivergedException
import me.ahoo.cosky.store.exceeds
import me.ahoo.cosky.store.key
import me.ahoo.cosky.store.redis.RedisKeys.Companion.token
import me.ahoo.cosky.store.requireWellFormed
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * A [Store] on Redis (design §5). Works on Standalone, Sentinel and Cluster: every command stays within one
 * namespace's slot, none blocks, and hints arrive on one subscription whichever node delivers them.
 *
 * @param tailPollInterval how often a follower reads when no hint arrives; a hint is only ever a shortcut (§6.1).
 */
class RedisStore(
    private val commands: RedisClusterAsyncCommands<String, ByteArray>,
    private val hintConnection: StatefulRedisPubSubConnection<String, ByteArray>,
    private val options: StoreOptions = StoreOptions(),
    private val tailPollInterval: Duration = 1.seconds,
) : Store, AutoCloseable {
    private val hints = ConcurrentHashMap<String, MutableStateFlow<Long>>()
    private val subscribed = AtomicBoolean()

    override suspend fun apply(namespace: String, mutations: List<Mutation>, actor: String): ApplyResult {
        requireWellFormed(namespace, mutations)
        if (mutations.exceeds(options.limits)) {
            return ApplyResult.BatchTooLarge
        }
        val keys = RedisKeys(namespace)
        val scriptKeys = mutableListOf(
            keys.state,
            keys.revision,
            keys.log,
            keys.lease,
            keys.leaseAttach,
            keys.historyIndex,
        )
        val args = mutableListOf(
            namespace.arg(),
            actor.arg(),
            mutations.size.arg(),
            options.logSize.arg(),
            options.limits.configHistorySize.arg(),
        )
        mutations.forEach { mutation ->
            scriptKeys += keys.objects(mutation.key.kind)
            scriptKeys += keys.versions(mutation.key.kind)
            scriptKeys += keys.history(mutation.key.name)
            args += arguments(mutation)
        }
        val reply = APPLY.run<List<Any?>>(commands, ScriptOutputType.MULTI, scriptKeys, args)
        return when (val status = reply[0].asText()) {
            "OK" -> ApplyResult.Applied(reply[1].asText().toLong(), reply[2].asText().toInt())
            "CONFLICT" -> ApplyResult.Conflict(reply[1].asText().toInt(), reply[2].asText().toLongOrNull())
            "LEASE_NOT_FOUND" -> ApplyResult.LeaseNotFound(reply[1].asText().toInt(), reply[2].asText().toLong())
            "NAMESPACE_NOT_ACTIVE" -> ApplyResult.NamespaceNotActive
            else -> error("Unexpected reply from apply: $status")
        }
    }

    private fun arguments(mutation: Mutation): List<ByteArray> {
        val key = mutation.key
        val type: String
        val bytes: ByteArray
        val lease: Long
        when (mutation) {
            is Mutation.Put -> {
                type = "P"
                bytes = mutation.resource.canonicalBytes()
                lease = mutation.resource.meta.lease
            }

            is Mutation.Delete -> {
                type = "D"
                bytes = ByteArray(0)
                lease = 0
            }
        }
        return listOf(
            type.arg(),
            key.kind.token.arg(),
            key.name.arg(),
            mutation.expectedRevision.arg(),
            bytes,
            lease.arg(),
        )
    }

    override suspend fun position(namespace: String): Position? {
        val keys = RedisKeys(namespace)
        val values = commands.mget(keys.state, keys.epoch, keys.revision).await()
        val byKey = values.filter { it.hasValue() }.associate { it.key to it.value.decodeToString() }
        if (byKey[keys.state] != STATE_ACTIVE) {
            return null
        }
        return Position(byKey.getValue(keys.epoch), byKey[keys.revision]?.toLong() ?: 0)
    }

    override fun readAll(namespace: String): Flow<Resource> = flow {
        val keys = RedisKeys(namespace)
        RedisKeys.KINDS.forEach { kind ->
            var cursor = SCAN_START
            do {
                val reply = READ_BATCH.run<List<Any?>>(
                    commands,
                    ScriptOutputType.MULTI,
                    listOf(keys.state, keys.versions(kind), keys.objects(kind)),
                    listOf(cursor.arg(), READ_BATCH_SIZE.arg()),
                )
                cursor = reply[0].asText()
                if (cursor == NOT_ACTIVE) {
                    return@flow
                }
                reply.drop(1).chunked(READ_BATCH_FIELDS).forEach { (_, meta, bytes) ->
                    emit(resourceOf(bytes as ByteArray, StoredMeta.parse(meta.asText())))
                }
            } while (cursor != SCAN_START)
        }
    }

    override fun versions(namespace: String): Flow<ObjectVersion> = flow {
        val keys = RedisKeys(namespace)
        RedisKeys.KINDS.forEach { kind ->
            var cursor: ScanCursor = ScanCursor.INITIAL
            do {
                val page: MapScanCursor<String, ByteArray> =
                    commands.hscan(keys.versions(kind), cursor, ScanArgs.Builder.limit(READ_BATCH_SIZE)).await()
                page.map.forEach { (name, meta) -> emit(versionOf(namespace, kind, name, meta)) }
                cursor = page
            } while (!page.isFinished)
        }
    }

    private fun versionOf(namespace: String, kind: Kind, name: String, meta: ByteArray): ObjectVersion {
        val key = ResourceKey.newBuilder().setNamespace(namespace).setKind(kind).setName(name).build()
        return ObjectVersion(key, StoredMeta.parse(meta.decodeToString()).revision)
    }

    override fun changes(namespace: String, from: Position): Flow<ChangeBatch> = flow {
        val keys = RedisKeys(namespace)
        val hint = hintsOf(namespace)
        var applied = from.revision
        var appliedMeta = ""
        var readSize = TAIL_BATCH_SIZE
        while (true) {
            val seenHint = hint.value
            // A failover shows up as reads that fail. They must not end this: the position kept here is what finds the
            // writes it lost (design §6.4).
            val reply = try {
                TAIL.run<List<Any?>>(
                    commands,
                    ScriptOutputType.MULTI,
                    listOf(keys.state, keys.epoch, keys.revision, keys.log),
                    listOf(from.epoch.arg(), applied.arg(), appliedMeta.arg(), readSize.arg()),
                )
            } catch (e: RedisException) {
                log.warn("Reading the log of namespace {} failed; trying again: {}", namespace, e.message)
                delay(tailPollInterval)
                continue
            }
            when (reply[0].asText()) {
                "NOT_ACTIVE" -> return@flow
                "EPOCH" -> throw TimelineDivergedException(Position(reply[1].asText(), reply[2].asText().toLong()))
                "DIVERGED" -> throw TimelineDivergedException(rotateEpoch(namespace, from.epoch))
                "GONE" -> throw RevisionGoneException(reply[1].asText().toLong())
            }
            appliedMeta = reply[2].asText()
            val read = reply.last().asList().map { logEntryOf(it) }
            val revisions = completeRevisions(read, readSize).groupBy { it.revision }
            revisions.forEach { (revision, group) ->
                emit(ChangeBatch(revision, group.map { it.change }))
            }
            revisions.entries.lastOrNull()?.let { (revision, group) ->
                applied = revision
                // What tail.lua compares: the entries of a revision differ, by when each object was created (§5.2).
                appliedMeta = group.first().meta
            }
            when {
                revisions.isNotEmpty() -> readSize = TAIL_BATCH_SIZE
                // One revision is larger than a read: a lease takes all its Instances along, however many.
                read.size >= readSize -> readSize *= 2
                else -> withTimeoutOrNull(tailPollInterval) { hint.first { it != seenHint } }
            }
        }
    }

    override suspend fun recentChanges(
        namespace: String,
        until: Long,
        limit: Int,
        maxBytes: Long,
    ): List<ChangeBatch> {
        val log = RedisKeys(namespace).log
        val newestFirst = mutableListOf<StreamMessage<String, ByteArray>>()
        var upper = Range.Boundary.including(until.toString())
        var bytes = 0L
        do {
            val count = minOf(limit - newestFirst.size, TAIL_BATCH_SIZE).toLong()
            val page = commands.xrevrange(log, Range.from(Range.Boundary.unbounded(), upper), Limit.from(count)).await()
            newestFirst += page
            bytes += page.sumOf { message -> message.body.values.sumOf { it.size.toLong() } }
            page.lastOrNull()?.let { upper = Range.Boundary.excluding(it.id) }
        } while (page.size.toLong() == count && newestFirst.size < limit && bytes < maxBytes)
        val oldest = newestFirst.lastOrNull() ?: return emptyList()
        // The limit or a trim may have cut through the oldest revision, and part of a revision is of no use.
        val cut = oldest.id.substringBefore('-').takeIf { oldest.id.substringAfter('-') != "0" }
        var remaining = maxBytes
        return newestFirst.asReversed()
            .dropWhile { it.id.substringBefore('-') == cut }
            .map { logEntryOf(it.id, it.body) }
            .groupBy { it.revision }
            .map { (revision, entries) -> ChangeBatch(revision, entries.map { it.change }) }
            .takeLastWhile { batch ->
                remaining -= batch.serializedSize
                remaining >= 0
            }
    }

    override suspend fun recordTopology(namespace: String, edges: Collection<TopologyEdge>, retention: Duration) {
        val keys = RedisKeys(namespace)
        TOPOLOGY_RECORD.run<Long>(
            commands,
            ScriptOutputType.INTEGER,
            listOf(keys.state, keys.topology),
            listOf(retention.inWholeMilliseconds.arg()) + edges.map { edgeMember(it).arg() },
        )
    }

    private fun edgeMember(edge: TopologyEdge): String = "${edge.consumer}$EDGE_SEPARATOR${edge.producer}"

    override suspend fun topology(namespace: String): Set<TopologyEdge> {
        val members = TOPOLOGY_READ.run<List<Any?>>(
            commands,
            ScriptOutputType.MULTI,
            listOf(RedisKeys(namespace).topology),
            emptyList(),
        )
        return members.mapTo(mutableSetOf()) { member ->
            val (consumer, producer) = member.asText().split(EDGE_SEPARATOR, limit = 2)
            TopologyEdge.newBuilder().setConsumer(consumer).setProducer(producer).build()
        }
    }

    override suspend fun rotateEpoch(namespace: String, expected: String): Position {
        val keys = RedisKeys(namespace)
        val reply = EPOCH_ROTATE.run<List<Any?>>(
            commands,
            ScriptOutputType.MULTI,
            listOf(keys.epoch, keys.revision),
            listOf(expected.arg(), newEpoch().arg(), namespace.arg()),
        )
        return Position(reply[0].asText(), reply[1].asText().toLong())
    }

    override suspend fun history(key: ResourceKey, limit: Int): List<Resource> {
        val stop = if (limit == 0) TO_THE_END else limit.toLong() - 1
        return commands.zrevrange(RedisKeys(key.namespace).history(key.name), 0, stop).await().map { member ->
            val separator = member.indexOf(0)
            val meta = StoredMeta.parse(member.copyOfRange(0, separator).decodeToString())
            resourceOf(member.copyOfRange(separator + 1, member.size), meta)
        }
    }

    override suspend fun grantLease(namespace: String, ttl: Duration, owner: String): GrantResult {
        val keys = RedisKeys(namespace)
        val reply = LEASE_GRANT.run<List<Any?>>(
            commands,
            ScriptOutputType.MULTI,
            listOf(keys.state, keys.lease, keys.leaseTtl, keys.leaseOwner, keys.leaseSequence),
            listOf(ttl.inWholeMilliseconds.arg(), owner.arg()),
        )
        if (reply[0].asText() != "OK") {
            return GrantResult.NamespaceNotActive
        }
        return GrantResult.Granted(Lease(reply[1].asText().toLong(), ttl))
    }

    override suspend fun leaseOwners(namespace: String, leases: Collection<Long>): Map<Long, String?> {
        val ids = leases.map { it.toString() }
        val owners = commands.hmget(RedisKeys(namespace).leaseOwner, *ids.toTypedArray()).await()
            .associate { it.key to it.getValueOrElse(null)?.decodeToString() }
        return leases.associateWith { owners[it.toString()] }
    }

    override suspend fun keepAlive(namespace: String, leases: Collection<Long>): Map<Long, LeaseRenewal?> {
        if (leases.isEmpty()) {
            return emptyMap()
        }
        val keys = RedisKeys(namespace)
        val ids = leases.toList()
        val reply = LEASE_KEEPALIVE.run<List<Any?>>(
            commands,
            ScriptOutputType.MULTI,
            listOf(keys.lease, keys.leaseTtl, keys.leaseAttach),
            ids.map { it.arg() },
        )
        return ids.zip(reply.chunked(2)).associate { (id, renewal) ->
            val (ttl, attached) = renewal
            id to ttl.asText().toLongOrNull()?.let { LeaseRenewal(it.milliseconds, (attached as Long).toInt()) }
        }
    }

    /**
     * Refuses a Redis that would lose data without anyone noticing, or that the scripts were not tested on (design
     * D31). Call it once before serving. Where `INFO` is not allowed it can only warn.
     *
     * @throws IllegalStateException when a prerequisite is not met.
     */
    suspend fun checkPrerequisites() {
        val reports = try {
            when (commands) {
                is RedisAdvancedClusterAsyncCommands -> commands.upstream().commands().info().map { it.await() }
                else -> listOf(commands.info().await())
            }
        } catch (e: RedisCommandExecutionException) {
            log.warn("Cannot check the Redis prerequisites, since INFO failed: {}", e.message)
            return
        }
        val checks = reports.map { RedisPrerequisites.check(it) }
        checks.flatMap { it.unknown }.distinct().forEach {
            log.warn("Cannot check the Redis prerequisites, since INFO does not report {}.", it)
        }
        val problems = checks.flatMap { it.problems }.distinct()
        check(problems.isEmpty()) { "This Redis cannot back CoSky: ${problems.joinToString(" ")}" }
    }

    override suspend fun expireLease(namespace: String, lease: Long, actor: String, force: Boolean): Boolean {
        val keys = RedisKeys(namespace)
        val reply = LEASE_EXPIRE.run<List<Any?>>(
            commands,
            ScriptOutputType.MULTI,
            listOf(
                keys.revision,
                keys.log,
                keys.lease,
                keys.leaseTtl,
                keys.leaseOwner,
                keys.leaseAttach,
                keys.objects(Kind.KIND_INSTANCE),
                keys.versions(Kind.KIND_INSTANCE),
            ),
            listOf(namespace.arg(), actor.arg(), lease.arg(), (if (force) 1 else 0).arg(), options.logSize.arg()),
        )
        return reply[0].asText() == "EXPIRED"
    }

    override suspend fun expiredLeases(namespace: String, limit: Int): List<Long> =
        leaseQuery(namespace, limit)[2].asList().map { it.asText().toLong() }

    override suspend fun leaseStats(namespace: String): LeaseStats {
        val reply = leaseQuery(namespace, 0)
        return LeaseStats(reply[0].asText().toInt(), reply[1].asText().toInt())
    }

    private suspend fun leaseQuery(namespace: String, limit: Int): List<Any?> {
        val keys = RedisKeys(namespace)
        return LEASE_QUERY.run(commands, ScriptOutputType.MULTI, listOf(keys.lease, keys.leaseTtl), listOf(limit.arg()))
    }

    override suspend fun createNamespace(namespace: String): Position {
        if (namespaceState(namespace) == NamespaceState.TERMINATING) {
            removeData(namespace)
        }
        val keys = RedisKeys(namespace)
        val reply = NAMESPACE_CREATE.run<List<Any?>>(
            commands,
            ScriptOutputType.MULTI,
            listOf(keys.state, keys.epoch, keys.revision),
            listOf(newEpoch().arg()),
        )
        return Position(reply[0].asText(), reply[1].asText().toLong())
    }

    override suspend fun namespaceState(namespace: String): NamespaceState? =
        when (commands.get(RedisKeys(namespace).state).await()?.decodeToString()) {
            STATE_ACTIVE -> NamespaceState.ACTIVE
            null -> null
            else -> NamespaceState.TERMINATING
        }

    override suspend fun terminateNamespace(namespace: String): TerminateResult {
        val keys = RedisKeys(namespace)
        val reply = NAMESPACE_TERMINATE.run<ByteArray>(
            commands,
            ScriptOutputType.VALUE,
            listOf(keys.state, keys.objects(Kind.KIND_CONFIG), keys.objects(Kind.KIND_INSTANCE)),
            listOf(namespace.arg()),
        )
        return TerminateResult.valueOf(reply.decodeToString().uppercase())
    }

    override suspend fun dropNamespace(namespace: String) {
        when (namespaceState(namespace)) {
            NamespaceState.ACTIVE -> error("Namespace $namespace is still active.")
            NamespaceState.TERMINATING -> removeData(namespace)
            null -> Unit
        }
    }

    /**
     * Removes everything but the state, which stays as a tombstone. Each step can run again if this one is interrupted,
     * and none touches a namespace that has been created again by another replica in the meantime.
     */
    private suspend fun removeData(namespace: String) {
        val keys = RedisKeys(namespace)
        val history = commands.smembers(keys.historyIndex).await().map { keys.history(it.decodeToString()) }
        val objects = RedisKeys.KINDS.flatMap { listOf(keys.objects(it), keys.versions(it)) }
        val fixed = listOf(
            keys.epoch,
            keys.revision,
            keys.log,
            keys.lease,
            keys.leaseTtl,
            keys.leaseOwner,
            keys.leaseAttach,
            keys.leaseSequence,
            keys.topology,
        )
        (history + objects + fixed + keys.historyIndex).chunked(REMOVE_BATCH_SIZE).forEach {
            NAMESPACE_REMOVE.run<Long>(commands, ScriptOutputType.INTEGER, listOf(keys.state) + it, emptyList())
        }
    }

    override suspend fun tryLock(name: String, owner: String, ttl: Duration): Boolean {
        val acquired = LOCK_ACQUIRE.run<Long>(
            commands,
            ScriptOutputType.INTEGER,
            listOf(RedisKeys.lock(name)),
            listOf(owner.arg(), ttl.inWholeMilliseconds.arg()),
        )
        return acquired == 1L
    }

    override suspend fun unlock(name: String, owner: String) {
        LOCK_RELEASE.run<Long>(commands, ScriptOutputType.INTEGER, listOf(RedisKeys.lock(name)), listOf(owner.arg()))
    }

    override fun close() {
        hintConnection.close()
    }

    private fun hintsOf(namespace: String): MutableStateFlow<Long> {
        if (subscribed.compareAndSet(false, true)) {
            hintConnection.addListener(
                object : RedisPubSubAdapter<String, ByteArray>() {
                    override fun message(channel: String, message: ByteArray) {
                        hints[message.decodeToString()]?.let { it.value += 1 }
                    }
                },
            )
            hintConnection.async().subscribe(RedisKeys.HINT_CHANNEL)
        }
        return hints.computeIfAbsent(namespace) { MutableStateFlow(0) }
    }

    /**
     * A full read may cut a revision in two; the rest of it comes with the next read, so batches stay whole.
     */
    private fun completeRevisions(entries: List<LogEntry>, readSize: Int): List<LogEntry> {
        if (entries.size < readSize) {
            return entries
        }
        val last = entries.last().revision
        return entries.takeWhile { it.revision != last }
    }

    private fun logEntryOf(raw: Any?): LogEntry {
        val entry = raw.asList()
        val fields = entry[1].asList().chunked(2).associate { (field, value) -> field.asText() to value as ByteArray }
        return logEntryOf(entry[0].asText(), fields)
    }

    private fun logEntryOf(id: String, fields: Map<String, ByteArray>): LogEntry {
        val meta = fields.getValue("m").decodeToString()
        val resource = resourceOf(fields.getValue("o"), StoredMeta.parse(meta))
        val type = if (fields.getValue("t").decodeToString() == "P") ChangeType.PUT else ChangeType.DELETE
        return LogEntry(id.substringBefore('-').toLong(), meta, Change(type, resource))
    }

    private fun newEpoch(): String = UUID.randomUUID().toString().replace("-", "")

    private data class LogEntry(val revision: Long, val meta: String, val change: Change)

    companion object {
        private val log = LoggerFactory.getLogger(RedisStore::class.java)
        private const val STATE_ACTIVE = "active"
        private const val NOT_ACTIVE = "NOT_ACTIVE"
        private const val SCAN_START = "0"
        private const val READ_BATCH_SIZE = 500L
        private const val READ_BATCH_FIELDS = 3
        private const val REMOVE_BATCH_SIZE = 100
        private const val TO_THE_END = -1L

        /**
         * What a read of the log starts with: above the largest batch of an apply, though an expiring lease can exceed
         * that, and few enough that a read of the largest Configs stays near 128 MiB (design D36).
         */
        private const val TAIL_BATCH_SIZE = 128

        private val CODEC: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)

        private val APPLY = RedisScript("apply")
        private val TAIL = RedisScript("tail")
        private val READ_BATCH = RedisScript("read_batch")
        private val EPOCH_ROTATE = RedisScript("epoch_rotate")
        private val LEASE_GRANT = RedisScript("lease_grant")
        private val LEASE_KEEPALIVE = RedisScript("lease_keepalive")
        private val LEASE_EXPIRE = RedisScript("lease_expire")
        private val LEASE_QUERY = RedisScript("lease_query")
        private val LOCK_ACQUIRE = RedisScript("lock_acquire")
        private val LOCK_RELEASE = RedisScript("lock_release")
        private val TOPOLOGY_RECORD = RedisScript("topology_record")
        private val TOPOLOGY_READ = RedisScript("topology_read")
        private val NAMESPACE_CREATE = RedisScript("namespace_create")
        private const val EDGE_SEPARATOR = "\u0000"
        private val TCP_USER_TIMEOUT = 30.seconds.toJavaDuration()
        private val NAMESPACE_TERMINATE = RedisScript("namespace_terminate")
        private val NAMESPACE_REMOVE = RedisScript("namespace_remove")

        fun standalone(
            client: RedisClient,
            options: StoreOptions = StoreOptions(),
            tailPollInterval: Duration = 1.seconds,
        ): RedisStore {
            client.options = clientOptions()
            return RedisStore(
                client.connect(CODEC).async() as RedisClusterAsyncCommands<String, ByteArray>,
                client.connectPubSub(CODEC),
                options,
                tailPollInterval,
            )
        }

        fun cluster(
            client: RedisClusterClient,
            options: StoreOptions = StoreOptions(),
            tailPollInterval: Duration = 1.seconds,
        ): RedisStore {
            client.setOptions(clusterClientOptions())
            return RedisStore(
                client.connect(CODEC).async(),
                client.connectPubSub(CODEC),
                options,
                tailPollInterval,
            )
        }

        /**
         * A host that vanishes sends no reset, and while commands wait for an answer keep-alive stays out of it, so the
         * system would go on retransmitting for many minutes with every command timing out. This gives up within half
         * a minute, and the client connects again, by then to whatever took over. Only Linux with epoll can do this,
         * which covers where it matters.
         */
        private fun socketOptions(): SocketOptions = SocketOptions.builder()
            .apply {
                if (EpollProvider.isAvailable()) {
                    tcpUserTimeout(
                        SocketOptions.TcpUserTimeoutOptions.builder().enable().tcpUserTimeout(TCP_USER_TIMEOUT).build(),
                    )
                }
            }
            .build()

        internal fun clientOptions(): ClientOptions = ClientOptions.builder().socketOptions(socketOptions()).build()

        internal fun clusterClientOptions(): ClusterClientOptions =
            ClusterClientOptions.builder().socketOptions(socketOptions()).build()
    }
}
