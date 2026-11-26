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

package me.ahoo.cosky.crossversion

import io.grpc.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.ahoo.cosky.api.v1.ConfigFormat
import me.ahoo.cosky.api.v1.CreateTokenResponse
import me.ahoo.cosky.api.v1.Kind
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.Verb
import me.ahoo.cosky.client.CoSkyClient
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds
import java.time.Duration as JavaDuration

/**
 * A rolling update: replicas of the release and of this tree serve one Redis, with auth on and one bootstrap token.
 * What either writes, the other reads and watches; the leases one grants, the other renews and reaps; the access one
 * grants, the other enforces; and neither finds that the data the other wrote differs from what it watched.
 *
 * Each test sets up what it needs, the reaper's leader included; the order only spares restarts.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MixedServersTest {
    private val admin = CoSkyServer.newToken()
    private val redis = Redis()

    // Reaping at once: the reaper's leader changes here on purpose, and the tests wait for what it reaps.
    private val baseline = BaselineServer(redis, admin, reaperGrace = JavaDuration.ZERO)
    private val current = CurrentServer(redis.uri, admin, reaperGrace = JavaDuration.ZERO)
    private val servers = listOf(baseline, current)
    private val opened = mutableMapOf<Pair<CoSkyServer, String>, Calls>()
    private val closing = mutableListOf<AutoCloseable>()

    @BeforeAll
    fun start() {
        // The first to start bootstraps the admin and leads the reaper.
        baseline.start()
        await("the baseline server to lead the reaper", LEAD_TIMEOUT) { baseline.leadsReaper() }
        current.start()
    }

    /**
     * A test that failed while a server was stopped leaves it so.
     */
    @BeforeEach
    fun bothRunning() {
        servers.filterNot { it.running }.forEach { it.start() }
    }

    @AfterEach
    fun closeEach() {
        closing.asReversed().forEach { it.close() }
        closing.clear()
        opened.values.forEach { it.close() }
        opened.clear()
    }

    @AfterAll
    fun stop() {
        current.close()
        baseline.close()
        redis.close()
    }

    private fun calls(server: CoSkyServer, token: String = admin) =
        opened.getOrPut(server to token) { Calls(server, token) }

    private fun clientOf(vararg servers: CoSkyServer, token: String = admin): CoSkyClient =
        client(token, *servers).also { closing += it }

    private suspend fun createNamespace(on: CoSkyServer): String {
        val name = uniqueName("mixed")
        val written = calls(on).put(namespace(name))
        // Read your writes on the other replica, whatever its version.
        servers.forEach { calls(it).get(key(SYSTEM, Kind.KIND_NAMESPACE, name), written.revision) }
        return name
    }

    /**
     * Once the server has seen the account, which may have been written through the other one.
     */
    private suspend fun tokenFor(account: String, through: CoSkyServer): CreateTokenResponse {
        var created: CreateTokenResponse? = null
        eventually {
            created = calls(through).createToken(account)
            true
        }
        return checkNotNull(created)
    }

    private fun other(server: CoSkyServer) = if (server === baseline) current else baseline

    /**
     * Makes [leader] lead the reaper: a server that stops lets go of the lock, and the one still running takes it.
     */
    private fun lead(leader: CoSkyServer) {
        val follower = other(leader)
        if (!leader.leadsReaper()) {
            stopAfterChecking(follower)
            await("the ${leader.name} server to lead the reaper", LEAD_TIMEOUT) { leader.leadsReaper() }
            follower.start()
        }
        follower.leadsReaper().assert().isFalse()
    }

    /**
     * What the server found goes with it once it stops.
     */
    private fun stopAfterChecking(server: CoSkyServer) {
        assertConsistent(server)
        server.stop()
    }

    /**
     * Neither the store's data nor its timeline differed from what the server watched, as far as its anti-entropy,
     * which runs every two seconds, and its revision checks found since it started.
     */
    private fun assertConsistent(server: CoSkyServer) {
        server.metric("cosky_anti_entropy_drift_total").assert().isEqualTo(0.0)
        server.metric("cosky_revision_regression_total").assert().isEqualTo(0.0)
    }

    /**
     * Both answer the same, object for object, down to revisions, timestamps and who wrote them.
     */
    private suspend fun assertSameData(namespace: String, kinds: List<Kind>) {
        kinds.forEach { kind ->
            eventually { calls(baseline).list(namespace, kind) == calls(current).list(namespace, kind) }
        }
    }

    @Test
    @Order(1)
    fun `the admin one version bootstrapped signs in on both, and a stranger on neither`() = scenario {
        assertSameData(SYSTEM, GLOBAL_KINDS)
        val admins = calls(current).list(SYSTEM, Kind.KIND_SERVICE_ACCOUNT).filter { it.meta.name == "admin" }
        admins.single().meta.updatedBy.assert().isEqualTo("system")
        servers.forEach { server ->
            statusOf { calls(server, CoSkyServer.newToken()).list(SYSTEM, Kind.KIND_NAMESPACE) }
                .assert().isEqualTo(Status.Code.UNAUTHENTICATED)
        }
    }

    @Test
    @Order(2)
    fun `what one version writes, the other reads and watches`() = scenario {
        val ns = createNamespace(on = baseline)
        val onBaseline = clientOf(baseline).namespace(ns)
        val onCurrent = clientOf(current).namespace(ns)
        val seenByBaseline = onBaseline.configs.watch("app.yaml")
        val seenByCurrent = onCurrent.configs.watch("app.yaml")
        val appKey = key(ns, Kind.KIND_CONFIG, "app.yaml")

        // gRPC through the release, read through this tree.
        val first = calls(baseline).put(config(ns, "app.yaml", "greeting: hello", ConfigFormat.CONFIG_FORMAT_YAML))
        seenByCurrent.first { it?.config?.content == "greeting: hello" }
        calls(current).get(appKey, first.revision).assert().isEqualTo(calls(baseline).get(appKey, first.revision))

        // REST through this tree, read through the release.
        val second = calls(current).putConfigOverRest(
            config(ns, "app.yaml", "greeting: bonjour", ConfigFormat.CONFIG_FORMAT_YAML),
        )
        second.changed.assert().isTrue()
        seenByBaseline.first { it?.config?.content == "greeting: bonjour" }
        calls(baseline).get(appKey, second.revision).assert().isEqualTo(calls(current).get(appKey, second.revision))
        calls(baseline).history(appKey).map { it.config.content }
            .assert().containsExactly("greeting: bonjour", "greeting: hello")
        calls(current).history(appKey).assert().isEqualTo(calls(baseline).history(appKey))

        // Instances registered through each, discovered through both.
        onCurrent.instances.register("order", endpoint("10.0.0.1"), instanceName = "order-current")
        onBaseline.instances.register("order", endpoint("10.0.0.2"), instanceName = "order-baseline")
        listOf(onBaseline, onCurrent).forEach { side ->
            side.instances.watch("order").first { list ->
                list.map { it.meta.name } == listOf("order-baseline", "order-current")
            }
            side.instances.services().assert().isEqualTo(setOf("order"))
        }

        val deleted = calls(current).delete(appKey)
        deleted.deleted.assert().isTrue()
        seenByBaseline.first { it == null }
        seenByCurrent.first { it == null }
        assertSameData(ns, NAMESPACED_KINDS)
        assertSameData(SYSTEM, GLOBAL_KINDS)
    }

    @Test
    @Order(3)
    fun `the access one version grants, the other enforces`() = scenario {
        val ns = createNamespace(on = current)
        val appKey = key(ns, Kind.KIND_CONFIG, "app.yaml")
        calls(baseline).put(config(ns, "app.yaml", "written by the admin"))

        // The namespace's own account, which the server that created the namespace wrote, takes a token from the
        // other: an application's, which reads Configs and registers, and writes no Config.
        val asApp = calls(current, tokenFor(ns, through = baseline).token)
        eventually { statusOf { asApp.get(appKey) } == null }
        statusOf { asApp.put(instance(ns, "order-1", "order")) }.assert().isNull()
        statusOf { asApp.put(config(ns, "app.yaml", "nope")) }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)

        // A Role and its binding written through this tree, the token through the release, used through the release.
        calls(current).put(serviceAccount("editor-$ns"))
        calls(current).put(
            role(
                "config-editor-$ns",
                rule(Kind.KIND_CONFIG, ns, Verb.VERB_GET, Verb.VERB_WATCH, Verb.VERB_PUT, Verb.VERB_DELETE),
            ),
        )
        calls(current).put(roleBinding("editor-$ns", "config-editor-$ns", "editor-$ns", ns))
        var editor = tokenFor("editor-$ns", through = baseline)
        val asEditor = calls(baseline, editor.token)
        eventually { statusOf { asEditor.put(config(ns, "app.yaml", "edited")) } == null }
        statusOf { asEditor.put(instance(ns, "order-2", "order")) }.assert().isEqualTo(Status.Code.PERMISSION_DENIED)
        statusOf { asEditor.get(key(SYSTEM, Kind.KIND_ROLE, "config-editor-$ns")) }
            .assert().isEqualTo(Status.Code.PERMISSION_DENIED)

        // Revoked through this tree, refused by the release.
        calls(current).revokeToken("editor-$ns", editor.id).deleted.assert().isTrue()
        eventually { statusOf { asEditor.list(ns, Kind.KIND_CONFIG) } == Status.Code.UNAUTHENTICATED }
        editor = tokenFor("editor-$ns", through = current)
        eventually { statusOf { calls(baseline, editor.token).list(ns, Kind.KIND_CONFIG) } == null }
        assertSameData(SYSTEM, GLOBAL_KINDS)
    }

    @Test
    @Order(4)
    fun `a namespace one version deletes is gone from the other, which creates it again`() = scenario {
        val ns = createNamespace(on = baseline)
        val appKey = key(ns, Kind.KIND_CONFIG, "app.yaml")
        val before = calls(baseline).put(config(ns, "app.yaml", "before")).revision
        // Only an empty namespace may go.
        calls(current).delete(appKey).deleted.assert().isTrue()
        val deleted = calls(current).delete(key(SYSTEM, Kind.KIND_NAMESPACE, ns))
        deleted.deleted.assert().isTrue()

        statusOf { calls(baseline).get(key(SYSTEM, Kind.KIND_NAMESPACE, ns), deleted.revision) }
            .assert().isEqualTo(Status.Code.NOT_FOUND)
        statusOf { calls(baseline).put(config(ns, "app.yaml", "too late")) }.assert().isEqualTo(Status.Code.NOT_FOUND)
        // Its own account goes with it.
        eventually {
            statusOf { calls(baseline).get(key(SYSTEM, Kind.KIND_SERVICE_ACCOUNT, ns)) } == Status.Code.NOT_FOUND
        }

        // Created again through the other version, it goes on from the revision it had.
        val created = calls(baseline).put(namespace(ns))
        servers.forEach { calls(it).get(key(SYSTEM, Kind.KIND_NAMESPACE, ns), created.revision) }
        val after = calls(current).put(config(ns, "app.yaml", "after")).revision
        after.assert().isGreaterThan(before)
        calls(baseline).get(appKey, after).config.content.assert().isEqualTo("after")
        assertSameData(ns, NAMESPACED_KINDS)
        assertSameData(SYSTEM, GLOBAL_KINDS)
    }

    @Test
    @Order(5)
    fun `a lease this tree grants, the release renews, and the release's reaper reaps`() = scenario {
        lead(baseline)
        leaseAcrossVersions(granting = current, renewing = baseline)
    }

    @Test
    @Order(6)
    fun `a lease the release grants, this tree renews, and this tree's reaper reaps`() = scenario {
        lead(current)
        leaseAcrossVersions(granting = baseline, renewing = current)
    }

    /**
     * The reaper leads on the [renewing] side.
     */
    private suspend fun leaseAcrossVersions(granting: CoSkyServer, renewing: CoSkyServer) {
        val ns = createNamespace(on = granting)
        val lease = calls(granting).grant(ns, LEASE_TTL)
        calls(granting).put(instance(ns, "order-1", "order", lease))
        val watched = clientOf(granting).namespace(ns).instances.watch("order")
        watched.first { it.size == 1 }

        // Renewed through the other version for longer than an unrenewed lease would last.
        val answers = calls(renewing).renew(ns, lease, times = RENEWALS)
        answers.map { it.ttlSeconds }.distinct().assert().containsExactly(LEASE_TTL.inWholeSeconds.toInt())
        answers.map { it.attachedCount }.distinct().assert().containsExactly(1)
        calls(renewing).list(ns, Kind.KIND_INSTANCE).single().meta.lease.assert().isEqualTo(lease)

        // Left alone, it is reaped by the version that did not grant it.
        watched.first { it.isEmpty() }
        eventually { renewing.metric(LEASES_EXPIRED, "namespace" to ns) == 1.0 }
        granting.metric(LEASES_EXPIRED, "namespace" to ns).assert().isEqualTo(0.0)
        servers.forEach { calls(it).renewOnce(ns, lease).ttlSeconds.assert().isEqualTo(0) }
        assertSameData(ns, NAMESPACED_KINDS)
    }

    @Test
    @Order(7)
    fun `a registration lives through a rolling update to the other version and back`() = scenario {
        val ns = createNamespace(on = baseline)
        // The application knows both replicas, and starts on the release's.
        val application = clientOf(baseline, current).namespace(ns)
        val registration = application.instances.register("order", endpoint("10.0.0.9"), instanceName = "order-1")
        var lease = 0L
        eventually {
            lease = calls(current).list(ns, Kind.KIND_INSTANCE).single().meta.lease
            lease != 0L
        }

        // A consumer that starts on this tree's replica, which goes first.
        val views = clientOf(current, baseline).namespace(ns).instances.watch("order")
        views.first { it.size == 1 }
        val gaps = CopyOnWriteArrayList<List<Resource>>()
        val following = launch { views.collect { if (it.isEmpty()) gaps += it } }

        listOf(current, baseline).forEach { stopping ->
            val running = other(stopping)
            stopAfterChecking(stopping)
            // Renewed through the replica still running, which reaps as well, long enough to reap a lease left alone.
            delay(PAST_REAPING)
            calls(running).list(ns, Kind.KIND_INSTANCE).single().meta.lease.assert().isEqualTo(lease)
            stopping.start()
        }
        gaps.assert().isEmpty()
        following.cancel()

        registration.close()
        views.first { it.isEmpty() }
        assertSameData(ns, NAMESPACED_KINDS)
    }

    @Test
    @Order(8)
    fun `neither version finds the data the other wrote differs from what it watched`() = scenario {
        // An anti-entropy round on each, after everything above.
        delay(ANTI_ENTROPY_ROUND)
        servers.forEach { assertConsistent(it) }
        val namespaces = calls(baseline).list(SYSTEM, Kind.KIND_NAMESPACE).map { it.meta.name }
        namespaces.forEach { assertSameData(it, NAMESPACED_KINDS) }
        assertSameData(SYSTEM, GLOBAL_KINDS)
    }

    private companion object {
        const val LEASES_EXPIRED = "cosky_lease_expired_total"
        const val RENEWALS = 8
        val LEAD_TIMEOUT: JavaDuration = JavaDuration.ofSeconds(30)
        val ANTI_ENTROPY_ROUND = 3.seconds
    }
}
