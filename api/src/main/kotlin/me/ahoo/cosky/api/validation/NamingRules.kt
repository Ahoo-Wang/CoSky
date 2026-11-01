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
package me.ahoo.cosky.api.validation

/**
 * Naming rules for resource names (design §4.2).
 *
 * Each rule caps the length separately from the pattern so that violations can say which of the two failed.
 */
object NamingRules {
    /** Namespace holding the global kinds; reserved, it never matches [NAMESPACE]. */
    const val SYSTEM_NAMESPACE = "_system"

    /** Wildcard accepted in `Rule.namespaces` and `RoleBindingSpec.namespaces`. */
    const val ANY_NAMESPACE = "*"

    const val MAX_NAMESPACE_LENGTH = 63
    const val MAX_SERVICE_LENGTH = 63
    const val MAX_CONFIG_NAME_LENGTH = 253
    const val MAX_INSTANCE_NAME_LENGTH = 253

    /**
     * Roles every server has (design §6.3). A Role cannot take these names, since it would be ignored in their favour.
     */
    val BUILT_IN_ROLES: Set<String> = setOf("admin", "viewer", "client")
    const val MAX_GLOBAL_NAME_LENGTH = 63

    /** The longest name rule plus the trailing `*`. */
    const val MAX_RULE_NAME_LENGTH = 254

    /** DNS label: no `{ } :`, so a namespace is safe inside a Redis hash tag. */
    val NAMESPACE = Regex("^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$")

    /** Path-style names are allowed; see [isConfigName] for the segment rule. */
    val CONFIG_NAME = Regex("^[A-Za-z0-9._/-]{1,253}$")

    /** DNS style. */
    val SERVICE = Regex("^[a-z0-9]([-a-z0-9.]{0,61}[a-z0-9])?$")

    /** See [isInstanceName]: `.` and `..` are excluded as well. */
    val INSTANCE_NAME = Regex("^[A-Za-z0-9._-]{1,253}$")

    /** Label and Instance metadata keys. */
    val ENTRY_KEY = Regex("^[A-Za-z0-9]([-A-Za-z0-9_.]*[A-Za-z0-9])?$")

    /** `Rule.names`: an exact value, or a prefix when it ends in `*`. For everything, leave `names` empty. */
    val RULE_NAME = Regex("^[A-Za-z0-9._/-]+\\*?$")

    /**
     * ServiceAccount, Role and RoleBinding names. §4.2 does not list them; they follow the DNS-style [SERVICE] rule.
     */
    val GLOBAL_NAME = SERVICE

    private val INSTANCE_NAME_ILLEGAL = Regex("[^A-Za-z0-9._-]")
    private val DOT_SEGMENTS = setOf(".", "..")

    fun isNamespace(value: String): Boolean = NAMESPACE.matches(value)

    fun isService(value: String): Boolean = SERVICE.matches(value)

    /**
     * [CONFIG_NAME], and no segment between `/` is empty, `.` or `..`: no leading or trailing `/`, no `//`. A name is
     * one REST path segment, so clients encode `/` as `%2F` (§4.5).
     */
    fun isConfigName(value: String): Boolean =
        CONFIG_NAME.matches(value) && value.split('/').none { it in DOT_SEGMENTS || it.isEmpty() }

    /** [INSTANCE_NAME], except `.` and `..`, which REST paths would collapse. */
    fun isInstanceName(value: String): Boolean = INSTANCE_NAME.matches(value) && value !in DOT_SEGMENTS

    /**
     * Default Instance name `<service>-<host>-<port>`, with characters the Instance name rule rejects replaced by `-`
     * and the result cut to [MAX_INSTANCE_NAME_LENGTH].
     */
    fun defaultInstanceName(service: String, host: String, port: Int): String =
        "$service-$host-$port".replace(INSTANCE_NAME_ILLEGAL, "-").take(MAX_INSTANCE_NAME_LENGTH)
}
