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
 * Endpoint host rule (design §4.2): an RFC 1123 hostname, an IPv4 literal or an IPv6 literal without brackets.
 *
 * Pure syntax checks; nothing is resolved.
 */
object HostRules {
    const val MAX_HOSTNAME_LENGTH = 253

    private val LABEL = Regex("^[A-Za-z0-9]([-A-Za-z0-9]{0,61}[A-Za-z0-9])?$")
    private val DIGITS = Regex("^[0-9]+$")

    /** 1–3 digits without a leading zero, or a single zero. */
    private val OCTET = Regex("^(0|[1-9][0-9]{0,2})$")
    private val HEX_GROUP = Regex("^[0-9A-Fa-f]{1,4}$")
    private const val IPV4_OCTETS = 4
    private const val IPV6_GROUPS = 8
    private const val MAX_OCTET = 255

    fun isHost(value: String): Boolean = isIpv4(value) || isIpv6(value) || isHostname(value)

    /**
     * Dot-separated labels of 1–63 letters, digits and inner hyphens, at most 253 characters, no trailing dot. An
     * all-numeric last label is rejected so that malformed IPv4 literals such as `256.1.1.1` do not pass as names.
     */
    fun isHostname(value: String): Boolean {
        if (value.length > MAX_HOSTNAME_LENGTH) {
            return false
        }
        val labels = value.split('.')
        return labels.all { LABEL.matches(it) } && !DIGITS.matches(labels.last())
    }

    /**
     * Dotted quad of decimal octets 0–255 without leading zeros (which some parsers read as octal).
     */
    fun isIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == IPV4_OCTETS && octets.all(::isOctet)
    }

    /**
     * RFC 4291 text form: eight groups of 1–4 hex digits, at most one `::`, optionally ending in an IPv4 literal.
     * Brackets and zone ids (`%eth0`) are rejected.
     */
    fun isIpv6(value: String): Boolean {
        if (!value.contains(':')) {
            return false
        }
        val halves = value.split("::")
        if (halves.size > 2) {
            return false
        }
        val groups = halves.map { half -> if (half.isEmpty()) emptyList() else half.split(':') }
        val all = groups.flatten()
        // Only the very last group may be an IPv4 literal; an empty trailing half (`a::`) has none.
        val last = groups.last().lastOrNull()
        val embeddedIpv4 = last != null && last.contains('.')
        if (embeddedIpv4 && !isIpv4(last)) {
            return false
        }
        val hexGroups = if (embeddedIpv4) all.dropLast(1) else all
        if (!hexGroups.all { HEX_GROUP.matches(it) }) {
            return false
        }
        val width = hexGroups.size + if (embeddedIpv4) 2 else 0
        return if (halves.size == 2) width < IPV6_GROUPS else width == IPV6_GROUPS
    }

    private fun isOctet(octet: String): Boolean = OCTET.matches(octet) && octet.toInt() <= MAX_OCTET
}
