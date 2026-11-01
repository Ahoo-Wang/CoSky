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

import me.ahoo.cosky.api.v1.ConfigFormat
import java.nio.file.Path

/**
 * The run cannot start as asked.
 */
internal class UsageException(message: String) : RuntimeException(message)

/**
 * @param source the Redis URI of 6.x; comma separated seed nodes when [cluster].
 * @param namespaces 6.x namespace to 8.0 namespace; null leaves it out.
 * @param defaultFormat of a Config whose name has no extension, which 6.x left to `file-extension`, by default yaml.
 */
internal data class Options(
    val source: String,
    val cluster: Boolean = false,
    val target: String,
    val token: String? = null,
    val history: Boolean = false,
    val dryRun: Boolean = false,
    val namespaces: Map<String, String?> = emptyMap(),
    val report: Path = Path.of(DEFAULT_REPORT),
    val defaultFormat: ConfigFormat = ConfigFormat.CONFIG_FORMAT_YAML,
) {
    companion object {
        const val DEFAULT_REPORT = "cosky-migration-report.md"
        const val SOURCE_VARIABLE = "COSKY_SOURCE_URI"
        const val TOKEN_VARIABLE = "COSKY_TOKEN"

        val USAGE = """
            Imports the Configs of a CoSky 6.x Redis into a CoSky 8.0 server, and reports what to rebuild by hand.

            Usage: cosky-migrate --source <redis-uri> --target <url> [options]

              --source <redis-uri>  The 6.x Redis; comma separated seed nodes with --cluster. A URI holding a password
                                    belongs in ${'$'}$SOURCE_VARIABLE, where ps cannot see it.
              --cluster             The 6.x Redis is a Redis Cluster.
              --target <url>        The REST address of the 8.0 server. ${'$'}$TOKEN_VARIABLE holds its token, if any.
              --history             Also import the versions 6.x still holds, oldest first.
              --dry-run             Read both sides and report, writing nothing to 8.0.
              --namespace <a>=<b>   Import 6.x namespace a into 8.0 namespace b; an empty b leaves a out. Repeatable.
              --report <path>       Where to write the report; $DEFAULT_REPORT by default.
              --default-format <f>  The format of a Config whose name has no extension: yaml, which 6.x assumed unless
                                    told otherwise by file-extension, properties, json or text.
              --help                Show this.

            Stop writing to 6.x first. A run that was interrupted continues when started again.
            Exit code: 0 when everything arrived, 1 when some Configs need attention, 2 when it could not run.
        """.trimIndent()

        /**
         * @return null when the user asked for help.
         */
        fun parse(args: List<String>, environment: Map<String, String>): Options? {
            if ("--help" in args) {
                return null
            }
            val flags = mutableSetOf<String>()
            val values = mutableMapOf<String, String>()
            val namespaces = linkedMapOf<String, String?>()
            val arguments = args.iterator()
            while (arguments.hasNext()) {
                when (val argument = arguments.next()) {
                    "--cluster", "--history", "--dry-run" -> flags += argument
                    "--source", "--target", "--report", "--default-format" ->
                        values[argument] = arguments.valueOf(argument)

                    "--namespace" -> namespaces += namespaceMapping(arguments.valueOf(argument))
                    else -> usage("Unknown argument $argument.")
                }
            }
            return Options(
                source = values["--source"] ?: environment[SOURCE_VARIABLE]
                    ?: usage("--source or \$$SOURCE_VARIABLE is required."),
                cluster = "--cluster" in flags,
                target = (values["--target"] ?: usage("--target is required.")).trimEnd('/'),
                token = environment[TOKEN_VARIABLE],
                history = "--history" in flags,
                dryRun = "--dry-run" in flags,
                namespaces = namespaces,
                report = Path.of(values["--report"] ?: DEFAULT_REPORT),
                defaultFormat = formatOf(values["--default-format"] ?: "yaml"),
            )
        }

        private fun formatOf(value: String): ConfigFormat = when (value) {
            "yaml" -> ConfigFormat.CONFIG_FORMAT_YAML
            "properties" -> ConfigFormat.CONFIG_FORMAT_PROPERTIES
            "json" -> ConfigFormat.CONFIG_FORMAT_JSON
            "text" -> ConfigFormat.CONFIG_FORMAT_TEXT
            else -> usage("--default-format is yaml, properties, json or text, not $value.")
        }

        private fun usage(problem: String): Nothing = throw UsageException(problem)

        private fun Iterator<String>.valueOf(flag: String): String {
            return if (hasNext()) next() else usage("$flag needs a value.")
        }

        private fun namespaceMapping(value: String): Pair<String, String?> {
            // 6.x allows = in a name, 8.0 does not, so the last one separates them.
            val from = value.substringBeforeLast('=', "")
            if (from.isEmpty()) {
                usage("--namespace looks like <6.x namespace>=<8.0 namespace>, not $value.")
            }
            return from to value.substringAfterLast('=').ifEmpty { null }
        }
    }
}
