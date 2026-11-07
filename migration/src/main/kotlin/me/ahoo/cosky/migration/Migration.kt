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

package me.ahoo.cosky.migration

import me.ahoo.cosky.api.configFormatOf
import me.ahoo.cosky.api.v1.Resource
import me.ahoo.cosky.api.v1.configSpec
import me.ahoo.cosky.api.v1.objectMeta
import me.ahoo.cosky.api.v1.putRequest
import me.ahoo.cosky.api.v1.resource
import me.ahoo.cosky.api.validation.NamingRules
import me.ahoo.cosky.api.validation.Validator

/**
 * One cutover (design D33). It fills [report] as it goes, so that a run that stops still says what it did.
 */
internal class Migration(
    private val source: Source,
    private val target: RestTarget,
    private val options: Options,
    private val report: MigrationReport,
) {
    fun run() {
        val mapping = NamespaceMapping.of(source.namespaces(), options.namespaces)
        report.identity = Identity(source.roles(), source.users(), mapping)
        mapping.sources.forEach { namespace ->
            val destination = mapping.targetOf(namespace)
            if (destination == null) {
                report.namespaces += NamespaceResult(namespace, null, source.configNames(namespace).size)
            } else {
                migrate(namespace, destination)
            }
        }
    }

    private fun migrate(from: String, to: String) {
        val result = NamespaceResult(from, to)
        report.namespaces += result
        val services = source.services(from)
        result.services = services.size
        result.illegalServices = services.filterNot(NamingRules::isService)
        result.fixedInstances = services.flatMap { source.fixedInstances(from, it) }
        // A namespace without Configs still has to exist, for its applications to register in.
        if (!target.namespaceExists(to) && !options.dryRun) {
            target.createNamespace(to, "Imported from 6.x namespace $from")
        }
        source.configNames(from).forEach { name ->
            migrateConfig(from, to, name)?.let { result.configs += it }
        }
    }

    private fun migrateConfig(from: String, to: String, name: String): ConfigResult? {
        // 8.0 makes no revision of a content it already has, so neither does a version that repeats the one before.
        val versions = source.versions(from, name).fold(listOf<String>()) { kept, content ->
            if (kept.lastOrNull() == content) kept else kept + content
        }.map { configOf(to, name, it) }
        val current = versions.lastOrNull() ?: return null
        problemOf(current)?.let { return ConfigResult(name, ConfigStatus.INVALID, it) }
        return try {
            val existing = target.config(to, name)
            when (val reached = existing?.let { reachedOf(to, name, versions, it) }) {
                versions.lastIndex -> ConfigResult(name, ConfigStatus.UNCHANGED)
                NOT_FOUND -> ConfigResult(name, ConfigStatus.CONFLICT, NOT_FROM_SIX)
                AMBIGUOUS -> ConfigResult(name, ConfigStatus.CONFLICT, AMBIGUOUS_HISTORY)
                else -> write(to, name, versions.drop((reached ?: NOT_FOUND) + 1), existing)
            }
        } catch (@Suppress("SwallowedException") e: RevisionConflictException) {
            ConfigResult(name, ConfigStatus.CONFLICT, CHANGED_DURING_RUN)
        } catch (e: TargetRejectedException) {
            ConfigResult(name, ConfigStatus.REJECTED, e.message.orEmpty())
        }
    }

    /**
     * How far along the versions of 6.x the Config on 8.0 already is, which is what an interrupted run left behind.
     * Contents repeat when a Config went back to an earlier value: then the history on 8.0 tells where it stands, and
     * what it cannot tell is reported, since a wrong guess would leave versions out for good. Without the history only
     * the current version counts, wherever its content appeared before.
     */
    private fun reachedOf(to: String, name: String, versions: List<Resource>, found: Resource): Int {
        val candidates = versions.indices.filter { versions[it].config.content == found.config.content }
        if (!options.history || candidates.size <= 1) {
            return candidates.lastOrNull() ?: NOT_FOUND
        }
        val written = target.history(to, name)
        // The oldest version kept is the first one ever, unless 8.0 has dropped older ones meanwhile.
        val complete = written.firstOrNull()?.meta?.let { it.revision == it.createRevision } ?: false
        val contents = written.map { it.config.content }
        val fitting = candidates.filter { end ->
            val before = versions.take(end + 1).map { it.config.content }
            if (complete) before == contents else before.takeLast(contents.size) == contents
        }
        return fitting.singleOrNull() ?: if (fitting.isEmpty()) NOT_FOUND else AMBIGUOUS
    }

    /**
     * @param pending the versions it does not have yet, of which only the last one is the current one.
     */
    private fun write(to: String, name: String, pending: List<Resource>, existing: Resource?): ConfigResult {
        val wanted = if (options.history) pending else pending.takeLast(1)
        // A version too large for 8.0 is only worth stopping for if it is the current one, checked by now.
        val writable = wanted.filter { problemOf(it) == null }
        var revision = existing?.meta?.revision
        if (!options.dryRun) {
            writable.forEach { revision = target.putConfig(to, name, it, revision) }
        }
        val refused = wanted.size - writable.size
        return ConfigResult(
            name,
            if (existing == null) ConfigStatus.CREATED else ConfigStatus.UPDATED,
            if (refused > 0) "$refused old versions 8.0 refuses were left out" else "",
        )
    }

    private fun problemOf(config: Resource): String? =
        Validator.DEFAULT.validate(putRequest { resource = config }).takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { it.description }

    private fun configOf(namespace: String, name: String, content: String): Resource = resource {
        meta = objectMeta {
            this.namespace = namespace
            this.name = name
        }
        config = configSpec {
            format = formatOf(name)
            this.content = content
        }
    }

    private fun formatOf(name: String) =
        if ('.' in name.substringAfterLast('/')) configFormatOf(name) else options.defaultFormat

    private companion object {
        const val NOT_FOUND = -1
        const val AMBIGUOUS = -2
        const val AMBIGUOUS_HISTORY =
            "Its versions on 8.0 fit more than one place in the history of 6.x. Delete it on 8.0 and run again."
        const val NOT_FROM_SIX =
            "Its content on 8.0 matches no version in 6.x. Keep it, or delete it on 8.0 and run again."
        const val CHANGED_DURING_RUN = "It changed on 8.0 during this run. Run again."
    }
}
