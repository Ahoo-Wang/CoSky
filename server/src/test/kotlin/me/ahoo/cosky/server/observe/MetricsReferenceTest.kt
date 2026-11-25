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

package me.ahoo.cosky.server.observe

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import me.ahoo.cosky.api.v1.GoneReason
import me.ahoo.test.asserts.assert
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText

/**
 * The metrics the server reports are those the table of the deployment guide lists, by name and labels, in English and
 * in Chinese. From 8.0.0-rc.1 on they only grow (CONTRIBUTING.md): CI holds that table to the one of a pull request's
 * base (`.github/scripts/names-only-grow.sh`), and this holds the server to the table.
 */
class MetricsReferenceTest {
    @Test
    fun `the deployment guide lists every metric the server reports, with its labels, and no other`() {
        val reported = reported()
        reference(REFERENCE).assert().containsExactlyInAnyOrderElementsOf(reported)
        reference(REFERENCE_ZH).assert().containsExactlyInAnyOrderElementsOf(reported)
    }

    @Test
    fun `every metric the server names is one reported here`() {
        val reported = reported().map { it.substringBefore('{') }
        named().filterNot { it in reported }.assert().isEmpty()
    }

    /**
     * Every meter of [CoSkyMetrics], once each of its calls has been made: `name{label,…}`.
     */
    private fun reported(): Set<String> {
        val registry = SimpleMeterRegistry()
        CoSkyMetrics(registry).apply {
            watchStarted()
            eventsSent(1)
            gone(GoneReason.GONE_REASON_SLOW)
            revisionRegression()
            cacheApplied(NAMESPACE, 1, 1)
            antiEntropyDrift(NAMESPACE)
            reaperFailure(NAMESPACE)
            leases(NAMESPACE, 1)
            leaseExpired(NAMESPACE)
            leaseProtection(NAMESPACE, true)
            authEnabled(true)
            authFailure(AuthFailure.MISSING)
            applyTimer.record(Duration.ofMillis(1))
        }
        // A registry without histograms of its own, as this one, adds gauges such as
        // cosky_store_apply_seconds.histogram for the buckets, which Prometheus reports as part of the timer instead.
        return registry.meters.filterNot { '.' in it.id.name }.mapTo(sortedSetOf()) { meter ->
            val labels = meter.id.tags.map { it.key }.sorted()
            if (labels.isEmpty()) meter.id.name else "${meter.id.name}{${labels.joinToString(",")}}"
        }
    }

    /**
     * The metric names the server's code spells out, wherever it registers them.
     */
    private fun named(): Set<String> = Files.walk(SOURCES).use { files ->
        files.filter { it.toString().endsWith(".kt") }.toList()
    }.flatMapTo(sortedSetOf()) { file ->
        LITERAL.findAll(file.readText()).map { it.groupValues[1] }.toList()
    }

    /**
     * The first column of the guide's table of metrics, as `name{label,…}`.
     */
    private fun reference(guide: Path): Set<String> = guide.readText().lineSequence()
        .filter { it.startsWith("| `cosky_") }
        .flatMap { row -> CODE.findAll(row.split('|')[1]).map { it.groupValues[1] } }
        .toSortedSet()

    private companion object {
        const val NAMESPACE = "prod"
        val SOURCES: Path = Path.of("src/main/kotlin")
        val REFERENCE: Path = Path.of("../wiki/guide/deployment.md")
        val REFERENCE_ZH: Path = Path.of("../wiki/zh/guide/deployment.md")
        val LITERAL = Regex("\"(cosky_[a-z_]+)\"")
        val CODE = Regex("`(cosky_[^`]+)`")
    }
}
