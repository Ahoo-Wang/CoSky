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

/**
 * Where each 6.x namespace goes. 6.x allows any name without `:`, and advises a hash tag such as `cosky-{default}`,
 * while an 8.0 name is a DNS label.
 */
internal class NamespaceMapping private constructor(private val targets: Map<String, String?>) {
    /**
     * Every namespace to read, in a stable order.
     */
    val sources: List<String>
        get() = targets.keys.sorted()

    /**
     * @return null when the namespace stays behind.
     */
    fun targetOf(source: String): String? = targets[source]

    /**
     * The namespace of a 6.x grant, which may no longer exist.
     */
    fun grantTargetOf(source: String): String? =
        if (source == NamingRules.ANY_NAMESPACE) source else targets[source]

    companion object {
        /** Holds the users and roles of 6.x, which do not migrate. */
        const val SYSTEM = "cosky-{system}"
        const val DEFAULT = "cosky-{default}"
        private val HASH_TAG = Regex("^(?:cosky-)?\\{([^{}]+)}$")

        /**
         * Decides everything before anything is written, so that a name it cannot place stops the run at its start
         * rather than halfway through.
         *
         * @param found the namespaces 6.x lists. A 6.x client can use a namespace nobody listed, so [overrides] may
         * name others, and [DEFAULT] is always read.
         * @throws UsageException when a name is illegal, or two namespaces would land on one.
         */
        fun of(found: Set<String>, overrides: Map<String, String?>): NamespaceMapping {
            val targets = (found + overrides.keys + DEFAULT).associateWith { source ->
                if (source in overrides) overrides[source] else nameFor(source)
            }
            val illegal = targets.filterValues { it != null && !NamingRules.isNamespace(it) }
            if (illegal.isNotEmpty()) {
                throw UsageException(
                    "These namespaces have no legal name in 8.0, which must look like ${NamingRules.NAMESPACE}: " +
                        illegal.entries.joinToString { (source, target) -> "$source (as $target)" } +
                        ". Name each with --namespace <6.x>=<8.0>, or leave it out with --namespace <6.x>=.",
                )
            }
            val merged = targets.entries.filter { it.value != null }.groupBy({ it.value }, { it.key })
                .filterValues { it.size > 1 }
            if (merged.isNotEmpty()) {
                throw UsageException(
                    "These namespaces would land on one: " +
                        merged.entries.joinToString { (target, sources) -> "${sources.sorted()} on $target" } +
                        ". Name them apart with --namespace <6.x>=<8.0>.",
                )
            }
            return NamespaceMapping(targets)
        }

        private fun nameFor(source: String): String? = when (source) {
            SYSTEM -> null
            else -> HASH_TAG.matchEntire(source)?.groupValues?.get(1) ?: source
        }
    }
}
