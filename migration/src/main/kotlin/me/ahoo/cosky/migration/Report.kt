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

import me.ahoo.cosky.api.validation.NamingRules

internal enum class ConfigStatus(val needsAttention: Boolean = false) {
    CREATED,
    UPDATED,
    UNCHANGED,
    CONFLICT(true),
    INVALID(true),
    REJECTED(true),
}

internal data class ConfigResult(val name: String, val status: ConfigStatus, val detail: String = "")

/**
 * @param target null when the namespace was left out.
 */
internal class NamespaceResult(val source: String, val target: String?, val leftOutConfigs: Int = 0) {
    val configs = mutableListOf<ConfigResult>()
    var services = 0
    var illegalServices = emptyList<String>()
    var fixedInstances = emptyList<FixedInstance>()

    fun count(status: ConfigStatus): Int = configs.count { it.status == status }

    val attention: List<ConfigResult>
        get() = configs.filter { it.status.needsAttention }
}

internal class Identity(val roles: List<SourceRole>, val users: List<SourceUser>, val mapping: NamespaceMapping)

/**
 * What the run did, and what it cannot do: the people and the instances that have to be rebuilt by hand.
 * It holds names, addresses and permissions, never the content of a Config, a password or a metadata value.
 */
internal class MigrationReport(
    private val source: String,
    private val target: String,
    private val history: Boolean,
    private val dryRun: Boolean,
) {
    val namespaces = mutableListOf<NamespaceResult>()
    var identity: Identity? = null

    /** Why the run stopped early. */
    var stopped: String? = null

    val needsAttention: Boolean
        get() = namespaces.any { it.attention.isNotEmpty() }

    /**
     * One line per namespace, for the terminal.
     */
    fun summary(): List<String> = namespaces.map { namespace ->
        if (namespace.target == null) {
            "${namespace.source}: left out, ${namespace.leftOutConfigs} Configs"
        } else {
            "${namespace.source} -> ${namespace.target}: ${namespace.count(ConfigStatus.CREATED)} created, " +
                "${namespace.count(ConfigStatus.UPDATED)} updated, ${namespace.count(ConfigStatus.UNCHANGED)} " +
                "unchanged, ${namespace.attention.size} need attention, " +
                "${namespace.fixedInstances.size} fixed instances to rebuild"
        }
    }

    fun render(): String = buildString {
        appendLine("# CoSky 6.x to 8.0 migration report")
        appendLine()
        appendLine("- Source: ${code(source)}")
        appendLine("- Target: ${code(target)}")
        appendLine("- Imported: " + if (history) "current versions and the history 6.x holds" else "current versions")
        if (dryRun) {
            appendLine("- **Dry run: nothing was written to 8.0. This is what a real run would do.**")
        }
        stopped?.let {
            appendLine()
            appendLine("**The run stopped early: $it** This report covers what it reached. Run it again to continue.")
        }
        appendLine()
        configs()
        instances()
        identity?.let { usersAndRoles(it) }
    }

    private fun StringBuilder.configs() {
        appendLine("## Configs")
        appendLine()
        table(
            listOf("6.x namespace", "8.0 namespace", "Created", "Updated", "Unchanged", "Need attention"),
            namespaces.map { namespace ->
                if (namespace.target == null) {
                    listOf(code(namespace.source), "left out, ${namespace.leftOutConfigs} Configs", "", "", "", "")
                } else {
                    listOf(
                        code(namespace.source),
                        code(namespace.target),
                        namespace.count(ConfigStatus.CREATED).toString(),
                        namespace.count(ConfigStatus.UPDATED).toString(),
                        namespace.count(ConfigStatus.UNCHANGED).toString(),
                        namespace.attention.size.toString(),
                    )
                }
            },
        )
        val attention = namespaces.flatMap { namespace -> namespace.attention.map { namespace.source to it } }
        if (attention.isNotEmpty()) {
            appendLine("### Configs that need attention")
            appendLine()
            table(
                listOf("6.x namespace", "Config", "Status", "What happened"),
                attention.map { (namespace, config) ->
                    listOf(code(namespace), code(config.name), config.status.name, config.detail)
                },
            )
        }
        val partial = namespaces.flatMap { namespace ->
            namespace.configs.filter { !it.status.needsAttention && it.detail.isNotEmpty() }
                .map { namespace.source to it }
        }
        if (partial.isNotEmpty()) {
            appendLine("### History that did not arrive")
            appendLine()
            table(
                listOf("6.x namespace", "Config", "What happened"),
                partial.map { (namespace, config) -> listOf(code(namespace), code(config.name), config.detail) },
            )
        }
    }

    private fun StringBuilder.instances() {
        appendLine("## Instances")
        appendLine()
        appendLine("Instances do not migrate: an application registers itself when it moves to 8.0.")
        appendLine()
        val migrated = namespaces.filter { it.target != null }
        table(
            listOf("8.0 namespace", "Services", "Fixed instances"),
            migrated.map {
                listOf(code(it.target.orEmpty()), it.services.toString(), it.fixedInstances.size.toString())
            },
        )
        val fixed = migrated.flatMap { namespace -> namespace.fixedInstances.map { namespace.target.orEmpty() to it } }
        if (fixed.isNotEmpty()) {
            appendLine("### Fixed instances to rebuild")
            appendLine()
            appendLine("Nothing renews these, so nothing registers them again. Create each one on 8.0 without a lease:")
            appendLine("`PUT /v1/namespaces/{namespace}/instances/{name}`.")
            appendLine()
            table(
                listOf("8.0 namespace", "Service", "6.x instance", "Endpoint", "Weight", "Metadata keys"),
                fixed.map { (namespace, instance) ->
                    listOf(
                        code(namespace),
                        code(instance.service),
                        code(instance.id),
                        code(instance.endpoint),
                        instance.weight,
                        instance.metadataKeys.joinToString(", "),
                    )
                },
            )
        }
        val illegal = migrated.flatMap { namespace -> namespace.illegalServices.map { namespace.source to it } }
        if (illegal.isNotEmpty()) {
            appendLine("### Services that must be renamed")
            appendLine()
            appendLine("8.0 service names must look like ${code(NamingRules.SERVICE.pattern)}. These applications")
            appendLine("cannot register until they change their name, such as `spring.application.name`.")
            appendLine()
            table(
                listOf("6.x namespace", "Service"),
                illegal.map { (namespace, service) -> listOf(code(namespace), code(service)) },
            )
        }
    }

    private fun StringBuilder.usersAndRoles(identity: Identity) {
        appendLine("## Users and roles")
        appendLine()
        appendLine("These do not migrate. 8.0 has no passwords: a person signs in through OIDC and is a `USER`")
        appendLine("subject, a program holds the token of a ServiceAccount. Rebuild each Role, then a RoleBinding for")
        appendLine("its subjects.")
        appendLine()
        appendLine("### Roles")
        appendLine()
        table(
            listOf("6.x role", "Description", "6.x grant", "8.0 rule for Config and Instance", "Note"),
            identity.roles.flatMap { role -> roleRows(role, identity.mapping) },
        )
        appendLine("### Users")
        appendLine()
        val known = identity.roles.map { it.name }.toSet()
        table(
            listOf("6.x user", "6.x roles", "Note"),
            identity.users.map { user ->
                val missing = user.roles - known
                listOf(
                    code(user.name),
                    user.roles.sorted().joinToString(", ", transform = ::code),
                    when {
                        user.roles.isEmpty() -> "No roles. The root user of 6.x is replaced by the bootstrap token."
                        missing.isNotEmpty() -> "Granted nothing by ${missing.sorted().joinToString()}: no such role."
                        else -> ""
                    },
                )
            },
        )
    }

    private fun roleRows(role: SourceRole, mapping: NamespaceMapping): List<List<String>> {
        val note = if (NamingRules.GLOBAL_NAME.matches(role.name)) "" else "Needs a new name: 8.0 refuses this one."
        if (role.grants.isEmpty()) {
            return listOf(listOf(code(role.name), role.description, "none", "", note))
        }
        return role.grants.entries.sortedBy { it.key }.map { (namespace, action) ->
            val destination = mapping.grantTargetOf(namespace)
            listOf(
                code(role.name),
                role.description,
                "${code(namespace)}: $action",
                destination?.let { "namespaces ${code(it)}, verbs ${verbsOf(action)}" }.orEmpty(),
                if (destination == null) "This namespace did not migrate. $note".trim() else note,
            )
        }
    }

    private fun verbsOf(action: String): String = when (action) {
        "r" -> "GET, WATCH"
        "w" -> "PUT, DELETE"
        else -> "GET, WATCH, PUT, DELETE"
    }

    private fun StringBuilder.table(header: List<String>, rows: List<List<String>>) {
        appendLine(row(header))
        appendLine(row(header.map { "---" }))
        rows.forEach { appendLine(row(it)) }
        appendLine()
    }

    private fun row(cells: List<String>): String =
        cells.joinToString(" | ", "| ", " |") { it.replace("|", "\\|").replace('\n', ' ') }

    private fun code(value: String): String = "`$value`"
}
